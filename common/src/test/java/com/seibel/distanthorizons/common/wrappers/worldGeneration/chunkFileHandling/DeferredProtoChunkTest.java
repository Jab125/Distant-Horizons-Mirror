package com.seibel.distanthorizons.common.wrappers.worldGeneration.chunkFileHandling;

#if MC_VER >= MC_26_3_0
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMapper;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class DeferredProtoChunkTest
{
	private static PalettedContainerFactory factory;
	private static Holder<Biome> plains;
	private static Holder<Biome> desert;
	private static final LevelHeightAccessor HEIGHT = LevelHeightAccessor.create(-64, 384);
	private static final ChunkPos POS = new ChunkPos(-2, -3);

	@BeforeClass
	public static void initialize()
	{
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		var lookup = VanillaRegistries.createWorldLookup().lookupOrThrow(Registries.BIOME);
		plains = lookup.getOrThrow(Biomes.PLAINS);
		desert = lookup.getOrThrow(Biomes.DESERT);
		IdMapper<Holder<Biome>> biomes = new IdMapper<>();
		biomes.add(plains);
		biomes.add(desert);
		// These tests use the containers, not serialization codecs.
		#if MC_VER > MC_26_3_0
		factory = new PalettedContainerFactory(Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY),
			Blocks.AIR.defaultBlockState(), null, plains, Strategy.createForNoiseBiomes(biomes), null,
			Strategy.createForBiomes(biomes), null);
		#else
		factory = new PalettedContainerFactory(Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY),
			Blocks.AIR.defaultBlockState(), null, Strategy.createForBiomes(biomes), plains, null);
		#endif
	}

	private static Holder<Biome> biome(ProtoChunk chunk, int x, int y, int z)
	{
		#if MC_VER > MC_26_3_0
		return chunk.getBiome(x, y, z);
		#else
		return chunk.getNoiseBiome(x, y, z);
		#endif
	}

	private static void fillBiomes(ProtoChunk chunk, BiomeResolver resolver)
	{
		#if MC_VER > MC_26_3_0
		chunk.fillBiomes(resolver);
		#else
		chunk.fillBiomesFromNoise(resolver);
		#endif
	}

	private static DeferredProtoChunk create(LevelChunkSection empty)
	{ return new DeferredProtoChunk(POS, HEIGHT, factory, empty); }

	private static LevelChunkSection[] rawSections(ProtoChunk chunk) throws Exception
	{
		// Inspect storage without triggering the public materializing accessor.
		Field field = ChunkAccess.class.getDeclaredField("sections");
		field.setAccessible(true);
		return (LevelChunkSection[]) field.get(chunk);
	}

	@Test
	public void emptyReadsPreserveVanillaValuesWithoutCreatingSections() throws Exception
	{
		LevelChunkSection empty = new LevelChunkSection(factory);
		DeferredProtoChunk chunk = create(empty);
		ProtoChunk vanilla = new ProtoChunk(POS, UpgradeData.EMPTY, HEIGHT, factory, null);
		assertThrows(IllegalStateException.class, () -> biome(chunk, -8, 0, -12));
		// FEATURES selects section biomes on both versions of the biome API.
		chunk.setPersistedStatus(net.minecraft.world.level.chunk.status.ChunkStatus.FEATURES);
		vanilla.setPersistedStatus(net.minecraft.world.level.chunk.status.ChunkStatus.FEATURES);
		for (int y = -65; y <= 320; y++)
		{
			BlockPos pos = new BlockPos(-31, y, -47);
			assertSame(vanilla.getBlockState(pos), chunk.getBlockState(pos));
			assertSame(vanilla.getFluidState(pos), chunk.getFluidState(pos));
			assertSame(biome(vanilla, -8, y, -12), biome(chunk, -8, y, -12));
		}
		assertEquals(-1, chunk.getHighestFilledSectionIndex());
		for (LevelChunkSection section : rawSections(chunk)) assertSame(empty, section);
	}

	@Test
	public void writesCreateOnlyTheirOwnSection() throws Exception
	{
		LevelChunkSection empty = new LevelChunkSection(factory);
		DeferredProtoChunk chunk = create(empty);
		DeferredProtoChunk other = create(empty);
		BlockPos pos = new BlockPos(-31, 71, -47);
		chunk.setBlockState(pos, Blocks.WATER.defaultBlockState(), 0);
		assertSame(Blocks.WATER.defaultBlockState(), chunk.getBlockState(pos));
		assertTrue(other.getBlockState(pos).isAir());
		assertEquals(1, Arrays.stream(rawSections(chunk)).filter(section -> section != empty).count());
		assertFalse(chunk.getFluidState(pos).isEmpty());
	}

	@Test
	public void exportedSectionsNeverShareMutableStorage()
	{
		LevelChunkSection empty = new LevelChunkSection(factory);
		DeferredProtoChunk chunk = create(empty);
		DeferredProtoChunk other = create(empty);
		Set<LevelChunkSection> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		for (LevelChunkSection section : chunk.getSections())
		{
			assertNotSame(empty, section);
			assertTrue(seen.add(section));
		}
		chunk.getSection(0).setBlockState(1, 1, 1, Blocks.STONE.defaultBlockState());
		assertTrue(other.getSection(0).getBlockState(1, 1, 1).isAir());
		assertTrue(chunk.getSection(1).getBlockState(1, 1, 1).isAir());
	}

	@Test
	public void biomeFillingMatchesVanillaWithoutAffectingOtherChunks()
	{
		LevelChunkSection empty = new LevelChunkSection(factory);
		DeferredProtoChunk chunk = create(empty);
		DeferredProtoChunk other = create(empty);
		ProtoChunk vanilla = new ProtoChunk(POS, UpgradeData.EMPTY, HEIGHT, factory, null);
		BiomeResolver resolver = (x, y, z) -> ((x ^ y ^ z) & 1) == 0 ? plains : desert;
		fillBiomes(chunk, resolver);
		fillBiomes(vanilla, resolver);
		chunk.setPersistedStatus(net.minecraft.world.level.chunk.status.ChunkStatus.FEATURES);
		vanilla.setPersistedStatus(net.minecraft.world.level.chunk.status.ChunkStatus.FEATURES);
		other.setPersistedStatus(net.minecraft.world.level.chunk.status.ChunkStatus.FEATURES);
		for (int y = -100; y < 100; y++) for (int z = -12; z < -8; z++) for (int x = -8; x < -4; x++)
		{
			assertSame(biome(vanilla, x, y, z), biome(chunk, x, y, z));
			assertSame(plains, biome(other, x, y, z));
		}
	}

	@Test
	public void randomWritesAndHeightmapsMatchVanilla()
	{
		DeferredProtoChunk chunk = create(new LevelChunkSection(factory));
		ProtoChunk vanilla = new ProtoChunk(POS, UpgradeData.EMPTY, HEIGHT, factory, null);
		var states = List.of(Blocks.STONE.defaultBlockState(), Blocks.WATER.defaultBlockState(),
			Blocks.OAK_LEAVES.defaultBlockState(), Blocks.AIR.defaultBlockState());
		Random random = new Random(20261029);
		for (int i = 0; i < 5000; i++)
		{
			BlockPos pos = new BlockPos(-32 + random.nextInt(16), -64 + random.nextInt(384), -48 + random.nextInt(16));
			var state = states.get(random.nextInt(states.size()));
			assertSame(vanilla.setBlockState(pos, state, 0), chunk.setBlockState(pos, state, 0));
		}
		for (int y = -64; y < 320; y++) for (int z = -48; z < -32; z++) for (int x = -32; x < -16; x++)
		{
			BlockPos pos = new BlockPos(x, y, z);
			assertSame(vanilla.getBlockState(pos), chunk.getBlockState(pos));
			assertSame(vanilla.getFluidState(pos), chunk.getFluidState(pos));
		}
		Set<Heightmap.Types> types = EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG);
		Heightmap.primeHeightmaps(vanilla, types);
		Heightmap.primeHeightmaps(chunk, types);
		for (var type : types) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++)
			assertEquals(vanilla.getHeight(type, x, z), chunk.getHeight(type, x, z));
		assertEquals(vanilla.getHighestFilledSectionIndex(), chunk.getHighestFilledSectionIndex());
	}

	@Test
	public void concurrentFirstAccessPublishesOneOwnedSection() throws Exception
	{
		LevelChunkSection empty = new LevelChunkSection(factory);
		DeferredProtoChunk chunk = create(empty);
		ExecutorService pool = Executors.newFixedThreadPool(8);
		CyclicBarrier start = new CyclicBarrier(8);
		try
		{
			List<Future<LevelChunkSection>> futures = new ArrayList<>();
			for (int i = 0; i < 8; i++) futures.add(pool.submit(() -> { start.await(); return chunk.getSection(0); }));
			LevelChunkSection first = futures.get(0).get(10, TimeUnit.SECONDS);
			assertNotSame(empty, first);
			for (var future : futures) assertSame(first, future.get(10, TimeUnit.SECONDS));
		}
		finally { pool.shutdownNow(); }
	}
}
#endif
