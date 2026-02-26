package com.seibel.distanthorizons.common.renderTest;


import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
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
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3f;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.render.glObject.GLEnums;
import com.seibel.distanthorizons.core.render.glObject.buffer.QuadElementBuffer;
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
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Consumer;
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
	
	
	
	//=============//
	// constructor //
	//=============//
	//region
	
	private McLodRenderer()
	{
		
	}
	
	private void tryInit()
	{
		if (this.init)
		{
			return;
		}
		this.init = true; // todo only set when succeeded (in case of exception)
		
		
		
		//GLMC.glBlendFunc(GL32.GL_SRC_ALPHA, GL32.GL_ONE_MINUS_SRC_ALPHA);
		//GLMC.glBlendFuncSeparate(GL32.GL_SRC_ALPHA, GL32.GL_ONE_MINUS_SRC_ALPHA, GL32.GL_ONE, GL32.GL_ZERO);
		
		
		
		GpuDevice gpuDevice = RenderSystem.getDevice();
		CommandEncoder commandEncoder = gpuDevice.createCommandEncoder();
		
		
		this.vertexFormat = VertexFormat.builder()
			.add("vPosition", DhVertexFormat.SHORT_POS) //.add(ELEMENT_POSITION)
			.add("paddingOne", DhVertexFormat.BYTE_PAD) //.add(ELEMENT_BYTE_PADDING)
			.add("light", DhVertexFormat.LIGHT)//.add(ELEMENT_LIGHT)
			.add("vColor", DhVertexFormat.RGBA_UBYTE_COLOR)//.add(ELEMENT_COLOR)
			.add("irisMaterial", DhVertexFormat.IRIS_MATERIAL)//.add(ELEMENT_IRIS_MATERIAL_INDEX)
			.add("irisNormal", DhVertexFormat.IRIS_NORMAL)//.add(ELEMENT_IRIS_NORMAL_INDEX)
			.add("paddingTwo", DhVertexFormat.BYTE_PAD)//.add(ELEMENT_BYTE_PADDING)
			.add("paddingThree", DhVertexFormat.BYTE_PAD)//.add(ELEMENT_BYTE_PADDING) // padding is to make sure the format is a multiple of 4
			.build();
		
		
		
		
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
			
			//pipelineBuilder.withSampler("uLightMap"); // TODO
			
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
	
	/**
	 * There's an Std180Builder and Std180SizeCalculator, if you missed them. Ensure the ubo in the shader is marked std180.
	 * These help keep alignment rules in check 
	 */
	private static ByteBuffer setUniform(ByteBuffer oldBuffer, Mat4f matrix)
	{ 
		return setUniform(oldBuffer, "Mat4f", (buffer) ->
			{
				FloatBuffer floatBuffer = buffer.asFloatBuffer();
				matrix.store(floatBuffer);
			}); 
	}
	private static ByteBuffer setUniform(ByteBuffer oldBuffer, DhApiVec3f vector)
	{ 
		return setUniform(oldBuffer, "Vec3f", (buffer) ->
			{
				FloatBuffer floatBuffer = buffer.asFloatBuffer();
				floatBuffer
					.put(vector.x)
					.put(vector.y)
					.put(vector.z)
					.put(0f);
			}); 
	}
	private static ByteBuffer setUniform(ByteBuffer oldBuffer, boolean value)
	{ 
		return setUniform(oldBuffer, "boolean", (buffer) ->
			{
				IntBuffer intBuffer = buffer.asIntBuffer();
				intBuffer.put(value ? 1 : 0);
			}); 
	}
	private static ByteBuffer setUniform(ByteBuffer oldBuffer, float value)
	{ 
		return setUniform(oldBuffer, "float", (buffer) ->
			{
				FloatBuffer floatBuffer = buffer.asFloatBuffer();
				floatBuffer.put(value);
			}); 
	}
	private static ByteBuffer setUniform(ByteBuffer oldBuffer, int value)
	{
		return setUniform(oldBuffer, "int", (buffer) ->
		{
			IntBuffer intBuffer = buffer.asIntBuffer();
			intBuffer.put(value);
		});
	}
	private static ByteBuffer setUniform(ByteBuffer oldBuffer, String uploadType, Consumer<ByteBuffer> modifyFunc)
	{
		int lengthInBytes;
		switch (uploadType)
		{
			case "Mat4f":
				lengthInBytes = 4 * 4 * Float.BYTES;
				break;
			case "float":
				lengthInBytes = Float.BYTES;
				break;
			case "Vec3f":
				lengthInBytes = 4 * Float.BYTES;
				break;
			case "int":
			case "boolean":
				lengthInBytes = Integer.BYTES;
				break;
				
			default:
				throw new UnsupportedOperationException("No known type ["+uploadType+"]");
		}
		
		
		// buffer to upload data
		ByteBuffer newBuffer = ByteBuffer.allocateDirect(oldBuffer.capacity() + lengthInBytes);
		newBuffer.order(ByteOrder.nativeOrder());
		newBuffer.put(oldBuffer);
		modifyFunc.accept(newBuffer);
		newBuffer.rewind();
		
		return newBuffer;
	}
	
	private static GpuBuffer finalizeUniformBuffer(String uniformName, ByteBuffer buffer, GpuBuffer vboGpuBuffer)
	{
		GpuDevice gpuDevice = RenderSystem.getDevice();
		CommandEncoder commandEncoder = gpuDevice.createCommandEncoder();
		
		
		// create VBO if needed
		if (vboGpuBuffer == null 
			|| vboGpuBuffer.size() < buffer.capacity())
		{
			// GpuBuffer.USAGE_UNIFORM = 128
			int usage = 8 | 32 | 128; // is this just using OpenGL VBO flags?, if so I can't find it, supposedly GlDevice on Mojang's side
			vboGpuBuffer = gpuDevice.createBuffer(() -> uniformName, usage, buffer.capacity());
		}
		
		int offset = 0;
		GpuBufferSlice bufferSlice = new GpuBufferSlice(vboGpuBuffer, offset, buffer.capacity());
		commandEncoder.writeToBuffer(bufferSlice, buffer);
		
		return vboGpuBuffer;
	}
	
	private GpuBuffer indexBuffer;
	
	private GpuBuffer fragUniformBuffer;
	private GpuBuffer vertUniformBuffer;
	
	private GpuTexture depthTexture;
	
	//endregion
	
	@Override
	public int getVertexSize()
	{
		VertexFormat format = VertexFormat.builder()
			.add("vPosition", DhVertexFormat.SHORT_POS) //.add(ELEMENT_POSITION)
			.add("paddingOne", DhVertexFormat.BYTE_PAD) //.add(ELEMENT_BYTE_PADDING)
			.add("light", DhVertexFormat.LIGHT)//.add(ELEMENT_LIGHT)
			.add("vColor", DhVertexFormat.RGBA_UBYTE_COLOR)//.add(ELEMENT_COLOR)
			.add("irisMaterial", DhVertexFormat.IRIS_MATERIAL)//.add(ELEMENT_IRIS_MATERIAL_INDEX)
			.add("irisNormal", DhVertexFormat.IRIS_NORMAL)//.add(ELEMENT_IRIS_NORMAL_INDEX)
			.add("paddingTwo", DhVertexFormat.BYTE_PAD)//.add(ELEMENT_BYTE_PADDING)
			.add("paddingThree", DhVertexFormat.BYTE_PAD)//.add(ELEMENT_BYTE_PADDING) // padding is to make sure the format is a multiple of 4
			.build();
		
		return format.getVertexSize();
	}
	
	
	
	//========//
	// render //
	//========//
	//region
	
	//@Override 
	//public void fillUniformData()
	//{
	//	
	//}
	//@Override 
	//public void setModelOffsetPos()
	//{
	//	
	//}
	
	@Override
	public void render(
		DhApiRenderParam renderEventParam, 
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
			ByteBuffer buffer = ByteBuffer.allocateDirect(0);
			buffer.order(ByteOrder.nativeOrder());

			buffer = setUniform(buffer, false); // "uIsWhiteWorld"
			
			buffer = setUniform(buffer, false); // spacer 1
			buffer = setUniform(buffer, false); // spacer 2
			buffer = setUniform(buffer, false); // spacer 3
			
			Mat4f combinedMatrix = new Mat4f(renderEventParam.dhProjectionMatrix);
			combinedMatrix.multiply(renderEventParam.dhModelViewMatrix);
			buffer = setUniform(buffer, combinedMatrix); // uCombinedMatrix
			
			buffer = setUniform(buffer, modelOffset); // uModelOffset
			buffer = setUniform(buffer, renderEventParam.worldYOffset); // uWorldYOffset
			buffer = setUniform(buffer,  0.01f); // uMircoOffset // 0.01 block offset

			float curveRatio = Config.Client.Advanced.Graphics.Experimental.earthCurveRatio.get();
			if (curveRatio < -1.0f || curveRatio > 1.0f)
			{
				curveRatio = /*6371KM*/ 6371000.0f / curveRatio;
			}
			else
			{
				// disable curvature if the config value is between -1 and 1
				curveRatio = 0.0f;
			}
			buffer = setUniform(buffer, curveRatio); // uEarthRadius

			this.vertUniformBuffer = finalizeUniformBuffer("vertUniformBlock", buffer, this.vertUniformBuffer);
		}
		
		
		profiler.popPush("set frag uniforms");
		{
			ByteBuffer buffer = ByteBuffer.allocateDirect(0);
			buffer.order(ByteOrder.nativeOrder());


			// Clip Uniform
			float dhNearClipDistance = RenderUtil.getNearClipPlaneInBlocks();
			if (!Config.Client.Advanced.Debugging.lodOnlyMode.get())
			{
				// this added value prevents the near clip plane and discard circle from touching, which looks bad
				dhNearClipDistance += 16f;
			}
			buffer = setUniform(buffer, dhNearClipDistance); // "uClipDistance"

			// Noise Uniforms
			buffer = setUniform(buffer, Config.Client.Advanced.Graphics.NoiseTexture.enableNoiseTexture.get()); // "uNoiseEnabled"
			buffer = setUniform(buffer, Config.Client.Advanced.Graphics.NoiseTexture.noiseSteps.get()); // "uNoiseSteps"
			buffer = setUniform(buffer, Config.Client.Advanced.Graphics.NoiseTexture.noiseIntensity.get()); // "uNoiseIntensity"
			buffer = setUniform(buffer, Config.Client.Advanced.Graphics.NoiseTexture.noiseDropoff.get()); // "uNoiseDropoff"
			buffer = setUniform(buffer, Config.Client.Advanced.Graphics.Quality.ditherDhFade.get()); // "uDitherDhRendering"

			this.fragUniformBuffer = finalizeUniformBuffer("fragUniformBlock", buffer, this.fragUniformBuffer);
		}
		
		profiler.popPush("create index buffer");
		{
			if (this.indexBuffer == null)
			{
				ByteBuffer buffer = MemoryUtil.memAlloc(LodBufferContainer.MAX_QUADS_PER_BUFFER * GLEnums.getTypeSize(GL32.GL_UNSIGNED_INT) * 6);
				QuadElementBuffer.buildBuffer(LodBufferContainer.MAX_QUADS_PER_BUFFER, buffer, GL32.GL_UNSIGNED_INT);
				
				
				// create VBO if needed
				if (indexBuffer == null
					|| indexBuffer.size() < buffer.capacity())
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
		
		// depth text
		if (this.depthTexture == null
			|| this.depthTexture.getWidth(0) != MC_RENDER.getTargetFramebufferViewportWidth()
			|| this.depthTexture.getHeight(0) != MC_RENDER.getTargetFramebufferViewportHeight())
		{
			if (this.depthTexture != null)
			{
				this.depthTexture.close();
			}
			
			int usage = 8 | 32 | 128;
			this.depthTexture = gpuDevice.createTexture("DhDepthTexture",
				usage,
				TextureFormat.DEPTH32,
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
			GpuTextureView colorTextureView = gpuDevice.createTextureView(Minecraft.getInstance().getMainRenderTarget().getColorTexture());
			GpuTextureView depthTextureView = gpuDevice.createTextureView(this.depthTexture);
			RenderPass renderPass = commandEncoder.createRenderPass(
			debugLabelSupplier,
			colorTextureView,
			optionalClearColorAsInt,
			depthTextureView, optionalDepthValueAsDouble))
		{
			//renderPass.pushDebugGroup();
			//renderPass.popDebugGroup();
			
			
			//renderPass.bindTexture("uLightMap", );
			
			
			
			renderPass.setUniform("vertUniformBlock", this.vertUniformBuffer);
			renderPass.setUniform("fragUniformBlock", this.fragUniformBuffer);
			
			//pipelineBuilder.withSampler("uLightMap");
			
			
			//boolean renderWireframe = Config.Client.Advanced.Debugging.renderWireframe.get();
			//if (renderWireframe)
			//{
			//	GL32.glPolygonMode(GL32.GL_FRONT_AND_BACK, GL32.GL_LINE);
			//	GLMC.disableFaceCulling();
			//}
			//else
			//{
			//	GL32.glPolygonMode(GL32.GL_FRONT_AND_BACK, GL32.GL_FILL);
			//	GLMC.enableFaceCulling();
			//}
			//
			//if (!opaquePass)
			//{
			//	GLMC.enableBlend();
			//	GLMC.enableDepthTest();
			//	GL32.glBlendEquation(GL32.GL_FUNC_ADD);
			//	GLMC.glBlendFuncSeparate(GL32.GL_SRC_ALPHA, GL32.GL_ONE_MINUS_SRC_ALPHA, GL32.GL_ONE, GL32.GL_ONE_MINUS_SRC_ALPHA);
			//}
			//else
			//{
			//	GLMC.disableBlend();
			//}
			
			
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
						/*indexCount*/
						(int)(bufferWrapper.vertexCount * 1.5), // convert vertex count to index count // TODO fix me!
						//LodBufferContainer.MAX_QUADS_PER_BUFFER, 
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
	
	public void clearDepth()
	{
		GpuDevice gpuDevice = RenderSystem.getDevice();
		CommandEncoder commandEncoder = gpuDevice.createCommandEncoder();

		if (this.depthTexture != null)
		{
			commandEncoder.clearDepthTexture(this.depthTexture, 1.0f);
		}
	}
	
	//endregion
	
	
	
}
