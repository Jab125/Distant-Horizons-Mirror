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

package com.seibel.distanthorizons.common.renderTest.apply;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.PolygonMode;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.seibel.distanthorizons.common.renderTest.helpers.DhVertexFormat;
import com.seibel.distanthorizons.common.renderTest.helpers.McTextureViewWrapper;
import com.seibel.distanthorizons.common.renderTest.helpers.McTextureWrapper;
import com.seibel.distanthorizons.common.renderTest.helpers.PostProcessHelper;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import net.minecraft.resources.Identifier;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * TODO ???
 */
public class McCopyRenderer
{
	public static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	private static final GpuDevice GPU_DEVICE = RenderSystem.getDevice();
	private static final CommandEncoder COMMAND_ENCODER = GPU_DEVICE.createCommandEncoder();
	
	public static final McCopyRenderer INSTANCE = new McCopyRenderer();
	
	private VertexFormat vertexFormat;
	private RenderPipeline pipeline;
	private boolean init = false;
	
	private GpuBuffer vboGpuBuffer;
	
	
	
	//=============//
	// constructor //
	//=============//
	//region
	
	private McCopyRenderer() { }
	
	private void tryInit()
	{
		if (this.init)
		{
			return;
		}
		this.init = true;
		
		
		
		GpuDevice gpuDevice = RenderSystem.getDevice();
		CommandEncoder commandEncoder = gpuDevice.createCommandEncoder();
		
		
		this.vertexFormat = VertexFormat.builder()
			.add("vPosition", DhVertexFormat.SCREEN_POS)
			.build();
		
		
		RenderPipeline.Builder pipelineBuilder = RenderPipeline.builder();
		{
			pipelineBuilder.withCull(false);
			pipelineBuilder.withDepthWrite(false);
			pipelineBuilder.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
			pipelineBuilder.withColorWrite(true);
			pipelineBuilder.withoutBlend();
			pipelineBuilder.withPolygonMode(PolygonMode.FILL);
			pipelineBuilder.withLocation(Identifier.parse("distanthorizons:copy_render"));
			
			pipelineBuilder.withVertexShader(Identifier.fromNamespaceAndPath("distanthorizons", "copy/vert"));
			pipelineBuilder.withFragmentShader(Identifier.fromNamespaceAndPath("distanthorizons", "copy/frag"));
			
			pipelineBuilder.withSampler("uCopyTexture");
			
			pipelineBuilder.withVertexFormat(this.vertexFormat, VertexFormat.Mode.TRIANGLE_FAN);
		}
		this.pipeline = pipelineBuilder.build();
		
		
		this.vboGpuBuffer = PostProcessHelper.createAndUploadScreenVertexData("McCopyRenderer");
		
	}
	
	//endregion
	
	
	
	//========//
	// render //
	//========//
	//region
	
	public void render(
		McTextureWrapper sourceColorTextureWrapper,
		McTextureViewWrapper destinationColorTextureWrapper)
	{
		this.render(
			sourceColorTextureWrapper.textureView, sourceColorTextureWrapper.textureSampler,
			destinationColorTextureWrapper.textureView);
	}
	public void render(
		McTextureWrapper sourceColorTextureWrapper,
		McTextureWrapper destinationColorTextureWrapper)
	{
		this.render(
			sourceColorTextureWrapper.textureView, sourceColorTextureWrapper.textureSampler,
			destinationColorTextureWrapper.textureView);
	}
	
	private void render(
		GpuTextureView sourceTextureView,
		GpuSampler sourceTextureSampler,
		GpuTextureView destinationTextureView)
	{
		this.tryInit();
		
		try (RenderPass renderPass = COMMAND_ENCODER.createRenderPass(
			this::getName,
			destinationTextureView,
			/*optionalClearColorAsInt*/ OptionalInt.empty(),
			/*depthTexture*/ null,
			/*optionalDepthValueAsDouble*/ OptionalDouble.empty()))
		{
			renderPass.bindTexture("uCopyTexture", sourceTextureView, sourceTextureSampler);
			
			renderPass.setVertexBuffer(0, this.vboGpuBuffer); // vertex buffer can only be "0" lol
			
			renderPass.setPipeline(this.pipeline);
			renderPass.draw(/*indexStart*/ 0, /*indexCount*/ 4);
		}
	}
	
	private String getName() { return "distantHorizons:McCopyRenderer"; }
	
	//endregion
	
	
	
}
