package com.seibel.distanthorizons.common.render.blaze;


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
import com.seibel.distanthorizons.common.render.blaze.apply.DhApplyRenderer;
import com.seibel.distanthorizons.common.render.blaze.helpers.*;
import com.seibel.distanthorizons.common.wrappers.misc.LightMapWrapper;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.render.glObject.GLEnums;
import com.seibel.distanthorizons.core.render.glObject.buffer.QuadElementBuffer;
import com.seibel.distanthorizons.core.render.renderer.RenderParams;
import com.seibel.distanthorizons.core.util.ColorUtil;
import com.seibel.distanthorizons.core.util.RenderUtil;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.core.util.objects.SortedArraySet;
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

/**
 * Renders a TODO
 */
public class McLodRenderer implements IMcLodRenderer
{
	public static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	
	private static final GpuDevice GPU_DEVICE = RenderSystem.getDevice();
	private static final CommandEncoder COMMAND_ENCODER = GPU_DEVICE.createCommandEncoder();
	
	public static final McLodRenderer INSTANCE = new McLodRenderer();
	
	
	private DhApplyRenderer applyRenderer;
	
	private VertexFormat vertexFormat;
	private RenderPipeline opaquePipeline;
	private RenderPipeline transparentPipeline;
	private boolean init = false;
	
	private GpuBuffer indexBuffer;
	
	private GpuBuffer fragUniformBuffer;
	private GpuBuffer vertSharedUniformBuffer;
	
	public final McTextureWrapper dhDepthTextureWrapper = McTextureWrapper.createDepth("DhDepthTexture");
	public final McTextureWrapper dhColorTextureWrapper = McTextureWrapper.createColor("DhColorTexture");
	
	
	
	//=============//
	// constructor //
	//=============//
	//region
	
	private McLodRenderer()
	{
		this.vertexFormat = VertexFormat.builder()
			.add("vPosition", DhVertexFormat.SHORT_XYZ_POS)
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
		
		
		this.applyRenderer = new DhApplyRenderer(
			"dh_apply_to_mc",
			null,
			"apply/vert", "apply/frag"
		);
		
		
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
			
			pipelineBuilder.withUniform("vertUniqueUniformBlock", UniformType.UNIFORM_BUFFER);
			pipelineBuilder.withUniform("vertSharedUniformBlock", UniformType.UNIFORM_BUFFER);
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
		SortedArraySet<LodBufferContainer> bufferContainers,
		IProfilerWrapper profiler)
	{
		this.tryInit();
		
		
		profiler.push("vert unique uniforms");
		{
			// create data //
			
			for (int lodIndex = 0; lodIndex < bufferContainers.size(); lodIndex++)
			{
				LodBufferContainer bufferContainer = bufferContainers.get(lodIndex);
				bufferContainer.uniforms.createBufferData(renderEventParam, bufferContainer);
				bufferContainer.uniforms.upload();
			}
		}
		
		profiler.popPush("vert share uniforms");
		{
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
				
				.putMat4f() // uCombinedMatrix
				.get();
			
			ByteBuffer buffer = ByteBuffer.allocateDirect(uniformBufferSize);
			buffer.order(ByteOrder.nativeOrder());
			Std140Builder.intoBuffer(buffer)
				.putInt(0) // uIsWhiteWorld
				
				.putFloat((float) renderEventParam.worldYOffset) // uWorldYOffset
				.putFloat(0.01f) // uMircoOffset // 0.01 block offset
				.putFloat(earthCurveRatio) // uEarthRadius
				
				.putMat4f(combinedMatrix.createJomlMatrix()) // uCombinedMatrix
				.get();
			
			this.vertSharedUniformBuffer = UniformHandler.createBuffer("vertSharedUniformBlock", uniformBufferSize, this.vertSharedUniformBuffer);
			GpuBufferSlice bufferSlice = new GpuBufferSlice(this.vertSharedUniformBuffer, 0, uniformBufferSize);
			
			COMMAND_ENCODER.writeToBuffer(bufferSlice, buffer);
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
			buffer.order(ByteOrder.nativeOrder());
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
			
			COMMAND_ENCODER.writeToBuffer(bufferSlice, buffer);
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
					this.indexBuffer = GPU_DEVICE.createBuffer(() -> "DH Index Buffer", usage, buffer.capacity());
				}
				
				int offset = 0;
				GpuBufferSlice bufferSlice = new GpuBufferSlice(this.indexBuffer, offset, buffer.capacity());
				COMMAND_ENCODER.writeToBuffer(bufferSlice, buffer);
			}
		}
		
		// textures
		this.dhDepthTextureWrapper.trySetup();
		this.dhColorTextureWrapper.trySetup();
		
		//LightMapWrapper lightMapWrapper = (LightMapWrapper) renderEventParam.lightmap;
		//this.mcLightTextureViewWrapper.trySetup(lightMapWrapper.gpuTexture);
		
		
		//LightMapWrapper lightMapWrapper = (LightMapWrapper) renderEventParam.lightmap;
		//McTextureViewWrapper lightmapTextureViewWrapper = lightMapWrapper.getTextureViewWrapper();
		//renderPass.bindTexture("uLightMap", lightmapTextureViewWrapper.textureView, lightmapTextureViewWrapper.textureSampler);
		
		
		{
			profiler.popPush("setup");
			
			// create a render pass
			OptionalInt optionalClearColorAsInt = OptionalInt.empty();
			OptionalDouble optionalDepthValueAsDouble = OptionalDouble.empty();
			
			try(RenderPass renderPass = COMMAND_ENCODER.createRenderPass(
				this::getName,
				this.dhColorTextureWrapper.textureView,
				optionalClearColorAsInt,
				this.dhDepthTextureWrapper.textureView, optionalDepthValueAsDouble)
				)
			{
				// bind MC Lightmap
				//renderPass.bindTexture("uLightMap", this.mcLightTextureViewWrapper.textureView, this.mcLightTextureViewWrapper.textureSampler);
				LightMapWrapper lightMapWrapper = (LightMapWrapper) renderEventParam.lightmap;
				McTextureViewWrapper lightmapTextureViewWrapper = lightMapWrapper.getTextureViewWrapper();
				renderPass.bindTexture("uLightMap", lightmapTextureViewWrapper.textureView, lightmapTextureViewWrapper.textureSampler);
				
				// set pipeline
				renderPass.setPipeline(opaquePass ? this.opaquePipeline : this.transparentPipeline);
				renderPass.setIndexBuffer(this.indexBuffer, VertexFormat.IndexType.INT);
				
				// shared uniforms
				renderPass.setUniform("fragUniformBlock", this.fragUniformBuffer);
				renderPass.setUniform("vertSharedUniformBlock", this.vertSharedUniformBuffer);
				
				
				
				for (int lodIndex = 0; lodIndex < bufferContainers.size(); lodIndex++)
				{
					profiler.popPush("binding");
					
					LodBufferContainer bufferContainer = bufferContainers.get(lodIndex);
					LodContainerUniformBufferWrapper uniformWrapper = (LodContainerUniformBufferWrapper)bufferContainer.uniforms;
					
					boolean columnBuilderDebugEnabled = Config.Client.Advanced.Debugging.ColumnBuilderDebugging.columnBuilderDebugEnable.get();
					if (columnBuilderDebugEnabled)
					{
						if (DhSectionPos.getDetailLevel(bufferContainer.pos) == Config.Client.Advanced.Debugging.ColumnBuilderDebugging.columnBuilderDebugDetailLevel.get()
							&& DhSectionPos.getX(bufferContainer.pos) == Config.Client.Advanced.Debugging.ColumnBuilderDebugging.columnBuilderDebugXPos.get()
							&& DhSectionPos.getZ(bufferContainer.pos) == Config.Client.Advanced.Debugging.ColumnBuilderDebugging.columnBuilderDebugZPos.get())
						{
							int breakpoint = 0;
						}
						else
						{
							continue;
						}
					}
					
					renderPass.setUniform("vertUniqueUniformBlock", uniformWrapper.gpuBuffer);
					
					
					
					profiler.popPush("rendering");
					
					// render each buffer
					IVertexBufferWrapper[] bufferWrapperList = opaquePass ? bufferContainer.vbos : bufferContainer.vbosTransparent;
					for (int i = 0; i < bufferWrapperList.length; i++)
					{
						VertexBufferWrapper bufferWrapper = (VertexBufferWrapper) bufferWrapperList[i];
						if (!bufferWrapper.uploaded
							|| bufferWrapper.vertexCount == 0)
						{
							continue;
						}
						
						//ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeBufferRenderEvent.class, new DhApiBeforeBufferRenderEvent.EventParam(renderEventParam, modelPos));
						
						renderPass.setVertexBuffer(0, bufferWrapper.vboGpuBuffer); // vertex buffer can only be "0" lol
						
						if (!bufferWrapper.vboGpuBuffer.isClosed())
						{
							renderPass.drawIndexed(
								/*indexStart*/ 0,
								/*firstIndex*/0,
								/*indexCount*/bufferWrapper.indexCount,
								/*instanceCount*/1);
						}
					}
				}
				
			}
		}
		
		profiler.pop();
	}
	private String getName() { return "distantHorizons:McLodRenderer"; }
	
	@Override
	public void applyToMcTexture() 
	{
		GpuTexture mcColorTexture = Minecraft.getInstance().getMainRenderTarget().getColorTexture();
		this.applyRenderer.render(this.dhColorTextureWrapper.texture, this.dhDepthTextureWrapper.texture, mcColorTexture);
	}
	
	@Override
	public void clearDepth() { this.dhDepthTextureWrapper.clearDepth(1.0f); }
	@Override
	public void clearColor() { this.dhColorTextureWrapper.clearColor(ColorUtil.argbToInt(1, 1, 1, 1)); }
	
	//endregion
	
	
	
}
