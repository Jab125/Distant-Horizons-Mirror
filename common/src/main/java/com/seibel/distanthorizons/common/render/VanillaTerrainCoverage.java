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

package com.seibel.distanthorizons.common.render;

#if MC_VER <= MC_26_2_0
public final class VanillaTerrainCoverage
{
	public static boolean isActive() { return false; }
	public static void update() { }
	public static void free() { }
}
#else
import com.seibel.distanthorizons.common.render.blaze.wrappers.texture.BlazeTextureWrapper;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IIrisAccessor;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.ISodiumAccessor;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IImmersivePortalsAccessor;
import com.seibel.distanthorizons.coreapi.DependencyInjection.OverrideInjector;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiShaderProgram;
import com.seibel.distanthorizons.api.interfaces.render.renderDef.AbstractDhApiRenderDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import java.nio.ByteBuffer;
import java.util.Arrays;
import com.seibel.distanthorizons.core.render.VanillaSectionCoverage;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import java.util.List;

public final class VanillaTerrainCoverage
{
	private static BlazeTextureWrapper texture;
	private static ByteBuffer pixels;
	private static byte[] previous;
	private static boolean active;
	private static int originX, originY, originZ, spanZ;

	public static boolean isActive() { return active && supportsNativeCoverage(); }
	public static BlazeTextureWrapper getTexture() { return texture; }
	public static int getSpanZ() { return spanZ; }
	public static int getOriginX() { return originX; }
	public static int getOriginY() { return originY; }
	public static int getOriginZ() { return originZ; }

	public static void update()
	{
		NativeTransparencyRenderer.reset();
		active = false;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || !supportsNativeCoverage()) return;
		ISodiumAccessor sodium = ModAccessorInjector.INSTANCE.get(ISodiumAccessor.class);
		VanillaSectionCoverage coverage;
		if (sodium != null)
		{
			coverage = sodium.getNativeTerrainCoverage();
		}
		else
		{
			var sections = mc.levelRenderer.visibleSections();
			coverage = sections.isEmpty() ? null : buildCoverage(mc, sections);
		}
		if (coverage == null) coverage = new VanillaSectionCoverage(0, mc.level.getMinY(), 0, 1, 1, 1);
		originX = coverage.originX;
		originY = coverage.originY;
		originZ = coverage.originZ;
		spanZ = coverage.depth;
		upload(coverage);
		active = true;
	}

	public static boolean canUseNativeTransparency() { return supportsNativeCoverage(); }

	private static boolean supportsNativeCoverage()
	{
		if (com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftRenderWrapper.INSTANCE.isFogStateSpecial()
			|| Config.Client.Advanced.Debugging.lodOnlyMode.get()
			|| Config.Client.Advanced.Graphics.Quality.transparency.get() != com.seibel.distanthorizons.api.enums.rendering.EDhApiTransparency.COMPLETE
			|| OverrideInjector.INSTANCE.get(IDhApiShaderProgram.class) != null
			|| OverrideInjector.INSTANCE.get(com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiGenericObjectShaderProgram.class) != null
			|| Config.Client.Advanced.Debugging.renderWireframe.get()) return false;
		IIrisAccessor iris = ModAccessorInjector.INSTANCE.get(IIrisAccessor.class);
		if (iris != null && iris.isShaderPackInUse()) return false;
		IImmersivePortalsAccessor portals = ModAccessorInjector.INSTANCE.get(IImmersivePortalsAccessor.class);
		if (portals != null && portals.getDelayedSetupComplete() && portals.isRenderingPortal()) return false;
		return usesBuiltInRenderDefinition();
	}

	private static boolean usesBuiltInRenderDefinition()
	{
		AbstractDhApiRenderDefinition definition = OverrideInjector.INSTANCE.get(AbstractDhApiRenderDefinition.class);
		return definition == null
			|| definition instanceof com.seibel.distanthorizons.common.render.openGl.GlDhRenderApiDefinition
			|| definition instanceof com.seibel.distanthorizons.common.render.blaze.BlazeDhRenderApiDefinition;
	}

	private static VanillaSectionCoverage buildCoverage(Minecraft mc, List<SectionRenderDispatcher.RenderSection> sections)
	{
		int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (var section : sections)
		{
			var pos = section.getRenderOrigin();
			minX = Math.min(minX, pos.getX() >> 4);
			minZ = Math.min(minZ, pos.getZ() >> 4);
			maxX = Math.max(maxX, pos.getX() >> 4);
			maxZ = Math.max(maxZ, pos.getZ() >> 4);
		}
		VanillaSectionCoverage coverage = new VanillaSectionCoverage(minX * 16, mc.level.getMinY(), minZ * 16,
			maxX - minX + 1, (mc.level.getHeight() + 15) / 16, maxZ - minZ + 1);
		for (var section : sections)
		{
			if (section.getSectionMesh() == CompiledSectionMesh.UNCOMPILED) continue;
			var pos = section.getRenderOrigin();
			coverage.include(pos.getX(), pos.getY(), pos.getZ());
		}
		return coverage;
	}

	private static void upload(VanillaSectionCoverage coverage)
	{
		byte[] data = coverage.getTexels();
		if (texture == null) texture = BlazeTextureWrapper.createColor("vanilla_section_coverage");
		boolean resized = texture.tryCreateOrResize(coverage.width, coverage.height);
		if (resized || !Arrays.equals(previous, data))
		{
			if (pixels == null || pixels.capacity() < data.length) pixels = ByteBuffer.allocateDirect(data.length);
			pixels.clear().put(data).flip();
			texture.writeToTexture(pixels, 0, 0, 0, coverage.width, coverage.height);
			previous = data;
		}
	}

	public static void free()
	{
		NativeTransparencyRenderer.free();
		active = false;
		if (texture != null)
		{
			texture.getTextureView().close();
			texture.getTexture().close();
			texture.getTextureSampler().close();
			texture = null;
		}
		previous = null;
		pixels = null;
	}
}
#endif
