package com.seibel.distanthorizons.common.render.blaze.wrappers.buffer;

#if MC_VER <= MC_1_21_10
public class BlazeVertexBufferWrapper {}

#else

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.seibel.distanthorizons.common.render.openGl.glObject.buffer.GlQuadIndexBuffer;
import com.seibel.distanthorizons.common.render.openGl.glObject.enums.GLEnums;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodQuadBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.objects.IVertexBufferWrapper;
import org.lwjgl.opengl.GL32;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class BlazeVertexBufferWrapper implements IVertexBufferWrapper
{
	private static final GpuDevice GPU_DEVICE = RenderSystem.getDevice();
	private static final CommandEncoder COMMAND_ENCODER = GPU_DEVICE.createCommandEncoder();
	
	
	public final String name;
	public String getName() { return this.name; }
	
	public GpuBuffer vboGpuBuffer = null;
	public GpuBuffer indexBuffer = null;
	
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
	public void upload(ByteBuffer vertexBuffer, int vertexCount)
	{
		int oldVertexCount = this.vertexCount;
		
		this.vertexCount = vertexCount;
		// 4 vertices per face, but 6 indices (IE 2 triangles) per face, aka need to multiply by 1.5
		this.indexCount = (int)(vertexCount * 1.5);
		this.uploaded = true;
		
		
		if (this.vboGpuBuffer == null
			// recreating if the size changes is always necessary (even if we only need a smaller amount)
			// due to a bug on Mac where it will attempt to render anything allocated in the buffer
			|| oldVertexCount != vertexCount)
		{
			if (this.vboGpuBuffer != null)
			{
				this.vboGpuBuffer.close();
			}
			
			int usage = GpuBuffer.USAGE_COPY_DST
				| GpuBuffer.USAGE_VERTEX;
			int byteSize = (vertexBuffer.limit() - vertexBuffer.position());
			this.vboGpuBuffer = GPU_DEVICE.createBuffer(this::getName, usage, byteSize);
			
			GpuBufferSlice bufferSlice = new GpuBufferSlice(this.vboGpuBuffer, /*offset*/0, byteSize);
			COMMAND_ENCODER.writeToBuffer(bufferSlice, vertexBuffer);
		}
		
		
		if (this.indexBuffer == null
			// recreating if the size changes is always necessary (even if we only need a smaller amount)
			// due to a bug on Mac where it will attempt to render anything allocated in the buffer
			|| oldVertexCount != vertexCount)
		{
			if (this.indexBuffer != null)
			{
				this.indexBuffer.close();
			}
			
			int quadCount = (this.vertexCount / 4);
			ByteBuffer indexBuffer = MemoryUtil.memAlloc(quadCount * 6 * GLEnums.getTypeSize(GL32.GL_UNSIGNED_INT));
			indexBuffer.order(ByteOrder.nativeOrder());
			GlQuadIndexBuffer.buildBuffer(quadCount, indexBuffer, GL32.GL_UNSIGNED_INT);
			
			int usage = GpuBuffer.USAGE_COPY_DST
				| GpuBuffer.USAGE_INDEX;
			this.indexBuffer = GPU_DEVICE.createBuffer(this::getIndexBufferName, usage, indexBuffer.capacity());
			
			GpuBufferSlice bufferSlice = new GpuBufferSlice(this.indexBuffer, /*offset*/ 0, indexBuffer.capacity());
			COMMAND_ENCODER.writeToBuffer(bufferSlice, indexBuffer);
		}
	}
	private String getIndexBufferName() { return "distantHorizons:LodIndexBuffer"; }
	
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
#endif