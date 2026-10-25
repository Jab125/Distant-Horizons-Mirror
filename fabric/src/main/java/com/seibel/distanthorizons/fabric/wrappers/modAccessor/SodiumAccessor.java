/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
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

package com.seibel.distanthorizons.fabric.wrappers.modAccessor;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.ISodiumAccessor;
import net.minecraft.client.Minecraft;

#if MC_VER > MC_26_2_0
import com.seibel.distanthorizons.core.render.VanillaSectionCoverage;
import com.seibel.distanthorizons.fabric.mixins.client.MixinSodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.LocalSectionIndex;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
#endif


#if MC_VER < MC_1_20_1
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
#endif
#if MC_VER < MC_1_17_1
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
#else 
#endif

public class SodiumAccessor implements ISodiumAccessor
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	/**
	 * This field is public because it's also used to check if we need Indium to be present <br>
	 * since Indium is needed for Sodium 0.5 and older.
	 */
	public static final boolean isSodiumV5OrLess;
	
	static 
	{
		isSodiumV5OrLess = !classPresent("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer");
	}
	
	
	
	//======================//
	// mod accessor methods //
	//======================//
	
	@Override
	public String getModName() { return "Sodium-Fabric"; }

	#if MC_VER > MC_26_2_0
	@Override
	public VanillaSectionCoverage getNativeTerrainCoverage()
	{
		SodiumWorldRenderer renderer = SodiumWorldRenderer.instanceNullable();
		if (renderer == null) return null;
		var manager = ((MixinSodiumWorldRenderer)(Object)renderer).distantHorizons$getSectionManager();
		if (manager == null) return null;
		var lists = manager.getRenderLists();
		int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		var iterator = lists.iterator(false);
		while (iterator.hasNext())
		{
			var list = iterator.next();
			if (list.getSectionsWithGeometryCount() == 0) continue;
			var region = list.getRegion();
			minX = Math.min(minX, region.getOriginX());
			minZ = Math.min(minZ, region.getOriginZ());
			maxX = Math.max(maxX, region.getOriginX() + RenderRegion.REGION_WIDTH * 16);
			maxZ = Math.max(maxZ, region.getOriginZ() + RenderRegion.REGION_LENGTH * 16);
		}
		if (minX == Integer.MAX_VALUE) return null;
		var level = Minecraft.getInstance().level;
		VanillaSectionCoverage coverage = new VanillaSectionCoverage(minX, level.getMinY(), minZ,
			(maxX - minX) / 16, (level.getHeight() + 15) / 16, (maxZ - minZ) / 16);
		iterator = lists.iterator(false);
		while (iterator.hasNext())
		{
			var list = iterator.next();
			var region = list.getRegion();
			var sections = list.sectionsWithGeometryIterator(false);
			if (sections == null) continue;
			while (sections.hasNext())
			{
				int index = sections.nextByteAsInt();
				coverage.include(region.getOriginX() + LocalSectionIndex.unpackX(index) * 16,
					region.getOriginY() + LocalSectionIndex.unpackY(index) * 16,
					region.getOriginZ() + LocalSectionIndex.unpackZ(index) * 16);
			}
		}
		return coverage;
	}
	#endif
	
	
	
	//================//
	// helper methods //
	//================//
	
	private static boolean classPresent(String className)
	{
		try
		{
			Class.forName(className);
			return true;
		}
		catch (ClassNotFoundException e)
		{
			return false;
		}
	}
	
}
