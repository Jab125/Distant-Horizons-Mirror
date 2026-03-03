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
import com.seibel.distanthorizons.api.enums.rendering.EDhApiFogColorMode;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiHeightFogDirection;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiHeightFogMixMode;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.IMcFogRenderer;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.IMcSsaoRenderer;
import net.minecraft.resources.Identifier;

import java.awt.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * Renders a TODO
 */
public class McFogRenderer implements IMcFogRenderer
{
	public static final DhLogger LOGGER = new DhLoggerBuilder().build(); 
	
	private static final IMinecraftClientWrapper MC = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	
	public static final McFogRenderer INSTANCE = new McFogRenderer();
	
	private VertexFormat vertexFormat;
	private RenderPipeline pipeline;
	private boolean init = false;
	
	private GpuBuffer fragUniformBuffer;
	
	private GpuBuffer vboGpuBuffer;
	
	public GpuTexture fogColorTexture;
	
	
	
	//=============//
	// constructor //
	//=============//
	//region
	
	private McFogRenderer() 
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
			pipelineBuilder.withLocation(Identifier.parse("distanthorizons:fog_render"));
			
			pipelineBuilder.withVertexShader(Identifier.fromNamespaceAndPath("distanthorizons", "fog/quad_apply"));
			pipelineBuilder.withFragmentShader(Identifier.fromNamespaceAndPath("distanthorizons", "fog/fog"));
			
			pipelineBuilder.withSampler("uMcDepthTexture");
			pipelineBuilder.withSampler("uCombinedMcDhColorTexture");
			
			pipelineBuilder.withSampler("uDhDepthTexture");
			
			pipelineBuilder.withUniform("fragUniformBlock", UniformType.UNIFORM_BUFFER);
			
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
			
			
			Supplier<String> labelSupplier = () -> "distantHorizons:McFogRenderer";
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
	
	@Override
	public void render(DhApiMat4f modelViewProjectionMatrix, float partialTicks)
	{
		this.tryInit();
		
		
		if (McLodRenderer.INSTANCE.dhDepthTexture == null
			|| McLodRenderer.INSTANCE.dhColorTexture == null)
		{
			return;	
		}
		
		
		GpuDevice gpuDevice = RenderSystem.getDevice();
		CommandEncoder commandEncoder = gpuDevice.createCommandEncoder();
		
		
		
		// textures
		if (this.fogColorTexture == null
			|| this.fogColorTexture.getWidth(0) != MC_RENDER.getTargetFramebufferViewportWidth()
			|| this.fogColorTexture.getHeight(0) != MC_RENDER.getTargetFramebufferViewportHeight())
		{
			if (this.fogColorTexture != null)
			{
				this.fogColorTexture.close();
			}
			
			// TODO USAGE_TEXTURE_BINDING = 4
			int usage = 4 | 8 | 32 | 128;
			this.fogColorTexture = gpuDevice.createTexture("FogColorTexture",
				usage,
				TextureFormat.RGBA8,
				MC_RENDER.getTargetFramebufferViewportWidth(), MC_RENDER.getTargetFramebufferViewportHeight(),
				1, 1
			);
		}
		
		
		{
			int uniformBufferSize = new Std140SizeCalculator()
				
				// fog uniforms
				.putVec4() // uFogColor
				.putFloat() //uFogScale
				.putFloat() //uFogVerticalScale
				// only used for debugging
				.putInt() //uFogDebugMode  // 1 = render everything with fog color // 7 = use debug rendering
				.putInt() //uFogFalloffType
				
				// fog config
				.putFloat() // uFarFogStart
				.putFloat() // uFarFogLength
				.putFloat() // uFarFogMin
				.putFloat() // uFarFogRange 
				.putFloat() // uFarFogDensity
				
				// height fog config
				.putFloat() // uHeightFogStart
				.putFloat() // uHeightFogLength
				.putFloat() // uHeightFogMin
				.putFloat() // uHeightFogRange
				.putFloat() // uHeightFogDensity
				
				// ??
				.putInt() // uHeightFogEnabled
				.putInt() // uHeightFogFalloffType
				.putInt() // uHeightBasedOnCamera
				.putFloat() // uHeightFogBaseHeight
				.putInt() // uHeightFogAppliesUp
				.putInt() // uHeightFogAppliesDown
				.putInt() // uUseSphericalFog
				.putInt() // uHeightFogMixingMode
				.putFloat() // uCameraBlockYPos
				
				.putMat4f() // uInvMvmProj
				
				.get();
			
			
			// create data //
			
			
			int lodDrawDistance = Config.Client.Advanced.Graphics.Quality.lodChunkRenderDistanceRadius.get() * LodUtil.CHUNK_WIDTH;
			
			
			Mat4f inverseMvmProjMatrix = new Mat4f(modelViewProjectionMatrix);
			inverseMvmProjMatrix.invert();
			
			if (modelViewProjectionMatrix == null)
			{
				return;
			}
			
			
			Color fogColor = this.getFogColor(partialTicks);
			
			// fog config
			float farFogStart = Config.Client.Advanced.Graphics.Fog.farFogStart.get();
			float farFogEnd = Config.Client.Advanced.Graphics.Fog.farFogEnd.get();
			float farFogMin = Config.Client.Advanced.Graphics.Fog.farFogMin.get();
			float farFogMax = Config.Client.Advanced.Graphics.Fog.farFogMax.get();
			float farFogDensity = Config.Client.Advanced.Graphics.Fog.farFogDensity.get();
			
			// override fog if underwater
			if (MC_RENDER.isFogStateSpecial())
			{
				// hide everything behind fog
				farFogStart = 0.0f;
				farFogEnd = 0.0f;
			}
			
			
			// height config
			EDhApiHeightFogMixMode heightFogMixingMode = Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogMixMode.get();
			boolean heightFogEnabled = heightFogMixingMode != EDhApiHeightFogMixMode.SPHERICAL && heightFogMixingMode != EDhApiHeightFogMixMode.CYLINDRICAL;
			boolean useSphericalFog = heightFogMixingMode == EDhApiHeightFogMixMode.SPHERICAL;
			EDhApiHeightFogDirection heightFogCameraDirection = Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogDirection.get();
			
			float heightFogStart = Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogStart.get();
			float heightFogEnd = Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogEnd.get();
			float heightFogMin = Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogMin.get();
			float heightFogMax = Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogMax.get();
			float heightFogDensity = Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogDensity.get();
			
			
			// upload data //
			
			ByteBuffer buffer = ByteBuffer.allocateDirect(uniformBufferSize);
			buffer.order(ByteOrder.LITTLE_ENDIAN);
			buffer = Std140Builder.intoBuffer(buffer)
				
				// fog uniforms
				.putVec4(
					fogColor.getRed() / 255.0f, 
					fogColor.getGreen() / 255.0f, 
					fogColor.getBlue() / 255.0f, 
					fogColor.getAlpha() / 255.0f) // uFogColor
				.putFloat(1.f / lodDrawDistance) //uFogScale
				.putFloat(1.f / MC.getWrappedClientLevel().getMaxHeight()) //uFogVerticalScale
				// only used for debugging
				.putInt(0) //uFogDebugMode  // 1 = render everything with fog color // 7 = use debug rendering
				.putInt(Config.Client.Advanced.Graphics.Fog.farFogFalloff.get().value) //uFogFalloffType
				
				// fog config
				.putFloat(farFogStart) // uFarFogStart
				.putFloat(farFogEnd - farFogStart) // uFarFogLength
				.putFloat(farFogMin) // uFarFogMin
				.putFloat(farFogMax - farFogMin) // uFarFogRange 
				.putFloat(farFogDensity) // uFarFogDensity
				
				// height fog config
				.putFloat(heightFogStart) // uHeightFogStart
				.putFloat(heightFogEnd - heightFogStart) // uHeightFogLength
				.putFloat(heightFogMin) // uHeightFogMin
				.putFloat(heightFogMax - heightFogMin) // uHeightFogRange
				.putFloat(heightFogDensity) // uHeightFogDensity
				
				// ??
				.putInt(heightFogEnabled ? 1 : 0) // uHeightFogEnabled
				.putInt(Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogFalloff.get().value) // uHeightFogFalloffType
				.putInt(heightFogCameraDirection.basedOnCamera ? 1 : 0) // uHeightBasedOnCamera
				.putFloat(Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogBaseHeight.get()) // uHeightFogBaseHeight
				.putInt(heightFogCameraDirection.fogAppliesUp ? 1 : 0) // uHeightFogAppliesUp
				.putInt(heightFogCameraDirection.fogAppliesDown ? 1 : 0) // uHeightFogAppliesDown
				.putInt(useSphericalFog ? 1 : 0) // uUseSphericalFog
				.putInt(heightFogMixingMode.value) // uHeightFogMixingMode
				.putFloat((float)MC_RENDER.getCameraExactPosition().y) // uCameraBlockYPos
				
				.putMat4f(inverseMvmProjMatrix.createJomlMatrix()) // uInvMvmProj
				
				.get()
			;
			
			this.fragUniformBuffer = UniformHandler.createBuffer("fragUniformBlock", uniformBufferSize, this.fragUniformBuffer);
			GpuBufferSlice bufferSlice = new GpuBufferSlice(this.fragUniformBuffer, 0, uniformBufferSize);
			
			commandEncoder.writeToBuffer(bufferSlice, buffer);
		}
		
		
		this.renderFogToTexture();
		McFogApplyRenderer.INSTANCE.render();
		
	}
	
	private Color getFogColor(float partialTicks)
	{
		Color fogColor;
		
		if (Config.Client.Advanced.Graphics.Fog.colorMode.get() == EDhApiFogColorMode.USE_SKY_COLOR)
		{
			fogColor = MC_RENDER.getSkyColor();
		}
		else
		{
			fogColor = MC_RENDER.getFogColor(partialTicks);
		}
		
		return fogColor;
	}
	
	private void renderFogToTexture()
	{
		GpuDevice gpuDevice = RenderSystem.getDevice();
		CommandEncoder commandEncoder = gpuDevice.createCommandEncoder();
		
		// create a render pass
		Supplier<String> debugLabelSupplier = () -> "distantHorizons:McFogRenderer";
		GpuTextureView colorTexture = gpuDevice.createTextureView(this.fogColorTexture);
		OptionalInt optionalClearColorAsInt = OptionalInt.empty();
		GpuTextureView depthTexture = null;
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
				// bind DH depth texture
				{
					GpuTextureView textureView = gpuDevice.createTextureView(McLodRenderer.INSTANCE.dhDepthTexture);
					GpuSampler gpuSampler = gpuDevice.createSampler(
						AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, // U,V
						FilterMode.NEAREST, FilterMode.NEAREST, // minFilter, magFilter
						1, // maxAnisotropy 
						OptionalDouble.empty() // maxLod
					);
					renderPass.bindTexture("uDhDepthTexture", textureView, gpuSampler);
				}
				
				
				renderPass.setUniform("fragUniformBlock", this.fragUniformBuffer);
				
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
	
	
	//endregion
	
	
	
}
