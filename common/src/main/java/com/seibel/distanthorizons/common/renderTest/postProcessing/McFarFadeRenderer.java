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
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.PolygonMode;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.seibel.distanthorizons.common.renderTest.helpers.*;
import com.seibel.distanthorizons.common.renderTest.apply.McCopyRenderer;
import com.seibel.distanthorizons.common.renderTest.McLodRenderer;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.RenderUtil;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.IMcFarFadeRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * Renders a TODO
 */
public class McFarFadeRenderer implements IMcFarFadeRenderer
{
	public static final DhLogger LOGGER = new DhLoggerBuilder().build(); 
	
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	
	private static final GpuDevice GPU_DEVICE = RenderSystem.getDevice();
	private static final CommandEncoder COMMAND_ENCODER = GPU_DEVICE.createCommandEncoder();
	
	public static final McFarFadeRenderer INSTANCE = new McFarFadeRenderer();
	
	private VertexFormat vertexFormat;
	private RenderPipeline pipeline;
	private boolean init = false;
	
	private GpuBuffer fragUniformBuffer;
	
	private GpuBuffer vboGpuBuffer;
	
	public final McTextureWrapper dhFadeColorTextureWrapper = McTextureWrapper.createColor("DhFadeColorTexture");
	public final McTextureViewWrapper mcColorTextureViewWrapper = new McTextureViewWrapper();
	
	
	
	//=============//
	// constructor //
	//=============//
	//region
	
	private McFarFadeRenderer() 
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
			pipelineBuilder.withLocation(Identifier.parse("distanthorizons:far_fade"));
			
			pipelineBuilder.withVertexShader(Identifier.fromNamespaceAndPath("distanthorizons", "fade/vert"));
			pipelineBuilder.withFragmentShader(Identifier.fromNamespaceAndPath("distanthorizons", "fade/dh_fade"));
			
			pipelineBuilder.withSampler("uMcColorTexture");
			
			pipelineBuilder.withSampler("uDhDepthTexture");
			pipelineBuilder.withSampler("uDhColorTexture");
			
			pipelineBuilder.withUniform("fragUniformBlock", UniformType.UNIFORM_BUFFER);
			
			pipelineBuilder.withVertexFormat(this.vertexFormat, VertexFormat.Mode.TRIANGLE_FAN);
		}
		this.pipeline = pipelineBuilder.build();
		
		
		this.vboGpuBuffer = PostProcessHelper.createAndUploadScreenVertexData("McFadeRenderer");
	}
	
	//endregion
	
	
	
	//========//
	// render //
	//========//
	//region
	
	@Override // TODO can probably just be DH mvm/proj matricies
	public void render(Mat4f mcModelViewMatrix, Mat4f mcProjectionMatrix)
	{
		this.tryInit();
		
		
		if (McLodRenderer.INSTANCE.dhDepthTextureWrapper.isEmpty()
			|| McLodRenderer.INSTANCE.dhColorTextureWrapper.isEmpty())
		{
			return;	
		}
		
		
		
		// textures
		this.dhFadeColorTextureWrapper.trySetup();
		this.mcColorTextureViewWrapper.trySetup(Minecraft.getInstance().getMainRenderTarget().getColorTexture());
		
		{
			int uniformBufferSize = new Std140SizeCalculator()
				.putFloat() // uStartFadeBlockDistance
				.putFloat() // uEndFadeBlockDistance
				.putMat4f() // uDhInvMvmProj
				.get();
			
			
			// create data //
			
			float dhFarClipDistance = RenderUtil.getFarClipPlaneDistanceInBlocks();
			float fadeStartDistance = dhFarClipDistance * 0.5f;
			float fadeEndDistance = dhFarClipDistance * 0.9f;
			
			
			Mat4f dhProjectionMatrix = RenderUtil.createLodProjectionMatrix(mcProjectionMatrix);
			Mat4f dhModelViewMatrix = RenderUtil.createLodModelViewMatrix(mcModelViewMatrix);
			
			Mat4f inverseDhModelViewProjectionMatrix = new Mat4f(dhProjectionMatrix);
			inverseDhModelViewProjectionMatrix.multiply(dhModelViewMatrix);
			inverseDhModelViewProjectionMatrix.invert();
			Mat4f inverseDhMvmProjMatrix = inverseDhModelViewProjectionMatrix;
			
			
			
			// upload data //
			
			ByteBuffer buffer = ByteBuffer.allocateDirect(uniformBufferSize);
			buffer.order(ByteOrder.nativeOrder());
			buffer = Std140Builder.intoBuffer(buffer)
				.putFloat(fadeStartDistance) // uStartFadeBlockDistance
				.putFloat(fadeEndDistance) // uEndFadeBlockDistance
				.putMat4f(inverseDhMvmProjMatrix.createJomlMatrix()) // uDhInvMvmProj
				.get()
			;
			
			this.fragUniformBuffer = UniformHandler.createBuffer("fragUniformBlock", uniformBufferSize, this.fragUniformBuffer);
			GpuBufferSlice bufferSlice = new GpuBufferSlice(this.fragUniformBuffer, 0, uniformBufferSize);
			
			COMMAND_ENCODER.writeToBuffer(bufferSlice, buffer);
		}
		
		
		this.renderFadeToTexture();
		McCopyRenderer.INSTANCE.render(this.dhFadeColorTextureWrapper, McLodRenderer.INSTANCE.dhColorTextureWrapper);
		
	}
	
	private void renderFadeToTexture()
	{
		GpuDevice gpuDevice = RenderSystem.getDevice();
		CommandEncoder commandEncoder = gpuDevice.createCommandEncoder();
		
		try (RenderPass renderPass = commandEncoder.createRenderPass(
			this::getName,
			this.dhFadeColorTextureWrapper.textureView, 
			/*optionalClearColorAsInt*/ OptionalInt.empty(),
			/*depthTexture*/ null, 
			/*optionalDepthValueAsDouble*/ OptionalDouble.empty()))
		{
			// MC texture
			renderPass.bindTexture("uMcColorTexture", this.mcColorTextureViewWrapper.textureView, this.mcColorTextureViewWrapper.textureSampler);
			
			// DH textures
			renderPass.bindTexture("uDhDepthTexture", McLodRenderer.INSTANCE.dhDepthTextureWrapper.textureView, McLodRenderer.INSTANCE.dhDepthTextureWrapper.textureSampler);
			renderPass.bindTexture("uDhColorTexture", McLodRenderer.INSTANCE.dhColorTextureWrapper.textureView, McLodRenderer.INSTANCE.dhColorTextureWrapper.textureSampler);
			
			renderPass.setUniform("fragUniformBlock", this.fragUniformBuffer);
			
			renderPass.setVertexBuffer(0, this.vboGpuBuffer); // vertex buffer can only be "0" lol
			renderPass.setPipeline(this.pipeline);
			
			renderPass.draw(/*indexStart*/ 0, /*indexCount*/ 4);
		}
	}
	private String getName() { return "distantHorizons:McFadeRenderer"; }
	
	
	//endregion
	
	
	
}
