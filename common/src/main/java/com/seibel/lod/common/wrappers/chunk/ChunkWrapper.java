package com.seibel.lod.common.wrappers.chunk;

import com.seibel.lod.common.wrappers.worldGeneration.mimicObject.LightedWorldGenRegion;
import com.seibel.lod.core.util.LevelPosUtil;
import com.seibel.lod.core.util.LodUtil;
import com.seibel.lod.core.wrapperInterfaces.block.BlockDetail;
import com.seibel.lod.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.lod.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.lod.common.wrappers.WrapperUtil;
import com.seibel.lod.common.wrappers.block.BlockDetailMap;
import com.seibel.lod.common.wrappers.world.BiomeWrapper;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 *
 * @author James Seibel
 * @version 11-21-2021
 */
public class ChunkWrapper implements IChunkWrapper
{
    private ChunkAccess chunk;
    private LevelReader lightSource;

    @Override
    public int getHeight(){
        return 255;
    }

    @Override
    public int getMinBuildHeight()
    {
        return 0;
    }
    @Override
    public int getMaxBuildHeight()
    {
        return chunk.getMaxBuildHeight();
    }

    @Override
    public int getHeightMapValue(int xRel, int zRel)
    {
        return chunk.getOrCreateHeightmapUnprimed(WrapperUtil.DEFAULT_HEIGHTMAP).getFirstAvailable(xRel, zRel);
    }

    @Override
    public IBiomeWrapper getBiome(int x, int y, int z)
    {
        return BiomeWrapper.getBiomeWrapper(chunk.getBiomes().getNoiseBiome(
                x >> 2, y >> 2, z >> 2));
    }

    @Override
    public BlockDetail getBlockDetail(int x, int y, int z) {
        BlockState blockState = chunk.getBlockState(new BlockPos(x, y, z));
        return BlockDetailMap.getBlockDetailWithCompleteTint(blockState, x, y, z, lightSource);
    }

    @Deprecated
    public ChunkWrapper(ChunkAccess chunk)
    {
        this.chunk = chunk;
        this.lightSource = null;
    }
    public ChunkWrapper(ChunkAccess chunk, LevelReader lightSource) {
        this.chunk = chunk;
        this.lightSource = lightSource;
    }

    public ChunkAccess getChunk() {
        return chunk;
    }

    @Override
    public int getChunkPosX(){
        return chunk.getPos().x;
    }

    @Override
    public int getChunkPosZ(){
        return chunk.getPos().z;
    }

    @Override
    public int getRegionPosX(){
        return LevelPosUtil.convert(LodUtil.CHUNK_DETAIL_LEVEL, getChunkPosX(), LodUtil.REGION_DETAIL_LEVEL);
    }

    @Override
    public int getRegionPosZ(){
        return LevelPosUtil.convert(LodUtil.CHUNK_DETAIL_LEVEL, getChunkPosZ(), LodUtil.REGION_DETAIL_LEVEL);
    }

    @Override
    public int getMaxY(int x, int z) {
        return chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, Math.floorMod(x, 16), Math.floorMod(z, 16));
    }

    @Override
    public int getMaxX(){
        return chunk.getPos().getMaxBlockX();
    }
    @Override
    public int getMaxZ(){
        return chunk.getPos().getMaxBlockZ();
    }
    @Override
    public int getMinX(){
        return chunk.getPos().getMinBlockX();
    }
    @Override
    public int getMinZ() {
        return chunk.getPos().getMinBlockZ();
    }

    @Override
    public boolean isLightCorrect(){
        return true;//chunk.isLightCorrect();
    }

    public boolean isWaterLogged(int x, int y, int z)
    {
        BlockState blockState = chunk.getBlockState(new BlockPos(x,y,z));

        //This type of block is always in water
        return (!(blockState.getBlock() instanceof LiquidBlockContainer) && (blockState.getBlock() instanceof SimpleWaterloggedBlock))
                && (blockState.hasProperty(BlockStateProperties.WATERLOGGED) && blockState.getValue(BlockStateProperties.WATERLOGGED));
    }

    @Override
    public int getEmittedBrightness(int x, int y, int z)
    {
        return chunk.getLightEmission(new BlockPos(x,y,z));
    }

    @Override
    public int getBlockLight(int x, int y, int z) {
        if (lightSource == null) return -1;
        return lightSource.getBrightness(LightLayer.BLOCK, new BlockPos(x,y,z));
    }

    @Override
    public int getSkyLight(int x, int y, int z) {
        if (lightSource == null) return -1;
        return lightSource.getBrightness(LightLayer.SKY, new BlockPos(x, y, z));
    }

	@Override
	public long getLongChunkPos() {
		return chunk.getPos().toLong();
	}

    @Override
    public boolean doesNearbyChunksExist() {
        if (lightSource instanceof LightedWorldGenRegion) return true;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (lightSource.getChunk(dx + getChunkPosX(), dz + getChunkPosZ(), ChunkStatus.BIOMES, false) == null)
                    return false;
            }
        }
        return true;
    }
}
