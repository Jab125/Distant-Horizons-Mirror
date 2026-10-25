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
public final class NativeTransparencyRenderer
{
	public static boolean isActive() { return false; }
	public static void reset() { }
	public static void free() { }
	public static void finishFrame() { }
}
#else
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.backend.opengl.GlBuffer;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiRendererMode;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiTerrainVertexFormat;
import com.seibel.distanthorizons.api.interfaces.render.renderDef.objects.*;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiFogRenderParam;
import com.seibel.distanthorizons.api.objects.util.IDhApiReadOnlyList;
import com.seibel.distanthorizons.common.render.blaze.postProcessing.BlazeDhFogRenderer;
import com.seibel.distanthorizons.common.render.blaze.util.BlazeDhVertexFormatUtil;
import com.seibel.distanthorizons.common.render.blaze.wrappers.BlazeVertexFormatBuilder;
import com.seibel.distanthorizons.common.render.blaze.wrappers.buffer.BlazeVertexBufferWrapper;
import com.seibel.distanthorizons.common.render.blaze.wrappers.texture.BlazeBlockTextureAtlas;
import com.seibel.distanthorizons.common.render.blaze.wrappers.texture.IDhBlazeTexture;
import com.seibel.distanthorizons.common.render.blaze.wrappers.uniform.BlazeUniformBufferWrapper;
import com.seibel.distanthorizons.common.render.openGl.glObject.buffer.GLBuffer;
import com.seibel.distanthorizons.common.render.openGl.glObject.buffer.GLVertexBuffer;
import com.seibel.distanthorizons.common.wrappers.misc.LightMapWrapper;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.render.RenderParam;
import com.seibel.distanthorizons.core.util.math.DhMat4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.oit.OitRenderPassProvider;
import net.minecraft.client.renderer.oit.OitStage;
import java.util.*;

public final class NativeTransparencyRenderer
{
	private static final List<Packet> PACKETS = new ArrayList<>();
	private static final Map<LodBufferContainer, BlazeUniformBufferWrapper> OFFSETS = new IdentityHashMap<>();
	private static BlazeUniformBufferWrapper vertex;
	private static BlazeUniformBufferWrapper material;
	private static final Map<GLBuffer, Long> LOCKS = new IdentityHashMap<>();
	private static final Map<EDhApiTerrainVertexFormat, OitPipelineSet> PIPELINES = new EnumMap<>(EDhApiTerrainVertexFormat.class);
	private static final Map<EDhApiTerrainVertexFormat, RenderPipeline> OPAQUE_PIPELINES = new EnumMap<>(EDhApiTerrainVertexFormat.class);
	private static IDhBlazeTexture lightmap;
	private static IDhBlazeTexture atlas;
	private static BorrowedAtlas borrowedAtlas;
	private static GpuBuffer fogUniforms;
	private static RenderParam frameParams;
	private static boolean prepared;
	private static int expectedStage;

	private NativeTransparencyRenderer() { }

	public static boolean requiresSharedOit()
	{
		return Minecraft.getInstance().level != null
			&& Config.Client.quickEnableRendering.get()
			&& Config.Client.Advanced.Debugging.rendererMode.get() == EDhApiRendererMode.DEFAULT
			&& VanillaTerrainCoverage.canUseNativeTransparency();
	}

	public static boolean isActive() { return requiresSharedOit() && VanillaTerrainCoverage.isActive(); }
	public static boolean isPreparedFrame() { return prepared && isActive(); }

	public static void reset()
	{
		releaseBuffers();
		PACKETS.clear();
		NativeGenericRenderer.reset();
		NativeReferenceRenderer.reset();
		prepared = false;
		expectedStage = 0;
	}

	public static void finishFrame()
	{
		releaseBuffers();
		prepared = false;
	}

	private static void releaseBuffers()
	{
		LOCKS.forEach((buffer, stamp) -> buffer.renderStampLock.unlockRead(stamp));
		LOCKS.clear();
	}

	public static void free()
	{
		reset();
		NativeGenericRenderer.free();
		NativeSsaoRenderer.free();
		NativeReferenceRenderer.free();
		OFFSETS.values().forEach(BlazeUniformBufferWrapper::close);
		OFFSETS.clear();
		if (vertex != null) vertex.close();
		if (material != null) material.close();
		vertex = null;
		material = null;
		lightmap = null;
		fogUniforms = null;
		if (borrowedAtlas != null) borrowedAtlas.close();
		borrowedAtlas = null;
		atlas = null;
	}

	private static boolean retain(GLBuffer buffer)
	{
		if (LOCKS.containsKey(buffer)) return true;
		long stamp = buffer.renderStampLock.tryReadLock();
		if (stamp == 0) return false;
		LOCKS.put(buffer, stamp);
		return buffer.getId() != 0;
	}

	public static void capture(RenderParam params, IDhApiReadOnlyList<? extends IDhApiTerrainBufferContainer> containers)
	{
		if (!isActive()) return;
		Set<LodBufferContainer> present = Collections.newSetFromMap(new IdentityHashMap<>());
		for (int i = 0; i < containers.size(); i++)
		{
			LodBufferContainer container = (LodBufferContainer)containers.get(i);
			if (!container.buffersUploaded) continue;
			present.add(container);
			BlazeUniformBufferWrapper offset = OFFSETS.computeIfAbsent(container, ignored -> new BlazeUniformBufferWrapper("native_terrain_offset"));
			offset.putVec3i(container.minCornerBlockPos.getX(), container.minCornerBlockPos.getY(), container.minCornerBlockPos.getZ()).finishAndUpload();
			for (boolean opaque : new boolean[]{true, false})
			for (IDhApiVertexBufferWrapper buffer : opaque ? container.getVboOpaqueWrappers() : container.getVboTransparentWrappers())
			{
				if (buffer instanceof BlazeVertexBufferWrapper blaze && blaze.uploaded && blaze.vertexCount > 0)
					PACKETS.add(new Packet(blaze.vertexGpuBuffer, blaze.getIndexGpuBuffer(), IndexType.INT, blaze.indexCount, offset, opaque, container.getVertexFormat()));
				else if (buffer instanceof GLVertexBuffer gl && retain(gl) && gl.getVertexCount() > 0 && retain(gl.getQuadIBO()))
					PACKETS.add(new Packet(new BorrowedBuffer(gl, GpuBuffer.USAGE_VERTEX), new BorrowedBuffer(gl.getQuadIBO(), GpuBuffer.USAGE_INDEX),
						gl.getQuadIBO().getGlType() == org.lwjgl.opengl.GL11.GL_UNSIGNED_INT ? IndexType.INT : IndexType.SHORT,
						gl.getVertexCount() / 4 * 6, offset, opaque, container.getVertexFormat()));
			}
		}
		OFFSETS.entrySet().removeIf(entry -> { if (present.contains(entry.getKey())) return false; entry.getValue().close(); return true; });
	}

	public static void prepare(RenderParam params, DhApiFogRenderParam fog)
	{
		if (!isActive()) return;
		lightmap = ((LightMapWrapper)params.lightmap).getTextureViewWrapper();
		if (vertex == null) vertex = new BlazeUniformBufferWrapper("native_terrain_vertex");
		if (material == null) material = new BlazeUniformBufferWrapper("native_terrain_material");
		if (params.renderDefinition instanceof com.seibel.distanthorizons.common.render.openGl.GlDhRenderApiDefinition)
		{
			var source = com.seibel.distanthorizons.common.render.openGl.terrain.GlBlockTextureAtlas.INSTANCE;
			if (borrowedAtlas == null || borrowedAtlas.revision != source.getRevision())
			{
				if (borrowedAtlas != null) borrowedAtlas.close();
				borrowedAtlas = new BorrowedAtlas(source.getTextureId(), source.getWidth(), source.getHeight(), source.getRevision());
			}
			atlas = borrowedAtlas;
		}
		else
		{
			BlazeBlockTextureAtlas.INSTANCE.uploadPendingTiles();
			atlas = BlazeBlockTextureAtlas.INSTANCE.getTextureWrapper();
		}
		DhMat4f matrix = new DhMat4f(params.mcProjectionMatrix);
		matrix.multiply(new DhMat4f(params.mcModelViewMatrix));
		int x = (int)Math.floor(params.exactCameraPosition.x);
		int y = (int)Math.floor(params.exactCameraPosition.y);
		int z = (int)Math.floor(params.exactCameraPosition.z);
		float curve = Config.Client.Advanced.Graphics.Experimental.earthCurveRatio.get();
		curve = Math.abs(curve) > 1 ? 6371000.0f / curve : 0;
		vertex.putInt(Config.Client.Advanced.Debugging.enableWhiteWorld.get() ? 1 : 0)
			.putFloat((float)params.worldYOffset).putFloat(0).putFloat(curve)
			.putFloat(-1).putFloat(1).putFloat(1).putVec3i(x,y,z)
			.putVec3f((float)(params.exactCameraPosition.x-x), (float)(params.exactCameraPosition.y-y), (float)(params.exactCameraPosition.z-z))
			.putMat4f(matrix).putInt(Config.Client.Advanced.Graphics.Texture.enableTexturedLods.get() ? 1 : 0)
			.putMat4f(params.mcModelViewMatrix).finishAndUpload();
		frameParams = params;
		uploadMaterial(true);
		fogUniforms = BlazeDhFogRenderer.INSTANCE.prepareUniforms(params, fog != null ? fog
			: com.seibel.distanthorizons.core.render.renderer.FogRenderParamFactory.getRenderParam(params).getFogRenderParam(), fog != null);
		NativeSsaoRenderer.prepare(params);
		prepared = true;
		NativeReferenceRenderer.captureSky();
	}

	public static RenderParam getFrameParams() { return frameParams; }

	public static void selectReference(boolean reference)
	{
		expectedStage = 0;
		uploadMaterial(!reference);
	}

	private static void uploadMaterial(boolean coverage)
	{
		RenderParam params = frameParams;
		material.putFloat(0).putFloat(Config.Client.Advanced.Graphics.NoiseTexture.noiseIntensity.get())
			.putInt(Config.Client.Advanced.Graphics.NoiseTexture.noiseSteps.get()).putInt(Config.Client.Advanced.Graphics.NoiseTexture.noiseDropoff.get())
			.putInt(0).putInt(Config.Client.Advanced.Graphics.NoiseTexture.enableNoiseTexture.get() ? 1 : 0).putInt(coverage ? 1 : 0)
			.putVec3f((float)(VanillaTerrainCoverage.getOriginX()-params.exactCameraPosition.x),
				(float)(VanillaTerrainCoverage.getOriginY()-params.exactCameraPosition.y), (float)(VanillaTerrainCoverage.getOriginZ()-params.exactCameraPosition.z))
			.putInt(VanillaTerrainCoverage.getSpanZ()).finishAndUpload();
	}

	private static RenderPipeline.Builder pipelineBuilder(boolean opaque, EDhApiTerrainVertexFormat vertexFormat)
	{
		var formatBuilder = new BlazeVertexFormatBuilder()
			.add("vPositionAndMeta", BlazeDhVertexFormatUtil.SHORT_XYZ_POS_AND_W_META)
			.add("vColor", BlazeDhVertexFormatUtil.RGBA_UBYTE_COLOR)
			.add("materialNormalTile", BlazeDhVertexFormatUtil.MATERIAL_NORMAL_TILE);
		boolean offsets = vertexFormat != EDhApiTerrainVertexFormat.POS_META_COLOR_MATERIAL_NORMAL_TEXTURE;
		if (offsets) formatBuilder.add("offsetData", BlazeDhVertexFormatUtil.OFFSET_DATA);
		var format = formatBuilder.build();
		var layout = BindGroupLayout.builder();
		for (String name : new String[]{"uLightMap","uBlockAtlas","uVanillaCoverageTexture","uNativeOcclusion"}) layout.withUniform(name, UniformType.COMBINED_IMAGE_SAMPLER);
		for (String name : new String[]{"vertUniqueUniformBlock","vertSharedUniformBlock","fragUniformBlock","nativeFogBlock","nativeOcclusionBlock","Projection"}) layout.withUniform(name, UniformType.UNIFORM_BUFFER);
		var base = RenderPipeline.builder().withVertexShader(net.minecraft.resources.Identifier.parse("distanthorizons:terrain/native/vert"))
			.withFragmentShader(net.minecraft.resources.Identifier.parse("distanthorizons:terrain/native/frag"))
			.withBindGroupLayout(layout.build()).withVertexBinding(0,format)
			.withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(true)
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN, opaque));
		if (offsets) base.withShaderDefine("DH_NATIVE_OFFSETS");
		return base;
	}

	public static void drawOpaque(com.mojang.blaze3d.pipeline.RenderTarget target)
	{
		if (!isActive() || !prepared || (PACKETS.isEmpty() && NativeGenericRenderer.isEmpty())) return;
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
			() -> "Distant Horizons native opaque terrain", target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), java.util.OptionalDouble.empty()))
		{
			drawPackets(pass, true, null);
			NativeGenericRenderer.draw(pass, null);
		}
	}

	public static void draw(OitStage stage, OitRenderPassProvider.Parameters targets,
		com.mojang.renderpearl.api.textures.GpuTextureView nativeAtlas,
		com.mojang.renderpearl.api.textures.GpuTextureView nativeLightmap)
	{
		if (!isActive() || !prepared) return;
		if (stage.ordinal() != expectedStage++) throw new IllegalStateException("Unexpected native transparency phase order");
		if (PACKETS.isEmpty() && NativeGenericRenderer.isEmpty()) return;
		try (RenderPass pass = OitRenderPassProvider.createRenderPass(stage, () -> "Distant Horizons native transparency", targets))
		{
			drawPackets(pass, false, stage);
			NativeGenericRenderer.draw(pass, stage);
		}
	}

	static void bindFog(RenderPass pass) { pass.setUniform("nativeFogBlock", fogUniforms); NativeSsaoRenderer.bind(pass); }

	private static void bind(RenderPass pass, RenderPipeline pipeline)
	{
		RenderSystem.bindDefaultUniforms(pass);
		pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
		pass.setUniform("uLightMap", lightmap.getTextureView(), lightmap.getTextureSampler());
		pass.setUniform("uBlockAtlas", atlas.getTextureView(), atlas.getTextureSampler());
		pass.setUniform("uVanillaCoverageTexture", VanillaTerrainCoverage.getTexture().getTextureView(), VanillaTerrainCoverage.getTexture().getTextureSampler());
		pass.setUniform("vertSharedUniformBlock", vertex.getGpuBuffer());
		pass.setUniform("fragUniformBlock", material.getGpuBuffer());
		bindFog(pass);
	}

	private static RenderPipeline pipeline(boolean opaque, EDhApiTerrainVertexFormat format, OitStage stage)
	{
		if (!opaque)
			return PIPELINES.computeIfAbsent(format, key -> OitPipelineSet.builder("distanthorizons_native_terrain_" + key.name().toLowerCase(java.util.Locale.ROOT), pipelineBuilder(false, key)).build()).getPipeline(stage);
		return OPAQUE_PIPELINES.computeIfAbsent(format, key -> pipelineBuilder(true, key)
			.withLocation(net.minecraft.resources.Identifier.parse("distanthorizons:native_opaque_terrain_" + key.name().toLowerCase(java.util.Locale.ROOT)))
			.withShaderDefine("DH_NATIVE_OPAQUE")
			.withColorTargetState(new ColorTargetState(Optional.empty(), com.mojang.renderpearl.api.GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL)).build());
	}

	private static void drawPackets(RenderPass pass, boolean opaque, OitStage stage)
	{
		EDhApiTerrainVertexFormat activeFormat = null;
		for (Packet packet : PACKETS)
		{
			if (packet.opaque != opaque) continue;
			if (packet.format != activeFormat)
			{
				bind(pass, pipeline(opaque, packet.format, stage));
				activeFormat = packet.format;
			}
			if (packet.vertices.isClosed() || packet.indices.isClosed()) throw new IllegalStateException("Terrain buffer changed within native transparency phases");
			pass.setUniform("vertUniqueUniformBlock", packet.offset.getGpuBuffer());
			pass.setVertexBuffer(0, packet.vertices.slice());
			pass.setIndexBuffer(packet.indices, packet.indexType);
			pass.drawIndexed(packet.count, 1, 0, 0, 0);
		}
	}

	private record Packet(GpuBuffer vertices, GpuBuffer indices, IndexType indexType, int count, BlazeUniformBufferWrapper offset, boolean opaque, EDhApiTerrainVertexFormat format) { }

	private static final class BorrowedAtlas implements IDhBlazeTexture, AutoCloseable
	{
		private final long revision;
		private final com.mojang.renderpearl.api.textures.GpuTextureView view;
		private final com.mojang.renderpearl.api.textures.GpuSampler sampler;
		BorrowedAtlas(int id, int width, int height, long revision)
		{
			this.revision = revision;
			this.view = RenderSystem.getDevice().createTextureView(new BorrowedTexture(id, width, height));
			this.sampler = RenderSystem.getDevice().createSampler(com.mojang.renderpearl.api.textures.AddressMode.REPEAT,
				com.mojang.renderpearl.api.textures.AddressMode.REPEAT, com.mojang.renderpearl.api.textures.FilterMode.NEAREST,
				com.mojang.renderpearl.api.textures.FilterMode.NEAREST, 1, OptionalDouble.of(4));
		}
		@Override public com.mojang.renderpearl.api.textures.GpuTextureView getTextureView() { return this.view; }
		@Override public com.mojang.renderpearl.api.textures.GpuSampler getTextureSampler() { return this.sampler; }
		@Override public void close() { this.view.close(); this.sampler.close(); }
	}

	static com.mojang.renderpearl.api.textures.GpuTexture borrowTexture(int id, int width, int height, com.mojang.renderpearl.api.GpuFormat format, int levels)
	{ return new BorrowedTexture(id, width, height, format, levels); }

	private static final class BorrowedTexture extends com.mojang.renderpearl.backend.opengl.GlTexture
	{
		BorrowedTexture(int id, int width, int height)
		{ this(id, width, height, com.mojang.renderpearl.api.GpuFormat.RGBA8_UNORM, 5); }
		BorrowedTexture(int id, int width, int height, com.mojang.renderpearl.api.GpuFormat format, int levels)
		{
			super(com.mojang.renderpearl.api.textures.GpuTexture.USAGE_TEXTURE_BINDING, "native_shared_atlas",
				format, width, height, 1, levels, id, null);
		}
		@Override public void close() { }
	}

	private static final class BorrowedBuffer extends GlBuffer
	{
		private final GLBuffer owner;
		BorrowedBuffer(GLBuffer owner, int usage) { super(usage, owner.getSize(), owner.getId(), false); this.owner = owner; }
		@Override public boolean isClosed() { return this.owner.getId() != this.handle(); }
		@Override public void close() { }
		@Override public GpuBufferSlice.MappedView map(long offset, long length, boolean read, boolean write) { throw new UnsupportedOperationException("Borrowed terrain buffers cannot be mapped"); }
	}
}
#endif
