/*
 *	This file is part of the Distant Horizons mod
 *	licensed under the GNU LGPL v3 License.
 *
 *	Copyright (C) 2020 James Seibel
 *
 *	This program is free software: you can redistribute it and/or modify
 *	it under the terms of the GNU Lesser General Public License as published by
 *	the Free Software Foundation, version 3.
 *
 *	This program is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *	GNU Lesser General Public License for more details.
 *
 *	You should have received a copy of the GNU Lesser General Public License
 *	along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.common.render;

#if MC_VER <= MC_26_2_0
public final class NativeSsaoRenderer { }
#else
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.textures.*;
import com.seibel.distanthorizons.common.render.blaze.BlazeDhMetaRenderer;
import com.seibel.distanthorizons.common.render.blaze.postProcessing.BlazeDhSsaoRenderer;
import com.seibel.distanthorizons.common.render.blaze.wrappers.texture.*;
import com.seibel.distanthorizons.common.render.blaze.wrappers.uniform.BlazeUniformBufferWrapper;
import com.seibel.distanthorizons.common.render.openGl.GlDhMetaRenderer;
import com.seibel.distanthorizons.common.render.openGl.postProcessing.ssao.GlDhSSAORenderer;
import com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftRenderWrapper;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.render.RenderParam;
import com.seibel.distanthorizons.core.util.RenderUtil;
import com.seibel.distanthorizons.api.enums.config.EDhApiDepthDirection;
import net.minecraft.resources.Identifier;
import java.util.*;

public final class NativeSsaoRenderer
{
	private static BlazeTextureWrapper factor = BlazeTextureWrapper.createFloatColor("native_occlusion_factor");
	private static BlazeUniformBufferWrapper blurUniforms, enabledUniform;
	private static GpuTextureView glColor, glDepth;
	private static GpuSampler glSampler;
	private static int colorId, depthId, width, height;
	private static RenderPipeline pipeline;
	private static boolean enabled;

	private NativeSsaoRenderer() { }
	public static boolean isEnabled() { return enabled; }
	public static BlazeTextureWrapper getFactorTexture() { return factor; }

	public static void prepare(RenderParam params)
	{
		enabled = Config.Client.Advanced.Graphics.enableSsao.get();
		if (enabledUniform == null) enabledUniform = new BlazeUniformBufferWrapper("native_occlusion_enabled");
		enabledUniform.putInt(enabled ? 1 : 0).finishAndUpload();
		if (!enabled) return;
		factor.tryCreateOrResizeToScreenSize();
		int w = MinecraftRenderWrapper.INSTANCE.getTargetFramebufferViewportWidth();
		int h = MinecraftRenderWrapper.INSTANCE.getTargetFramebufferViewportHeight();
		if (blurUniforms == null) blurUniforms = new BlazeUniformBufferWrapper("native_occlusion_blur");
		blurUniforms.putVec2f(w, h).putInt(2).putFloat(RenderUtil.getNearClipPlaneInBlocks())
			.putFloat(RenderUtil.getFarClipPlaneDistanceInBlocks())
			.putInt(params.renderDefinition.getDepthDirection() == EDhApiDepthDirection.REVERSE_Z ? 1 : 0).finishAndUpload();
		if (pipeline == null)
		{
			var layout = BindGroupLayout.builder().withUniform("uSourceColorTexture", UniformType.COMBINED_IMAGE_SAMPLER)
				.withUniform("uSourceDepthTexture", UniformType.COMBINED_IMAGE_SAMPLER).withUniform("applyFragUniformBlock", UniformType.UNIFORM_BUFFER);
			pipeline = RenderPipeline.builder().withLocation(Identifier.parse("distanthorizons:native_occlusion_blur"))
				.withShaderDefine("DH_NATIVE_OCCLUSION_FLOAT").withVertexShader(Identifier.parse("minecraft:core/screenquad")).withFragmentShader(Identifier.parse("distanthorizons:ssao/blaze/apply"))
				.withBindGroupLayout(layout.build()).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
				.withDepthStencilState(Optional.empty()).withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.R32_FLOAT, ColorTargetState.WRITE_ALL)).build();
		}
		boolean gl = params.renderDefinition instanceof com.seibel.distanthorizons.common.render.openGl.GlDhRenderApiDefinition;
		if (gl) prepareGl(w, h);
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
			() -> "Distant Horizons native occlusion", factor.getTextureView(), Optional.empty()))
		{
			pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
			pass.setUniform("applyFragUniformBlock", blurUniforms.getGpuBuffer());
			pass.setUniform("uSourceColorTexture", gl ? glColor : BlazeDhSsaoRenderer.INSTANCE.getRawTexture().getTextureView(),
				gl ? glSampler : BlazeDhSsaoRenderer.INSTANCE.getRawTexture().getTextureSampler());
			pass.setUniform("uSourceDepthTexture", gl ? glDepth : BlazeDhMetaRenderer.INSTANCE.dhDepthTextureWrapper.getTextureView(),
				gl ? glSampler : BlazeDhMetaRenderer.INSTANCE.dhDepthTextureWrapper.getTextureSampler());
			pass.draw(3, 1, 0, 0);
		}
	}

	private static void prepareGl(int w, int h)
	{
		int color = GlDhSSAORenderer.INSTANCE.getRawTextureId();
		int depth = GlDhMetaRenderer.INSTANCE.getActiveDepthTextureId();
		if (glColor == null || color != colorId || depth != depthId || w != width || h != height)
		{
			closeGlViews();
			glColor = RenderSystem.getDevice().createTextureView(NativeTransparencyRenderer.borrowTexture(color, w, h, GpuFormat.R16_FLOAT, 1));
			glDepth = RenderSystem.getDevice().createTextureView(NativeTransparencyRenderer.borrowTexture(depth, w, h, GpuFormat.D32_FLOAT, 1));
			colorId = color; depthId = depth; width = w; height = h;
		}
		if (glSampler == null) glSampler = RenderSystem.getDevice().createSampler(AddressMode.CLAMP_TO_EDGE,
			AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.of(0));
	}

	public static void bind(RenderPass pass)
	{
		var texture = enabled ? factor : VanillaTerrainCoverage.getTexture();
		pass.setUniform("nativeOcclusionBlock", enabledUniform.getGpuBuffer());
		pass.setUniform("uNativeOcclusion", texture.getTextureView(), texture.getTextureSampler());
	}

	private static void closeGlViews()
	{
		if (glColor != null) glColor.close();
		if (glDepth != null) glDepth.close();
		glColor = null; glDepth = null;
	}

	public static void free()
	{
		closeGlViews();
		if (glSampler != null) glSampler.close();
		glSampler = null;
		if (blurUniforms != null) blurUniforms.close();
		if (enabledUniform != null) enabledUniform.close();
		blurUniforms = null; enabledUniform = null; enabled = false;
		if (!factor.isEmpty())
		{
			factor.getTextureView().close(); factor.getTexture().close(); factor.getTextureSampler().close();
		}
		factor = BlazeTextureWrapper.createFloatColor("native_occlusion_factor");
	}
}
#endif
