package com.seibel.distanthorizons.common.renderTest.helpers;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.IVertexBufferWrapper;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

public class VertexBufferWrapper implements IVertexBufferWrapper
{
	
	public GpuBuffer vboGpuBuffer = null;
	public int vertexCount = -1;
	public int indexCount = -1;
	public boolean uploaded = false;
	
	
	@Override
	public int getVertexCount() { return this.vertexCount ;}
	
	
	
	//========//
	// render //
	//========//
	//region
	
	@Override
	public void upload(ByteBuffer buffer, int vertexCount)
	{
		this.vertexCount = vertexCount;
		this.indexCount = (int)(vertexCount * 1.5); // TODO why multiply by 1.5?
		this.uploaded = true;
		
		
		GpuDevice gpuDevice = RenderSystem.getDevice();
		CommandEncoder commandEncoder = gpuDevice.createCommandEncoder();
		
		Supplier<String> labelSupplier = () -> "distantHorizons:McLodRenderer";
		int usage = 8 | 32; // is this just using OpenGL VBO flags?, if so I can't find it, supposedly GlDevice on Mojang's side
		int byteSize = (buffer.limit() - buffer.position());
		this.vboGpuBuffer = gpuDevice.createBuffer(labelSupplier, usage, byteSize);
		
		{
			GpuBufferSlice bufferSlice = new GpuBufferSlice(this.vboGpuBuffer, /*offset*/0, byteSize);
			commandEncoder.writeToBuffer(bufferSlice, buffer);
		}
	}
	
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
