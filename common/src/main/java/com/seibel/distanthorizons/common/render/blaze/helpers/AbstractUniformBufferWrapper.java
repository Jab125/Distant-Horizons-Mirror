package com.seibel.distanthorizons.common.render.blaze.helpers;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.IUniformBufferWrapper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public abstract class AbstractUniformBufferWrapper implements IUniformBufferWrapper
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	
	private final String name;
	
	private ByteBuffer buffer = null;
	public GpuBuffer gpuBuffer = null;
	
	
	
	//========//
	// render //
	//========//
	//region
	
	public AbstractUniformBufferWrapper() { this.name = this.getClass().getSimpleName(); }
	public AbstractUniformBufferWrapper(String name) { this.name = name; }
	
	protected ByteBuffer getOrCreateBuffer(int size)
	{
		GpuDevice gpuDevice = RenderSystem.getDevice();
		
		if (this.buffer == null 
			|| this.buffer.capacity() != size)
		{
			this.buffer = ByteBuffer.allocateDirect(size);
			this.buffer.order(ByteOrder.nativeOrder());
			
			// GpuBuffer.USAGE_UNIFORM = 128
			// GpuBuffer.USAGE_INDEX = 64
			int usage = 8 | 32 | 128; // is this just using OpenGL VBO flags?, if so I can't find it, supposedly GlDevice on Mojang's side
			int byteSize = (this.buffer.limit() - this.buffer.position());
			this.gpuBuffer = gpuDevice.createBuffer(this::getName, usage, byteSize);
		}
		
		return this.buffer;
	}
	
	@Override
	public void upload() throws IllegalStateException
	{
		if (this.buffer == null)
		{
			throw new IllegalStateException("Upload called before buffer was created");
		}
		
		
		
		GpuDevice gpuDevice = RenderSystem.getDevice();
		CommandEncoder commandEncoder = gpuDevice.createCommandEncoder();
		
		int byteSize = (this.buffer.limit() - this.buffer.position());
		GpuBufferSlice bufferSlice = new GpuBufferSlice(this.gpuBuffer, /*offset*/0, byteSize);
		if (!bufferSlice.buffer().isClosed())
		{
			commandEncoder.writeToBuffer(bufferSlice, this.buffer);
		}
		else
		{
			LOGGER.warn("Uploading to buffer ["+this.name+"] failed due to already being closed");
		}
	}
	private String getName() { return this.name; }
	
	@Override
	public void close()
	{
		if (this.gpuBuffer != null)
		{
			this.gpuBuffer.close();
		}
	}
	
	
	
	//endregion
	
}
