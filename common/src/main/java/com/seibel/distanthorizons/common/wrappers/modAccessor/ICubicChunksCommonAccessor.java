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

package com.seibel.distanthorizons.common.wrappers.modAccessor;

import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IModAccessor;
#if MC_VER <= MC_1_12_2
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.jetbrains.annotations.Nullable;
#endif

public interface ICubicChunksCommonAccessor extends IModAccessor
{
	#if MC_VER <= MC_1_12_2
	/**
	 * Returns the live (not copied) block storages of the cubes
	 * in the given column, from Y 0 - 256. <br>
	 * On the server missing cubes are loaded from disk or fully generated (populated and lit),
	 * on the client only already loaded cubes are returned. <br><br>
	 *
	 * Must be called on the thread that owns the world.
	 *
	 * @return null if the chunk isn't in a Cubic Chunks world
	 */
	@Nullable
	ExtendedBlockStorage[] getCubeStorages(Chunk chunk);

	/**
	 * Returns true if all of the column's cubes from Y 0 - 256 are loaded and lit,
	 * so {@link #getCubeStorages} doesn't need to load or generate anything. <br>
	 * Always true if the chunk isn't in a Cubic Chunks world. <br><br>
	 *
	 * Must be called on the thread that owns the world.
	 */
	boolean areAllCubesReady(Chunk chunk);

	/** @return true if the world is a Cubic Chunks world */
	boolean isCubicWorld(@Nullable World world);
	#endif
}
