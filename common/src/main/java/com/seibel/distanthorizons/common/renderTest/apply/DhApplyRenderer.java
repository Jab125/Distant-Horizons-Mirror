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
import com.seibel.distanthorizons.common.renderTest.helpers.McTextureViewWrapper;
import com.seibel.distanthorizons.common.renderTest.helpers.McTextureWrapper;
import com.seibel.distanthorizons.common.renderTest.helpers.PostProcessHelper;
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
	
	protected final String name;
	protected final String identifierName;
	public String getIdentifierName() { return this.identifierName; }
	
	@Nullable
	private final BlendFunction blendFunction;
	private final String vertexShaderPath;
	private final String fragmentShaderPath;
	
	private final McTextureViewWrapper sourceColorTextureViewWrapper = new McTextureViewWrapper();
	private final McTextureViewWrapper sourceDepthTextureViewWrapper = new McTextureViewWrapper();
	
	private final McTextureViewWrapper destinationColorTextureViewWrapper = new McTextureViewWrapper();
	
	
	
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
		this.name = name;
		this.identifierName = "distanthorizons:"+this.name;
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
		this.vboGpuBuffer = PostProcessHelper.createAndUploadScreenVertexData(this.name);
		
		this.sourceColorTextureViewWrapper.trySetup(sourceColorTexture);
		this.sourceDepthTextureViewWrapper.trySetup(sourceDepthTexture);
		
		this.destinationColorTextureViewWrapper.trySetup(destinationColorTexture);
		
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
			this.destinationColorTextureViewWrapper.textureView,
			/*optionalClearColorAsInt*/ OptionalInt.empty(),
			/*depthTexture*/ null,
			/*optionalDepthValueAsDouble*/ OptionalDouble.empty()))
		{
			renderPass.bindTexture("uSourceColorTexture", this.sourceColorTextureViewWrapper.textureView, this.sourceColorTextureViewWrapper.textureSampler);
			renderPass.bindTexture("uSourceDepthTexture", this.sourceDepthTextureViewWrapper.textureView, this.sourceDepthTextureViewWrapper.textureSampler);
			
			renderPass.setVertexBuffer(0, this.vboGpuBuffer);
			renderPass.setPipeline(this.pipeline);
			
			renderPass.draw(/*indexStart*/ 0, /*indexCount*/ 4);
		}
	}
	
	//endregion
	
	
	
}
