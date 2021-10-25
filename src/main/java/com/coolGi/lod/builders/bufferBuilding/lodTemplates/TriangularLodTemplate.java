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

package com.coolGi.lod.builders.bufferBuilding.lodTemplates;

import java.util.Map;

import com.coolGi.lod.enums.DebugMode;
import com.coolGi.lod.proxy.ClientProxy;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * TODO #21 TriangularLodTemplate
 * Builds each LOD chunk as a singular rectangular prism.
 *
 * @author coolGi2007
 * @author James Seibel
 * @version 10-24-2021
 */
public class TriangularLodTemplate extends AbstractLodTemplate
{
	@Override
	public void addLodToBuffer(BufferBuilder buffer, BlockPos bufferCenterBlockPos, long data, Map<Direction, long[]> adjData, byte detailLevel, int posX, int posZ, Box box, DebugMode debugging, NativeImage lightMap, boolean[] adjShadeDisabled) {
		ClientProxy.LOGGER.error(DynamicLodTemplate.class.getSimpleName() + " is not implemented!");
	}
}
