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

#if MC_VER > MC_26_2_0

import com.mojang.serialization.MapCodec;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.CyclicBarrier;

import static org.junit.Assert.*;

public class RoughSurfaceDensitySamplerTest
{
	private static RandomState randomState;

	@BeforeClass
	public static void initializeRegistries()
	{
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		var registries = VanillaRegistries.createWorldLookup();
		var settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS)
			.getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
		randomState = RandomState.create(registries.lookupOrThrow(Registries.NOISE), 21426927, settings);
	}

	private static RoughSurfaceDensitySampler createSampler(ContextSampler input)
	{ return new RoughSurfaceDensitySampler(randomState, DensityFunctions.cache(input)); }

	@Test
	public void scalarQueriesDoNotUseVolumeValues()
	{
		ContextSampler input = new ContextSampler();
		RoughSurfaceDensitySampler sampler = createSampler(input);
		DensityVolume volume = new DensityVolume(4, 8, 4, -4, -8, -4);
		DensityBuffer output = DensityBuffer.createUnpooled(volume.size());
		sampler.begin();
		try
		{
			sampler.sampleVolume(output, volume);
			assertEquals(9.5f, output.get(0), 0.0f);
			assertEquals(3.25f, sampler.sampleValue(-2, -3, -1), 0.0f);
			assertSame(Beardifier.EMPTY, input.context.getField(Beardifier.CONTEXT_KEY));
		}
		finally
		{
			sampler.end();
		}
	}

	@Test
	public void ordinaryContextsKeepVanillaVolumeLookup()
	{
		SamplerContext context = SamplerContext.builder().enableCaches().build();
		DensityVolume volume = new DensityVolume(4, 8, 4, -4, -8, -4);
		DensitySampler.Bound sampler = randomState.getSampler(DensityFunctions.cache(new ContextSampler())).bind(context);
		sampler.sampleVolume(DensityBuffer.createUnpooled(volume.size()), volume);
		assertEquals(9.5f, sampler.sampleValue(-2, -3, -1), 0.0f);
		assertEquals(3.25f, sampler.sampleValue(0, 0, 0), 0.0f);
	}

	@Test
	public void steppedVolumesDoNotSupplyScalarValues()
	{
		RoughSurfaceDensitySampler sampler = createSampler(new ContextSampler());
		DensityVolume volume = new DensityVolume(2, 2, 2, -4, -8, -4, 4, 8, 4);
		sampler.begin();
		try
		{
			sampler.sampleVolume(DensityBuffer.createUnpooled(volume.size()), volume);
			for (int x : new int[] {-5, -4, -3, 0, 1, 4})
			{
				assertEquals(3.25f, sampler.sampleValue(x, 0, 0), 0.0f);
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
		RoughSurfaceDensitySampler sampler = createSampler(new ContextSampler());
		assertThrows(IllegalStateException.class, () -> sampler.sampleValue(0, 0, 0));
		sampler.begin();
		try
		{
			assertEquals(3.25f, sampler.sampleValue(0, 0, 0), 0.0f);
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
	public void eachRequestReleasesItsContext()
	{
		ContextSampler input = new ContextSampler();
		RoughSurfaceDensitySampler sampler = createSampler(input);
		SamplerContext previous = null;
		for (int request = 0; request < 10; request++)
		{
			sampler.begin();
			try
			{
				sampler.sampleValue(0, 0, 0);
				SamplerContext current = input.context;
				assertNotSame(previous, current);
				sampler.sampleValue(1, 1, 1);
				assertSame(current, input.context);
				previous = current;
			}
			finally
			{
				sampler.end();
			}
		}
	}

	@Test
	public void failedRequestsCanReleaseTheirContext()
	{
		RoughSurfaceDensitySampler sampler = createSampler(new ContextSampler());
		assertThrows(IllegalArgumentException.class, () ->
		{
			sampler.begin();
			try
			{
				throw new IllegalArgumentException("Test failure");
			}
			finally
			{
				sampler.end();
			}
		});
		sampler.begin();
		sampler.end();
		assertThrows(IllegalStateException.class, () -> sampler.sampleValue(0, 0, 0));
	}

	@Test
	public void workersHaveIndependentContexts() throws Exception
	{
		CyclicBarrier barrier = new CyclicBarrier(4);
		ContextSampler input = new ContextSampler();
		RoughSurfaceDensitySampler sampler = createSampler(input);
		ExecutorService workers = Executors.newFixedThreadPool(4);
		try
		{
			java.util.List<Future<Float>> results = new java.util.ArrayList<>();
			for (int worker = 0; worker < 4; worker++)
			{
				results.add(workers.submit(() ->
				{
					sampler.begin();
					try
					{
						barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
						return sampler.sampleValue(0, 0, 0);
					}
					finally
					{
						sampler.end();
					}
				}));
			}
			for (Future<Float> result : results)
			{
				assertEquals(3.25f, result.get(), 0.0f);
			}
			assertEquals(4, input.contexts.size());
		}
		finally
		{
			workers.shutdownNow();
		}
	}

	@Test
	public void generatorsDoNotShareContexts()
	{
		ContextSampler firstInput = new ContextSampler();
		ContextSampler secondInput = new ContextSampler();
		RoughSurfaceDensitySampler first = createSampler(firstInput);
		RoughSurfaceDensitySampler second = createSampler(secondInput);
		first.begin();
		second.begin();
		try
		{
			first.sampleValue(0, 0, 0);
			second.sampleValue(0, 0, 0);
			assertNotSame(firstInput.context, secondInput.context);
			first.end();
			assertEquals(3.25f, second.sampleValue(0, 0, 0), 0.0f);
		}
		finally
		{
			first.end();
			second.end();
		}
	}

	@Test
	public void reusedWorkerDoesNotRetainFailedRequests() throws Exception
	{
		RoughSurfaceDensitySampler sampler = createSampler(new ContextSampler());
		ExecutorService worker = Executors.newSingleThreadExecutor();
		try
		{
			for (int request = 0; request < 10; request++)
			{
				Future<?> failure = worker.submit(() ->
				{
					sampler.begin();
					try
					{
						throw new IllegalArgumentException("Test failure");
					}
					finally
					{
						sampler.end();
					}
				});
				assertThrows(java.util.concurrent.ExecutionException.class, failure::get);
				worker.submit(() ->
				{
					assertThrows(IllegalStateException.class, () -> sampler.sampleValue(0, 0, 0));
					sampler.begin();
					try
					{
						assertEquals(3.25f, sampler.sampleValue(0, 0, 0), 0.0f);
					}
					finally
					{
						sampler.end();
					}
				}).get();
			}
		}
		finally
		{
			worker.shutdownNow();
		}
	}

	@Test
	public void cancelledWorkerReleasesItsContext() throws Exception
	{
		RoughSurfaceDensitySampler sampler = createSampler(new ContextSampler());
		java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch wait = new java.util.concurrent.CountDownLatch(1);
		ExecutorService worker = Executors.newSingleThreadExecutor();
		try
		{
			Future<?> cancelled = worker.submit(() ->
			{
				sampler.begin();
				try
				{
					started.countDown();
					wait.await();
				}
				catch (InterruptedException expected)
				{
					Thread.currentThread().interrupt();
				}
				finally
				{
					sampler.end();
				}
			});
			assertTrue(started.await(10, java.util.concurrent.TimeUnit.SECONDS));
			assertTrue(cancelled.cancel(true));
			worker.submit(() ->
			{
				assertThrows(IllegalStateException.class, () -> sampler.sampleValue(0, 0, 0));
				sampler.begin();
				sampler.end();
			}).get(10, java.util.concurrent.TimeUnit.SECONDS);
		}
		finally
		{
			wait.countDown();
			worker.shutdownNow();
		}
	}

	private static class ContextSampler implements DensitySampler, DensityFunction
	{
		private SamplerContext context;
		private final java.util.Set<SamplerContext> contexts = java.util.concurrent.ConcurrentHashMap.newKeySet();

		@Override
		public float sampleValue(SamplerContext context, int x, int y, int z)
		{
			this.context = context;
			this.contexts.add(context);
			return 3.25f;
		}

		@Override
		public void sampleVolume(SamplerContext context, DensityBuffer output, DensityVolume volume)
		{ output.fill(9.5f); }

		@Override
		public DensitySampler compileSampler(CompileContext context)
		{ return this; }

		@Override
		public DensityFunction rewriteChildren(DfRewriteRule rule)
		{ return this; }

		@Override
		public Interval range()
		{ return Interval.of(3.25f, 9.5f); }

		@Override
		public int domainAxes()
		{ return ALL_AXES; }

		@Override
		public MapCodec<? extends DensityFunction> codec()
		{ throw new UnsupportedOperationException(); }
	}
}
#endif
