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

package com.seibel.distanthorizons.common.renderTest;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.*;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.LogicOp;
import com.mojang.blaze3d.platform.PolygonMode;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.seibel.distanthorizons.api.enums.config.EDhApiGpuUploadMethod;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.render.glObject.buffer.GLVertexBuffer;
import com.seibel.distanthorizons.core.render.glObject.shader.Shader;
import com.seibel.distanthorizons.core.render.glObject.shader.ShaderProgram;
import com.seibel.distanthorizons.core.render.glObject.vertexAttribute.AbstractVertexAttribute;
import com.seibel.distanthorizons.core.render.glObject.vertexAttribute.VertexPointer;
import com.seibel.distanthorizons.core.util.ColorUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.IMcTestRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.StringUtil;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL43;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

import static com.ibm.icu.impl.ValidIdentifiers.Datatype.x;

/**
 * Renders a UV colored quad
 * to the center of the screen to confirm DH's
 * apply shader is running correctly
 */
public class McTestRenderer implements IMcTestRenderer
{
	public static final DhLogger LOGGER = new DhLoggerBuilder().build(); 
	
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	
	public static final McTestRenderer INSTANCE = new McTestRenderer();
	
	private VertexFormat vertexFormat;
	private RenderPipeline pipeline;
	private boolean init = false;
	
	private VertexFormatElement posForm;
	private VertexFormatElement colForm;
	
	private GpuBuffer vboGpuBuffer;
	
	
	//=============//
	// constructor //
	//=============//
	//region
	
	private McTestRenderer() 
	{
		this.posForm = VertexFormatElement.register(/*id*/7, /*index*/0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.POSITION, /*count*/ 2);
		this.colForm = VertexFormatElement.register(/*id*/8, /*index*/0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.COLOR, /*count*/ 4);
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
			.add("vPosition", this.posForm)
			.add("vColor", this.colForm)
			.build();
		
		int breakpointOne = 0;
		// needs to manually be set if the VertexFormatElement isn't registered
		this.vertexFormat.getOffsetsByElement()[this.posForm.id()] = 0;
		this.vertexFormat.getOffsetsByElement()[this.colForm.id()] = Float.BYTES * 2;
		
		int breakpointTwo = 0;
		
		
		RenderPipeline.Builder pipelineBuilder = RenderPipeline.builder();
		{
			pipelineBuilder.withCull(false);
			pipelineBuilder.withDepthWrite(false);
			pipelineBuilder.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
			pipelineBuilder.withColorWrite(true);
			//builder.withBlend(BlendFunction.OVERLAY);
			pipelineBuilder.withPolygonMode(PolygonMode.FILL);
			pipelineBuilder.withLocation(Identifier.parse("distanthorizons:test_render"));
			
			pipelineBuilder.withVertexShader(Identifier.fromNamespaceAndPath("distanthorizons", "vert"));
			pipelineBuilder.withFragmentShader(Identifier.fromNamespaceAndPath("distanthorizons", "frag"));
			
			pipelineBuilder.withVertexFormat(this.vertexFormat, VertexFormat.Mode.TRIANGLE_FAN);
		}
		this.pipeline = pipelineBuilder.build();
		
		
		// upload vertex data
		{
			// vertices for the OpenGL/Vulkan Triangle
			float[] vertices = new float[]
				{
					// PosX,Y,    ColorR,G,B,A
					-0.5f, -0.5f,   1.0f, 0.0f, 0.0f, 1.0f,
					0.5f, -0.5f,   0.0f, 1.0f, 0.0f, 1.0f,
					0.0f,  0.5f,   0.0f, 0.0f, 1.0f, 1.0f,
				};
			
			
			Supplier<String> labelSupplier = () -> "distantHorizons:McTestRenderer";
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
		
		
		
		GpuDevice gpuDevice = RenderSystem.getDevice();
		CommandEncoder commandEncoder = gpuDevice.createCommandEncoder();
		
		
		
		// create a render pass
		{
			Supplier<String> debugLabelSupplier = () -> "distantHorizons:McTestRenderer";
			GpuTextureView colorTexture = gpuDevice.createTextureView(Minecraft.getInstance().getMainRenderTarget().getColorTexture());
			OptionalInt optionalClearColorAsInt = OptionalInt.empty();
			GpuTextureView depthTexture = gpuDevice.createTextureView(Minecraft.getInstance().getMainRenderTarget().getDepthTexture());
			OptionalDouble optionalDepthValueAsDouble = OptionalDouble.empty();
			
			try (RenderPass renderPass = commandEncoder.createRenderPass(
				debugLabelSupplier,
				colorTexture,
				optionalClearColorAsInt,
				depthTexture, optionalDepthValueAsDouble))
			{
				//renderPass.pushDebugGroup();
				//renderPass.popDebugGroup();
				
				
				// render pass setup
				{
					// set uniform
					if (false)
					{
						Supplier<String> labelSupplier = () -> "";
						int usage = 0;
						int size = 0;
						GpuBuffer gpuBuffer = gpuDevice.createBuffer(labelSupplier, usage, size);
						
						renderPass.setUniform("name", gpuBuffer);
					}
					
					// bind depth texture
					if (false)
					{
						GpuTexture bindDepthTexture = Minecraft.getInstance().getMainRenderTarget().getDepthTexture();
						
						GpuTextureView textureView = gpuDevice.createTextureView(bindDepthTexture);
						GpuSampler gpuSampler = gpuDevice.createSampler(
							AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, // U,V
							FilterMode.NEAREST, FilterMode.NEAREST, // minFilter, magFilter
							0, // maxAnisotropy 
							OptionalDouble.empty() // maxLod
						);
						renderPass.bindTexture("depth", textureView, gpuSampler);
					}
					
					// index buffer
					if (false)
					{
						GpuBuffer buffer = null;
						renderPass.setIndexBuffer(buffer, VertexFormat.IndexType.INT);
					}
					
					// bind VBO
					{
						renderPass.setVertexBuffer(0, vboGpuBuffer); // vertex buffer can only be "0" lol
					}
					
					// set pipeline
					{
						renderPass.setPipeline(this.pipeline);
					}
				}
				
				// draw render pass
				{
					int indexStart = 0;
					int indexCount = 3;
					renderPass.draw(indexStart, indexCount);
				}
			}
		}
		
		// clear depth texture
		{
			//GpuTexture depthTex = Minecraft.getInstance().getMainRenderTarget().getDepthTexture();
			//double newDepth = 0;
			//commandEncoder.clearDepthTexture(depthTex, newDepth);
		}
	}
	
	//endregion
	
	
	
}
