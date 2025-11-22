package com.seibel.distanthorizons.common.wrappers.worldGeneration.internalServer;

import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.ChunkPosGenStream;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.GenerationEvent;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.GlobalWorldGenParams;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.generation.DhLightingEngine;
import com.seibel.distanthorizons.core.level.IDhServerLevel;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class InternalServerGenerator
{
	public static final DhLogger LOGGER = new DhLoggerBuilder()
			.name("LOD World Gen - Internal Server")
			.fileLevelConfig(Config.Common.Logging.logWorldGenEventToFile)
			.build();
	
	public static final DhLogger CHUNK_LOAD_LOGGER = new DhLoggerBuilder()
			.name("LOD Chunk Loading")
			.fileLevelConfig(Config.Common.Logging.logWorldGenChunkLoadEventToFile)
			.build();
	
	#if MC_VER < MC_1_21_5
	private static final TicketType<ChunkPos> DH_SERVER_GEN_TICKET = TicketType.create("dh_server_gen_ticket", Comparator.comparingLong(ChunkPos::toLong));
	#elif MC_VER < MC_1_21_9
	private static final TicketType DH_SERVER_GEN_TICKET = new TicketType(/* timeout, 0 = disabled*/0L, /* persist */ false, TicketType.TicketUse.LOADING);
	#else
	private static final TicketType DH_SERVER_GEN_TICKET = new TicketType(/* timeout, 0 = disabled*/0L, /* flags */TicketType.FLAG_LOADING);
	#endif
	
	
	private final GlobalWorldGenParams params;
	private final IDhServerLevel dhServerLevel;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public InternalServerGenerator(GlobalWorldGenParams params, IDhServerLevel dhServerLevel)
	{
		this.params = params;
		this.dhServerLevel = dhServerLevel;
	}
	
	
	
	//============//
	// generation //
	//============//
	
	public void generateChunksViaInternalServer(GenerationEvent genEvent) throws InterruptedException
	{
		LinkedBlockingQueue<Runnable> runnableQueue = new LinkedBlockingQueue<>();
		
		Map<DhChunkPos, ChunkWrapper> chunkWrappersByDhPos = Collections.synchronizedMap(new HashMap<>());
		
		
		
		//===================================//
		// create generation queue runnables //
		//===================================//
		
		// request each chunk pos from the server
		CompletableFuture<?>[] requestFutures =
			ChunkPosGenStream.getStream(genEvent.minPos.getX(), genEvent.minPos.getZ(), genEvent.widthInChunks, 0)
				.map(chunkPos ->
				{
					return requestChunkFromServerAsync(this.params.level, chunkPos, true)
						.whenCompleteAsync((chunk, throwable) ->
						{
							// unwrap the CompletionException if necessary
							Throwable actualThrowable = throwable;
							while (actualThrowable instanceof CompletionException)
							{
								actualThrowable = actualThrowable.getCause();
							}
							
							if (throwable != null)
							{
								CHUNK_LOAD_LOGGER.warn("DistantHorizons: Couldn't load chunk [" + chunkPos + "] from server, error: [" + actualThrowable.getMessage() + "].", actualThrowable);
							}
							
							if (chunk != null)
							{
								ChunkWrapper chunkWrapper = new ChunkWrapper(chunk, this.dhServerLevel.getLevelWrapper());
								chunkWrappersByDhPos.put(new DhChunkPos(chunkPos.x, chunkPos.z), chunkWrapper);
							}
						}, runnableQueue::add);
				})
				.toArray(CompletableFuture[]::new);
		
		// handle each generated chunk
		CompletableFuture<Void> processGeneratedChunksFuture =
			CompletableFuture.allOf(requestFutures)
				.whenCompleteAsync((voidObj, throwable) ->
				{
					// generate chunk lighting using DH's lighting engine
					int maxSkyLight = this.dhServerLevel.getServerLevelWrapper().hasSkyLight() ? LodUtil.MAX_MC_LIGHT : LodUtil.MIN_MC_LIGHT;
					
					ArrayList<IChunkWrapper> generatedChunks = new ArrayList<>(chunkWrappersByDhPos.values());
					for (IChunkWrapper iChunkWrapper : generatedChunks)
					{
						((ChunkWrapper) iChunkWrapper).recalculateDhHeightMapsIfNeeded();
						
						// pre-generated chunks should have lighting but new ones won't
						if (!iChunkWrapper.isDhBlockLightingCorrect())
						{
							DhLightingEngine.INSTANCE.bakeChunkBlockLighting(iChunkWrapper, generatedChunks, maxSkyLight);
						}
						
						this.dhServerLevel.updateBeaconBeamsForChunk(iChunkWrapper, generatedChunks);
					}
					
					for (IChunkWrapper iChunkWrapper : generatedChunks)
					{
						genEvent.resultConsumer.accept(iChunkWrapper);
					}
				}, runnableQueue::add)
				.whenCompleteAsync((unused, throwable) ->
				{
					// cleanup
					// release the generated chunks
					
					Iterator<ChunkPos> iterator = ChunkPosGenStream.getStream(genEvent.minPos.getX(), genEvent.minPos.getZ(), genEvent.widthInChunks, 0).iterator();
					while (iterator.hasNext())
					{
						ChunkPos chunkPos = iterator.next();
						releaseChunkFromServer(this.params.level, chunkPos, true);
					}
				});
		
		processGeneratedChunksFuture.whenCompleteAsync((unused, throwable) -> { }, runnableQueue::add); // trigger wakeup
		
		
		
		//===============//
		// run each step //
		//===============//
		
		while (!processGeneratedChunksFuture.isDone())
		{
			try
			{
				Runnable command = runnableQueue.poll(1, TimeUnit.SECONDS);
				if (command != null)
				{
					command.run();
				}
			}
			catch (InterruptedException e)
			{
				// interrupted, release chunk to server
				Iterator<ChunkPos> iterator = ChunkPosGenStream.getStream(genEvent.minPos.getX(), genEvent.minPos.getZ(), genEvent.widthInChunks, 0).iterator();
				while (iterator.hasNext())
				{
					ChunkPos chunkPos = iterator.next();
					releaseChunkFromServer(this.params.level, chunkPos, true);
				}
				
				throw e;
			}
		}
	}
	/** @param generateUpToFeatures if false this generate the chunk up to "FULL" status */
	private static CompletableFuture<ChunkAccess> requestChunkFromServerAsync(ServerLevel level, ChunkPos pos, boolean generateUpToFeatures)
	{
		return CompletableFuture.supplyAsync(() ->
		{
			int chunkLevel;
			#if MC_VER <= MC_1_19_4
			// 33 is equivalent to FULL Chunk
			chunkLevel = generateUpToFeatures ? 33 + ChunkStatus.getDistance(ChunkStatus.FEATURES) : 33;
			#else
			// 33 is equivalent to FULL Chunk
			chunkLevel = generateUpToFeatures ? ChunkLevel.byStatus(ChunkStatus.FEATURES) : 33;
			#endif
			
			#if MC_VER < MC_1_21_5
			level.getChunkSource().distanceManager.addTicket(DH_SERVER_GEN_TICKET, pos, chunkLevel, pos);
			#else
			level.getChunkSource().addTicketWithRadius(DH_SERVER_GEN_TICKET, pos, 0);
			#endif
			level.getChunkSource().distanceManager.runAllUpdates(level.getChunkSource().chunkMap); // probably not the most optimal to run updates here, but fast enough
			ChunkHolder holder = level.getChunkSource().chunkMap.getUpdatingChunkIfPresent(pos.toLong());
			if (holder == null)
			{
				throw new IllegalStateException("No chunk holder after ticket has been added");
			}
			
			#if MC_VER <= MC_1_20_4
			return holder.getOrScheduleFuture(ChunkStatus.FEATURES, level.getChunkSource().chunkMap)
					.thenApply(result -> result.left().orElseThrow(() -> new RuntimeException(result.right().get().toString()))); // can throw if the server is shutting down
			#elif MC_VER <= MC_1_20_6
			return holder.getOrScheduleFuture(ChunkStatus.FEATURES, level.getChunkSource().chunkMap)
					.thenApply(result -> result.orElseThrow(() -> new RuntimeException(result.toString()))); // can throw if the server is shutting down
			#else
			return holder.scheduleChunkGenerationTask(ChunkStatus.FEATURES, level.getChunkSource().chunkMap)
					.thenApply(result -> result.orElseThrow(() -> new RuntimeException(result.getError()))); // can throw if the server is shutting down
			#endif
			
		}, level.getChunkSource().chunkMap.mainThreadExecutor)
			.thenCompose(Function.identity());
	}
	/** @param chunkWasGeneratedUpToFeatures if false this assumes the chunk was generated to "FULL" status */
	private static void releaseChunkFromServer(ServerLevel level, ChunkPos pos, boolean chunkWasGeneratedUpToFeatures)
	{
		level.getChunkSource().chunkMap.mainThreadExecutor.execute(() ->
		{
			try
			{
				int chunkLevel;
				#if MC_VER <= MC_1_19_4
				// 33 is equivalent to FULL Chunk
				chunkLevel = chunkWasGeneratedUpToFeatures ? 33 + ChunkStatus.getDistance(ChunkStatus.FEATURES) : 33;
				#else
				// 33 is equivalent to FULL Chunk
				chunkLevel = chunkWasGeneratedUpToFeatures ? ChunkLevel.byStatus(ChunkStatus.FEATURES) : 33;
				#endif
				
				#if MC_VER < MC_1_21_5
				level.getChunkSource().distanceManager.removeTicket(DH_SERVER_GEN_TICKET, pos, chunkLevel, pos);
				#else
				level.getChunkSource().removeTicketWithRadius(DH_SERVER_GEN_TICKET, pos, 0);
				#endif
				
				// mitigate OOM issues in vanilla chunk system: see https://github.com/pop4959/Chunky/pull/383
				level.getChunkSource().chunkMap.tick(() -> false);
				#if MC_VER > MC_1_16_5
				level.entityManager.tick();
				#endif
			}
			catch (Exception e)
			{
				LOGGER.warn("Failed to release chunk back to internal server. Error: ["+e.getMessage()+"]", e);
			}
		});
	}
	
	
	
}
