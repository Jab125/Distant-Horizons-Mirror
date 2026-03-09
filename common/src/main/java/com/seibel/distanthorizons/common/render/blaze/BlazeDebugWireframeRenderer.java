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

package com.seibel.distanthorizons.common.render.blaze;

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
import com.mojang.blaze3d.vertex.VertexFormat;
import com.seibel.distanthorizons.common.render.blaze.helpers.UniformHandler;
import com.seibel.distanthorizons.common.render.blaze.util.DhBlazeVertexFormatUtil;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.render.renderer.DebugRenderer;
import com.seibel.distanthorizons.core.render.renderer.RenderParams;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.core.util.math.Vec3d;
import com.seibel.distanthorizons.core.util.math.Vec3f;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.IMcDebugRenderer;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * TODO
 */
public class BlazeDebugWireframeRenderer implements IMcDebugRenderer
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	
	private static final GpuDevice GPU_DEVICE = RenderSystem.getDevice();
	private static final CommandEncoder COMMAND_ENCODER = GPU_DEVICE.createCommandEncoder();
	
	public static BlazeDebugWireframeRenderer INSTANCE = new BlazeDebugWireframeRenderer();
	
	
	
	/** A box from 0,0,0 to 1,1,1 */
	private static final float[] BOX_VERTICES = {
		//region
		// Pos x y z
		0, 0, 0,
		1, 0, 0,
		1, 1, 0,
		0, 1, 0,
		0, 0, 1,
		1, 0, 1,
		1, 1, 1,
		0, 1, 1,
		//endregion
	};
	
	private static final int[] BOX_OUTLINE_INDICES = {
		//region
		0, 1,
		1, 2,
		2, 3,
		3, 0,
		
		4, 5,
		5, 6,
		6, 7,
		7, 4,
		
		0, 4,
		1, 5,
		2, 6,
		3, 7,
		//endregion
	};
	
	
	
	
	// rendering setup
	private boolean init = false;
	
	private VertexFormat vertexFormat;
	
	private RenderPipeline pipeline;
	
	private GpuBuffer boxVertexBuffer;
	private GpuBuffer boxIndexBuffer;
	
	private GpuBuffer uniformBuffer;
	
	
	
	//=============//
	// constructor //
	//=============//
	//region
	
	public BlazeDebugWireframeRenderer() { }
	
	public void init()
	{
		if (this.init)
		{
			return;
		}
		this.init = true;
		
		this.vertexFormat = VertexFormat.builder()
			.add("vPosition", DhBlazeVertexFormatUtil.FLOAT_XYZ_POS)
			.build();
		
		this.createPipelines();
		this.createBuffers();
		
	}
	private void createPipelines()
	{
		RenderPipeline.Builder pipelineBuilder = RenderPipeline.builder();
		{
			pipelineBuilder.withCull(false);
			pipelineBuilder.withDepthWrite(false);
			pipelineBuilder.withDepthTestFunction(DepthTestFunction.LESS_DEPTH_TEST);
			pipelineBuilder.withColorWrite(true);
			pipelineBuilder.withoutBlend();
			pipelineBuilder.withPolygonMode(PolygonMode.WIREFRAME);
			pipelineBuilder.withLocation(Identifier.parse("distanthorizons:debug_wireframe_renderer"));
			
			pipelineBuilder.withVertexShader(Identifier.fromNamespaceAndPath("distanthorizons", "debug/blaze/vert"));
			pipelineBuilder.withFragmentShader(Identifier.fromNamespaceAndPath("distanthorizons", "debug/blaze/frag"));
			
			pipelineBuilder.withUniform("uniformBlock", UniformType.UNIFORM_BUFFER);
			
			pipelineBuilder.withVertexFormat(this.vertexFormat, VertexFormat.Mode.DEBUG_LINES);
		}
		this.pipeline = pipelineBuilder.build();
		
	}
	private void createBuffers()
	{
		// box vertices 
		ByteBuffer boxVerticesBuffer = MemoryUtil.memAlloc(BOX_VERTICES.length * Float.BYTES);
		boxVerticesBuffer.asFloatBuffer().put(BOX_VERTICES);
		boxVerticesBuffer.rewind();
		MemoryUtil.memFree(boxVerticesBuffer);
		
		// upload vertex data
		{
			int usage = GpuBuffer.USAGE_COPY_DST 
				| GpuBuffer.USAGE_VERTEX;
			int size = BOX_VERTICES.length * Float.BYTES;
			this.boxVertexBuffer = GPU_DEVICE.createBuffer(() -> "distantHorizons:McDebugWireframeBox", usage, size);
			
			{
				int length = BOX_VERTICES.length * Float.BYTES;
				GpuBufferSlice bufferSlice = new GpuBufferSlice(this.boxVertexBuffer, /*offset*/ 0, length);
				
				ByteBuffer byteBuffer = ByteBuffer.allocateDirect(BOX_VERTICES.length * Float.BYTES);
				byteBuffer.order(ByteOrder.nativeOrder());
				byteBuffer.asFloatBuffer().put(BOX_VERTICES);
				byteBuffer.rewind();
				
				COMMAND_ENCODER.writeToBuffer(bufferSlice, byteBuffer);
			}
		}
		
		// box vertex indexes
		{
			ByteBuffer buffer = ByteBuffer.allocateDirect(BOX_OUTLINE_INDICES.length * Integer.BYTES);
			buffer.order(ByteOrder.nativeOrder());
			buffer.asIntBuffer().put(BOX_OUTLINE_INDICES);
			buffer.rewind();
			
			
			int usage = GpuBuffer.USAGE_COPY_DST 
				| GpuBuffer.USAGE_VERTEX 
				| GpuBuffer.USAGE_INDEX
				| GpuBuffer.USAGE_UNIFORM;
			this.boxIndexBuffer = GPU_DEVICE.createBuffer(() -> "DH Debug Index Buffer", usage, buffer.capacity());
			
			int offset = 0;
			GpuBufferSlice bufferSlice = new GpuBufferSlice(this.boxIndexBuffer, offset, buffer.capacity());
			COMMAND_ENCODER.writeToBuffer(bufferSlice, buffer);
		}
	}
	
	//endregion
	
	
	
	//===========//
	// rendering //
	//===========//
	//region
	
	private Mat4f combinedMatrixThisFrame = new Mat4f();
	
	@Override
	public void render(RenderParams renderParams, Collection<DebugRenderer.BoxParticle> boxCollection)
	{
		
		this.combinedMatrixThisFrame = new Mat4f(renderParams.dhProjectionMatrix);
		this.combinedMatrixThisFrame.multiply(renderParams.dhModelViewMatrix);
		
		for (DebugRenderer.BoxParticle box : boxCollection)
		{
			this.render(box.createNewRenderBox());
		}
	}
	
	@Override
	public void render(DebugRenderer.Box box)
	{
		this.init();
		
		if (McLodRenderer.INSTANCE.dhColorTextureWrapper.isEmpty()
			|| McLodRenderer.INSTANCE.dhDepthTextureWrapper.isEmpty())
		{
			return;
		}
		
		// shouldn't happen, but just in case
		if (box == null)
		{
			return;
		}
		
		
		
		// uniforms
		{
			int uniformBufferSize = new Std140SizeCalculator()
				.putMat4f() // uTransform
				.putVec4() // uColor
				.get();
			
			
			// create data //
			
			Vec3d camPos = MC_RENDER.getCameraExactPosition();
			Vec3f camPosFloatThisFrame = new Vec3f((float) camPos.x, (float) camPos.y, (float) camPos.z);
			
			Mat4f boxTransform = Mat4f.createTranslateMatrix(
				box.minPos.x - camPosFloatThisFrame.x,
				box.minPos.y - camPosFloatThisFrame.y,
				box.minPos.z - camPosFloatThisFrame.z);
			boxTransform.multiply(Mat4f.createScaleMatrix(
				box.maxPos.x - box.minPos.x,
				box.maxPos.y - box.minPos.y,
				box.maxPos.z - box.minPos.z));
			
			Mat4f transformMatrix = this.combinedMatrixThisFrame.copy();
			transformMatrix.multiply(boxTransform);
			
			
			// upload data //
			
			ByteBuffer buffer = ByteBuffer.allocateDirect(uniformBufferSize);
			buffer.order(ByteOrder.nativeOrder());
			buffer = Std140Builder.intoBuffer(buffer)
				.putMat4f(transformMatrix.createJomlMatrix()) // uTransform
				.putVec4(
					box.color.getRed() / 255.0f,
					box.color.getGreen() / 255.0f,
					box.color.getBlue() / 255.0f,
					box.color.getAlpha() / 255.0f) // uColor
				.get()
			;
			
			this.uniformBuffer = UniformHandler.createBuffer("uniformBlock", uniformBufferSize, this.uniformBuffer);
			GpuBufferSlice bufferSlice = new GpuBufferSlice(this.uniformBuffer, 0, uniformBufferSize);
			
			COMMAND_ENCODER.writeToBuffer(bufferSlice, buffer);
		}
		
		
		
		// render //
		
		try (RenderPass renderPass = COMMAND_ENCODER.createRenderPass(
			this::getRenderPassName,
			McLodRenderer.INSTANCE.dhColorTextureWrapper.textureView, 
			/*optionalClearColorAsInt*/ OptionalInt.empty(),
			McLodRenderer.INSTANCE.dhDepthTextureWrapper.textureView, 
			/*optionalDepthValueAsDouble*/ OptionalDouble.empty()))
		{
			// Bind instance data //
			renderPass.setUniform("uniformBlock", this.uniformBuffer);
			
			renderPass.setPipeline(this.pipeline);
			renderPass.setIndexBuffer(this.boxIndexBuffer, VertexFormat.IndexType.INT);
			
			renderPass.setVertexBuffer(0, this.boxVertexBuffer);
			
			renderPass.drawIndexed(
				/*indexStart*/ 0,
				/*firstIndex*/0,
				/*indexCount*/BOX_OUTLINE_INDICES.length,
				/*instanceCount*/1);
		}
	}
	private String getRenderPassName() { return "distantHorizons:McDebugRenderer"; }
	
	//endregion
	
	
	
}
