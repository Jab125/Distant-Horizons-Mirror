package com.seibel.distanthorizons.common.render.blaze.wrappers.buffer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.IVertexBufferWrapper;

import java.nio.ByteBuffer;

public class BlazeVertexBufferWrapper implements IVertexBufferWrapper
{
	private static final GpuDevice GPU_DEVICE = RenderSystem.getDevice();
	private static final CommandEncoder COMMAND_ENCODER = GPU_DEVICE.createCommandEncoder();
	
	
	public final String name;
	public String getName() { return this.name; }
	
	public GpuBuffer vboGpuBuffer = null;
	public int vertexCount = -1;
	public int indexCount = -1;
	public boolean uploaded = false;
	
	
	
	//=============//
	// constructor //
	//=============//
	//region
	
	public BlazeVertexBufferWrapper(String name) { this.name = name; }
	
	//endregion
	
	
	
	//========//
	// upload //
	//========//
	//region
	
	@Override
	public void upload(ByteBuffer buffer, int vertexCount)
	{
		this.vertexCount = vertexCount;
		this.indexCount = (int)(vertexCount * 1.5); // TODO why multiply by 1.5?
		this.uploaded = true;
		
		
		int usage = GpuBuffer.USAGE_COPY_DST
			| GpuBuffer.USAGE_VERTEX;
		int byteSize = (buffer.limit() - buffer.position());
		this.vboGpuBuffer = GPU_DEVICE.createBuffer(this::getName, usage, byteSize);
		
		GpuBufferSlice bufferSlice = new GpuBufferSlice(this.vboGpuBuffer, /*offset*/0, byteSize);
		COMMAND_ENCODER.writeToBuffer(bufferSlice, buffer);
	}
	
	//endregion
	
	
	
	//================//
	// base overrides //
	//================//
	//region
	
	@Override
	public void close()
	{
		if (this.vboGpuBuffer != null)
		{
			this.vboGpuBuffer.close();
		}
	}
	
	//endregion
	
	
	
}
