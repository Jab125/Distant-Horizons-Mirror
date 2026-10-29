package com.seibel.distanthorizons.common.wrappers.worldGeneration.chunkFileHandling;

#if MC_VER >= MC_1_21_9
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.ticks.ProtoChunkTicks;

// Fresh DH border chunks usually need structure metadata without any mutable sections.
final class DeferredProtoChunk extends ProtoChunk
{
	// Acquire/release publishes fully initialized sections to other workers.
	private static final VarHandle SECTION = MethodHandles.arrayElementVarHandle(LevelChunkSection[].class);
	// This shared placeholder must never escape through mutable section access.
	private final LevelChunkSection emptySection;
	private final PalettedContainerFactory containerFactory;

	DeferredProtoChunk(ChunkPos pos, LevelHeightAccessor height, PalettedContainerFactory factory, LevelChunkSection emptySection)
	{
		super(pos, UpgradeData.EMPTY, placeholders(height.getSectionsCount(), emptySection),
			new ProtoChunkTicks<>(), new ProtoChunkTicks<>(), height, factory, null);
		this.emptySection = emptySection;
		this.containerFactory = factory;
	}

	private static LevelChunkSection[] placeholders(int count, LevelChunkSection emptySection)
	{
		LevelChunkSection[] result = new LevelChunkSection[count];
		Arrays.fill(result, emptySection);
		return result;
	}

	@Override
	public LevelChunkSection getSection(int index)
	{
		LevelChunkSection section = (LevelChunkSection) SECTION.getAcquire(this.sections, index);
		if (section != this.emptySection) return section;
		synchronized (this)
		{
			// Another worker may have created this section while we waited.
			section = this.sections[index];
			if (section == this.emptySection)
			{
				section = new LevelChunkSection(this.containerFactory);
				SECTION.setRelease(this.sections, index, section);
			}
			return section;
		}
	}

	@Override
	public LevelChunkSection[] getSections()
	{
		// Materialize every section before exposing mutable storage to callers.
		for (int i = 0; i < this.sections.length; i++) this.getSection(i);
		return this.sections;
	}

	@Override
	public BlockState getBlockState(BlockPos pos)
	{
		// Empty block and fluid reads must not trigger section allocation.
		if (this.isOutsideBuildHeight(pos.getY())) return Blocks.VOID_AIR.defaultBlockState();
		LevelChunkSection section = (LevelChunkSection) SECTION.getAcquire(this.sections, this.getSectionIndex(pos.getY()));
		return section.hasOnlyAir() ? Blocks.AIR.defaultBlockState()
			: section.getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
	}

	@Override
	public FluidState getFluidState(BlockPos pos)
	{
		if (this.isOutsideBuildHeight(pos.getY())) return Fluids.EMPTY.defaultFluidState();
		LevelChunkSection section = (LevelChunkSection) SECTION.getAcquire(this.sections, this.getSectionIndex(pos.getY()));
		return section.hasOnlyAir() ? Fluids.EMPTY.defaultFluidState()
			: section.getFluidState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
	}

	@Override
	public int getHighestFilledSectionIndex()
	{
		// The vanilla scan calls getSections(), which would materialize empty borders.
		for (int i = this.sections.length - 1; i >= 0; i--)
			if (!((LevelChunkSection) SECTION.getAcquire(this.sections, i)).hasOnlyAir()) return i;
		return -1;
	}
}
#endif
