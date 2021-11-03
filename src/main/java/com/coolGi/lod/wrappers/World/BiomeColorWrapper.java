package com.coolGi.lod.wrappers.World;

import com.coolGi.lod.util.LodUtil;
import com.coolGi.lod.wrappers.Block.BlockPosWrapper;
import net.minecraft.client.renderer.BiomeColors;

import java.awt.*;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


public class BiomeColorWrapper
{

    public static int getGrassColor(WorldWrapper worldWrapper, BlockPosWrapper blockPosWrapper)
    {
        return BiomeColors.getAverageGrassColor(worldWrapper.getWorld(), blockPosWrapper.getBlockPos());
    }
    public static int getWaterColor(WorldWrapper worldWrapper, BlockPosWrapper blockPosWrapper)
    {

        return BiomeColors.getAverageWaterColor(worldWrapper.getWorld(), blockPosWrapper.getBlockPos());
    }
    public static int getFoliageColor(WorldWrapper worldWrapper, BlockPosWrapper blockPosWrapper)
    {

        return BiomeColors.getAverageFoliageColor(worldWrapper.getWorld(), blockPosWrapper.getBlockPos());
    }
}

