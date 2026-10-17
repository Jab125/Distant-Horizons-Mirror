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
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Interval;
import net.minecraft.util.context.ContextKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.op.CacheFunction;

import java.util.HashMap;
import java.util.Map;
#endif

public final class RoughSurfaceDensitySampler
{
	#if MC_VER <= MC_26_2_0
	private final DensityFunction density;
	private final ThreadLocal<Request> current = new ThreadLocal<>();

	public RoughSurfaceDensitySampler(DensityFunction density)
	{ this.density = density; }

	#else
	private static final ContextKey<Request> REQUEST = new ContextKey<>(
		Identifier.fromNamespaceAndPath("distanthorizons", "rough_surface_request"));

	private final DensitySampler sampler;
	private final int cacheCount;
	private final ThreadLocal<Request> current = new ThreadLocal<>();

	public RoughSurfaceDensitySampler(RandomState randomState, DensityFunction density)
	{
		CacheRoot root = new CacheRoot(density);
		this.sampler = randomState.getSampler(root);
		this.cacheCount = root.cacheCount;
	}

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
		Request request = new Request(this.cacheCount);
		ContextMap fields = ContextMap.builder()
			.set(Beardifier.CONTEXT_KEY, Beardifier.EMPTY)
			.set(REQUEST, request)
			.build();
		request.bound = this.sampler.bind(SamplerContext.builder().setUserFields(fields).build());
		this.current.set(request);
		#endif
	}

	public void end()
	{
		#if MC_VER <= MC_26_2_0
		this.current.remove();
		#else
		Request request = this.current.get();
		try
		{
			if (request != null)
			{
				request.close();
			}
		}
		finally
		{
			this.current.remove();
		}
		#endif
	}

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
	{ return this.activeRequest().bound.sampleValue(x, y, z); }

	void sampleVolume(DensityBuffer output, DensityVolume volume)
	{ this.activeRequest().bound.sampleVolume(output, volume); }

	private Request activeRequest()
	{
		Request request = this.current.get();
		if (request == null)
		{
			throw new IllegalStateException("Rough surface sampling is not active");
		}
		return request;
	}

	private static final class Request
	{
		private final CacheCell[] cells;
		private DensitySampler.Bound bound;

		private Request(int count)
		{ this.cells = new CacheCell[count]; }

		private CacheCell cell(int index)
		{
			CacheCell cell = this.cells[index];
			if (cell == null)
			{
				this.cells[index] = cell = new CacheCell();
			}
			return cell;
		}

		private void close()
		{
			for (CacheCell cell : this.cells)
			{
				if (cell != null && cell.buffer != null)
				{
					cell.buffer.close();
				}
			}
		}
	}

	private static final class CacheCell
	{
		private long valueKey;
		private float value = Float.NaN;
		private DensityVolume volume;
		private ScopedDensityBuffer buffer;
	}

	private record CachedSampler(int index, DensitySampler input) implements DensitySampler
	{
		private CacheCell cell(SamplerContext context)
		{ return context.getField(REQUEST).cell(this.index); }

		@Override
		public float sampleValue(SamplerContext context, int x, int y, int z)
		{
			CacheCell cell = this.cell(context);
			long key = BlockPos.asLong(x, y, z);
			if (cell.valueKey == key && !Float.isNaN(cell.value))
			{
				return cell.value;
			}
			// Scalar cache entries never come from a sampled volume.
			float value = this.input.sampleValue(context, x, y, z);
			cell.valueKey = key;
			cell.value = value;
			return value;
		}

		@Override
		public void sampleVolume(SamplerContext context, DensityBuffer output, DensityVolume volume)
		{
			CacheCell cell = this.cell(context);
			if (cell.buffer == null || !volume.equals(cell.volume))
			{
				this.fill(context, cell, volume);
			}
			output.copyFrom(cell.buffer);
		}

		private void fill(SamplerContext context, CacheCell cell, DensityVolume volume)
		{
			ScopedDensityBuffer buffer = context.acquireBuffer(volume);
			try
			{
				this.input.sampleVolume(context, buffer, volume);
			}
			catch (RuntimeException | Error failure)
			{
				buffer.close();
				throw failure;
			}
			if (cell.buffer != null)
			{
				cell.buffer.close();
			}
			cell.volume = volume;
			cell.buffer = buffer;
		}
	}

	private static final class CacheRoot implements DensityFunction
	{
		private final DensityFunction source;
		private int cacheCount;

		private CacheRoot(DensityFunction source)
		{ this.source = source; }

		@Override
		public DensitySampler compileSampler(CompileContext context)
		{
			CacheGraph graph = new CacheGraph(context);
			DensitySampler sampler = graph.prepare(this.source).compileSampler(context);
			this.cacheCount = graph.nextSlot;
			return sampler;
		}

		// Keep graph preparation inside the native compile context.
		@Override
		public DensityFunction rewriteChildren(DfRewriteRule rule)
		{ return this; }

		@Override
		public Interval range()
		{ return this.source.range(); }

		@Override
		public int domainAxes()
		{ return ALL_AXES; }

		@Override
		public MapCodec<? extends DensityFunction> codec()
		{ throw new UnsupportedOperationException(); }
	}

	private static final class CacheGraph
	{
		private final DensityFunction.CompileContext context;
		private final Map<DensityFunction, PreparedDensity> prepared = new HashMap<>();
		private int nextSlot;

		private CacheGraph(DensityFunction.CompileContext context)
		{ this.context = context; }

		private DensityFunction prepare(DensityFunction input)
		{
			// Match native cache deduplication before uniform-axis slicing.
			return DfRewriteRule.SLICE_UNIFORM_AXES.rewrite(this.rewrite(input));
		}

		private DensityFunction rewrite(DensityFunction input)
		{
			DensityFunction function = DfRewriteRule.INLINE_REFERENCE.rewrite(input);
			return function instanceof CacheFunction cache
				? this.cache(cache.input()) : function.rewriteChildren(this::rewrite);
		}

		private PreparedDensity cache(DensityFunction input)
		{
			PreparedDensity value = this.prepared.get(input);
			if (value == null)
			{
				int index = this.nextSlot++;
				DensityFunction child = this.prepare(input);
				DensitySampler sampler = new CachedSampler(index, child.compileSampler(this.context));
				value = new PreparedDensity(child.range(), child.domainAxes(), sampler);
				this.prepared.put(input, value);
			}
			return value;
		}
	}

	private record PreparedDensity(Interval range, int domainAxes, DensitySampler sampler) implements DensityFunction
	{
		@Override
		public DensitySampler compileSampler(CompileContext context)
		{ return this.sampler; }

		@Override
		public DensityFunction rewriteChildren(DfRewriteRule rule)
		{ return this; }

		@Override
		public MapCodec<? extends DensityFunction> codec()
		{ throw new UnsupportedOperationException(); }
	}

	#endif
}
#endif
