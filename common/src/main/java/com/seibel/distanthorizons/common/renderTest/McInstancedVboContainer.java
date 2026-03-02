package com.seibel.distanthorizons.common.renderTest;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBox;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.render.glObject.GLEnums;
import com.seibel.distanthorizons.core.render.glObject.GLProxy;
import com.seibel.distanthorizons.core.render.glObject.buffer.QuadElementBuffer;
import com.seibel.distanthorizons.core.render.renderer.generic.IInstancedVboContainer;
import com.seibel.distanthorizons.core.render.renderer.generic.RenderableBoxGroup;
import com.seibel.distanthorizons.core.util.ColorUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import org.lwjgl.opengl.GL32;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.function.Supplier;

/**
 * For use by {@link RenderableBoxGroup} 
 * 
 * @see RenderableBoxGroup
 */
public class McInstancedVboContainer implements IInstancedVboContainer
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	
	/** A box from 0,0,0 to 1,1,1 */
	private static final float[] BOX_VERTICES = {
		//region
		// Pos x y z
		
		// min X, vertical face
		0, 0, 0,
		1, 0, 0,
		1, 1, 0,
		0, 1, 0,
		// max X, vertical face
		0, 1, 1,
		1, 1, 1,
		1, 0, 1,
		0, 0, 1,
		
		// min Z, vertical face
		0, 0, 1,
		0, 0, 0,
		0, 1, 0,
		0, 1, 1,
		// max Z, vertical face
		1, 0, 1,
		1, 1, 1,
		1, 1, 0,
		1, 0, 0,
		
		// min Y, horizontal face
		0, 0, 1,
		1, 0, 1,
		1, 0, 0,
		0, 0, 0,
		// max Y, horizontal face
		0, 1, 1,
		1, 1, 1,
		1, 1, 0,
		0, 1, 0,
		//endregion
	};
	
	private static final int[] BOX_INDICES = {
		//region
		// min X, vertical face
		2, 1, 0,
		0, 3, 2,
		// max X, vertical face
		6, 5, 4,
		4, 7, 6,
		
		// min Z, vertical face
		10, 9, 8,
		8, 11, 10,
		// max Z, vertical face
		14, 13, 12,
		12, 15, 14,
		
		// min Y, horizontal face
		18, 17, 16,
		16, 19, 18,
		// max Y, horizontal face
		20, 21, 22,
		22, 23, 20,
		//endregion
	};
	
	public static final int[][][] DIRECTION_VERTEX_IBO_QUAD = new int[][][]
		///region
		{
			// X,Z //
			{ // UP
				{1, 0}, // 0
				{1, 1}, // 1
				{0, 1}, // 2
				{0, 0}, // 3
			},
			{ // DOWN
				{0, 0}, // 0
				{0, 1}, // 1
				{1, 1}, // 2
				{1, 0}, // 3
			},
			
			// X,Y //
			{ // NORTH
				{0, 0}, // 0
				{0, 1}, // 1
				{1, 1}, // 2
				
				{1, 0}, // 3
			},
			{ // SOUTH
				{1, 0}, // 0
				{1, 1}, // 1
				{0, 1}, // 2
				
				{0, 0}, // 3
			},
			
			// Z,Y //
			{ // WEST
				{0, 0}, // 0
				{1, 0}, // 1
				{1, 1}, // 2
				
				{0, 1}, // 3
			},
			{ // EAST
				{0, 1}, // 0
				{1, 1}, // 1
				{1, 0}, // 2
				
				{0, 0}, // 3
			},
		};
	///endregion
	
	
	
	
	
	public GpuBuffer vboGpuBuffer;
	public GpuBuffer indexGpuBuffer;
	
	//public int[] chunkPosData = new int[0];
	//public float[] subChunkPosData = new float[0];
	////public float[] scalingData = new float[0];
	//public float[] colorData = new float[0];
	//public int[] materialData = new int[0];
	private ByteBuffer vertexBuffer = ByteBuffer.allocateDirect(0);
	private ByteBuffer indexBuffer = ByteBuffer.allocateDirect(0);
	
	public int uploadedBoxCount = 0;
	
	private EState state = EState.NEW;
	@Override
	public IInstancedVboContainer.EState getState() { return this.state; }
	@Override
	public void setState(IInstancedVboContainer.EState state) { this.state = state; }
	
	
	
	//===========================//
	// render building/uploading //
	//===========================//
	//region
	
	public void updateVertexData(List<DhApiRenderableBox> uploadBoxList)
	{
		int boxCount = uploadBoxList.size();
		if (boxCount == 0)
		{
			return; // TODO done just to fix a buffer empty crash
		}
		
		
		// recreate the data arrays if their size is different
		if (this.uploadedBoxCount != boxCount)
		{
			this.uploadedBoxCount = boxCount;
			
			int vertexBufferSize = this.vertexBufferSize();
			this.vertexBuffer = ByteBuffer.allocateDirect(vertexBufferSize);
			this.vertexBuffer.order(ByteOrder.LITTLE_ENDIAN);
			
			int indexBufferSize = this.indexBufferSize();
			this.indexBuffer = ByteBuffer.allocateDirect(indexBufferSize);
			this.indexBuffer.order(ByteOrder.LITTLE_ENDIAN);
		}
		this.vertexBuffer.position(0);
		this.indexBuffer.position(0);
		
		
		
		//QuadElementBuffer.buildBuffer(quadCount, this.indexBuffer, GL32.GL_UNSIGNED_INT);
		
		
		
		for (int boxIndex = 0; boxIndex < boxCount; boxIndex++)
		{
			// index
			int indexOffset = (boxIndex * 24 /*24 is the number of vertices in a box*/);
			for (int i = 0; i < BOX_INDICES.length; i++)
			{
				this.indexBuffer.putInt(BOX_INDICES[i] + indexOffset);
			}
			
			
			
			
			// vertex
			DhApiRenderableBox box = uploadBoxList.get(boxIndex);
			
			final double[] boxVertices = {
				//region
				// Pos x y z
				
				// min X, vertical face
				box.minPos.x, box.minPos.y, box.minPos.z,
				box.maxPos.x, box.minPos.y, box.minPos.z,
				box.maxPos.x, box.maxPos.y, box.minPos.z,
				box.minPos.x, box.maxPos.y, box.minPos.z,
				// max X, vertical face
				box.minPos.x, box.maxPos.y, box.maxPos.z,
				box.maxPos.x, box.maxPos.y, box.maxPos.z,
				box.maxPos.x, box.minPos.y, box.maxPos.z,
				box.minPos.x, box.minPos.y, box.maxPos.z,
				
				// min Z, vertical face
				box.minPos.x, box.minPos.y, box.maxPos.z,
				box.minPos.x, box.minPos.y, box.minPos.z,
				box.minPos.x, box.maxPos.y, box.minPos.z,
				box.minPos.x, box.maxPos.y, box.maxPos.z,
				// max Z, vertical face
				box.maxPos.x, box.minPos.y, box.maxPos.z,
				box.maxPos.x, box.maxPos.y, box.maxPos.z,
				box.maxPos.x, box.maxPos.y, box.minPos.z,
				box.maxPos.x, box.minPos.y, box.minPos.z,
				
				// min Y, horizontal face
				box.minPos.x, box.minPos.y, box.maxPos.z,
				box.maxPos.x, box.minPos.y, box.maxPos.z,
				box.maxPos.x, box.minPos.y, box.minPos.z,
				box.minPos.x, box.minPos.y, box.minPos.z,
				// max Y, horizontal face
				box.minPos.x, box.maxPos.y, box.maxPos.z,
				box.maxPos.x, box.maxPos.y, box.maxPos.z,
				box.maxPos.x, box.maxPos.y, box.minPos.z,
				box.minPos.x, box.maxPos.y, box.minPos.z,
				//endregion
			};
			
			for (int vertexIndex = 0; vertexIndex < boxVertices.length; vertexIndex+=3)
			{
				this.vertexBuffer.putFloat((float)boxVertices[vertexIndex]); // x
				this.vertexBuffer.putFloat((float)boxVertices[vertexIndex+1]); // y
				this.vertexBuffer.putFloat((float)boxVertices[vertexIndex+2]); // z
				
				int color = ColorUtil.toColorInt(box.color);
				byte r = (byte) ColorUtil.getRed(color);
				byte g = (byte) ColorUtil.getGreen(color);
				byte b = (byte) ColorUtil.getBlue(color);
				byte a = (byte) ColorUtil.getAlpha(color);
				this.vertexBuffer.put(r);
				this.vertexBuffer.put(g);
				this.vertexBuffer.put(b);
				this.vertexBuffer.put(a);
				
				this.vertexBuffer.put(box.material);
				// TODO make sure this all is a multiple of 4 like LodQuadBuilder (might cause issues with AMD/Mac otherwise)
			}
		}
		this.vertexBuffer.flip();
		this.indexBuffer.flip();
		
		
		this.state = McInstancedVboContainer.EState.READY_TO_UPLOAD;
	}
	
	private int vertexBufferSize()
	{
		int faceCount = this.uploadedBoxCount * 6;
		int vertexCount = faceCount * 6;
		
		int byteSize = vertexCount * 3 * Float.BYTES; // x,y,z
		byteSize += vertexCount * 4; // r,g,b,a
		byteSize += 1; // material
		return byteSize;
	}
	private int indexBufferSize()
	{
		int quadCount = this.uploadedBoxCount * 36;
		int byteSize = quadCount * GLEnums.getTypeSize(GL32.GL_UNSIGNED_INT) * 6;
		return byteSize;
		
		//int faceCount = this.uploadedBoxCount * 6;
		//int vertexCount = faceCount * 6;
		//int byteSize = vertexCount * Integer.BYTES;
		//return byteSize;
	}
	
	public void uploadDataToGpu()
	{
		GpuDevice gpuDevice = RenderSystem.getDevice();
		CommandEncoder commandEncoder = gpuDevice.createCommandEncoder();
		
		// vertex
		{
			int totalVertexByteSize = this.vertexBufferSize();
			if (this.vboGpuBuffer == null
				|| this.vboGpuBuffer.size() < totalVertexByteSize)
			{
				Supplier<String> labelSupplier = () -> "distantHorizons:GenericContainerVertex";
				int usage = 8 | 32; // is this just using OpenGL VBO flags?, if so I can't find it, supposedly GlDevice on Mojang's side
				this.vboGpuBuffer = gpuDevice.createBuffer(labelSupplier, usage, totalVertexByteSize);
			}
			
			GpuBufferSlice bufferSlice = new GpuBufferSlice(this.vboGpuBuffer, /*offset*/0, totalVertexByteSize);
			commandEncoder.writeToBuffer(bufferSlice, this.vertexBuffer);
		}
		
		// index
		{
			int totalVertexByteSize = this.indexBufferSize();
			if (this.indexGpuBuffer == null
				|| this.indexGpuBuffer.size() < totalVertexByteSize)
			{
				Supplier<String> labelSupplier = () -> "distantHorizons:GenericContainerIndex";
				// GpuBuffer.USAGE_UNIFORM = 128
				// GpuBuffer.USAGE_INDEX = 64
				int usage = 8 | 32 | 64 | 128; // is this just using OpenGL VBO flags?, if so I can't find it, supposedly GlDevice on Mojang's side
				this.indexGpuBuffer = gpuDevice.createBuffer(labelSupplier, usage, totalVertexByteSize);
			}
			
			int offset = 0;
			GpuBufferSlice bufferSlice = new GpuBufferSlice(this.indexGpuBuffer, offset, totalVertexByteSize);
			
			commandEncoder.writeToBuffer(bufferSlice, this.indexBuffer);
		}
		
		this.state = EState.RENDER;
	}
	
	//endregion
	
	
	
	//================//
	// base overrides //
	//================//
	//region
	
	@Override
	public void close()
	{
		GLProxy.queueRunningOnRenderThread(() -> 
		{
			if (this.vboGpuBuffer != null)
			{
				this.vboGpuBuffer.close();
			}
			if (this.indexGpuBuffer != null)
			{
				this.indexGpuBuffer.close();
			}
		});
	}
	
	//endregion
	
	
	
}
