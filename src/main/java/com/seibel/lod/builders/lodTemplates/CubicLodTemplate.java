/*
 *    This file is part of the LOD Mod, licensed under the GNU GPL v3 License.
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
package com.seibel.lod.builders.lodTemplates;

import java.awt.Color;

import com.seibel.lod.enums.ShadingMode;
import com.seibel.lod.handlers.LodConfig;
import com.seibel.lod.objects.LevelPos;
import com.seibel.lod.objects.LodDataPoint;
import com.seibel.lod.util.LodUtil;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

/**
 * Builds LODs as rectangular prisms.
 *
 * @author James Seibel
 * @version 8-10-2021
 */
public class CubicLodTemplate extends AbstractLodTemplate
{
    private final int CULL_OFFSET = 16;

    public CubicLodTemplate()
    {

    }

    @Override
    public void addLodToBuffer(BufferBuilder buffer, BlockPos playerBlockPos, LodDataPoint data, LodDataPoint[][] adjData,
                               LevelPos levelPos, boolean debugging)
    {
        AxisAlignedBB bbox;

        int width = (int) Math.pow(2, levelPos.detailLevel);

        // add each LOD for the detail level
        bbox = generateBoundingBox(
                data.height,
                data.depth,
                width,
                levelPos.posX * width,
                0,
                levelPos.posZ * width);

        Color color = data.color;
        if (LodConfig.CLIENT.debugMode.get())
        {
            color = LodUtil.DEBUG_DETAIL_LEVEL_COLORS[levelPos.detailLevel];
        }

        if (bbox != null)
        {
            addBoundingBoxToBuffer(buffer, bbox, color, playerBlockPos, adjData);
        }

    }

    /*
     * @Override public void addLodToBuffer(BufferBuilder buffer,
     * LodQuadTreeDimension lodDim, LodQuadTreeNode lod, double xOffset, double
     * yOffset, double zOffset, boolean debugging) { AxisAlignedBB bbox;
     *
     * bbox = generateBoundingBox( lod.getLodDataPoint().height,
     * lod.getLodDataPoint().depth, lod.width, xOffset, yOffset, zOffset);
     *
     * Color color = lod.getLodDataPoint().color;
     *
     * if (bbox != null) { addBoundingBoxToBuffer(buffer, bbox, color); }
     *
     * }
     */

    private AxisAlignedBB generateBoundingBox(int height, int depth, int width, double xOffset, double yOffset, double zOffset)
    {
        // don't add an LOD if it is empty
        if (height == -1 && depth == -1)
            return null;

        if (depth == height)
        {
            // if the top and bottom points are at the same height
            // render this LOD as 1 block thick
            height++;
        }

        return new AxisAlignedBB(0, depth, 0, width, height, width).move(xOffset, yOffset, zOffset);
    }

    private void addBoundingBoxToBuffer(BufferBuilder buffer, AxisAlignedBB bb, Color c, BlockPos playerBlockPos, LodDataPoint[][] adjData)
    {
        Color topColor = c;
        Color northSouthColor = c;
        Color eastWestColor = c;
        Color bottomColor = c;

        // darken the bottom and side colors if requested
        if (LodConfig.CLIENT.shadingMode.get() == ShadingMode.DARKEN_SIDES)
        {
            // the side colors are different because
            // when using fast lighting in Minecraft the north/south
            // and east/west sides are different in a similar way
            int northSouthDarkenAmount = 25;
            int eastWestDarkenAmount = 50;
            int bottomDarkenAmount = 75;

            northSouthColor = new Color(Math.max(0, c.getRed() - northSouthDarkenAmount), Math.max(0, c.getGreen() - northSouthDarkenAmount), Math.max(0, c.getBlue() - northSouthDarkenAmount), c.getAlpha());
            eastWestColor = new Color(Math.max(0, c.getRed() - eastWestDarkenAmount), Math.max(0, c.getGreen() - eastWestDarkenAmount), Math.max(0, c.getBlue() - eastWestDarkenAmount), c.getAlpha());
            bottomColor = new Color(Math.max(0, c.getRed() - bottomDarkenAmount), Math.max(0, c.getGreen() - bottomDarkenAmount), Math.max(0, c.getBlue() - bottomDarkenAmount), c.getAlpha());
        }

        // apply the user specified saturation and brightness
        float saturationMultiplier = LodConfig.CLIENT.saturationMultiplier.get().floatValue();
        float brightnessMultiplier = LodConfig.CLIENT.brightnessMultiplier.get().floatValue();

        topColor = applySaturationAndBrightnessMultipliers(topColor, saturationMultiplier, brightnessMultiplier);
        northSouthColor = applySaturationAndBrightnessMultipliers(northSouthColor, saturationMultiplier, brightnessMultiplier);
        bottomColor = applySaturationAndBrightnessMultipliers(bottomColor, saturationMultiplier, brightnessMultiplier);
        int minY;
        int maxY;
        LodDataPoint data;
        /**TODO make all of this more automatic if possible*/
        if (playerBlockPos.getY() > bb.maxY - CULL_OFFSET)
        {
            // top (facing up)
            addPosAndColor(buffer, bb.minX, bb.maxY, bb.minZ, topColor.getRed(), topColor.getGreen(), topColor.getBlue(), topColor.getAlpha());
            addPosAndColor(buffer, bb.minX, bb.maxY, bb.maxZ, topColor.getRed(), topColor.getGreen(), topColor.getBlue(), topColor.getAlpha());
            addPosAndColor(buffer, bb.maxX, bb.maxY, bb.maxZ, topColor.getRed(), topColor.getGreen(), topColor.getBlue(), topColor.getAlpha());
            addPosAndColor(buffer, bb.maxX, bb.maxY, bb.minZ, topColor.getRed(), topColor.getGreen(), topColor.getBlue(), topColor.getAlpha());
        }
        if (playerBlockPos.getY() < bb.minY + CULL_OFFSET)
        {
            // bottom (facing down)
            addPosAndColor(buffer, bb.maxX, bb.minY, bb.minZ, bottomColor.getRed(), bottomColor.getGreen(), bottomColor.getBlue(), bottomColor.getAlpha());
            addPosAndColor(buffer, bb.maxX, bb.minY, bb.maxZ, bottomColor.getRed(), bottomColor.getGreen(), bottomColor.getBlue(), bottomColor.getAlpha());
            addPosAndColor(buffer, bb.minX, bb.minY, bb.maxZ, bottomColor.getRed(), bottomColor.getGreen(), bottomColor.getBlue(), bottomColor.getAlpha());
            addPosAndColor(buffer, bb.minX, bb.minY, bb.minZ, bottomColor.getRed(), bottomColor.getGreen(), bottomColor.getBlue(), bottomColor.getAlpha());
        }

        if (playerBlockPos.getZ() > bb.minZ - CULL_OFFSET)
        {
            // south (facing -Z)

            data = adjData[1][1];
            if (data == null)
            {
                addPosAndColor(buffer, bb.maxX, bb.minY, bb.maxZ, northSouthColor.getRed(), northSouthColor.getGreen(), northSouthColor.getBlue(), northSouthColor.getAlpha());
                addPosAndColor(buffer, bb.maxX, bb.maxY, bb.maxZ, northSouthColor.getRed(), northSouthColor.getGreen(), northSouthColor.getBlue(), northSouthColor.getAlpha());
                addPosAndColor(buffer, bb.minX, bb.maxY, bb.maxZ, northSouthColor.getRed(), northSouthColor.getGreen(), northSouthColor.getBlue(), northSouthColor.getAlpha());
                addPosAndColor(buffer, bb.minX, bb.minY, bb.maxZ, northSouthColor.getRed(), northSouthColor.getGreen(), northSouthColor.getBlue(), northSouthColor.getAlpha());
            } else
            {
                maxY = data.height;
                if (maxY < bb.maxY)
                {
                    minY = (int) Math.max(maxY, bb.minY);
                    addPosAndColor(buffer, bb.maxX, minY, bb.maxZ, northSouthColor.getRed(), northSouthColor.getGreen(), northSouthColor.getBlue(), northSouthColor.getAlpha());
                    addPosAndColor(buffer, bb.maxX, bb.maxY, bb.maxZ, northSouthColor.getRed(), northSouthColor.getGreen(), northSouthColor.getBlue(), northSouthColor.getAlpha());
                    addPosAndColor(buffer, bb.minX, bb.maxY, bb.maxZ, northSouthColor.getRed(), northSouthColor.getGreen(), northSouthColor.getBlue(), northSouthColor.getAlpha());
                    addPosAndColor(buffer, bb.minX, minY, bb.maxZ, northSouthColor.getRed(), northSouthColor.getGreen(), northSouthColor.getBlue(), northSouthColor.getAlpha());
                }
                minY = data.depth;
                if (minY > bb.minY)
                {
                    maxY = (int) Math.min(minY, bb.maxX);
                    addPosAndColor(buffer, bb.maxX, bb.minY, bb.maxZ, northSouthColor.getRed(), northSouthColor.getGreen(), northSouthColor.getBlue(), northSouthColor.getAlpha());
                    addPosAndColor(buffer, bb.maxX, maxY, bb.maxZ, northSouthColor.getRed(), northSouthColor.getGreen(), northSouthColor.getBlue(), northSouthColor.getAlpha());
                    addPosAndColor(buffer, bb.minX, maxY, bb.maxZ, northSouthColor.getRed(), northSouthColor.getGreen(), northSouthColor.getBlue(), northSouthColor.getAlpha());
                    addPosAndColor(buffer, bb.minX, bb.minY, bb.maxZ, northSouthColor.getRed(), northSouthColor.getGreen(), northSouthColor.getBlue(), northSouthColor.getAlpha());
                }
            }
        }

        if (playerBlockPos.getZ() < bb.maxZ + CULL_OFFSET)
        {
            data = adjData[1][0];
            // north (facing +Z)
            if (data == null)
            {
                addPosAndColor(buffer, bb.minX, bb.minY, bb.minZ, northSouthColor.getRed(), northSouthColor.getGreen(), northSouthColor.getBlue(), northSouthColor.getAlpha());
                addPosAndColor(buffer, bb.minX, bb.maxY, bb.minZ, northSouthColor.getRed(), northSouthColor.getGreen(), northSouthColor.getBlue(), northSouthColor.getAlpha());
                addPosAndColor(buffer, bb.maxX, bb.maxY, bb.minZ, northSouthColor.getRed(), northSouthColor.getGreen(), northSouthColor.getBlue(), northSouthColor.getAlpha());
                addPosAndColor(buffer, bb.maxX, bb.minY, bb.minZ, northSouthColor.getRed(), northSouthColor.getGreen(), northSouthColor.getBlue(), northSouthColor.getAlpha());
            } else
            {
                maxY = data.height;
                if (maxY < bb.maxY)
                {
                    minY = (int) Math.max(maxY, bb.minY);
                    addPosAndColor(buffer, bb.minX, minY, bb.minZ, northSouthColor.getRed(), northSouthColor.getGreen(), northSouthColor.getBlue(), northSouthColor.getAlpha());
                    addPosAndColor(buffer, bb.minX, bb.maxY, bb.minZ, northSouthColor.getRed(), northSouthColor.getGreen(), northSouthColor.getBlue(), northSouthColor.getAlpha());
                    addPosAndColor(buffer, bb.maxX, bb.maxY, bb.minZ, northSouthColor.getRed(), northSouthColor.getGreen(), northSouthColor.getBlue(), northSouthColor.getAlpha());
                    addPosAndColor(buffer, bb.maxX, minY, bb.minZ, northSouthColor.getRed(), northSouthColor.getGreen(), northSouthColor.getBlue(), northSouthColor.getAlpha());
                }
                minY = data.depth;
                if (minY > bb.minY)
                {
                    maxY = (int) Math.min(minY, bb.maxX);
                    addPosAndColor(buffer, bb.minX, bb.minY, bb.minZ, northSouthColor.getRed(), northSouthColor.getGreen(), northSouthColor.getBlue(), northSouthColor.getAlpha());
                    addPosAndColor(buffer, bb.minX, maxY, bb.minZ, northSouthColor.getRed(), northSouthColor.getGreen(), northSouthColor.getBlue(), northSouthColor.getAlpha());
                    addPosAndColor(buffer, bb.maxX, maxY, bb.minZ, northSouthColor.getRed(), northSouthColor.getGreen(), northSouthColor.getBlue(), northSouthColor.getAlpha());
                    addPosAndColor(buffer, bb.maxX, bb.minY, bb.minZ, northSouthColor.getRed(), northSouthColor.getGreen(), northSouthColor.getBlue(), northSouthColor.getAlpha());
                }
            }
        }

        if (playerBlockPos.getX() < bb.maxX + CULL_OFFSET)
        {
            // west (facing -X)
            data = adjData[0][0];
            if (data == null)
            {
                addPosAndColor(buffer, bb.minX, bb.minY, bb.minZ, eastWestColor.getRed(), eastWestColor.getGreen(), eastWestColor.getBlue(), eastWestColor.getAlpha());
                addPosAndColor(buffer, bb.minX, bb.minY, bb.maxZ, eastWestColor.getRed(), eastWestColor.getGreen(), eastWestColor.getBlue(), eastWestColor.getAlpha());
                addPosAndColor(buffer, bb.minX, bb.maxY, bb.maxZ, eastWestColor.getRed(), eastWestColor.getGreen(), eastWestColor.getBlue(), eastWestColor.getAlpha());
                addPosAndColor(buffer, bb.minX, bb.maxY, bb.minZ, eastWestColor.getRed(), eastWestColor.getGreen(), eastWestColor.getBlue(), eastWestColor.getAlpha());
            } else
            {
                maxY = data.height;
                if (maxY < bb.maxY)
                {
                    minY = (int) Math.max(maxY, bb.minY);
                    addPosAndColor(buffer, bb.minX, minY, bb.minZ, eastWestColor.getRed(), eastWestColor.getGreen(), eastWestColor.getBlue(), eastWestColor.getAlpha());
                    addPosAndColor(buffer, bb.minX, minY, bb.maxZ, eastWestColor.getRed(), eastWestColor.getGreen(), eastWestColor.getBlue(), eastWestColor.getAlpha());
                    addPosAndColor(buffer, bb.minX, bb.maxY, bb.maxZ, eastWestColor.getRed(), eastWestColor.getGreen(), eastWestColor.getBlue(), eastWestColor.getAlpha());
                    addPosAndColor(buffer, bb.minX, bb.maxY, bb.minZ, eastWestColor.getRed(), eastWestColor.getGreen(), eastWestColor.getBlue(), eastWestColor.getAlpha());
                }
                minY = data.depth;
                if (minY > bb.minY)
                {
                    maxY = (int) Math.min(minY, bb.maxX);
                    addPosAndColor(buffer, bb.minX, bb.minY, bb.minZ, eastWestColor.getRed(), eastWestColor.getGreen(), eastWestColor.getBlue(), eastWestColor.getAlpha());
                    addPosAndColor(buffer, bb.minX, bb.minY, bb.maxZ, eastWestColor.getRed(), eastWestColor.getGreen(), eastWestColor.getBlue(), eastWestColor.getAlpha());
                    addPosAndColor(buffer, bb.minX, maxY, bb.maxZ, eastWestColor.getRed(), eastWestColor.getGreen(), eastWestColor.getBlue(), eastWestColor.getAlpha());
                    addPosAndColor(buffer, bb.minX, maxY, bb.minZ, eastWestColor.getRed(), eastWestColor.getGreen(), eastWestColor.getBlue(), eastWestColor.getAlpha());
                }
            }
        }

        if (playerBlockPos.getX() > bb.minX - CULL_OFFSET)
        {
            // east (facing +X)
            data = adjData[0][1];
            if (data == null)
            {
                addPosAndColor(buffer, bb.maxX, bb.maxY, bb.minZ, eastWestColor.getRed(), eastWestColor.getGreen(), eastWestColor.getBlue(), eastWestColor.getAlpha());
                addPosAndColor(buffer, bb.maxX, bb.maxY, bb.maxZ, eastWestColor.getRed(), eastWestColor.getGreen(), eastWestColor.getBlue(), eastWestColor.getAlpha());
                addPosAndColor(buffer, bb.maxX, bb.minY, bb.maxZ, eastWestColor.getRed(), eastWestColor.getGreen(), eastWestColor.getBlue(), eastWestColor.getAlpha());
                addPosAndColor(buffer, bb.maxX, bb.minY, bb.minZ, eastWestColor.getRed(), eastWestColor.getGreen(), eastWestColor.getBlue(), eastWestColor.getAlpha());
            } else
            {
                maxY = data.height;
                if (maxY < bb.maxY)
                {
                    minY = (int) Math.max(maxY, bb.minY);
                    addPosAndColor(buffer, bb.maxX, bb.maxY, bb.minZ, eastWestColor.getRed(), eastWestColor.getGreen(), eastWestColor.getBlue(), eastWestColor.getAlpha());
                    addPosAndColor(buffer, bb.maxX, bb.maxY, bb.maxZ, eastWestColor.getRed(), eastWestColor.getGreen(), eastWestColor.getBlue(), eastWestColor.getAlpha());
                    addPosAndColor(buffer, bb.maxX, minY, bb.maxZ, eastWestColor.getRed(), eastWestColor.getGreen(), eastWestColor.getBlue(), eastWestColor.getAlpha());
                    addPosAndColor(buffer, bb.maxX, minY, bb.minZ, eastWestColor.getRed(), eastWestColor.getGreen(), eastWestColor.getBlue(), eastWestColor.getAlpha());
                }
                minY = data.depth;
                if (minY > bb.minY)
                {
                    maxY = (int) Math.min(minY, bb.maxX);
                    addPosAndColor(buffer, bb.maxX, maxY, bb.minZ, eastWestColor.getRed(), eastWestColor.getGreen(), eastWestColor.getBlue(), eastWestColor.getAlpha());
                    addPosAndColor(buffer, bb.maxX, maxY, bb.maxZ, eastWestColor.getRed(), eastWestColor.getGreen(), eastWestColor.getBlue(), eastWestColor.getAlpha());
                    addPosAndColor(buffer, bb.maxX, bb.minY, bb.maxZ, eastWestColor.getRed(), eastWestColor.getGreen(), eastWestColor.getBlue(), eastWestColor.getAlpha());
                    addPosAndColor(buffer, bb.maxX, bb.minY, bb.minZ, eastWestColor.getRed(), eastWestColor.getGreen(), eastWestColor.getBlue(), eastWestColor.getAlpha());
                }
            }
        }
    }

    @Override
    public int getBufferMemoryForSingleNode(int detailLevel)
    {
        // (sidesOnACube * pointsInASquare * (positionPoints + colorPoints))) *
        // howManyPointsPerLodChunk
        return (6 * 4 * (3 + 4));
    }

}
