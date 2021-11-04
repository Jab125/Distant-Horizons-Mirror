/*
 *    This file is part of the Distant Horizon mod (formerly the LOD Mod),
 *    licensed under the GNU GPL v3 License.
 *
 *    Copyright (C) 2020  James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.lod.builders.worldGeneration;


import com.seibel.lod.util.LodUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

import java.util.HashMap;


/**
 * This is a fake ServerWorld used when generating features.
 * This allows us to keep each LodChunk generation independent
 * of the actual ServerWorld, allowing us
 * to multithread generation.
 *
 * @author coolGi2007
 * @author James Seibel
 * @version 10-24-2021
 */

// TODO: Implement the methods
public class LodServerLevel implements Level
{

	public HashMap<Heightmap.Types, Heightmap> heightmaps = new HashMap<>();
	
	public final ChunkAccess chunk;
	
	public final ServerLevel serverWorld;
	
	public LodServerLevel(ServerLevel newServerWorld, ChunkAccess newChunk)
	{
		chunk = newChunk;
		serverWorld = newServerWorld;
	}
	
	
	@Override
	public int getHeight(Type heightmapType, int x, int z)
	{
		// make sure the block position is set relative to the chunk
		x = x % LodUtil.CHUNK_WIDTH;
		x = (x < 0) ? x + 16 : x;
		
		z = z % LodUtil.CHUNK_WIDTH;
		z = (z < 0) ? z + 16 : z;
		
		return chunk.getOrCreateHeightmapUnprimed(LodUtil.DEFAULT_HEIGHTMAP).getFirstAvailable(x, z);
	}
	
	@Override
	public Biome getBiome(BlockPos pos)
	{
		return chunk.getBiomes().getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
	}
	
	@Override
	public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLeft)
	{
		return chunk.setBlockState(pos, state, false) == state;
	}
	
	@Override
	public BlockState getBlockState(BlockPos pos)
	{
		return chunk.getBlockState(pos);
	}
	
	@Override
	public FluidState getFluidState(BlockPos pos)
	{
		return chunk.getFluidState(pos);
	}


	@Override
	public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> state)
	{
		return state.test(chunk.getBlockState(pos));
	}
	
	@Override
	public ITickList<Block> getBlockTicks()
	{
		return EmptyTickList.empty();
	}
	
	@Override
	public IChunk getChunk(int x, int z, ChunkStatus requiredStatus, boolean nonnull)
	{
		return chunk;
	}
	
	@Override
	public Stream<? extends StructureStart<?>> startsForFeature(SectionPos p_241827_1_, Structure<?> p_241827_2_)
	{
		return serverWorld.startsForFeature(p_241827_1_, p_241827_2_);
	}
	
	@Override
	public ITickList<Fluid> getLiquidTicks()
	{
		return EmptyTickList.empty();
	}

	@Override
	public WorldLightManager getLightEngine()
	{
		return new WorldLightManager(null, false, false);
	}
	
	@Override
	public long getSeed()
	{
		return serverWorld.getSeed();
	}
	
	@Override
	public DynamicRegistries registryAccess()
	{
		return serverWorld.registryAccess();
	}
	
	
	/**
	 * All methods below shouldn't be needed
	 * and thus have been left unimplemented.
	 */
	
	
	@Override
	public Random getRandom()
	{
		throw new UnsupportedOperationException("Not Implemented");
	}

	@Override
	public void playSound(PlayerEntity player, BlockPos pos, SoundEvent soundIn, SoundCategory category, float volume,
			float pitch)
	{
		throw new UnsupportedOperationException("Not Implemented");
	}
	
	@Override
	public void addParticle(IParticleData particleData, double x, double y, double z, double xSpeed, double ySpeed,
			double zSpeed)
	{
		throw new UnsupportedOperationException("Not Implemented");
	}
	
	@Override
	public BiomeManager getBiomeManager()
	{
		throw new UnsupportedOperationException("Not Implemented");
	}

	@Override
	public int getSeaLevel()
	{
		throw new UnsupportedOperationException("Not Implemented");
	}
	
	@Override
	public float getShade(Direction p_230487_1_, boolean p_230487_2_)
	{
		throw new UnsupportedOperationException("Not Implemented");
	}
	
	@Override
	public WorldBorder getWorldBorder()
	{
		throw new UnsupportedOperationException("Not Implemented");
	}
	
	@Override
	public boolean removeBlock(BlockPos pos, boolean isMoving)
	{
		throw new UnsupportedOperationException("Not Implemented");
	}
	
	@Override
	public boolean destroyBlock(BlockPos pos, boolean dropBlock, Entity entity, int recursionLeft)
	{
		throw new UnsupportedOperationException("Not Implemented");
	}


	@Override
	public ServerWorld getLevel()
	{
		throw new UnsupportedOperationException("Not Implemented");
	}
	
	
	@Override
	public AbstractChunkProvider getChunkSource()
	{
		throw new UnsupportedOperationException("Not Implemented");
	}
	
	
	@Override
	public DifficultyInstance getCurrentDifficultyAt(BlockPos arg0)
	{
		throw new UnsupportedOperationException("Not Implemented");
	}
	
	
	@Override
	public IWorldInfo getLevelData()
	{
		throw new UnsupportedOperationException("Not Implemented");
	}


	@Override
	public void levelEvent(PlayerEntity arg0, int arg1, BlockPos arg2, int arg3)
	{
		throw new UnsupportedOperationException("Not Implemented");
		
	}
	
	
	@Override
	public List<Entity> getEntities(Entity arg0, AxisAlignedBB arg1, Predicate<? super Entity> arg2)
	{
		throw new UnsupportedOperationException("Not Implemented");
	}
	
	
	@Override
	public <T extends Entity> List<T> getEntitiesOfClass(Class<? extends T> arg0, AxisAlignedBB arg1,
			Predicate<? super T> arg2)
	{
		throw new UnsupportedOperationException("Not Implemented");
	}
	
	
	@Override
	public List<? extends PlayerEntity> players()
	{
		throw new UnsupportedOperationException("Not Implemented");
	}
	
	
	@Override
	public int getSkyDarken()
	{
		throw new UnsupportedOperationException("Not Implemented");
	}
	
	
	@Override
	public Biome getUncachedNoiseBiome(int p_225604_1_, int p_225604_2_, int p_225604_3_)
	{
		throw new UnsupportedOperationException("Not Implemented");
	}
	
	
	@Override
	public boolean isClientSide()
	{
		throw new UnsupportedOperationException("Not Implemented");
	}
	
	
	@Override
	public DimensionType dimensionType()
	{
		throw new UnsupportedOperationException("Not Implemented");
	}
	
	
	@Override
	public TileEntity getBlockEntity(BlockPos p_175625_1_)
	{
		throw new UnsupportedOperationException("Not Implemented");
	}

}
