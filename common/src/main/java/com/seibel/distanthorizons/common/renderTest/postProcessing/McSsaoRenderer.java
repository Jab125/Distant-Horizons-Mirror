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

package com.seibel.distanthorizons.common.renderTest.postProcessing;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.PolygonMode;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import com.seibel.distanthorizons.common.renderTest.apply.DhApplyRenderer;
import com.seibel.distanthorizons.common.renderTest.helpers.*;
import com.seibel.distanthorizons.common.renderTest.McLodRenderer;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.IMcSsaoRenderer;
import net.minecraft.resources.Identifier;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * Renders a TODO
 */
public class McSsaoRenderer implements IMcSsaoRenderer
{
	public static final DhLogger LOGGER = new DhLoggerBuilder().build(); 
	
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	
	private static final GpuDevice GPU_DEVICE = RenderSystem.getDevice();
	private static final CommandEncoder COMMAND_ENCODER = GPU_DEVICE.createCommandEncoder();
	
	public static final McSsaoRenderer INSTANCE = new McSsaoRenderer();
	
	
	private DhApplyRenderer applyRenderer;
	
	private VertexFormat vertexFormat;
	private RenderPipeline pipeline;
	private boolean init = false;
	
	private GpuBuffer fragUniformBuffer;
	
	private GpuBuffer vboGpuBuffer;
	
	public McTextureWrapper ssaoColorTextureWrapper = McTextureWrapper.createColor("DhSsaoTexture");
	
	
	
	//=============//
	// constructor //
	//=============//
	//region
	
	private McSsaoRenderer() 
	{
		this.vertexFormat = VertexFormat.builder()
			.add("vPosition", DhVertexFormat.SCREEN_POS)
			.build();
	}
	
	private void tryInit()
	{
		if (this.init)
		{
			return;
		}
		this.init = true;
		
		
		this.applyRenderer = new DhApplyRenderer(
			"ssao_apply_to_dh",
			new BlendFunction(SourceFactor.ZERO, DestFactor.SRC_ALPHA, SourceFactor.ZERO, DestFactor.ONE),
			"apply/vert", "ssao/apply"
		);
		
		GpuDevice gpuDevice = RenderSystem.getDevice();
		CommandEncoder commandEncoder = gpuDevice.createCommandEncoder();
		
		
		
		RenderPipeline.Builder pipelineBuilder = RenderPipeline.builder();
		{
			pipelineBuilder.withCull(false);
			pipelineBuilder.withDepthWrite(false);
			pipelineBuilder.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
			pipelineBuilder.withColorWrite(true);
			pipelineBuilder.withoutBlend();
			pipelineBuilder.withPolygonMode(PolygonMode.FILL);
			pipelineBuilder.withLocation(Identifier.parse("distanthorizons:ssao_render"));
			
			pipelineBuilder.withVertexShader(Identifier.fromNamespaceAndPath("distanthorizons", "ssao/quad_apply"));
			pipelineBuilder.withFragmentShader(Identifier.fromNamespaceAndPath("distanthorizons", "ssao/ao"));
			
			pipelineBuilder.withSampler("uDhDepthTexture");
			
			pipelineBuilder.withUniform("fragUniformBlock", UniformType.UNIFORM_BUFFER);
			
			pipelineBuilder.withVertexFormat(this.vertexFormat, VertexFormat.Mode.TRIANGLE_FAN);
		}
		this.pipeline = pipelineBuilder.build();
		
		
		this.vboGpuBuffer = PostProcessHelper.createAndUploadScreenVertexData("McSsao");
	}
	
	//endregion
	
	
	
	//========//
	// render //
	//========//
	//region
	
	@Override
	public void render(DhApiMat4f dhProjectionMatrix)
	{
		this.tryInit();
		
		
		if (McLodRenderer.INSTANCE.dhDepthTextureWrapper.isEmpty()
			|| McLodRenderer.INSTANCE.dhColorTextureWrapper.isEmpty())
		{
			return;	
		}
		
		
		
		// textures
		this.ssaoColorTextureWrapper.trySetup();
		
		{
			int uniformBufferSize = new Std140SizeCalculator()
				.putInt() // uSampleCount\
				
				.putFloat() // uRadius
				.putFloat() // uStrength
				.putFloat() // uMinLight
				.putFloat() // uBias
				.putFloat() // uFadeDistanceInBlocks
				
				.putMat4f() // uInvProj
				.putMat4f() // uProj
				.get();
			
			
			// create data //
			
			Mat4f projMatrix = new Mat4f(dhProjectionMatrix);
			Mat4f invertedProjMatrix = new Mat4f(dhProjectionMatrix);
			invertedProjMatrix.invert();
			
			
			// upload data //
			
			ByteBuffer buffer = ByteBuffer.allocateDirect(uniformBufferSize);
			buffer.order(ByteOrder.nativeOrder());
			buffer = Std140Builder.intoBuffer(buffer)
				.putInt(6) // uSampleCount
				
				.putFloat(4.0f) // uRadius
				.putFloat(0.2f) // uStrength
				.putFloat(0.25f) // uMinLight
				.putFloat(0.02f) // uBias
				.putFloat(1_600.0f) // uFadeDistanceInBlocks
				
				.putMat4f(invertedProjMatrix.createJomlMatrix())
				.putMat4f(projMatrix.createJomlMatrix())
				.get()
			;
			
			this.fragUniformBuffer = UniformHandler.createBuffer("fragUniformBlock", uniformBufferSize, this.fragUniformBuffer);
			GpuBufferSlice bufferSlice = new GpuBufferSlice(this.fragUniformBuffer, 0, uniformBufferSize);
			
			COMMAND_ENCODER.writeToBuffer(bufferSlice, buffer);
		}
		
		
		this.renderSsaoToTexture();
		this.applyRenderer.render(this.ssaoColorTextureWrapper.texture, McLodRenderer.INSTANCE.dhDepthTextureWrapper.texture, McLodRenderer.INSTANCE.dhColorTextureWrapper.texture);
		
	}
	
	private void renderSsaoToTexture()
	{
		try (RenderPass renderPass = COMMAND_ENCODER.createRenderPass(
			this::getName,
			this.ssaoColorTextureWrapper.textureView,
			/*optionalClearColorAsInt*/ OptionalInt.empty(),
			/*depthTexture*/ null,
			/*optionalDepthValueAsDouble*/ OptionalDouble.empty()))
		{
			renderPass.bindTexture("uDhDepthTexture", McLodRenderer.INSTANCE.dhDepthTextureWrapper.textureView, McLodRenderer.INSTANCE.dhDepthTextureWrapper.textureSampler);
			
			renderPass.setUniform("fragUniformBlock", this.fragUniformBuffer);
			
			renderPass.setVertexBuffer(0, this.vboGpuBuffer); // vertex buffer can only be "0" lol
			
			renderPass.setPipeline(this.pipeline);
			renderPass.draw(/*indexStart*/ 0, /*indexCount*/ 4);
		}
	}
	private String getName() { return "distantHorizons:McSsaoRenderer"; }
	
	
	//endregion
	
	
	
}
