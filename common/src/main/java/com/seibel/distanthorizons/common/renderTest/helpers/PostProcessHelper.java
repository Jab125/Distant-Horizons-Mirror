package com.seibel.distanthorizons.common.renderTest.helpers;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Supplier;

public class PostProcessHelper
{
	
	private static final GpuDevice GPU_DEVICE = RenderSystem.getDevice();
	private static final CommandEncoder COMMAND_ENCODER = GPU_DEVICE.createCommandEncoder();
	
	// vertices for a full-screen quad
	private static final float[] VERTICES = new float[]
		{
			// PosX,Y,
			-1f, -1f,
			1f, -1f,
			1f,  1f,
			-1f,  1f,
		};
	
	
	public static GpuBuffer createAndUploadScreenVertexData(String name)
	{
		
		Supplier<String> labelSupplier = () -> "distantHorizons:"+name;
		// TODO
		int usage = 8 | 32; // is this just using OpenGL VBO flags?, if so I can't find it, supposedly GlDevice on Mojang's side
		int size = VERTICES.length * Float.BYTES;
		GpuBuffer vboGpuBuffer = GPU_DEVICE.createBuffer(labelSupplier, usage, size);
		
		{
			int length = VERTICES.length * Float.BYTES;
			GpuBufferSlice bufferSlice = new GpuBufferSlice(vboGpuBuffer, /*offset*/ 0, length);
			
			ByteBuffer byteBuffer = ByteBuffer.allocateDirect(VERTICES.length * Float.BYTES);
			// Fill buffer with vertices.
			byteBuffer.order(ByteOrder.nativeOrder());
			byteBuffer.asFloatBuffer().put(VERTICES);
			byteBuffer.rewind();
			
			COMMAND_ENCODER.writeToBuffer(bufferSlice, byteBuffer);
		}
		
		return vboGpuBuffer;
	}
	
}
