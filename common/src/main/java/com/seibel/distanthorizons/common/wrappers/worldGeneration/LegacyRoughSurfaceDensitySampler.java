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

#if MC_VER <= MC_1_18_2 || MC_VER > MC_26_2_0
public class LegacyRoughSurfaceDensitySampler { }
#else

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

import java.util.HashMap;
import java.util.Map;

public final class LegacyRoughSurfaceDensitySampler
{
	private final DensityFunction density;
	private final ThreadLocal<Request> current = new ThreadLocal<>();

	public LegacyRoughSurfaceDensitySampler(DensityFunction density)
	{ this.density = density; }

	public void begin()
	{
		if (this.current.get() != null)
		{
			throw new IllegalStateException("Rough surface sampling is already active");
		}
		this.current.set(new Request(this.density.mapAll(new CacheVisitor())));
	}

	public void end()
	{ this.current.remove(); }

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
		public double minValue()
		{ return this.input.minValue(); }

		@Override
		public double maxValue()
		{ return this.input.maxValue(); }

		@Override
		public KeyDispatchDataCodec<? extends DensityFunction> codec()
		{ return this.input.codec(); }
	}
}
#endif
