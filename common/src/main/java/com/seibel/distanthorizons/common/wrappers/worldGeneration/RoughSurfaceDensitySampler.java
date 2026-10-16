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
package com.seibel.distanthorizons.common.wrappers.worldGeneration;

#if MC_VER <= MC_26_2_0
public class RoughSurfaceDensitySampler { }
#else

import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

public final class RoughSurfaceDensitySampler
{
	private static final ContextKey<Boolean> SCALAR_CACHE = new ContextKey<>(
		Identifier.fromNamespaceAndPath("distanthorizons", "rough_surface_scalar_cache"));

	private final DensitySampler sampler;
	private final ThreadLocal<DensitySampler.Bound> current = new ThreadLocal<>();

	public RoughSurfaceDensitySampler(DensitySampler sampler)
	{ this.sampler = sampler; }

	public void begin()
	{
		if (this.current.get() != null)
		{
			throw new IllegalStateException("Rough surface sampling is already active");
		}
		this.current.set(this.sampler.bind(createContext()));
	}

	public void end()
	{ this.current.remove(); }

	public float sampleValue(int x, int y, int z)
	{
		DensitySampler.Bound bound = this.current.get();
		if (bound == null)
		{
			throw new IllegalStateException("Rough surface sampling is not active");
		}
		return bound.sampleValue(x, y, z);
	}

	static SamplerContext createContext()
	{
		ContextMap fields = ContextMap.builder()
			.set(Beardifier.CONTEXT_KEY, Beardifier.EMPTY)
			.set(SCALAR_CACHE, true)
			.build();
		return SamplerContext.builder().setUserFields(fields).enableCaches().build();
	}

	public static int scalarIndex(SamplerContext context, DensityVolume volume, int x, int y, int z)
	{
		// Scalar and volume sampling can evaluate different arithmetic paths.
		return Boolean.TRUE.equals(context.getField(SCALAR_CACHE)) ? -1 : volume.indexOfBlock(x, y, z);
	}
}
#endif
