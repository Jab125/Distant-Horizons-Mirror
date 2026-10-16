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
package com.seibel.distanthorizons.neoforge.mixins.server;

import org.spongepowered.asm.mixin.Mixin;

#if MC_VER <= MC_26_2_0
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "net.minecraft.world.level.levelgen.densityfunction.SamplerContext", remap = false)
public class MixinSamplerContext { }
#else

import com.seibel.distanthorizons.common.wrappers.worldGeneration.RoughSurfaceDensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SamplerContext.class)
public class MixinSamplerContext
{
	@Redirect(method = "sampleValueCached", at = @At(value = "INVOKE", target =
		"Lnet/minecraft/world/level/levelgen/densityfunction/DensityVolume;indexOfBlock(III)I"))
	private int scalarIndex(DensityVolume volume, int x, int y, int z)
	{
		return RoughSurfaceDensitySampler.scalarIndex((SamplerContext) (Object) this, volume, x, y, z);
	}
}
#endif
