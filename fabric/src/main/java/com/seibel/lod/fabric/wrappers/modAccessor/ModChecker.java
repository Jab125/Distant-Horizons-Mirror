/*
 *    This file is part of the Distant Horizons mod (formerly the LOD Mod),
 *    licensed under the GNU GPL v3 License.
 *
 *    Copyright (C) 2020-2022  James Seibel
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
 
package com.seibel.lod.fabric.wrappers.modAccessor;

import com.seibel.lod.core.wrapperInterfaces.modAccessor.IModChecker;
import com.seibel.lod.fabric.Main;
import com.seibel.lod.fabric.quilt.QuiltUtils;
import net.fabricmc.loader.api.FabricLoader;

public class ModChecker implements IModChecker {
    public static final ModChecker INSTANCE = new ModChecker();

    @Override
    public boolean isModLoaded(String modid) {
        if (Main.isQuilt) {
            return QuiltUtils.isModLoaded(modid);
        } else {
            return FabricLoader.getInstance().isModLoaded(modid);
        }
    }
}
