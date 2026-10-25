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
public final class NativeGenericRenderer { }
#else
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.seibel.distanthorizons.common.render.blaze.objects.BlazeGenericObjectVertexContainer;
import com.seibel.distanthorizons.common.render.blaze.util.BlazeDhVertexFormatUtil;
import com.seibel.distanthorizons.common.render.blaze.wrappers.BlazeVertexFormatBuilder;
import com.seibel.distanthorizons.common.render.blaze.wrappers.texture.IDhBlazeTexture;
import com.seibel.distanthorizons.common.render.blaze.wrappers.uniform.BlazeUniformBufferWrapper;
import com.seibel.distanthorizons.common.wrappers.misc.LightMapWrapper;
import com.seibel.distanthorizons.core.render.RenderParam;
import com.seibel.distanthorizons.core.render.renderer.RenderableBoxGroup;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.math.DhMat4f;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBoxGroupShading;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.oit.OitStage;
import net.minecraft.resources.Identifier;
import java.util.*;

public final class NativeGenericRenderer
{
	private static final List<Packet> PACKETS = new ArrayList<>();
	private static final Map<RenderableBoxGroup, BlazeUniformBufferWrapper> UNIFORMS = new IdentityHashMap<>();
	private static final Set<RenderableBoxGroup> PRESENT = Collections.newSetFromMap(new IdentityHashMap<>());
	private static OitPipelineSet pipelines;
	private static RenderPipeline opaquePipeline;

	private NativeGenericRenderer() { }

	public static boolean isEmpty() { return PACKETS.isEmpty(); }

	public static void reset()
	{
		UNIFORMS.entrySet().removeIf(entry -> {
			if (PRESENT.contains(entry.getKey())) return false;
			entry.getValue().close();
			return true;
		});
		PRESENT.clear();
		PACKETS.clear();
	}

	public static void free()
	{
		UNIFORMS.values().forEach(BlazeUniformBufferWrapper::close);
		UNIFORMS.clear();
		PRESENT.clear();
		PACKETS.clear();
	}

	public static void capture(RenderParam params, RenderableBoxGroup group, BlazeGenericObjectVertexContainer buffer)
	{
		if (!NativeTransparencyRenderer.isActive() || buffer == null || buffer.uploadedBoxCount == 0) return;
		if (!PRESENT.add(group)) throw new IllegalStateException("Generic object submitted twice in one frame");
		var uniforms = UNIFORMS.computeIfAbsent(group, ignored -> new BlazeUniformBufferWrapper("native_generic_object"));
		var origin = group.getOriginBlockPos();
		var camera = params.exactCameraPosition;
		var shading = group.shading == null ? DhApiRenderableBoxGroupShading.getUnshaded() : group.shading;
		var matrix = new DhMat4f(params.mcProjectionMatrix);
		matrix.multiply(params.mcModelViewMatrix);
		uniforms.putVec3i(LodUtil.getChunkPosFromDouble(origin.x), LodUtil.getChunkPosFromDouble(origin.y), LodUtil.getChunkPosFromDouble(origin.z))
			.putVec3f(LodUtil.getSubChunkPosFromDouble(origin.x), LodUtil.getSubChunkPosFromDouble(origin.y), LodUtil.getSubChunkPosFromDouble(origin.z))
			.putVec3i(LodUtil.getChunkPosFromDouble(camera.x), LodUtil.getChunkPosFromDouble(camera.y), LodUtil.getChunkPosFromDouble(camera.z))
			.putVec3f(LodUtil.getSubChunkPosFromDouble(camera.x), LodUtil.getSubChunkPosFromDouble(camera.y), LodUtil.getSubChunkPosFromDouble(camera.z))
			.putMat4f(matrix).putInt(group.getSkyLight()).putInt(group.getBlockLight())
			.putFloat(shading.north).putFloat(shading.south).putFloat(shading.east).putFloat(shading.west).putFloat(shading.top).putFloat(shading.bottom).putInt(group.ssaoEnabled ? 1 : 0)
			.finishAndUpload();
		PACKETS.add(new Packet(buffer.vboGpuBuffer, buffer.indexGpuBuffer, buffer.uploadedBoxCount * 36,
			uniforms, ((LightMapWrapper)params.lightmap).getTextureViewWrapper()));
	}

	private static RenderPipeline.Builder pipelineBuilder(boolean opaque)
	{
		var format = new BlazeVertexFormatBuilder().add("vPosition", BlazeDhVertexFormatUtil.FLOAT_XYZ_POS)
			.add("aColor", BlazeDhVertexFormatUtil.RGBA_UBYTE_COLOR).add("aMaterial", BlazeDhVertexFormatUtil.IRIS_MATERIAL)
			.add("paddingOne", BlazeDhVertexFormatUtil.BYTE_PAD).add("paddingTwo", BlazeDhVertexFormatUtil.BYTE_PAD)
			.add("paddingThree", BlazeDhVertexFormatUtil.BYTE_PAD).build();
		var layout = BindGroupLayout.builder().withUniform("uLightMap", UniformType.COMBINED_IMAGE_SAMPLER)
			.withUniform("vertUniformBlock", UniformType.UNIFORM_BUFFER).withUniform("nativeFogBlock", UniformType.UNIFORM_BUFFER)
			.withUniform("nativeOcclusionBlock", UniformType.UNIFORM_BUFFER).withUniform("uNativeOcclusion", UniformType.COMBINED_IMAGE_SAMPLER)
			.withUniform("Projection", UniformType.UNIFORM_BUFFER).build();
		return RenderPipeline.builder().withVertexShader(Identifier.parse("distanthorizons:generic/native/vert"))
			.withFragmentShader(Identifier.parse("distanthorizons:generic/native/frag")).withVertexBinding(0, format)
			.withBindGroupLayout(layout).withCull(true).withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN, opaque));
	}

	public static void draw(RenderPass pass, OitStage stage)
	{
		if (PACKETS.isEmpty()) return;
		RenderPipeline pipeline;
		if (stage == null)
		{
			if (opaquePipeline == null) opaquePipeline = pipelineBuilder(true)
				.withLocation(Identifier.parse("distanthorizons:native_opaque_objects")).withShaderDefine("DH_NATIVE_OPAQUE")
				.withColorTargetState(new ColorTargetState(Optional.empty(), com.mojang.renderpearl.api.GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL)).build();
			pipeline = opaquePipeline;
		}
		else
		{
			if (pipelines == null) pipelines = OitPipelineSet.builder("distanthorizons_native_objects", pipelineBuilder(false)).build();
			pipeline = pipelines.getPipeline(stage);
		}
		pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
		NativeTransparencyRenderer.bindFog(pass);
		for (Packet packet : PACKETS)
		{
			if (packet.vertices.isClosed() || packet.indices.isClosed()) throw new IllegalStateException("Generic buffer changed within native transparency phases");
			pass.setUniform("vertUniformBlock", packet.uniforms.getGpuBuffer());
			pass.setUniform("uLightMap", packet.lightmap.getTextureView(), packet.lightmap.getTextureSampler());
			pass.setVertexBuffer(0, packet.vertices.slice());
			pass.setIndexBuffer(packet.indices, IndexType.INT);
			pass.drawIndexed(packet.count, 1, 0, 0, 0);
		}
	}

	private record Packet(GpuBuffer vertices, GpuBuffer indices, int count, BlazeUniformBufferWrapper uniforms, IDhBlazeTexture lightmap) { }
}
#endif
