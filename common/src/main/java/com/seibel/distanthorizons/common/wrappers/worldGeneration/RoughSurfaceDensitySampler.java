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

#if MC_VER <= MC_1_18_2
public class RoughSurfaceDensitySampler { }
#else

#if MC_VER <= MC_26_2_0
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

import java.util.HashMap;
import java.util.Map;
#else
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
#endif

public final class RoughSurfaceDensitySampler
{
	#if MC_VER <= MC_26_2_0
	private final DensityFunction density;
	private final ThreadLocal<Request> current = new ThreadLocal<>();

	public RoughSurfaceDensitySampler(DensityFunction density)
	{ this.density = density; }

	#else
	private static final ContextKey<Boolean> SCALAR_CACHE = new ContextKey<>(
		Identifier.fromNamespaceAndPath("distanthorizons", "rough_surface_scalar_cache"));

	private final DensitySampler sampler;
	private final ThreadLocal<DensitySampler.Bound> current = new ThreadLocal<>();

	public RoughSurfaceDensitySampler(DensitySampler sampler)
	{ this.sampler = sampler; }

	#endif

	public void begin()
	{
		if (this.current.get() != null)
		{
			throw new IllegalStateException("Rough surface sampling is already active");
		}
		#if MC_VER <= MC_26_2_0
		this.current.set(new Request(this.density.mapAll(new CacheVisitor())));
		#else
		this.current.set(this.sampler.bind(createContext()));
		#endif
	}

	public void end()
	{ this.current.remove(); }

	#if MC_VER <= MC_26_2_0
	public double sampleValue(int x, int y, int z)
	{
		Request request = this.current.get();
		if (request == null)
		{
			throw new IllegalStateException("Rough surface sampling is not active");
		}
		request.context.x = x;
		request.context.y = y;
		request.context.z = z;
		return request.density.compute(request.context);
	}

	private static final class Request
	{
		private final DensityFunction density;
		private final SampleContext context = new SampleContext();

		private Request(DensityFunction density)
		{ this.density = density; }
	}

	private static final class SampleContext implements DensityFunction.FunctionContext
	{
		private int x;
		private int y;
		private int z;
		private long yReads;

		@Override
		public int blockX()
		{ return this.x; }

		@Override
		public int blockY()
		{
			this.yReads++;
			return this.y;
		}

		@Override
		public int blockZ()
		{ return this.z; }
	}

	private static final class CacheVisitor implements DensityFunction.Visitor
	{
		private final Map<DensityFunction, DensityFunction> mapped = new HashMap<>();

		@Override
		public DensityFunction apply(DensityFunction function)
		{
			return this.mapped.computeIfAbsent(function, input ->
				input instanceof DensityFunctions.MarkerOrMarked
					? new CachedFunction(((DensityFunctions.MarkerOrMarked) input).wrapped())
					: input);
		}
	}

	private static final class CachedFunction implements DensityFunction.SimpleFunction
	{
		private final DensityFunction input;
		private int x;
		private int y;
		private int z;
		private double value;
		private boolean valid;
		private boolean readsY;

		private CachedFunction(DensityFunction input)
		{ this.input = input; }

		@Override
		public double compute(DensityFunction.FunctionContext context)
		{
			if (!(context instanceof SampleContext))
			{
				return this.input.compute(context);
			}
			SampleContext point = (SampleContext) context;
			if (this.valid && this.x == point.x && this.z == point.z
				&& (!this.readsY || this.y == point.y))
			{
				if (this.readsY)
				{
					// Enclosing caches must retain a cached child's Y dependency.
					point.yReads++;
				}
				return this.value;
			}
			return this.sample(point);
		}

		private double sample(SampleContext point)
		{
			// Reuse a column value only when its evaluation never reads Y.
			long before = point.yReads;
			double result = this.input.compute(point);
			this.x = point.x;
			this.y = point.y;
			this.z = point.z;
			this.value = result;
			this.readsY = before != point.yReads;
			this.valid = true;
			return result;
		}

		@Override
		public void fillArray(double[] output, DensityFunction.ContextProvider provider)
		{ this.input.fillArray(output, provider); }

		@Override
		public double minValue()
		{ return this.input.minValue(); }

		@Override
		public double maxValue()
		{ return this.input.maxValue(); }

		@Override
		public KeyDispatchDataCodec<? extends DensityFunction> codec()
		{ return this.input.codec(); }
	}

	#else
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

	#endif
}
#endif
