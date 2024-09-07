/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
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

package com.seibel.distanthorizons.common.wrappers.chunk;

import com.seibel.distanthorizons.common.wrappers.block.BiomeWrapper;
import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.common.wrappers.misc.MutableBlockPosWrapper;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.mimicObject.DhLitWorldGenRegion;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.ChunkLightStorage;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IMutableBlockPosWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;

import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

#if MC_VER >= MC_1_17_1
import net.minecraft.core.QuartPos;
#endif

#if MC_VER == MC_1_16_5
import net.minecraft.world.level.chunk.LevelChunkSection;
#endif

#if MC_VER == MC_1_17_1
import net.minecraft.world.level.chunk.LevelChunkSection;
#endif

#if MC_VER == MC_1_18_2
import net.minecraft.world.level.chunk.LevelChunkSection;
#endif

#if MC_VER == MC_1_19_2 || MC_VER == MC_1_19_4
import net.minecraft.world.level.chunk.LevelChunkSection;
#endif

#if MC_VER >= MC_1_20_1
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.core.SectionPos;
#endif

#if MC_VER <= MC_1_20_4
import net.minecraft.world.level.chunk.ChunkStatus;
#else
import net.minecraft.world.level.chunk.status.ChunkStatus;
#endif


public class ChunkWrapper implements IChunkWrapper
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	/** can be used for interactions with the underlying chunk where creating new BlockPos objects could cause issues for the garbage collector. */
	private static final ThreadLocal<BlockPos.MutableBlockPos> MUTABLE_BLOCK_POS_REF = ThreadLocal.withInitial(() -> new BlockPos.MutableBlockPos());
	private static final ThreadLocal<MutableBlockPosWrapper> MUTABLE_BLOCK_POS_WRAPPER_REF = ThreadLocal.withInitial(() -> new MutableBlockPosWrapper());
	
	
	private final ChunkAccess chunk;
	private final DhChunkPos chunkPos;
	private final LevelReader lightSource;
	private final ILevelWrapper wrappedLevel;
	
	private boolean isDhBlockLightCorrect = false;
	private boolean isDhSkyLightCorrect = false;
	/** only used when connected to a dedicated server */
	private boolean isMcClientLightingCorrect = false;
	
	private ChunkLightStorage blockLightStorage;
	private ChunkLightStorage skyLightStorage;
	
	private ArrayList<DhBlockPos> blockLightPosList = null;
	
	private int minNonEmptyHeight = Integer.MIN_VALUE;
	private int maxNonEmptyHeight = Integer.MAX_VALUE;
	
	private int blockBiomeHashCode = 0;
	
	/**
	 * Due to vanilla `isClientLightReady()` not being designed for use by a non-render thread, it may return 'true'
	 * before the light engine has ticked, (right after all light changes is marked by the engine to be processed).
	 * To fix this, on client-only mode, we mixin-redirect the `isClientLightReady()` so that after the call, it will
	 * trigger a synchronous update of this flag here on all chunks that are wrapped. <br><br>
	 *
	 * Note: Using a static weak hash map to store the chunks that need to be updated, as instance of chunk wrapper
	 * can be duplicated, with same chunk instance. And the data stored here are all temporary, and thus will not be
	 * visible when a chunk is re-wrapped later. <br>
	 * (Also, thread safety done via a reader writer lock)
	 */
	private static final ConcurrentLinkedQueue<ChunkWrapper> chunksNeedingClientLightUpdating = new ConcurrentLinkedQueue<>(); 
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public ChunkWrapper(ChunkAccess chunk, LevelReader lightSource, ILevelWrapper wrappedLevel)
	{
		this.chunk = chunk;
		this.lightSource = lightSource;
		this.wrappedLevel = wrappedLevel;
		this.chunkPos = new DhChunkPos(chunk.getPos().x, chunk.getPos().z);
		
		// FIXME +1 is to handle the fact that LodDataBuilder adds +1 to all block lighting calculations, also done in the relative position validator

		chunksNeedingClientLightUpdating.add(this);
	}
	
	
	
	//=========//
	// getters //
	//=========//
	
	@Override
	public int getHeight() { return getHeight(this.chunk); }
	public static int getHeight(ChunkAccess chunk)
	{
		#if MC_VER < MC_1_17_1
		return 255;
		#else
		return chunk.getHeight();
		#endif
	}
	
	@Override
	public int getMinBuildHeight() { return getMinBuildHeight(this.chunk); }
	public static int getMinBuildHeight(ChunkAccess chunk)
	{
		#if MC_VER < MC_1_17_1
		return 0;
		#else
		return chunk.getMinBuildHeight();
		#endif
	}
	
	@Override
	public int getMaxBuildHeight() { return getMaxBuildHeight(this.chunk); }
	public static int getMaxBuildHeight(ChunkAccess chunk) { return chunk.getMaxBuildHeight(); }
	
	@Override
	public int getMinNonEmptyHeight()
	{
		if (this.minNonEmptyHeight != Integer.MIN_VALUE)
		{
			return this.minNonEmptyHeight;
		}
		
		
		// default if every section is empty or missing
		this.minNonEmptyHeight = this.getMinBuildHeight();
		
		// determine the lowest empty section (bottom up)
		LevelChunkSection[] sections = this.chunk.getSections();
		for (int index = 0; index < sections.length; index++)
		{
			if (sections[index] == null)
			{
				continue;
			}
			
			if (!isChunkSectionEmpty(sections[index]))
			{
				this.minNonEmptyHeight = this.getChunkSectionMinHeight(index);
				break;
			}
		}
		
		return this.minNonEmptyHeight;
	}
	
	
	@Override
	public int getMaxNonEmptyHeight()
	{
		if (this.maxNonEmptyHeight != Integer.MAX_VALUE)
		{
			return this.maxNonEmptyHeight;
		}
		
		
		// default if every section is empty or missing
		this.maxNonEmptyHeight = this.getMaxBuildHeight();
		
		// determine the highest empty section (top down)
		LevelChunkSection[] sections = this.chunk.getSections();
		for (int index = sections.length-1; index >= 0; index--)
		{
			// update at each position to fix using the max height if the chunk is empty
			this.maxNonEmptyHeight = this.getChunkSectionMinHeight(index) + 16;
			
			if (sections[index] == null)
			{
				continue;
			}
			
			if (!isChunkSectionEmpty(sections[index]))
			{
				// non-empty section found
				break;
			}
		}
		
		return this.maxNonEmptyHeight;
	}
	private static boolean isChunkSectionEmpty(LevelChunkSection section)
	{
		#if MC_VER == MC_1_16_5
		return section.isEmpty();
		#elif MC_VER == MC_1_17_1
		return section.isEmpty();
		#else
		return section.hasOnlyAir();
		#endif
	}
	private int getChunkSectionMinHeight(int index) { return (index * 16) + this.getMinBuildHeight(); }
	
	
	@Override
	public int getSolidHeightMapValue(int xRel, int zRel) { return this.chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE).getFirstAvailable(xRel, zRel); }
	
	@Override
	public int getLightBlockingHeightMapValue(int xRel, int zRel) { return this.chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.MOTION_BLOCKING).getFirstAvailable(xRel, zRel); }
	
	
	@Override
	public IBiomeWrapper getBiome(int relX, int relY, int relZ)
	{
		#if MC_VER < MC_1_17_1
		return BiomeWrapper.getBiomeWrapper(this.chunk.getBiomes().getNoiseBiome(
				relX >> 2, relY >> 2, relZ >> 2),
				this.wrappedLevel);
		#elif MC_VER < MC_1_18_2
		return BiomeWrapper.getBiomeWrapper(this.chunk.getBiomes().getNoiseBiome(
				QuartPos.fromBlock(relX), QuartPos.fromBlock(relY), QuartPos.fromBlock(relZ)),
				this.wrappedLevel);
		#elif MC_VER < MC_1_18_2
		return BiomeWrapper.getBiomeWrapper(this.chunk.getNoiseBiome(
				QuartPos.fromBlock(relX), QuartPos.fromBlock(relY), QuartPos.fromBlock(relZ)),
				this.wrappedLevel);
		#else 
		//Now returns a Holder<Biome> instead of Biome
		return BiomeWrapper.getBiomeWrapper(this.chunk.getNoiseBiome(
				QuartPos.fromBlock(relX), QuartPos.fromBlock(relY), QuartPos.fromBlock(relZ)),
				this.wrappedLevel);
		#endif
	}
	
	@Override
	public IBlockStateWrapper getBlockState(int relX, int relY, int relZ)
	{
		this.throwIndexOutOfBoundsIfRelativePosOutsideChunkBounds(relX, relY, relZ);
		
		BlockPos.MutableBlockPos blockPos = MUTABLE_BLOCK_POS_REF.get();
		
		blockPos.setX(relX);
		blockPos.setY(relY);
		blockPos.setZ(relZ);
		
		return BlockStateWrapper.fromBlockState(this.chunk.getBlockState(blockPos), this.wrappedLevel);
	}
	
	@Override
	public IBlockStateWrapper getBlockState(int relX, int relY, int relZ, IMutableBlockPosWrapper mcBlockPos, IBlockStateWrapper guess)
	{
		this.throwIndexOutOfBoundsIfRelativePosOutsideChunkBounds(relX, relY, relZ);
		
		BlockPos.MutableBlockPos pos = (BlockPos.MutableBlockPos)mcBlockPos.getWrappedMcObject();
		pos.setX(relX);
		pos.setY(relY);
		pos.setZ(relZ);
		
		return BlockStateWrapper.fromBlockState(this.chunk.getBlockState(pos), this.wrappedLevel, guess);
	}
	
	@Override
	public IMutableBlockPosWrapper getMutableBlockPosWrapper() { return MUTABLE_BLOCK_POS_WRAPPER_REF.get(); }
	
	@Override
	public DhChunkPos getChunkPos() { return this.chunkPos; }
	
	public ChunkAccess getChunk() { return this.chunk; }
	
	public ChunkStatus getStatus() { return getStatus(this.getChunk()); }
	public static ChunkStatus getStatus(ChunkAccess chunk)
	{
		#if MC_VER < MC_1_21_1 
		return chunk.getStatus();
		#else
		return chunk.getPersistedStatus(); 
		#endif
	}
	
	@Override
	public int getMaxBlockX() { return this.chunk.getPos().getMaxBlockX(); }
	@Override
	public int getMaxBlockZ() { return this.chunk.getPos().getMaxBlockZ(); }
	@Override
	public int getMinBlockX() { return this.chunk.getPos().getMinBlockX(); }
	@Override
	public int getMinBlockZ() { return this.chunk.getPos().getMinBlockZ(); }
	
	
	
	//==========//
	// lighting //
	//==========//
	
	@Override 
	public void setIsDhSkyLightCorrect(boolean isDhLightCorrect) { this.isDhSkyLightCorrect = isDhLightCorrect; }
	@Override 
	public void setIsDhBlockLightCorrect(boolean isDhLightCorrect) { this.isDhBlockLightCorrect = isDhLightCorrect; }
	
	@Override
	public boolean isDhBlockLightingCorrect() { return this.isDhBlockLightCorrect; }
	@Override
	public boolean isDhSkyLightCorrect() { return this.isDhSkyLightCorrect; }
	
	
	@Override
	public int getDhBlockLight(int relX, int y, int relZ)
	{
		this.throwIndexOutOfBoundsIfRelativePosOutsideChunkBounds(relX, y, relZ);
		return this.getBlockLightStorage().get(relX, y, relZ);
	}
	@Override
	public void setDhBlockLight(int relX, int y, int relZ, int lightValue)
	{
		this.throwIndexOutOfBoundsIfRelativePosOutsideChunkBounds(relX, y, relZ);
		this.getBlockLightStorage().set(relX, y, relZ, lightValue);
	}
	
	private ChunkLightStorage getBlockLightStorage()
	{
		if (this.blockLightStorage == null)
		{
			this.blockLightStorage = ChunkLightStorage.createBlockLightStorage(this);
		}
		return this.blockLightStorage;
	}
	public void setBlockLightStorage(ChunkLightStorage lightStorage) { this.blockLightStorage = lightStorage; }
	@Override
	public void clearDhBlockLighting() { this.getBlockLightStorage().clear(); }
	
	
	@Override
	public int getDhSkyLight(int relX, int y, int relZ)
	{
		this.throwIndexOutOfBoundsIfRelativePosOutsideChunkBounds(relX, y, relZ);
		return this.getSkyLightStorage().get(relX, y, relZ);
	}
	@Override
	public void setDhSkyLight(int relX, int y, int relZ, int lightValue)
	{
		this.throwIndexOutOfBoundsIfRelativePosOutsideChunkBounds(relX, y, relZ);
		this.getSkyLightStorage().set(relX, y, relZ, lightValue);
	}
	@Override
	public void clearDhSkyLighting() { this.getSkyLightStorage().clear(); }
	
	private ChunkLightStorage getSkyLightStorage()
	{
		if (this.skyLightStorage == null)
		{
			this.skyLightStorage = ChunkLightStorage.createSkyLightStorage(this);
		}
		return this.skyLightStorage;
	}
	public void setSkyLightStorage(ChunkLightStorage lightStorage) { this.skyLightStorage = lightStorage; }
	
	
	/** 
	 * FIXME synchronized is necessary for a rare issue where this method is called from two separate threads at the same time
	 *  before the list has finished populating.
	 */
	@Override
	public synchronized ArrayList<DhBlockPos> getWorldBlockLightPosList()
	{
		// only populate the list once
		if (this.blockLightPosList == null)
		{
			this.blockLightPosList = new ArrayList<>();
			
			
			#if MC_VER < MC_1_20_1
			this.chunk.getLights().forEach((blockPos) ->
			{
				this.blockLightPosList.add(new DhBlockPos(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
			});
			#else
			this.chunk.findBlockLightSources((blockPos, blockState) ->
			{
				DhBlockPos pos = new DhBlockPos(blockPos.getX(), blockPos.getY(), blockPos.getZ());
				
				// this can be uncommented if MC decides to return relative block positions in the future instead of world positions
				//pos.mutateToChunkRelativePos(pos);
				//pos.mutateOffset(this.chunkPos.getMinBlockX(), 0, this.chunkPos.getMinBlockZ(), pos);
				
				this.blockLightPosList.add(pos);
			});
			#endif
		}
		
		return this.blockLightPosList;
	}
	
	public static void syncedUpdateClientLightStatus()
	{
		#if MC_VER < MC_1_18_2
		// TODO: Check what to do in 1.18.1 and older
		
		// since we don't currently handle this list,
		// clear it to prevent memory leaks
		chunksNeedingClientLightUpdating.clear();
		
		#else
		
		// update the chunks client lighting
		ChunkWrapper chunkWrapper = chunksNeedingClientLightUpdating.poll();
		while (chunkWrapper != null)
		{
			chunkWrapper.updateIsClientLightingCorrect();
			chunkWrapper = chunksNeedingClientLightUpdating.poll();
		}
		
		#endif
	}
	/** Should be called after client light updates are triggered. */
	private void updateIsClientLightingCorrect()
	{
		if (this.chunk instanceof LevelChunk && ((LevelChunk) this.chunk).getLevel() instanceof ClientLevel)
		{
			LevelChunk levelChunk = (LevelChunk) this.chunk;
			ClientChunkCache clientChunkCache = ((ClientLevel) levelChunk.getLevel()).getChunkSource();
			this.isMcClientLightingCorrect = clientChunkCache.getChunkForLighting(this.chunk.getPos().x, this.chunk.getPos().z) != null &&
					#if MC_VER <= MC_1_17_1
					levelChunk.isLightCorrect();
					#elif MC_VER < MC_1_20_1
					levelChunk.isClientLightReady();
					#else
					checkLightSectionsOnChunk(levelChunk, levelChunk.getLevel().getLightEngine());
					#endif
		}
	}
	#if MC_VER >= MC_1_20_1
	private static boolean checkLightSectionsOnChunk(LevelChunk chunk, LevelLightEngine engine)
	{
		LevelChunkSection[] sections = chunk.getSections();
		int minY = chunk.getMinSection();
		int maxY = chunk.getMaxSection();
		for (int y = minY; y < maxY; ++y)
		{
			LevelChunkSection section = sections[chunk.getSectionIndexFromSectionY(y)];
			if (section.hasOnlyAir()) continue;
			if (!engine.lightOnInSection(SectionPos.of(chunk.getPos(), y)))
			{
				return false;
			}
		}
		return true;
	}
	#endif
	
	
	
	//===============//
	// other methods //
	//===============//
	
	@Override
	public boolean doNearbyChunksExist()
	{
		if (this.lightSource instanceof DhLitWorldGenRegion)
		{
			return true;
		}
		
		for (int dx = -1; dx <= 1; dx++)
		{
			for (int dz = -1; dz <= 1; dz++)
			{
				if (dx == 0 && dz == 0)
				{
					continue;
				}
				else if (this.lightSource.getChunk(dx + this.chunk.getPos().x, dz + this.chunk.getPos().z, ChunkStatus.BIOMES, false) == null)
				{
					return false;
				}
			}
		}
		
		return true;
	}
	
	@Override
	public boolean isStillValid() { return this.wrappedLevel.tryGetChunk(this.chunkPos) == this; }
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public String toString() { return this.chunk.getClass().getSimpleName() + this.chunk.getPos(); }
	
	//@Override 
	//public int hashCode()
	//{
	//	if (this.blockBiomeHashCode == 0)
	//	{
	//		this.blockBiomeHashCode = this.getBlockBiomeHashCode();
	//	}
	//	
	//	return this.blockBiomeHashCode;
	//}
	
}
