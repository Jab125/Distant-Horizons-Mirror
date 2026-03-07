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
import com.mojang.blaze3d.pipeline.BlendFunction;
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
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * TODO ???
 */
public class DhApplyRenderer
{
	public static final DhLogger LOGGER = new DhLoggerBuilder().build(); 
	
	private static final GpuDevice GPU_DEVICE = RenderSystem.getDevice();
	private static final CommandEncoder COMMAND_ENCODER = GPU_DEVICE.createCommandEncoder();
	
	private RenderPipeline pipeline;
	
	protected GpuBuffer vboGpuBuffer;
	
	protected final String identifierName;
	public String getIdentifierName() { return this.identifierName; }
	
	@Nullable
	private final BlendFunction blendFunction;
	private final String vertexShaderPath;
	private final String fragmentShaderPath;
	
	private GpuTextureView sourceColorTextureView;
	private GpuSampler sourceColorSampler;
	
	private GpuTextureView sourceDepthTextureView;
	private GpuSampler sourceDepthSampler;
	
	private GpuTextureView destinationColorTextureView;
	
	
	
	//=============//
	// constructor //
	//=============//
	//region
	
	public DhApplyRenderer(
		String name,
		@Nullable BlendFunction blendFunction,
		String vertexShaderPath, String fragmentShaderPath
		)
	{
		this.identifierName = "distanthorizons:"+name;
		this.blendFunction = blendFunction;
		
		this.vertexShaderPath = vertexShaderPath;
		this.fragmentShaderPath = fragmentShaderPath;
	}
	
	private void tryInit(
		GpuTexture sourceColorTexture,
		GpuTexture sourceDepthTexture,
		GpuTexture destinationColorTexture)
	{
		this.createPipeline();
		this.uploadVertexData();
		this.createTextureViews(sourceColorTexture, sourceDepthTexture, destinationColorTexture);
	}
	private void createPipeline()
	{
		if (this.pipeline != null)
		{
			return;
		}
		
		VertexFormat vertexFormat = VertexFormat.builder()
			.add("vPosition", DhVertexFormat.SCREEN_POS)
			.build();
		
		RenderPipeline.Builder pipelineBuilder = RenderPipeline.builder();
		{
			pipelineBuilder.withCull(false);
			pipelineBuilder.withDepthWrite(false);
			pipelineBuilder.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
			pipelineBuilder.withColorWrite(true);
			
			if (this.blendFunction != null)
			{
				pipelineBuilder.withBlend(this.blendFunction);
			}
			else
			{
				pipelineBuilder.withoutBlend();
			}
			
			pipelineBuilder.withPolygonMode(PolygonMode.FILL);
			pipelineBuilder.withLocation(Identifier.parse(this.identifierName)); // TODO will complain if capital letters are included
			
			// TODO manually validate paths to confirm they exist and end with ".fsh" or ".vsh", MC silently fails if the files are missing/improperly named
			pipelineBuilder.withVertexShader(Identifier.fromNamespaceAndPath("distanthorizons", this.vertexShaderPath));
			pipelineBuilder.withFragmentShader(Identifier.fromNamespaceAndPath("distanthorizons", this.fragmentShaderPath));
			
			pipelineBuilder.withSampler("uSourceColorTexture");
			pipelineBuilder.withSampler("uSourceDepthTexture");
			
			pipelineBuilder.withVertexFormat(vertexFormat, VertexFormat.Mode.TRIANGLE_FAN);
		}
		this.pipeline = pipelineBuilder.build();
	}
	private void uploadVertexData()
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
		
		
		Supplier<String> labelSupplier = () -> "distantHorizons:"+this.identifierName;
		int usage = 8 | 32; // is this just using OpenGL VBO flags?, if so I can't find it, supposedly GlDevice on Mojang's side
		int size = vertices.length * Float.BYTES;
		this.vboGpuBuffer = GPU_DEVICE.createBuffer(labelSupplier, usage, size);
		
		{
			int offset = 0;
			int length = vertices.length * Float.BYTES;
			GpuBufferSlice bufferSlice = new GpuBufferSlice(this.vboGpuBuffer, offset, length);
			
			ByteBuffer byteBuffer = ByteBuffer.allocateDirect(vertices.length * Float.BYTES);
			// Fill buffer with vertices.
			byteBuffer.order(ByteOrder.nativeOrder());
			byteBuffer.asFloatBuffer().put(vertices);
			byteBuffer.rewind();
			
			COMMAND_ENCODER.writeToBuffer(bufferSlice, byteBuffer);
		}
	}
	private void createTextureViews(
		GpuTexture sourceColorTexture,
		GpuTexture sourceDepthTexture,
		GpuTexture destinationColorTexture)
	{
		
		// source color
		if (this.sourceColorTextureView == null
			|| this.sourceColorTextureView.texture() != sourceColorTexture)
		{
			if (this.sourceColorTextureView != null)
			{
				this.sourceColorTextureView.close();
			}
			
			this.sourceColorTextureView = GPU_DEVICE.createTextureView(sourceColorTexture);
			this.sourceColorSampler = GPU_DEVICE.createSampler(
				AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, // U,V
				FilterMode.NEAREST, FilterMode.NEAREST, // minFilter, magFilter
				1, // maxAnisotropy 
				OptionalDouble.empty() // maxLod
			);
		}
		
		// source depth
		if (this.sourceDepthTextureView == null
			|| this.sourceDepthTextureView.texture() != sourceDepthTexture)
		{
			if (this.sourceDepthTextureView != null)
			{
				this.sourceDepthTextureView.close();
			}
			
			this.sourceDepthTextureView = GPU_DEVICE.createTextureView(sourceDepthTexture);
			this.sourceDepthSampler = GPU_DEVICE.createSampler(
				AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, // U,V
				FilterMode.NEAREST, FilterMode.NEAREST, // minFilter, magFilter
				1, // maxAnisotropy 
				OptionalDouble.empty() // maxLod
			);
		}
		
		// destination color
		if (this.destinationColorTextureView == null
			|| this.destinationColorTextureView.texture() != destinationColorTexture)
		{
			if (this.destinationColorTextureView != null)
			{
				this.destinationColorTextureView.close();
			}
			
			this.destinationColorTextureView = GPU_DEVICE.createTextureView(destinationColorTexture);
		}
	}
	
	//endregion
	
	
	
	//========//
	// render //
	//========//
	//region
	
	public void render(
		GpuTexture sourceColorTexture,
		GpuTexture sourceDepthTexture,
		GpuTexture destinationColorTexture)
	{
		this.tryInit(sourceColorTexture, sourceDepthTexture, destinationColorTexture);
		
		try (RenderPass renderPass = COMMAND_ENCODER.createRenderPass(
				this::getIdentifierName,
				this.destinationColorTextureView, /*optionalClearColorAsInt*/ OptionalInt.empty(),
				/*depthTexture*/ null, /*optionalDepthValueAsDouble*/ OptionalDouble.empty()))
		{
			renderPass.bindTexture("uSourceColorTexture", this.sourceColorTextureView, this.sourceColorSampler);
			renderPass.bindTexture("uSourceDepthTexture", this.sourceDepthTextureView, this.sourceDepthSampler);
			
			renderPass.setVertexBuffer(0, this.vboGpuBuffer);
			renderPass.setPipeline(this.pipeline);
			
			renderPass.draw(/*indexStart*/ 0, /*indexCount*/ 4);
		}
	}
	
	//endregion
	
	
	
}
