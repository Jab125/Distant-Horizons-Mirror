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

#if MC_VER > MC_1_18_2 && MC_VER <= MC_26_2_0

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.resources.ResourceKey;
#if MC_VER <= MC_1_19_2
import net.minecraft.data.BuiltinRegistries;
#else
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
#endif
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

import static org.junit.Assert.*;

public class LegacyRoughSurfaceDensitySamplerTest
{
	@BeforeClass
	public static void bootstrap()
	{
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void vanillaOverworldSamplingTimings()
	{
		#if MC_VER <= MC_1_19_2
		RandomState state = RandomState.create(BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(NoiseGeneratorSettings.OVERWORLD),
			BuiltinRegistries.NOISE, 42);
		#else
		HolderLookup.Provider registries = VanillaRegistries.createLookup();
		RandomState state = RandomState.create(registries.lookupOrThrow(Registries.NOISE_SETTINGS)
			.getOrThrow(NoiseGeneratorSettings.OVERWORLD).value(), registries.lookupOrThrow(Registries.NOISE), 42);
		#endif
		DensityFunction original = state.router().finalDensity();
		for (int warmup = 0; warmup < 3; warmup++)
		{
			timeGraph(original, false);
			timeGraph(original, true);
		}
		for (int pair = 0; pair < 5; pair++)
		{
			long[] first = timeGraph(original, pair % 2 != 0);
			long[] second = timeGraph(original, pair % 2 == 0);
			assertEquals(first[1], second[1]);
			long baseline = pair % 2 == 0 ? first[0] : second[0];
			long cached = pair % 2 == 0 ? second[0] : first[0];
			System.out.println("Scalar sampling pair " + pair + ": baseline=" + baseline + " ns, cached=" + cached + " ns");
		}
	}

	private static long[] timeGraph(DensityFunction original, boolean cached)
	{
		LegacyRoughSurfaceDensitySampler sampler = new LegacyRoughSurfaceDensitySampler(original);
		long start = System.nanoTime();
		if (cached)
		{
			sampler.begin();
		}
		double sum = 0;
		try
		{
			for (int x = -8; x < 8; x += 2)
			{
				for (int z = -8; z < 8; z += 2)
				{
					for (int y = 320; y >= -64; y -= 8)
					{
						sum += cached ? sampler.sampleValue(x, y, z)
							: original.compute(new DensityFunction.SinglePointContext(x, y, z));
					}
				}
			}
		}
		finally
		{
			sampler.end();
		}
		return new long[] {System.nanoTime() - start, Double.doubleToRawLongBits(sum)};
	}

	@Test
	public void vanillaDensityGraphsKeepExactScalarValues()
	{
		#if MC_VER > MC_1_19_2
		HolderLookup.Provider registries = VanillaRegistries.createLookup();
		#endif
		for (long seed : new long[] {0, -1, 42})
		{
			for (ResourceKey<NoiseGeneratorSettings> key : java.util.Arrays.asList(
				NoiseGeneratorSettings.OVERWORLD, NoiseGeneratorSettings.LARGE_BIOMES,
				NoiseGeneratorSettings.AMPLIFIED, NoiseGeneratorSettings.NETHER,
				NoiseGeneratorSettings.END, NoiseGeneratorSettings.CAVES,
				NoiseGeneratorSettings.FLOATING_ISLANDS))
			{
				#if MC_VER <= MC_1_19_2
				RandomState state = RandomState.create(BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(key),
					BuiltinRegistries.NOISE, seed);
				#else
				RandomState state = RandomState.create(registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(key).value(),
					registries.lookupOrThrow(Registries.NOISE), seed);
				#endif
				assertGraphValues(state.router().finalDensity());
			}
		}
	}

	private static void assertGraphValues(DensityFunction original)
	{
		LegacyRoughSurfaceDensitySampler sampler = new LegacyRoughSurfaceDensitySampler(original);
		sampler.begin();
		try
		{
			for (int x : new int[] {-65, -1, 0, 65})
			{
				for (int z : new int[] {-65, -1, 0, 65})
				{
					for (int y = 320; y >= -64; y -= 8)
					{
						double expected = original.compute(new DensityFunction.SinglePointContext(x, y, z));
						assertBits(expected, sampler.sampleValue(x, y, z));
						assertBits(expected, sampler.sampleValue(x, y, z));
					}
				}
			}
		}
		finally
		{
			sampler.end();
		}
	}

	@Test
	public void samplingRequiresAnActiveRequest()
	{
		LegacyRoughSurfaceDensitySampler sampler = new LegacyRoughSurfaceDensitySampler(DensityFunctions.zero());
		assertThrows(IllegalStateException.class, () -> sampler.sampleValue(0, 0, 0));
		sampler.begin();
		try
		{
			assertEquals(0.0, sampler.sampleValue(0, 0, 0), 0.0);
			assertThrows(IllegalStateException.class, sampler::begin);
		}
		finally
		{
			sampler.end();
		}
		assertThrows(IllegalStateException.class, () -> sampler.sampleValue(0, 0, 0));
		sampler.end();
	}

	@Test
	public void columnValuesAreReusedWithoutAssumingMarkersAreIndependentOfY()
	{
		CountingFunction input = new CountingFunction(point -> point.blockX() * 0.25 + point.blockZ());
		LegacyRoughSurfaceDensitySampler sampler = new LegacyRoughSurfaceDensitySampler(DensityFunctions.flatCache(input));
		sampler.begin();
		try
		{
			for (int y = -64; y <= 320; y++)
			{
				assertEquals(-2.75, sampler.sampleValue(-3, y, -2), 0.0);
			}
			assertEquals(1, input.calls.get());
			sampler.sampleValue(-2, 0, -2);
			assertEquals(2, input.calls.get());
		}
		finally
		{
			sampler.end();
		}
	}

	@Test
	public void markersKeepExactCoordinatesAndScalarArithmetic()
	{
		assertMarkerValues(DensityFunctions::flatCache);
		assertMarkerValues(DensityFunctions::cache2d);
		assertMarkerValues(DensityFunctions::cacheOnce);
		assertMarkerValues(DensityFunctions::cacheAllInCell);
		assertMarkerValues(DensityFunctions::interpolated);
	}

	private static void assertMarkerValues(Function<DensityFunction, DensityFunction> marker)
	{
		CountingFunction input = new CountingFunction(point ->
			Math.sin(point.blockX() * 0.13) + point.blockY() * 0.015625 - point.blockZ() * 0.31);
		DensityFunction original = marker.apply(input);
		LegacyRoughSurfaceDensitySampler sampler = new LegacyRoughSurfaceDensitySampler(original);
		int[] coordinates = {Integer.MIN_VALUE, -30000000, -4097, -65, -1, 0, 1, 64, 4096, 30000000, Integer.MAX_VALUE};
		sampler.begin();
		try
		{
			for (int x : coordinates)
			{
				for (int y : coordinates)
				{
					for (int z : coordinates)
					{
						double expected = original.compute(new DensityFunction.SinglePointContext(x, y, z));
						assertBits(expected, sampler.sampleValue(x, y, z));
						assertBits(expected, sampler.sampleValue(x, y, z));
					}
				}
			}
		}
		finally
		{
			sampler.end();
		}
	}

	@Test
	public void cachedChildrenPropagateTheirYDependency()
	{
		DensityFunction inner = DensityFunctions.cacheOnce(new CountingFunction(point -> point.blockY() * 0.125));
		DensityFunction graph = DensityFunctions.add(inner, DensityFunctions.flatCache(inner));
		LegacyRoughSurfaceDensitySampler sampler = new LegacyRoughSurfaceDensitySampler(graph);
		sampler.begin();
		try
		{
			for (int y : new int[] {0, 8, -8, 64, -64, 0})
			{
				assertBits(y * 0.25, sampler.sampleValue(0, y, 0));
			}
		}
		finally
		{
			sampler.end();
		}
	}

	@Test
	public void yDependencyCanChangeBetweenColumns()
	{
		CountingFunction input = new CountingFunction(point -> point.blockX() < 0 ? -0.0 : point.blockY() * 0.5);
		LegacyRoughSurfaceDensitySampler sampler = new LegacyRoughSurfaceDensitySampler(DensityFunctions.cache2d(input));
		sampler.begin();
		try
		{
			assertBits(-0.0, sampler.sampleValue(-1, 0, 0));
			assertBits(-0.0, sampler.sampleValue(-1, 100, 0));
			assertBits(0.0, sampler.sampleValue(1, 0, 0));
			assertBits(50.0, sampler.sampleValue(1, 100, 0));
		}
		finally
		{
			sampler.end();
		}
	}

	@Test
	public void eachRequestStartsWithEmptyCaches()
	{
		CountingFunction input = new CountingFunction(point -> 3.25);
		LegacyRoughSurfaceDensitySampler sampler = new LegacyRoughSurfaceDensitySampler(DensityFunctions.cacheOnce(input));
		for (int request = 1; request <= 3; request++)
		{
			sampler.begin();
			try
			{
				sampler.sampleValue(0, 0, 0);
				sampler.sampleValue(0, 0, 0);
				assertEquals(request, input.calls.get());
			}
			finally
			{
				sampler.end();
			}
		}
	}

	@Test
	public void failedSamplesDoNotPoisonCachedValues()
	{
		CountingFunction input = new CountingFunction(point ->
		{
			if (point.blockY() == 1)
			{
				throw new IllegalArgumentException("Test failure");
			}
			return point.blockY() * 0.5;
		});
		LegacyRoughSurfaceDensitySampler sampler = new LegacyRoughSurfaceDensitySampler(DensityFunctions.cacheOnce(input));
		sampler.begin();
		try
		{
			assertBits(0.0, sampler.sampleValue(0, 0, 0));
			assertThrows(IllegalArgumentException.class, () -> sampler.sampleValue(0, 1, 0));
			assertBits(0.0, sampler.sampleValue(0, 0, 0));
			assertBits(1.0, sampler.sampleValue(0, 2, 0));
		}
		finally
		{
			sampler.end();
		}
	}

	@Test
	public void generatorsDoNotShareCaches()
	{
		CountingFunction input = new CountingFunction(point -> point.blockY() * 0.5);
		DensityFunction graph = DensityFunctions.cacheOnce(input);
		LegacyRoughSurfaceDensitySampler first = new LegacyRoughSurfaceDensitySampler(graph);
		LegacyRoughSurfaceDensitySampler second = new LegacyRoughSurfaceDensitySampler(graph);
		first.begin();
		second.begin();
		try
		{
			assertBits(0.0, first.sampleValue(0, 0, 0));
			assertBits(0.5, second.sampleValue(0, 1, 0));
			assertBits(0.0, first.sampleValue(0, 0, 0));
			assertEquals(2, input.calls.get());
			first.end();
			assertBits(0.5, second.sampleValue(0, 1, 0));
		}
		finally
		{
			first.end();
			second.end();
		}
	}

	@Test
	public void failedRequestsReleaseWorkerCaches() throws Exception
	{
		CountingFunction input = new CountingFunction(point -> 3.25);
		LegacyRoughSurfaceDensitySampler sampler = new LegacyRoughSurfaceDensitySampler(DensityFunctions.cacheOnce(input));
		ExecutorService worker = Executors.newSingleThreadExecutor();
		try
		{
			Future<?> failure = worker.submit(() ->
			{
				sampler.begin();
				try
				{
					sampler.sampleValue(0, 0, 0);
					throw new IllegalArgumentException("Test failure");
				}
				finally
				{
					sampler.end();
				}
			});
			assertThrows(java.util.concurrent.ExecutionException.class, () -> failure.get(10, TimeUnit.SECONDS));
			worker.submit(() ->
			{
				assertThrows(IllegalStateException.class, () -> sampler.sampleValue(0, 0, 0));
				sampler.begin();
				try
				{
					assertBits(3.25, sampler.sampleValue(0, 0, 0));
				}
				finally
				{
					sampler.end();
				}
			}).get(10, TimeUnit.SECONDS);
			assertEquals(2, input.calls.get());
		}
		finally
		{
			worker.shutdownNow();
		}
	}

	@Test
	public void workersHaveIndependentCaches() throws Exception
	{
		CountingFunction input = new CountingFunction(point -> point.blockY() * 0.5);
		LegacyRoughSurfaceDensitySampler sampler = new LegacyRoughSurfaceDensitySampler(DensityFunctions.cacheOnce(input));
		ExecutorService workers = Executors.newFixedThreadPool(4);
		CyclicBarrier barrier = new CyclicBarrier(4);
		try
		{
			java.util.List<Future<Double>> results = new java.util.ArrayList<>();
			for (int i = 0; i < 4; i++)
			{
				final int y = i;
				results.add(workers.submit(() ->
				{
					sampler.begin();
					try
					{
						barrier.await(10, TimeUnit.SECONDS);
						sampler.sampleValue(0, y, 0);
						barrier.await(10, TimeUnit.SECONDS);
						return sampler.sampleValue(0, y, 0);
					}
					finally
					{
						sampler.end();
					}
				}));
			}
			for (int i = 0; i < 4; i++)
			{
				assertBits(i * 0.5, results.get(i).get(10, TimeUnit.SECONDS));
			}
			assertEquals(4, input.calls.get());
		}
		finally
		{
			workers.shutdownNow();
		}
	}

	private static void assertBits(double expected, double actual)
	{ assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual)); }

	private static final class CountingFunction implements DensityFunction.SimpleFunction
	{
		private final ToDoubleFunction<DensityFunction.FunctionContext> function;
		private final AtomicInteger calls = new AtomicInteger();

		private CountingFunction(ToDoubleFunction<DensityFunction.FunctionContext> function)
		{ this.function = function; }

		@Override
		public double compute(DensityFunction.FunctionContext context)
		{
			this.calls.incrementAndGet();
			return this.function.applyAsDouble(context);
		}

		@Override
		public double minValue()
		{ return Double.NEGATIVE_INFINITY; }

		@Override
		public double maxValue()
		{ return Double.POSITIVE_INFINITY; }

		@Override
		public KeyDispatchDataCodec<? extends DensityFunction> codec()
		{ throw new UnsupportedOperationException(); }
	}
}
#endif
