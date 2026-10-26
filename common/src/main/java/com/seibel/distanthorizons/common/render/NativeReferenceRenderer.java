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
public final class NativeReferenceRenderer { }
#else
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.seibel.distanthorizons.common.render.blaze.wrappers.texture.BlazeTextureWrapper;
import com.seibel.distanthorizons.common.render.blaze.wrappers.uniform.BlazeUniformBufferWrapper;
import com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftRenderWrapper;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.render.RenderParam;
import com.seibel.distanthorizons.core.util.RenderUtil;
import com.seibel.distanthorizons.core.util.math.DhMat4f;
import com.seibel.distanthorizons.api.enums.config.EDhApiMcRenderingFadeMode;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.resources.Identifier;
import java.util.*;
import org.joml.Vector4f;

public final class NativeReferenceRenderer
{
	private static BlazeTextureWrapper SKY = BlazeTextureWrapper.createColor("native_reference_sky");
	private static BlazeTextureWrapper SAVED_COLOR = BlazeTextureWrapper.createColor("native_saved_color");
	private static BlazeTextureWrapper SAVED_DEPTH = BlazeTextureWrapper.createDepth("native_saved_depth");
	private static BlazeTextureWrapper REFERENCE_COLOR = BlazeTextureWrapper.createColor("native_reference_color");
	private static BlazeTextureWrapper REFERENCE_DEPTH = BlazeTextureWrapper.createDepth("native_reference_depth");
	private static BlazeUniformBufferWrapper uniforms;
	private static RenderPipeline pipeline;
	private static boolean rendering;
	private static boolean ready;

	private NativeReferenceRenderer() { }

	public static boolean isRendering() { return rendering; }
	public static void reset() { rendering = false; ready = false; }
	public static boolean isNeeded()
	{
		return NativeTransparencyRenderer.isPreparedFrame() && !rendering
			&& Config.Client.Advanced.Graphics.Quality.vanillaFadeMode.get() != EDhApiMcRenderingFadeMode.NONE;
	}

	public static void captureSky()
	{
		if (!isNeeded()) return;
		SKY.tryCreateOrResizeToScreenSize();
		copy(MinecraftRenderWrapper.INSTANCE.getRenderTarget().getColorTexture(), SKY.getTexture());
	}

	private static void copy(GpuTexture source, GpuTexture target)
	{
		RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(source, target,
			0, 0, 0, 0, 0, source.getWidth(0), source.getHeight(0));
	}

	public static void begin(RenderTarget target, FeatureRenderDispatcher.PreparedFrame features)
	{
		for (var texture : new BlazeTextureWrapper[]{SAVED_COLOR, SAVED_DEPTH, REFERENCE_COLOR, REFERENCE_DEPTH}) texture.tryCreateOrResizeToScreenSize();
		copy(target.getColorTexture(), SAVED_COLOR.getTexture());
		copy(target.getDepthTexture(), SAVED_DEPTH.getTexture());
		copy(SKY.getTexture(), target.getColorTexture());
		RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(target.getDepthTexture(), 0);
		rendering = true;
		NativeTransparencyRenderer.selectReference(true);
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
			() -> "Distant Horizons reference opaque features", target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty()))
		{
			RenderSystem.bindDefaultUniforms(pass);
			features.executeSolid(pass);
		}
	}

	public static void end(RenderTarget target)
	{
		copy(target.getColorTexture(), REFERENCE_COLOR.getTexture());
		copy(target.getDepthTexture(), REFERENCE_DEPTH.getTexture());
		copy(SAVED_COLOR.getTexture(), target.getColorTexture());
		copy(SAVED_DEPTH.getTexture(), target.getDepthTexture());
		rendering = false;
		ready = true;
		NativeTransparencyRenderer.selectReference(false);
	}

	public static void clearTransparency(LevelTargetBundle targets)
	{
		var encoder = RenderSystem.getDevice().createCommandEncoder();
		encoder.clearColorTexture(targets.depthBounds.get().getColorTexture(), new Vector4f(-Float.MAX_VALUE, 0, 0, 0));
		encoder.clearColorTexture(targets.depthBoundsCulled.get().getColorTexture(), new Vector4f(-Float.MAX_VALUE, 0, 0, 0));
		encoder.clearColorTexture(targets.accumulate.get().getColorTexture(), new Vector4f(0));
		for (var target : targets.transmittance) encoder.clearColorTexture(target.get().getColorTexture(), new Vector4f(0));
	}

	public static void apply(RenderTarget target, RenderParam params)
	{
		if (rendering || !NativeTransparencyRenderer.isPreparedFrame()) return;
		boolean antialiasing = Config.Client.Advanced.Graphics.enableAntiAliasing.get();
		if (!ready && (!antialiasing || Config.Client.Advanced.Graphics.Quality.vanillaFadeMode.get() != EDhApiMcRenderingFadeMode.NONE)) return;
		SAVED_COLOR.tryCreateOrResizeToScreenSize();
		copy(target.getColorTexture(), SAVED_COLOR.getTexture());
		if (uniforms == null) uniforms = new BlazeUniformBufferWrapper("native_reference_fade");
		float near = RenderUtil.getNearClipPlaneInBlocks() + 16;
		DhMat4f inverseProjectionView = new DhMat4f(params.mcProjectionMatrix);
		inverseProjectionView.multiply(new DhMat4f(params.mcModelViewMatrix));
		inverseProjectionView.invert();
		uniforms.putMat4f(inverseProjectionView).putFloat(near * 1.5f * .9f).putFloat(near * 1.9f * .9f)
			.putInt(antialiasing ? 1 : 0).putInt(ready ? 1 : 0)
			.putVec3f((float)(VanillaTerrainCoverage.getOriginX() - params.exactCameraPosition.x),
				(float)(VanillaTerrainCoverage.getOriginY() - params.exactCameraPosition.y),
				(float)(VanillaTerrainCoverage.getOriginZ() - params.exactCameraPosition.z))
			.putInt(VanillaTerrainCoverage.getSpanZ()).finishAndUpload();
		if (pipeline == null)
		{
			var layout = BindGroupLayout.builder();
			for (String name : new String[]{"uNativeColor", "uNativeDepth", "uReferenceColor", "uReferenceDepth", "uVanillaCoverageTexture"}) layout.withUniform(name, UniformType.COMBINED_IMAGE_SAMPLER);
			layout.withUniform("nativeFadeBlock", UniformType.UNIFORM_BUFFER);
			pipeline = RenderPipeline.builder().withLocation(Identifier.parse("distanthorizons:native_reference_fade"))
				.withVertexShader(Identifier.parse("minecraft:core/screenquad")).withFragmentShader(Identifier.parse("distanthorizons:fade/native/reference"))
				.withBindGroupLayout(layout.build()).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
				.withDepthStencilState(Optional.empty()).withColorTargetState(new ColorTargetState(Optional.empty(), com.mojang.renderpearl.api.GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL)).build();
		}
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
			() -> "Distant Horizons resolved terrain fade", target.getColorTextureView(), Optional.empty()))
		{
			pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
			pass.setUniform("nativeFadeBlock", uniforms.getGpuBuffer());
			pass.setUniform("uNativeColor", SAVED_COLOR.getTextureView(), SAVED_COLOR.getTextureSampler());
			pass.setUniform("uNativeDepth", target.getDepthTextureView(), SAVED_COLOR.getTextureSampler());
			pass.setUniform("uReferenceColor", (ready ? REFERENCE_COLOR : SAVED_COLOR).getTextureView(), SAVED_COLOR.getTextureSampler());
			pass.setUniform("uReferenceDepth", ready ? REFERENCE_DEPTH.getTextureView() : target.getDepthTextureView(), SAVED_COLOR.getTextureSampler());
			pass.setUniform("uVanillaCoverageTexture", VanillaTerrainCoverage.getTexture().getTextureView(), VanillaTerrainCoverage.getTexture().getTextureSampler());
			pass.draw(3, 1, 0, 0);
		}
	}

	public static void free()
	{
		reset();
		for (var wrapper : new BlazeTextureWrapper[]{SKY, SAVED_COLOR, SAVED_DEPTH, REFERENCE_COLOR, REFERENCE_DEPTH})
		{
			if (wrapper.isEmpty()) continue;
			wrapper.getTextureView().close();
			wrapper.getTexture().close();
			wrapper.getTextureSampler().close();
		}
		if (uniforms != null) uniforms.close();
		uniforms = null;
		SKY = BlazeTextureWrapper.createColor("native_reference_sky");
		SAVED_COLOR = BlazeTextureWrapper.createColor("native_saved_color");
		SAVED_DEPTH = BlazeTextureWrapper.createDepth("native_saved_depth");
		REFERENCE_COLOR = BlazeTextureWrapper.createColor("native_reference_color");
		REFERENCE_DEPTH = BlazeTextureWrapper.createDepth("native_reference_depth");
	}
}
#endif
