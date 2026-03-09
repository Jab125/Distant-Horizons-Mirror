package com.seibel.distanthorizons.common.render.blaze.wrappers.uniform;

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
	
	private static final GpuDevice GPU_DEVICE = RenderSystem.getDevice();
	private static final CommandEncoder COMMAND_ENCODER = GPU_DEVICE.createCommandEncoder();
	
	
	private final String name;
	
	private ByteBuffer buffer = null;
	public GpuBuffer gpuBuffer = null;
	
	
	
	//=============//
	// constructor //
	//=============//
	//region
	
	public AbstractUniformBufferWrapper() { this.name = this.getClass().getSimpleName(); }
	
	//endregion
	
	
	
	//========//
	// render //
	//========//
	//region
	
	protected ByteBuffer getOrCreateBuffer(int size)
	{
		if (this.buffer == null 
			|| this.buffer.capacity() != size)
		{
			this.buffer = ByteBuffer.allocateDirect(size);
			this.buffer.order(ByteOrder.nativeOrder());
			
			int usage = GpuBuffer.USAGE_COPY_DST 
				| GpuBuffer.USAGE_VERTEX 
				| GpuBuffer.USAGE_UNIFORM;
			int byteSize = (this.buffer.limit() - this.buffer.position());
			this.gpuBuffer = GPU_DEVICE.createBuffer(this::getBufferName, usage, byteSize);
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
		
		
		
		int byteSize = (this.buffer.limit() - this.buffer.position());
		GpuBufferSlice bufferSlice = new GpuBufferSlice(this.gpuBuffer, /*offset*/0, byteSize);
		if (!bufferSlice.buffer().isClosed())
		{
			COMMAND_ENCODER.writeToBuffer(bufferSlice, this.buffer);
		}
		else
		{
			LOGGER.warn("Uploading to buffer ["+this.name+"] failed due to already being closed");
		}
	}
	private String getBufferName() { return this.name; }
	
	//endregion
	
	
	
	//================//
	// base overrides //
	//================//
	//region
	
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
