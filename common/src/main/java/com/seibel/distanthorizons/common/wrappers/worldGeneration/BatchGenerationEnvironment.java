/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2021  Tom Lee (TomTheFurry)
 *    Copyright (C) 2020-2023 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.common.wrappers.worldGeneration;

import com.google.common.collect.ImmutableMap;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.mimicObject.*;
import com.seibel.distanthorizons.core.dataObjects.transformers.FullDataToRenderDataTransformer;
import com.seibel.distanthorizons.core.generation.DhLightingEngine;
import com.seibel.distanthorizons.core.level.IDhServerLevel;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.logging.ConfigBasedLogger;
import com.seibel.distanthorizons.core.logging.ConfigBasedSpamLogger;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.util.objects.EventTimer;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.gridList.ArrayGridList;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.ChunkLightStorage;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.worldGeneration.AbstractBatchGenerationEnvironmentWrapper;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.seibel.distanthorizons.common.wrappers.DependencySetupDoneCheck;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.step.StepBiomes;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.step.StepFeatures;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.step.StepNoise;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.step.StepStructureReference;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.step.StepStructureStart;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.step.StepSurface;

#if MC_VER >= MC_1_19_4
import net.minecraft.core.registries.Registries;
#else
import net.minecraft.core.Registry;
#endif

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.nbt.CompoundTag;
import org.apache.logging.log4j.LogManager;

#if MC_VER <= MC_1_20_4
import net.minecraft.world.level.chunk.ChunkStatus;
#else
import net.minecraft.world.level.chunk.status.ChunkStatus;
#endif

/*
Total:                   3.135214124s
=====================================
Empty Chunks:            0.000558328s
StructureStart Step:     0.025177207s
StructureReference Step: 0.00189559s
Biome Step:              0.13789155s
Noise Step:              1.570347555s
Surface Step:            0.741238194s
Carver Step:             0.000009923s
Feature Step:            0.389072425s
Lod Generation:          0.269023348s
*/
public final class BatchGenerationEnvironment extends AbstractBatchGenerationEnvironmentWrapper
{
	public static final ConfigBasedSpamLogger PREF_LOGGER =
			new ConfigBasedSpamLogger(LogManager.getLogger("LodWorldGen"),
					() -> Config.Client.Advanced.Logging.logWorldGenPerformance.get(), 1);
	public static final ConfigBasedLogger EVENT_LOGGER =
			new ConfigBasedLogger(LogManager.getLogger("LodWorldGen"),
					() -> Config.Client.Advanced.Logging.logWorldGenEvent.get());
	public static final ConfigBasedLogger LOAD_LOGGER =
			new ConfigBasedLogger(LogManager.getLogger("LodWorldGen"),
					() -> Config.Client.Advanced.Logging.logWorldGenLoadEvent.get());
	
	public static class PerfCalculator
	{
		private static final String[] TIME_NAMES = {
				"total",
				"setup",
				"structStart",
				"structRef",
				"biome",
				"noise",
				"surface",
				"carver",
				"feature",
				"light",
				"cleanup",
				//"lodCreation" (No longer used)
		};
		
		public static final int SIZE = 50;
		ArrayList<Rolling> times = new ArrayList<>();
		
		public PerfCalculator()
		{
			for (int i = 0; i < 11; i++)
			{
				times.add(new Rolling(SIZE));
			}
		}
		
		public void recordEvent(EventTimer event)
		{
			for (EventTimer.Event e : event.events)
			{
				String name = e.name;
				int index = Arrays.asList(TIME_NAMES).indexOf(name);
				if (index == -1) continue;
				times.get(index).add(e.timeNs);
			}
			times.get(0).add(event.getTotalTimeNs());
		}
		
		public String toString()
		{
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < times.size(); i++)
			{
				if (times.get(i).getAverage() == 0) continue;
				sb.append(TIME_NAMES[i]).append(": ").append(times.get(i).getAverage()).append("\n");
			}
			return sb.toString();
		}
		
	}
	
	private final IDhServerLevel serverlevel;
	
	//=================Generation Step===================
	
	public final LinkedBlockingQueue<GenerationEvent> generationEventList = new LinkedBlockingQueue<>();
	public final GlobalParameters params;
	public final StepStructureStart stepStructureStart = new StepStructureStart(this);
	public final StepStructureReference stepStructureReference = new StepStructureReference(this);
	public final StepBiomes stepBiomes = new StepBiomes(this);
	public final StepNoise stepNoise = new StepNoise(this);
	public final StepSurface stepSurface = new StepSurface(this);
	public final StepFeatures stepFeatures = new StepFeatures(this);
	public boolean unsafeThreadingRecorded = false;
	public static final long EXCEPTION_TIMER_RESET_TIME = TimeUnit.NANOSECONDS.convert(1, TimeUnit.SECONDS);
	public static final int EXCEPTION_COUNTER_TRIGGER = 20;
	public static final int RANGE_TO_RANGE_EMPTY_EXTENSION = 1;
	public int unknownExceptionCount = 0;
	public long lastExceptionTriggerTime = 0;
	
	private AtomicReference<RegionFileStorageExternalCache> regionFileStorageCacheRef = new AtomicReference<>();
	
	public RegionFileStorageExternalCache getOrCreateRegionFileCache(RegionFileStorage storage)
	{
		RegionFileStorageExternalCache cache = regionFileStorageCacheRef.get();
		if (cache == null)
		{
			cache = new RegionFileStorageExternalCache(storage);
			if (!regionFileStorageCacheRef.compareAndSet(null, cache))
			{
				cache = regionFileStorageCacheRef.get();
			}
		}
		return cache;
	}
	
	public static ThreadLocal<Boolean> isDistantGeneratorThread = new ThreadLocal<>();
	public static ThreadLocal<Object> onDistantGenerationMixinData = new ThreadLocal<>();
	public static boolean isCurrentThreadDistantGeneratorThread() { return (isDistantGeneratorThread.get() != null); }
	public static void putDistantGenerationMixinData(Object data)
	{
		LodUtil.assertTrue(isCurrentThreadDistantGeneratorThread());
		onDistantGenerationMixinData.set(data);
	}
	public static Object getDistantGenerationMixinData()
	{
		LodUtil.assertTrue(isCurrentThreadDistantGeneratorThread());
		return onDistantGenerationMixinData.get();
	}
	
	public static void clearDistantGenerationMixinData()
	{
		LodUtil.assertTrue(isCurrentThreadDistantGeneratorThread());
		onDistantGenerationMixinData.remove();
	}
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public static final ImmutableMap<EDhApiWorldGenerationStep, Integer> WORLD_GEN_CHUNK_BORDER_NEEDED_BY_GEN_STEP;
	public static final int MAX_WORLD_GEN_CHUNK_BORDER_NEEDED;
	
	static
	{
		DependencySetupDoneCheck.getIsCurrentThreadDistantGeneratorThread = BatchGenerationEnvironment::isCurrentThreadDistantGeneratorThread;
		
		boolean isTerraFirmaCraft = false;
		try
		{
			Class.forName("net.dries007.tfc.world.TFCChunkGenerator");
			isTerraFirmaCraft = true;
		}
		catch (ClassNotFoundException e)
		{
			//Ignore
		}
		EVENT_LOGGER.info("DH TerraFirmaCraft detection: " + isTerraFirmaCraft);
		ImmutableMap.Builder<EDhApiWorldGenerationStep, Integer> builder = ImmutableMap.builder();
		builder.put(EDhApiWorldGenerationStep.EMPTY, 1);
		builder.put(EDhApiWorldGenerationStep.STRUCTURE_START, 0);
		builder.put(EDhApiWorldGenerationStep.STRUCTURE_REFERENCE, 0);
		builder.put(EDhApiWorldGenerationStep.BIOMES, isTerraFirmaCraft ? 1 : 0);
		builder.put(EDhApiWorldGenerationStep.NOISE, isTerraFirmaCraft ? 1 : 0);
		builder.put(EDhApiWorldGenerationStep.SURFACE, 0);
		builder.put(EDhApiWorldGenerationStep.CARVERS, 0);
		builder.put(EDhApiWorldGenerationStep.LIQUID_CARVERS, 0);
		builder.put(EDhApiWorldGenerationStep.FEATURES, 0);
		builder.put(EDhApiWorldGenerationStep.LIGHT, 0);
		WORLD_GEN_CHUNK_BORDER_NEEDED_BY_GEN_STEP = builder.build();
		
		// TODO this is a test to see if the additional boarder is actually necessary or not.
		//  If world generators end up having infinite loops or other unexplained issues,
		//  this should be set back to the commented out logic below
		MAX_WORLD_GEN_CHUNK_BORDER_NEEDED = 0;
		//MAX_WORLD_GEN_CHUNK_BORDER_NEEDED = WORLD_GEN_CHUNK_BORDER_NEEDED_BY_GEN_STEP.values().stream().mapToInt(Integer::intValue).max().getAsInt();
	}
	
	public BatchGenerationEnvironment(IDhServerLevel serverlevel)
	{
		super(serverlevel);
		this.serverlevel = serverlevel;
		
		EVENT_LOGGER.info("================WORLD_GEN_STEP_INITING=============");
		
		serverlevel.getServerLevelWrapper().getDimensionType();
		
		ChunkGenerator generator = ((ServerLevelWrapper) (serverlevel.getServerLevelWrapper())).getLevel().getChunkSource().getGenerator();
		if (!(generator instanceof NoiseBasedChunkGenerator ||
				generator instanceof DebugLevelSource ||
				generator instanceof FlatLevelSource))
		{
			if (generator.getClass().toString().equals("class com.terraforged.mod.chunk.TFChunkGenerator"))
			{
				EVENT_LOGGER.info("TerraForge Chunk Generator detected: [" + generator.getClass() + "], Distant Generation will try its best to support it.");
				EVENT_LOGGER.info("If it does crash, turn Distant Generation off or set it to to [" + EDhApiDistantGeneratorMode.PRE_EXISTING_ONLY + "].");
			}
			else if (generator.getClass().toString().equals("class net.dries007.tfc.world.TFCChunkGenerator"))
			{
				EVENT_LOGGER.info("TerraFirmaCraft Chunk Generator detected: [" + generator.getClass() + "], Distant Generation will try its best to support it.");
				EVENT_LOGGER.info("If it does crash, turn Distant Generation off or set it to to [" + EDhApiDistantGeneratorMode.PRE_EXISTING_ONLY + "].");
			}
			else
			{
				EVENT_LOGGER.warn("Unknown Chunk Generator detected: [" + generator.getClass() + "], Distant Generation May Fail!");
				EVENT_LOGGER.warn("If it does crash, disable Distant Generation or set the Generation Mode to [" + EDhApiDistantGeneratorMode.PRE_EXISTING_ONLY + "].");
			}
		}
		
		this.params = new GlobalParameters(serverlevel);
	}
	
	
	
	//=================//
	// synchronization //
	//=================//
	
	public <T> T joinSync(CompletableFuture<T> future)
	{
		if (!unsafeThreadingRecorded && !future.isDone())
		{
			EVENT_LOGGER.error("Unsafe MultiThreading in Chunk Generator: ", new RuntimeException("Concurrent future"));
			EVENT_LOGGER.error("To increase stability, it is recommended to set world generation threads count to 1.");
			unsafeThreadingRecorded = true;
		}
		
		return future.join();
	}
	
	public void updateAllFutures()
	{
		if (this.unknownExceptionCount > 0)
		{
			if (System.nanoTime() - this.lastExceptionTriggerTime >= EXCEPTION_TIMER_RESET_TIME)
			{
				this.unknownExceptionCount = 0;
			}
		}
		
		
		// Update all current out standing jobs
		Iterator<GenerationEvent> iter = this.generationEventList.iterator();
		while (iter.hasNext())
		{
			GenerationEvent event = iter.next();
			if (event.future.isDone())
			{
				if (event.future.isCompletedExceptionally() && !event.future.isCancelled())
				{
					try
					{
						event.future.get(); // Should throw exception
						LodUtil.assertNotReach();
					}
					catch (Exception e)
					{
						this.unknownExceptionCount++;
						this.lastExceptionTriggerTime = System.nanoTime();
						EVENT_LOGGER.error("Batching World Generator event ["+event+"] threw an exception: "+e.getMessage(), e);
					}
				}
				
				iter.remove();
			}
			else if (event.hasTimeout(Config.Client.Advanced.WorldGenerator.worldGenerationTimeoutLengthInSeconds.get(), TimeUnit.SECONDS))
			{
				EVENT_LOGGER.warn(
						"Batching World Generator: [" + event + "] timed out and terminated after ["+Config.Client.Advanced.WorldGenerator.worldGenerationTimeoutLengthInSeconds.get()+"] seconds. " +
								"\nYour computer might be overloaded or your world gen mods might be causing world gen to take longer than expected. " +
								"\nEither increase DH's world gen timeout or reduce your computer's CPU load.");
				EVENT_LOGGER.debug("Dump PrefEvent: " + event.timer);
				try
				{
					if (!event.terminate())
					{
						EVENT_LOGGER.error("Failed to terminate the stuck generation event!");
					}
				}
				finally
				{
					iter.remove();
				}
			}
		}
		
		if (this.unknownExceptionCount > EXCEPTION_COUNTER_TRIGGER)
		{
			EVENT_LOGGER.error("Too many exceptions in Batching World Generator! Disabling the generator.");
			this.unknownExceptionCount = 0;
			Config.Client.Advanced.WorldGenerator.enableDistantGeneration.set(false);
		}
	}
	
	
	
	//==================//
	// world generation //
	//==================//
	
	public void generateLodFromList(GenerationEvent genEvent) throws InterruptedException
	{
		EVENT_LOGGER.debug("Lod Generate Event: " + genEvent.minPos);
		
		// Minecraft's generation events expect odd chunk width areas (3x3, 7x7, or 11x11),
		// but DH submits square generation events (4x4).
		// We handle this later, although that handling would need to change if the gen size ever changed.
		LodUtil.assertTrue(genEvent.size % 2 == 0, "Generation events are expected to be an evan number of chunks wide.");
		
		
		
		int borderSize = MAX_WORLD_GEN_CHUNK_BORDER_NEEDED;
		// genEvent.size - 1 converts the even width size to an odd number for MC compatability
		int refSize = (genEvent.size - 1) + (borderSize * 2);
		int refPosX = genEvent.minPos.getX() - borderSize;
		int refPosZ = genEvent.minPos.getZ() - borderSize;
		
		LightGetterAdaptor lightGetterAdaptor = new LightGetterAdaptor(this.params.level);
		DummyLightEngine dummyLightEngine = new DummyLightEngine(lightGetterAdaptor);
		
		
		
		//====================================//
		// offset and generate odd width area //
		//====================================//
		
		// reused data between each offset
		HashMap<DhChunkPos, ChunkLightStorage> chunkSkyLightingByDhPos = new HashMap<>();
		HashMap<DhChunkPos, ChunkLightStorage> chunkBlockLightingByDhPos = new HashMap<>();
		HashMap<DhChunkPos, ChunkAccess> generatedChunkByDhPos = new HashMap<>();
		HashMap<DhChunkPos, ChunkWrapper> chunkWrappersByDhPos = new HashMap<>();
		
		// offset 1 chunk in both X and Z direction so we can generate an even number of chunks wide
		// while still submitting odd numbers to MC's internal generators
		for (int xOffset = 0; xOffset < 2; xOffset++)
		{
			// final is so the offset can be used in lambdas
			final int xOffsetFinal = xOffset;
			for (int zOffset = 0; zOffset < 2; zOffset++)
			{
				final int zOffsetFinal = zOffset;
				
				
				
				//================//
				// variable setup //
				//================//
				
				int radius = refSize / 2; 
				int centerX = refPosX + radius + xOffset;
				int centerZ = refPosZ + radius + zOffset;
				
				// get/create the list of chunks we're going to generate
				ArrayGridList<ChunkAccess> regionChunks = new ArrayGridList<>(
						refSize, 
						(x, z) -> this.generateEmptyChunk(
								x + refPosX + xOffsetFinal, 
								z + refPosZ + zOffsetFinal, 
								chunkSkyLightingByDhPos, chunkBlockLightingByDhPos, generatedChunkByDhPos));
				ChunkAccess centerChunk = regionChunks.stream().filter(chunk -> chunk.getPos().x == centerX && chunk.getPos().z == centerZ).findFirst().get();
				
				genEvent.refreshTimeout();
				DhLitWorldGenRegion region = new DhLitWorldGenRegion(
						centerX, centerZ,
						centerChunk,
						this.params.level, dummyLightEngine, regionChunks,
						ChunkStatus.STRUCTURE_STARTS, radius,
						// this method shouldn't be necessary since we're passing in a pre-populated
						// list of chunks, but just in case
						(x, z) -> this.generateEmptyChunk(x, z, chunkSkyLightingByDhPos, chunkBlockLightingByDhPos, generatedChunkByDhPos)
				);
				lightGetterAdaptor.setRegion(region);
				genEvent.threadedParam.makeStructFeat(region, this.params);
				
				
				
				//=========================//
				// create chunk wrappers   //
				// and get existing chunks //
				//=========================//
				
				ArrayGridList<ChunkWrapper> chunkWrapperList = new ArrayGridList<>(regionChunks.gridSize);
				regionChunks.forEachPos((relX, relZ) ->
				{
					// ArrayGridList's use relative positions and don't have a center position
					// so we need to use the offsetFinal to select the correct position
					DhChunkPos chunkPos = new DhChunkPos(relX + xOffsetFinal, relZ + zOffsetFinal);
					ChunkAccess chunk = regionChunks.get(relX, relZ);
					
					if (chunkWrappersByDhPos.containsKey(chunkPos))
					{
						chunkWrapperList.set(relX, relZ, chunkWrappersByDhPos.get(chunkPos));
					}
					else if (chunk != null)
					{
						// wrap the chunk
						ChunkWrapper chunkWrapper = new ChunkWrapper(chunk, region, this.serverlevel.getLevelWrapper());
						chunkWrapperList.set(relX, relZ, chunkWrapper);
						
						// try setting the wrapper's lighting
						if (chunkBlockLightingByDhPos.containsKey(chunkWrapper.getChunkPos()))
						{
							chunkWrapper.setBlockLightStorage(chunkBlockLightingByDhPos.get(chunkWrapper.getChunkPos()));
							chunkWrapper.setSkyLightStorage(chunkSkyLightingByDhPos.get(chunkWrapper.getChunkPos()));
							chunkWrapper.setIsDhBlockLightCorrect(true);
							chunkWrapper.setIsDhSkyLightCorrect(true);
						}
						
						chunkWrappersByDhPos.put(chunkPos, chunkWrapper);
					}
					else //if (chunk == null)
					{
						LodUtil.assertNotReach("Programmer Error: No chunk found in grid list, position offset is likely wrong.");
					}
				});
				
				
				
				//=================//
				// generate chunks //
				//=================//
				
				this.generateDirect(genEvent, chunkWrapperList, borderSize, genEvent.targetGenerationStep, region);
				
				genEvent.timer.nextEvent("cleanup");
			}	
		}
		
		
		
		//=========================//
		// submit generated chunks //
		//=========================//
		
		for (DhChunkPos dhChunkPos : chunkWrappersByDhPos.keySet())
		{
			ChunkWrapper wrappedChunk = chunkWrappersByDhPos.get(dhChunkPos);
			ChunkAccess target = wrappedChunk.getChunk();
			if (target instanceof LevelChunk)
			{
				#if MC_VER == MC_1_16_5 || MC_VER == MC_1_17_1
				((LevelChunk) target).setLoaded(true);
				#else
				((LevelChunk) target).loaded = true;
				#endif
			}
			
			boolean isFull = ChunkWrapper.getStatus(target) == ChunkStatus.FULL || target instanceof LevelChunk;
			#if MC_VER >= MC_1_18_2
			boolean isPartial = target.isOldNoiseGeneration();
			#endif
			if (isFull)
			{
				LOAD_LOGGER.debug("Detected full existing chunk at {}", target.getPos());
				genEvent.resultConsumer.accept(wrappedChunk);
			}
			#if MC_VER >= MC_1_18_2
			else if (isPartial)
			{
				LOAD_LOGGER.debug("Detected old existing chunk at {}", target.getPos());
				genEvent.resultConsumer.accept(wrappedChunk);
			}
			#endif
			else if (ChunkWrapper.getStatus(target) == ChunkStatus.EMPTY)
			{
				genEvent.resultConsumer.accept(wrappedChunk);
			}
			else
			{
				genEvent.resultConsumer.accept(wrappedChunk);
			}
		}
		
		genEvent.timer.complete();
		genEvent.refreshTimeout();
		if (PREF_LOGGER.canMaybeLog())
		{
			genEvent.threadedParam.perf.recordEvent(genEvent.timer);
			PREF_LOGGER.debugInc("{}", genEvent.timer);
		}
	}
	private ChunkAccess generateEmptyChunk(
			int x, int z,
			HashMap<DhChunkPos, ChunkLightStorage> chunkSkyLightingByDhPos,
			HashMap<DhChunkPos, ChunkLightStorage> chunkBlockLightingByDhPos,
			HashMap<DhChunkPos, ChunkAccess> generatedChunkByDhPos)
	{
		ChunkPos chunkPos = new ChunkPos(x, z);
		DhChunkPos dhChunkPos = new DhChunkPos(x, z);
		
		if (generatedChunkByDhPos.containsKey(dhChunkPos))
		{
			return generatedChunkByDhPos.get(dhChunkPos);
		}
		
		
		ChunkAccess newChunk = null;
		try
		{
			// get the chunk
			CompoundTag chunkData = this.getChunkNbtData(chunkPos);
			newChunk = this.loadOrMakeChunk(chunkPos, chunkData);
			
			if (Config.Client.Advanced.LodBuilding.pullLightingForPregeneratedChunks.get())
			{
				// attempt to get chunk lighting
				ChunkLoader.CombinedChunkLightStorage combinedLights = ChunkLoader.readLight(newChunk, chunkData);
				if (combinedLights != null)
				{
					chunkSkyLightingByDhPos.put(dhChunkPos, combinedLights.skyLightStorage);
					chunkBlockLightingByDhPos.put(dhChunkPos, combinedLights.blockLightStorage);
				}
			}
		}
		catch (RuntimeException loadChunkError)
		{
			// Continue...
		}
		
		if (newChunk == null)
		{
			newChunk = new ProtoChunk(chunkPos, UpgradeData.EMPTY
							#if MC_VER >= MC_1_17_1 , this.params.level #endif
							#if MC_VER >= MC_1_18_2 , this.params.biomes, null #endif
			);
		}
		
		generatedChunkByDhPos.put(dhChunkPos, newChunk);
		return newChunk;
	}
	private CompoundTag getChunkNbtData(ChunkPos chunkPos)
	{
		ServerLevel level = this.params.level;
		
		CompoundTag chunkData = null;
		try
		{
			IOWorker ioWorker = level.getChunkSource().chunkMap.worker;
			
			#if MC_VER <= MC_1_18_2
			chunkData = ioWorker.load(chunkPos);
			#else
			
			// timeout should prevent locking up the thread if the ioWorker dies or has issues 
			int maxGetTimeInSec = Config.Client.Advanced.WorldGenerator.worldGenerationTimeoutLengthInSeconds.get();
			CompletableFuture<Optional<CompoundTag>> future = ioWorker.loadAsync(chunkPos);
			try
			{
				Optional<CompoundTag> data = future.get(maxGetTimeInSec, TimeUnit.SECONDS);
				if (data.isPresent())
				{
					chunkData = data.get();
				}
			}
			catch (Exception e)
			{
				LOAD_LOGGER.warn("Unable to get chunk at pos ["+chunkPos+"] after ["+maxGetTimeInSec+"] milliseconds.", e);
				future.cancel(true);
			}
			#endif
		}
		catch (Exception e)
		{
			LOAD_LOGGER.error("DistantHorizons: Couldn't load or make chunk " + chunkPos + ". Error: " + e.getMessage(), e);
		}
		
		return chunkData;
	}
	private ChunkAccess loadOrMakeChunk(ChunkPos chunkPos, CompoundTag chunkData)
	{
		ServerLevel level = this.params.level;
		
		if (chunkData == null)
		{
			return CreateEmptyChunk(level, chunkPos);
		}
		else
		{
			try
			{
				LOAD_LOGGER.debug("DistantHorizons: Loading chunk [" + chunkPos + "] from disk.");
				return ChunkLoader.read(level, chunkPos, chunkData);
			}
			catch (Exception e)
			{
				LOAD_LOGGER.error(
						"DistantHorizons: couldn't load or make chunk at ["+chunkPos+"]." +
								"Please try optimizing your world to fix this issue. \n" +
								"World optimization can be done from the singleplayer world selection screen.\n" +
								"Error: ["+e.getMessage()+"]."
						, e);
				
				return CreateEmptyChunk(level, chunkPos);
			}
		}
	}
	private static ProtoChunk CreateEmptyChunk(ServerLevel level, ChunkPos chunkPos)
	{
		return new ProtoChunk(chunkPos, UpgradeData.EMPTY
					#if MC_VER >= MC_1_17_1 , level #endif
					#if MC_VER >= MC_1_18_2 , level.registryAccess().registryOrThrow(
						#if MC_VER < MC_1_19_4
				Registry.BIOME_REGISTRY
						#else
				Registries.BIOME
						#endif
		), null #endif
		);
	}
	
	
	
	
	public void generateDirect(
			GenerationEvent genEvent, ArrayGridList<ChunkWrapper> chunksToGenerate, int border,
			EDhApiWorldGenerationStep step, DhLitWorldGenRegion region) throws InterruptedException
	{
		if (Thread.interrupted())
		{
			return;
		}
		
		try
		{
			chunksToGenerate.forEach((chunkWrapper) ->
			{
				ChunkAccess chunk = chunkWrapper.getChunk();
				if (chunk instanceof ProtoChunk)
				{
					ProtoChunk protoChunk = ((ProtoChunk) chunk);
					
					protoChunk.setLightEngine(region.getLightEngine());
				}
			});
			
			if (step == EDhApiWorldGenerationStep.EMPTY)
			{
				return;
			}
			
			genEvent.timer.nextEvent("structStart");
			throwIfThreadInterrupted();
			this.stepStructureStart.generateGroup(genEvent.threadedParam, region, GetCutoutFrom(chunksToGenerate, EDhApiWorldGenerationStep.STRUCTURE_START));
			genEvent.refreshTimeout();
			if (step == EDhApiWorldGenerationStep.STRUCTURE_START)
			{
				return;
			}
			
			genEvent.timer.nextEvent("structRef");
			throwIfThreadInterrupted();
			this.stepStructureReference.generateGroup(genEvent.threadedParam, region, GetCutoutFrom(chunksToGenerate, EDhApiWorldGenerationStep.STRUCTURE_REFERENCE));
			genEvent.refreshTimeout();
			if (step == EDhApiWorldGenerationStep.STRUCTURE_REFERENCE)
			{
				return;
			}
			
			genEvent.timer.nextEvent("biome");
			throwIfThreadInterrupted();
			this.stepBiomes.generateGroup(genEvent.threadedParam, region, GetCutoutFrom(chunksToGenerate, EDhApiWorldGenerationStep.BIOMES));
			genEvent.refreshTimeout();
			if (step == EDhApiWorldGenerationStep.BIOMES)
			{
				return;
			}
			
			genEvent.timer.nextEvent("noise");
			throwIfThreadInterrupted();
			this.stepNoise.generateGroup(genEvent.threadedParam, region, GetCutoutFrom(chunksToGenerate, EDhApiWorldGenerationStep.NOISE));
			genEvent.refreshTimeout();
			if (step == EDhApiWorldGenerationStep.NOISE)
			{
				return;
			}
			
			genEvent.timer.nextEvent("surface");
			throwIfThreadInterrupted();
			this.stepSurface.generateGroup(genEvent.threadedParam, region, GetCutoutFrom(chunksToGenerate, EDhApiWorldGenerationStep.SURFACE));
			genEvent.refreshTimeout();
			if (step == EDhApiWorldGenerationStep.SURFACE)
			{
				return;
			}
			
			genEvent.timer.nextEvent("carver");
			throwIfThreadInterrupted();
			// caves can generally be ignored since they aren't generally visible from far away
			if (step == EDhApiWorldGenerationStep.CARVERS)
			{
				return;
			}
			
			genEvent.timer.nextEvent("feature");
			throwIfThreadInterrupted();
			this.stepFeatures.generateGroup(genEvent.threadedParam, region, GetCutoutFrom(chunksToGenerate, EDhApiWorldGenerationStep.FEATURES));
			genEvent.refreshTimeout();
		}
		finally
		{
			genEvent.timer.nextEvent("light");
			
			// generate lighting using DH's lighting engine
				
			int maxSkyLight = this.serverlevel.getServerLevelWrapper().hasSkyLight() ? 15 : 0;
			
			// only light generated chunks,
			// attempting to light un-generated chunks will cause lighting issues on bordering generated chunks
			ArrayList<IChunkWrapper> iChunkWrapperList = new ArrayList<>();
			for (int i = 0; i < chunksToGenerate.size(); i++) // regular for loop since enhanced for loops increase GC pressure slightly
			{
				ChunkWrapper chunkWrapper = chunksToGenerate.get(i);
				if (chunkWrapper.getStatus() != ChunkStatus.EMPTY)
				{
					iChunkWrapperList.add(chunkWrapper);
				}
			}
			
			// light each chunk in the list
			for (int i = 0; i < iChunkWrapperList.size(); i++)
			{
				IChunkWrapper centerChunk = iChunkWrapperList.get(i);
				if (centerChunk == null)
				{
					continue;
				}
				
				throwIfThreadInterrupted();
				
				// make sure the height maps are all properly generated
				// if this isn't done everything else afterward may fail
				Heightmap.primeHeightmaps(((ChunkWrapper)centerChunk).getChunk(), ChunkStatus.FEATURES.heightmapsAfter());
				
				// pre-generated chunks should have lighting but new ones won't
				if (!centerChunk.isDhBlockLightingCorrect())
				{
					DhLightingEngine.INSTANCE.bakeChunkBlockLighting(centerChunk, iChunkWrapperList, maxSkyLight);
				}
			}
			
			genEvent.refreshTimeout();
		}
	}
	private static <T> ArrayGridList<T> GetCutoutFrom(ArrayGridList<T> total, int border) { return new ArrayGridList<>(total, border, total.gridSize - border); }
	//private static <T> ArrayGridList<T> GetCutoutFrom(ArrayGridList<T> total, EDhApiWorldGenerationStep step) { return GetCutoutFrom(total, MaxBorderNeeded - WORLD_GEN_CHUNK_BORDER_NEEDED_BY_GEN_STEP.get(step)); }
	private static <T> ArrayGridList<T> GetCutoutFrom(ArrayGridList<T> total, EDhApiWorldGenerationStep step) { return GetCutoutFrom(total, 0); }
	
	
	@Override
	public int getEventCount() { return this.generationEventList.size(); }
	
	@Override
	public void stop()
	{
		EVENT_LOGGER.info(BatchGenerationEnvironment.class.getSimpleName() + " shutting down...");
		
		EVENT_LOGGER.info("Canceling in progress generation event futures...");
		Iterator<GenerationEvent> iter = this.generationEventList.iterator();
		while (iter.hasNext())
		{
			GenerationEvent event = iter.next();
			event.future.cancel(true);
			iter.remove();
		}
		
		// clear the chunk cache
		RegionFileStorageExternalCache regionStorage = this.regionFileStorageCacheRef.get();
		if (regionStorage != null)
		{
			try
			{
				regionStorage.close();
			}
			catch (IOException e)
			{
				EVENT_LOGGER.error("Failed to close region file storage cache!", e);
			}
		}
		
		EVENT_LOGGER.info(BatchGenerationEnvironment.class.getSimpleName() + " shutdown complete.");
	}
	
	@Override
	public CompletableFuture<Void> generateChunks(
			int minX, int minZ, int genSize, EDhApiWorldGenerationStep targetStep,
			ExecutorService worldGeneratorThreadPool, Consumer<IChunkWrapper> resultConsumer)
	{
		//System.out.println("GenerationEvent: "+genSize+"@"+minX+","+minZ+" "+targetStep);
		
		// TODO: Check event overlap via e.tooClose()
		GenerationEvent genEvent = GenerationEvent.startEvent(new DhChunkPos(minX, minZ), genSize, this, targetStep, resultConsumer, worldGeneratorThreadPool);
		this.generationEventList.add(genEvent);
		return genEvent.future;
	}
	
	
	
	//================//
	// helper methods //
	//================//
	
	/**
	 * Called before code that may run for an extended period of time. <br>
	 * This is necessary to allow canceling world gen since waiting
	 * for some world gen requests to finish can take a while.
	 */
	public static void throwIfThreadInterrupted() throws InterruptedException
	{
		if (Thread.interrupted())
		{
			throw new InterruptedException(FullDataToRenderDataTransformer.class.getSimpleName() + " task interrupted.");
		}
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	@FunctionalInterface
	public interface IEmptyChunkGeneratorFunc
	{
		ChunkAccess generate(int x, int z);
	}
	
}