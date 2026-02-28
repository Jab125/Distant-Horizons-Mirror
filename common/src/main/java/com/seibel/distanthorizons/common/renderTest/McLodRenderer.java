package com.seibel.distanthorizons.common.renderTest;


import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BlendFunction;
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
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3f;
import com.seibel.distanthorizons.common.wrappers.misc.LightMapWrapper;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.render.glObject.GLEnums;
import com.seibel.distanthorizons.core.render.glObject.buffer.QuadElementBuffer;
import com.seibel.distanthorizons.core.render.renderer.RenderParams;
import com.seibel.distanthorizons.core.util.ColorUtil;
import com.seibel.distanthorizons.core.util.RenderUtil;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IProfilerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.IMcLodRenderer;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.IVertexBufferWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL32;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * Renders a TODO
 */
public class McLodRenderer implements IMcLodRenderer
{
	public static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	
	public static final McLodRenderer INSTANCE = new McLodRenderer();
	
	private VertexFormat vertexFormat;
	private RenderPipeline opaquePipeline;
	private RenderPipeline transparentPipeline;
	private boolean init = false;
	
	private GpuBuffer indexBuffer;
	
	private GpuBuffer fragUniformBuffer;
	private GpuBuffer vertUniformBuffer;
	
	public GpuTexture dhDepthTexture;
	public GpuTexture dhColorTexture;
	
	
	
	//=============//
	// constructor //
	//=============//
	//region
	
	private McLodRenderer()
	{
		this.vertexFormat = VertexFormat.builder()
			.add("vPosition", DhVertexFormat.SHORT_POS)
			.add("meta", DhVertexFormat.META)
			.add("vColor", DhVertexFormat.RGBA_UBYTE_COLOR)
			.add("irisMaterial", DhVertexFormat.IRIS_MATERIAL)
			.add("irisNormal", DhVertexFormat.IRIS_NORMAL)
			.add("paddingTwo", DhVertexFormat.BYTE_PAD)
			.add("paddingThree", DhVertexFormat.BYTE_PAD) // padding is to make sure the format is a multiple of 4
			.build();
	}
	
	private void tryInit()
	{
		if (this.init)
		{
			return;
		}
		this.init = true; // todo only set when succeeded (in case of exception)
		
		
		
		GpuDevice gpuDevice = RenderSystem.getDevice();
		CommandEncoder commandEncoder = gpuDevice.createCommandEncoder();
		
		
		RenderPipeline.Builder pipelineBuilder = RenderPipeline.builder();
		{
			pipelineBuilder.withCull(true);
			pipelineBuilder.withDepthWrite(true);
			pipelineBuilder.withDepthTestFunction(DepthTestFunction.LESS_DEPTH_TEST);
			pipelineBuilder.withColorWrite(true);
			pipelineBuilder.withPolygonMode(PolygonMode.FILL);
			pipelineBuilder.withLocation(Identifier.parse("distanthorizons:lod_render"));
			
			pipelineBuilder.withVertexShader(Identifier.fromNamespaceAndPath("distanthorizons", "lod/vert"));
			pipelineBuilder.withFragmentShader(Identifier.fromNamespaceAndPath("distanthorizons", "lod/frag"));
			
			pipelineBuilder.withSampler("uLightMap");
			
			pipelineBuilder.withUniform("vertUniformBlock", UniformType.UNIFORM_BUFFER);
			pipelineBuilder.withUniform("fragUniformBlock", UniformType.UNIFORM_BUFFER);
			
			pipelineBuilder.withVertexFormat(this.vertexFormat, VertexFormat.Mode.TRIANGLES);
		}
		
		// opaque
		{
			pipelineBuilder.withoutBlend();
			this.opaquePipeline = pipelineBuilder.build();
		}
		
		// transparent
		{
			pipelineBuilder.withBlend(BlendFunction.TRANSLUCENT);
			this.transparentPipeline = pipelineBuilder.build();
		}
		
		
		
	}
	
	//endregion
	
	@Override
	public int getVertexSize() { return this.vertexFormat.getVertexSize(); }
	
	
	
	//========//
	// render //
	//========//
	//region
	
	@Override
	public void render(
		RenderParams renderEventParam, 
		boolean opaquePass,
		DhApiVec3f modelOffset, 
		IVertexBufferWrapper[] bufferList,
		IProfilerWrapper profiler)
	{
		this.tryInit();
		
		
		
		GpuDevice gpuDevice = RenderSystem.getDevice();
		CommandEncoder commandEncoder = gpuDevice.createCommandEncoder();
		
		
		
		profiler.push("set vert uniforms");
		{
			// create data //
			
			Mat4f combinedMatrix = new Mat4f(renderEventParam.dhProjectionMatrix);
			combinedMatrix.multiply(renderEventParam.dhModelViewMatrix);
			
			float earthCurveRatio = Config.Client.Advanced.Graphics.Experimental.earthCurveRatio.get();
			if (earthCurveRatio < -1.0f || earthCurveRatio > 1.0f)
			{
				earthCurveRatio = /*6371KM*/ 6371000.0f / earthCurveRatio;
			}
			else
			{
				// disable curvature if the config value is between -1 and 1
				earthCurveRatio = 0.0f;
			}
			
			
			// upload data //
			
			int uniformBufferSize = new Std140SizeCalculator()
				.putInt() // uIsWhiteWorld
				
				.putFloat() // uWorldYOffset
				.putFloat() // uMircoOffset
				.putFloat() // uEarthRadius
				
				.putVec3() // uModelOffset
				
				.putMat4f() // uCombinedMatrix
				.get();
			
			ByteBuffer buffer = ByteBuffer.allocateDirect(uniformBufferSize);
			buffer.order(ByteOrder.LITTLE_ENDIAN);
			buffer = Std140Builder.intoBuffer(buffer)
				.putInt(0) // uIsWhiteWorld
				
				.putFloat((float)renderEventParam.worldYOffset) // uWorldYOffset
				.putFloat(0.01f) // uMircoOffset // 0.01 block offset
				.putFloat(earthCurveRatio) // uEarthRadius
				
				.putVec3(modelOffset.x, modelOffset.y, modelOffset.z) // uModelOffset
				
				.putMat4f(combinedMatrix.createJomlMatrix()) // uCombinedMatrix
				.get();
			
			this.vertUniformBuffer = UniformHandler.createBuffer("vertUniformBlock", uniformBufferSize, this.vertUniformBuffer);
			GpuBufferSlice bufferSlice = new GpuBufferSlice(this.vertUniformBuffer, 0, uniformBufferSize);
			
			commandEncoder.writeToBuffer(bufferSlice, buffer);
			
		}
		
		
		profiler.popPush("set frag uniforms");
		{
			int uniformBufferSize = new Std140SizeCalculator()
				.putFloat() // uClipDistance
				.putFloat() // uNoiseIntensity
				.putInt() // uNoiseSteps
				.putInt() // uNoiseDropoff
				.putInt() // uDitherDhRendering
				.putInt() // uNoiseEnabled
				.get();
			
			
			// create data //
			
			float dhNearClipDistance = RenderUtil.getNearClipPlaneInBlocks();
			if (!Config.Client.Advanced.Debugging.lodOnlyMode.get())
			{
				// this added value prevents the near clip plane and discard circle from touching, which looks bad
				dhNearClipDistance += 16f;
			}
			
			
			// upload data //
			
			ByteBuffer buffer = ByteBuffer.allocateDirect(uniformBufferSize);
			buffer.order(ByteOrder.LITTLE_ENDIAN);
			buffer = Std140Builder.intoBuffer(buffer)
				.putFloat(dhNearClipDistance) // uClipDistance
				.putFloat(Config.Client.Advanced.Graphics.NoiseTexture.noiseIntensity.get()) // uNoiseIntensity
				.putInt(Config.Client.Advanced.Graphics.NoiseTexture.noiseSteps.get()) // uNoiseSteps
				.putInt(Config.Client.Advanced.Graphics.NoiseTexture.noiseDropoff.get()) // uNoiseDropoff
				.putInt(Config.Client.Advanced.Graphics.Quality.ditherDhFade.get() ? 1 : 0) // uDitherDhRendering
				.putInt(Config.Client.Advanced.Graphics.NoiseTexture.enableNoiseTexture.get() ? 1 : 0) // uNoiseEnabled
				.get()
			;
			
			this.fragUniformBuffer = UniformHandler.createBuffer("fragUniformBlock", uniformBufferSize, this.fragUniformBuffer);
			GpuBufferSlice bufferSlice = new GpuBufferSlice(this.fragUniformBuffer, 0, uniformBufferSize);
			
			commandEncoder.writeToBuffer(bufferSlice, buffer);
		}
		
		// create index buffer
		{
			if (this.indexBuffer == null)
			{
				ByteBuffer buffer = MemoryUtil.memAlloc(LodBufferContainer.MAX_QUADS_PER_BUFFER * GLEnums.getTypeSize(GL32.GL_UNSIGNED_INT) * 6);
				QuadElementBuffer.buildBuffer(LodBufferContainer.MAX_QUADS_PER_BUFFER, buffer, GL32.GL_UNSIGNED_INT);
				
				
				// create VBO if needed
				if (this.indexBuffer == null
					|| this.indexBuffer.size() < buffer.capacity())
				{
					// GpuBuffer.USAGE_UNIFORM = 128
					// GpuBuffer.USAGE_INDEX = 64
					int usage = 8 | 32 | 64 | 128; // is this just using OpenGL VBO flags?, if so I can't find it, supposedly GlDevice on Mojang's side
					this.indexBuffer = gpuDevice.createBuffer(() -> "DH Index Buffer", usage, buffer.capacity());
				}
				
				int offset = 0;
				GpuBufferSlice bufferSlice = new GpuBufferSlice(this.indexBuffer, offset, buffer.capacity());
				commandEncoder.writeToBuffer(bufferSlice, buffer);
			}
		}
		
		// textures
		if (this.dhDepthTexture == null
			|| this.dhDepthTexture.getWidth(0) != MC_RENDER.getTargetFramebufferViewportWidth()
			|| this.dhDepthTexture.getHeight(0) != MC_RENDER.getTargetFramebufferViewportHeight())
		{
			if (this.dhDepthTexture != null)
			{
				this.dhDepthTexture.close();
				this.dhColorTexture.close();
			}
			
			// TODO USAGE_TEXTURE_BINDING = 4
			int usage = 4 | 8 | 32 | 128;
			this.dhDepthTexture = gpuDevice.createTexture("DhDepthTexture",
				usage,
				TextureFormat.DEPTH32,
				MC_RENDER.getTargetFramebufferViewportWidth(), MC_RENDER.getTargetFramebufferViewportHeight(),
				1, 1
			);
			this.dhColorTexture = gpuDevice.createTexture("DhColorTexture",
				usage,
				TextureFormat.RGBA8,
				MC_RENDER.getTargetFramebufferViewportWidth(), MC_RENDER.getTargetFramebufferViewportHeight(),
				1, 1
			);
		}
		
		
		profiler.popPush("create render pass");
		
		// create a render pass
		Supplier<String> debugLabelSupplier = () -> "distantHorizons:McLodRenderer";
		OptionalInt optionalClearColorAsInt = OptionalInt.empty();
		OptionalDouble optionalDepthValueAsDouble = OptionalDouble.empty();
		
		try (
			GpuTextureView colorTextureView = gpuDevice.createTextureView(this.dhColorTexture);
			GpuTextureView depthTextureView = gpuDevice.createTextureView(this.dhDepthTexture);
			RenderPass renderPass = commandEncoder.createRenderPass(
			debugLabelSupplier,
			colorTextureView,
			optionalClearColorAsInt,
			depthTextureView, optionalDepthValueAsDouble))
		{
			//renderPass.pushDebugGroup();
			//renderPass.popDebugGroup();
			
			
			// bind MC color texture
			{
				LightMapWrapper lightMapWrapper = (LightMapWrapper) renderEventParam.lightmap;
				
				GpuTextureView textureView = gpuDevice.createTextureView(lightMapWrapper.gpuTexture);
				GpuSampler gpuSampler = gpuDevice.createSampler(
					AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, // U,V
					FilterMode.NEAREST, FilterMode.NEAREST, // minFilter, magFilter
					1, // maxAnisotropy 
					OptionalDouble.empty() // maxLod
				);
				renderPass.bindTexture("uLightMap", textureView, gpuSampler);
			}
			
			
			renderPass.setUniform("vertUniformBlock", this.vertUniformBuffer);
			renderPass.setUniform("fragUniformBlock", this.fragUniformBuffer);
			
			
			
			profiler.popPush("set pipeline");
			
			renderPass.setPipeline(opaquePass ? this.opaquePipeline : this.transparentPipeline);
			renderPass.setIndexBuffer(this.indexBuffer, VertexFormat.IndexType.INT);
			
			// render pass setup
			for (int i = 0; i < bufferList.length; i++)
			{
				VertexBufferWrapper bufferWrapper = (VertexBufferWrapper)bufferList[i];
				if (!bufferWrapper.uploaded
					|| bufferWrapper.vertexCount == 0)
				{
					continue;
				}
				
				profiler.popPush("set VBO");
				
				renderPass.setVertexBuffer(0, bufferWrapper.vboGpuBuffer); // vertex buffer can only be "0" lol
				
				profiler.popPush("render");
				
				try
				{
					renderPass.drawIndexed(
						/*indexStart*/ 0,
						/*firstIndex*/0,
						/*indexCount*/bufferWrapper.indexCount,
						/*instanceCount*/1);
				}
				catch (IllegalStateException e)
				{
					if (!e.getMessage().contains("Vertex buffer at slot 0 has been closed"))
					{
						throw new RuntimeException(e);
					}
				}
				
			}
		}
		
		
		
		profiler.pop();
	}
	
	@Override
	public void applyToMcTexture() { McApplyRenderer.INSTANCE.render(); }
	
	@Override
	public void clearDepth()
	{
		GpuDevice gpuDevice = RenderSystem.getDevice();
		CommandEncoder commandEncoder = gpuDevice.createCommandEncoder();

		if (this.dhDepthTexture != null)
		{
			commandEncoder.clearDepthTexture(this.dhDepthTexture, 1.0f);
		}
	}
	
	@Override
	public void clearColor()
	{
		GpuDevice gpuDevice = RenderSystem.getDevice();
		CommandEncoder commandEncoder = gpuDevice.createCommandEncoder();
		
		if (this.dhColorTexture != null)
		{
			commandEncoder.clearColorTexture(this.dhColorTexture, ColorUtil.argbToInt(1, 1, 1, 1));
		}
	}
	
	//endregion
	
	
	
}
