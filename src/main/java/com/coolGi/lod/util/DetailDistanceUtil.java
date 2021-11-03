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

package com.coolGi.lod.util;

import com.coolGi.lod.config.LodConfig;
import com.coolGi.lod.enums.DistanceGenerationMode;
import com.coolGi.lod.enums.HorizontalQuality;
import com.coolGi.lod.enums.HorizontalResolution;
import com.coolGi.lod.wrappers.MinecraftWrapper;

/**
 * 
 * @author Leonardo Amato
 * @version 10-24-2021
 */
public class DetailDistanceUtil
{
	private static final double genMultiplier = 1.0;
	private static final double treeGenMultiplier = 1.0;
	private static final double treeCutMultiplier = 1.0;
	private static int minGenDetail = LodConfig.Client.Graphics.QualityOption.drawResolution.detailLevel;
	private static int minDrawDetail = Math.max(LodConfig.Client.Graphics.QualityOption.drawResolution.detailLevel, LodConfig.Client.Graphics.QualityOption.drawResolution.detailLevel);
	private static final int maxDetail = LodUtil.REGION_DETAIL_LEVEL + 1;
	private static final int minDistance = 0;
	private static int minDetailDistance = (int) (MinecraftWrapper.INSTANCE.getRenderDistance()*16 * 1.42f);
	private static int maxDistance = LodConfig.Client.Graphics.QualityOption.lodChunkRenderDistance * 16 * 2;
	
	
	private static final HorizontalResolution[] lodGenDetails = {
			HorizontalResolution.BLOCK,
			HorizontalResolution.TWO_BLOCKS,
			HorizontalResolution.FOUR_BLOCKS,
			HorizontalResolution.HALF_CHUNK,
			HorizontalResolution.CHUNK,
			HorizontalResolution.CHUNK,
			HorizontalResolution.CHUNK,
			HorizontalResolution.CHUNK,
			HorizontalResolution.CHUNK,
			HorizontalResolution.CHUNK,
			HorizontalResolution.CHUNK };
	
	
	
	public static void updateSettings()
	{
		minDetailDistance = (int) (MinecraftWrapper.INSTANCE.getRenderDistance()*16 * 1.42f);
		minGenDetail = LodConfig.Client.Graphics.QualityOption.drawResolution.detailLevel;
		minDrawDetail = Math.max(LodConfig.Client.Graphics.QualityOption.drawResolution.detailLevel, LodConfig.Client.Graphics.QualityOption.drawResolution.detailLevel);
		maxDistance = LodConfig.Client.Graphics.QualityOption.lodChunkRenderDistance * 16 * 8;
	}
	
	public static int baseDistanceFunction(int detail)
	{
		if (detail <= minGenDetail)
			return minDistance;
		if (detail >= maxDetail)
			return maxDistance;
		
		int distanceUnit = LodConfig.Client.Graphics.QualityOption.horizontalScale.distanceUnit;
		if (LodConfig.Client.Graphics.QualityOption.horizontalQuality == HorizontalQuality.LOWEST)
			return (detail * distanceUnit);
		else
		{
			double base = LodConfig.Client.Graphics.QualityOption.horizontalQuality.quadraticBase;
			return (int) (Math.pow(base, detail) * distanceUnit);
		}
	}
	
	public static int getDrawDistanceFromDetail(int detail)
	{
		return baseDistanceFunction(detail);
	}
	
	public static byte baseInverseFunction(int distance, int minDetail, boolean useRenderMinDistance)
	{
		int detail;
		if (distance == 0)
			return (byte) minDetail;
		if (distance < minDetailDistance && useRenderMinDistance)
			return (byte) minDetail;
		int distanceUnit = LodConfig.Client.Graphics.QualityOption.horizontalScale.distanceUnit;
		if (LodConfig.Client.Graphics.QualityOption.horizontalQuality == HorizontalQuality.LOWEST)
			detail = (byte) distance/distanceUnit;
		else
		{
			double base = LodConfig.Client.Graphics.QualityOption.horizontalQuality.quadraticBase;
			double logBase = Math.log(base);
			//noinspection IntegerDivisionInFloatingPointContext
			detail = (byte) (Math.log(distance / distanceUnit) / logBase);

		}
		return (byte) LodUtil.clamp(minDetail, detail, maxDetail - 1);
	}
	
	public static byte getDrawDetailFromDistance(int distance)
	{
		return baseInverseFunction(distance, minDrawDetail, false);
	}
	
	public static byte getGenerationDetailFromDistance(int distance)
	{
		return baseInverseFunction((int) (distance * genMultiplier), minGenDetail, true);
	}
	
	public static byte getTreeCutDetailFromDistance(int distance)
	{
		return baseInverseFunction((int) (distance * treeCutMultiplier), minGenDetail, true);
	}
	
	
	public static byte getTreeGenDetailFromDistance(int distance)
	{
		return baseInverseFunction((int) (distance * treeGenMultiplier), minGenDetail, true);
	}
	
	public static DistanceGenerationMode getDistanceGenerationMode(int detail)
	{
		return LodConfig.Client.WorldGenerator.distanceGenerationMode;
	}
	
	public static byte getLodDrawDetail(int detail)
	{
		if (detail < minDrawDetail)
		{
			if (LodConfig.Client.Graphics.AdvancedGraphicsOption.alwaysDrawAtMaxQuality)
				return getLodGenDetail(minDrawDetail).detailLevel;
			else
				return (byte) minDrawDetail;
		}
		else
		{
			if (LodConfig.Client.Graphics.AdvancedGraphicsOption.alwaysDrawAtMaxQuality)
				return getLodGenDetail(detail).detailLevel;
			else
				return (byte) detail;
		}
	}
	
	public static HorizontalResolution getLodGenDetail(int detail)
	{
		if (detail < minGenDetail)
			return lodGenDetails[minGenDetail];
		else
			return lodGenDetails[detail];
	}
	
	
	public static byte getCutLodDetail(int detail)
	{
		if (detail < minGenDetail)
			return lodGenDetails[minGenDetail].detailLevel;
		else if (detail == maxDetail)
			return LodUtil.REGION_DETAIL_LEVEL;
		else
			return lodGenDetails[detail].detailLevel;
	}
	
	public static int getMaxVerticalData(int detail)
	{
		return LodConfig.Client.Graphics.QualityOption.verticalQuality.maxVerticalData[LodUtil.clamp(minGenDetail, detail, LodUtil.REGION_DETAIL_LEVEL)];
	}
	
}
