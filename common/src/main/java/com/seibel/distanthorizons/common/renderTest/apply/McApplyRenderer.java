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
import com.seibel.distanthorizons.common.renderTest.McLodRenderer;
import com.seibel.distanthorizons.common.renderTest.helpers.DhVertexFormat;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * TODO ???
 */
public class McApplyRenderer
{
	public static final DhLogger LOGGER = new DhLoggerBuilder().build(); 
	
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	
	public static final McApplyRenderer INSTANCE = new McApplyRenderer();
	
	private VertexFormat vertexFormat;
	private RenderPipeline pipeline;
	private boolean init = false;
	
	private GpuBuffer vboGpuBuffer;
	
	private GpuTextureView dhColorTextureView;
	private GpuSampler dhColorTextureSampler;
	
	private GpuTextureView dhDepthTextureView;
	private GpuSampler dhDepthTextureSampler;
	
	private GpuTextureView mcColorTextureView;
	
	
	//=============//
	// constructor //
	//=============//
	//region
	
	private McApplyRenderer() 
	{
		
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
			pipelineBuilder.withLocation(Identifier.parse("distanthorizons:apply_render"));
			
			pipelineBuilder.withVertexShader(Identifier.fromNamespaceAndPath("distanthorizons", "apply/vert"));
			pipelineBuilder.withFragmentShader(Identifier.fromNamespaceAndPath("distanthorizons", "apply/frag"));
			
			pipelineBuilder.withSampler("uDhColorTexture");
			pipelineBuilder.withSampler("uDhDepthTexture");
			
			pipelineBuilder.withVertexFormat(this.vertexFormat, VertexFormat.Mode.TRIANGLE_FAN);
		}
		this.pipeline = pipelineBuilder.build();
		
		
		// upload vertex data
		{
			// vertices for a full-screen quad
			float[] vertices = new float[]
				{
					// PosX,Y,
					-1f, -1f,
					1f, -1f,
					1f,  1f,
					-1f,  1f,
				};
			
			
			Supplier<String> labelSupplier = () -> "distantHorizons:McApplyRenderer";
			int usage = 8 | 32; // is this just using OpenGL VBO flags?, if so I can't find it, supposedly GlDevice on Mojang's side
			int size = vertices.length * Float.BYTES;
			this.vboGpuBuffer = gpuDevice.createBuffer(labelSupplier, usage, size);
			
			{
				int offset = 0;
				int length = vertices.length * Float.BYTES;
				GpuBufferSlice bufferSlice = new GpuBufferSlice(this.vboGpuBuffer, offset, length);
				
				ByteBuffer byteBuffer = ByteBuffer.allocateDirect(vertices.length * Float.BYTES);
				// Fill buffer with vertices.
				byteBuffer.order(ByteOrder.nativeOrder());
				byteBuffer.asFloatBuffer().put(vertices);
				byteBuffer.rewind();
				
				commandEncoder.writeToBuffer(bufferSlice, byteBuffer);
			}
		}
		
	}
	
	//endregion
	
	
	
	//========//
	// render //
	//========//
	//region
	
	public void render()
	{
		this.tryInit();
		
		
		if (McLodRenderer.INSTANCE.dhColorTexture == null)
		{
			return;	
		}
		
		if (McLodRenderer.INSTANCE.dhDepthTexture == null)
		{
			return;
		}
		
		
		GpuDevice gpuDevice = RenderSystem.getDevice();
		CommandEncoder commandEncoder = gpuDevice.createCommandEncoder();
		
		
		{
			GpuTexture mcColorTexture = Minecraft.getInstance().getMainRenderTarget().getColorTexture();
			if (this.mcColorTextureView == null
				|| this.mcColorTextureView.texture() != mcColorTexture)
			{
				this.mcColorTextureView = gpuDevice.createTextureView(mcColorTexture);
			}
			
			
			if (this.dhColorTextureSampler == null)
			{
				this.dhColorTextureSampler = gpuDevice.createSampler(
					AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, // U,V
					FilterMode.NEAREST, FilterMode.NEAREST, // minFilter, magFilter
					1, // maxAnisotropy 
					OptionalDouble.empty() // maxLod
				);
				
				this.dhDepthTextureSampler = gpuDevice.createSampler(
					AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, // U,V
					FilterMode.NEAREST, FilterMode.NEAREST, // minFilter, magFilter
					1, // maxAnisotropy 
					OptionalDouble.empty() // maxLod
				);
			}
			
		}
		
		
		// create a render pass
		{
			Supplier<String> debugLabelSupplier = () -> "distantHorizons:McApplyRenderer";
			OptionalInt optionalClearColorAsInt = OptionalInt.empty();
			GpuTextureView depthTexture = null;
			OptionalDouble optionalDepthValueAsDouble = OptionalDouble.empty();
			
			try (RenderPass renderPass = commandEncoder.createRenderPass(
				debugLabelSupplier,
				this.mcColorTextureView,
				optionalClearColorAsInt,
				depthTexture, optionalDepthValueAsDouble))
			{
				//renderPass.pushDebugGroup();
				//renderPass.popDebugGroup();
				
				
				// render pass setup
				{
					renderPass.bindTexture("uDhDepthTexture", McLodRenderer.INSTANCE.dhDepthTextureView, this.dhColorTextureSampler);
					renderPass.bindTexture("uDhColorTexture", McLodRenderer.INSTANCE.dhColorTextureView, this.dhDepthTextureSampler);
					
					// bind VBO
					renderPass.setVertexBuffer(0, this.vboGpuBuffer); // vertex buffer can only be "0" lol
					
					// set pipeline
					renderPass.setPipeline(this.pipeline);
				}
				
				// draw render pass
				{
					int indexStart = 0;
					int indexCount = 4;
					renderPass.draw(indexStart, indexCount);
				}
			}
		}
	}
	
	//endregion
	
	
	
}
