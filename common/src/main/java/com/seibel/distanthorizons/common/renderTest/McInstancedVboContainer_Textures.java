package com.seibel.distanthorizons.common.renderTest;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBox;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.render.glObject.GLProxy;
import com.seibel.distanthorizons.core.render.renderer.generic.IInstancedVboContainer;
import com.seibel.distanthorizons.core.render.renderer.generic.RenderableBoxGroup;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;

import java.awt.*;
import java.nio.ByteBuffer;
import java.util.List;


/**
 * For use by {@link RenderableBoxGroup}
 *
 * @see RenderableBoxGroup
 */
public class McInstancedVboContainer_Textures implements IInstancedVboContainer
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	
	
	
	public GpuTexture colorTexture;
	public GpuTexture scaleTexture;
	public GpuTexture chunkPosXTexture;
	public GpuTexture chunkPosYTexture;
	public GpuTexture chunkPosZTexture;
	public GpuTexture subChunkPosTexture;
	public GpuTexture materialTexture;
	
	public int[] chunkPosData = new int[0];
	public float[] subChunkPosData = new float[0];
	public float[] scaleData = new float[0];
	public byte[] colorData = new byte[0];
	public int[] materialData = new int[0];
	
	public int uploadedBoxCount = 0;
	
	private EState state = EState.NEW;
	@Override
	public EState getState() { return this.state; }
	@Override
	public void setState(EState state) { this.state = state; }
	
	
	
	//===========================//
	// render building/uploading //
	//===========================//
	//region
	
	public void updateVertexData(List<DhApiRenderableBox> uploadBoxList)
	{
		int boxCount = uploadBoxList.size();
		
		
		// recreate the data arrays if their size is different
		if (this.uploadedBoxCount != boxCount)
		{
			this.uploadedBoxCount = boxCount;
			
			this.chunkPosData = new int[boxCount * 3]; // 3 elements XYZ
			this.subChunkPosData = new float[boxCount * 3]; // 3 elements XYZ
			this.scaleData = new float[boxCount * 3]; // 3 elements XYZ
			
			this.colorData = new byte[boxCount * 4]; // 4 elements, RGBA
			this.materialData = new int[boxCount];
		}
		
		
		// transformation / scaling //
		for (int i = 0; i < boxCount; i++)
		{
			DhApiRenderableBox box = uploadBoxList.get(i);
			
			int dataIndex = i * 3;
			
			this.chunkPosData[dataIndex] = LodUtil.getChunkPosFromDouble(box.minPos.x);
			this.chunkPosData[dataIndex + 1] = LodUtil.getChunkPosFromDouble(box.minPos.y);
			this.chunkPosData[dataIndex + 2] = LodUtil.getChunkPosFromDouble(box.minPos.z);
			
			this.subChunkPosData[dataIndex] = LodUtil.getSubChunkPosFromDouble(box.minPos.x);
			this.subChunkPosData[dataIndex + 1] = LodUtil.getSubChunkPosFromDouble(box.minPos.y);
			this.subChunkPosData[dataIndex + 2] = LodUtil.getSubChunkPosFromDouble(box.minPos.z);
			
			this.scaleData[dataIndex] = (float) (box.maxPos.x - box.minPos.x);
			this.scaleData[dataIndex + 1] = (float) (box.maxPos.y - box.minPos.y);
			this.scaleData[dataIndex + 2] = (float) (box.maxPos.z - box.minPos.z);
		}
		
		
		// colors/materials //
		for (int i = 0; i < boxCount; i++)
		{
			DhApiRenderableBox box = uploadBoxList.get(i);
			Color color = box.color;
			int colorIndex = i * 4;
			this.colorData[colorIndex] = (byte)color.getRed();
			this.colorData[colorIndex + 1] = (byte)color.getGreen();
			this.colorData[colorIndex + 2] = (byte)color.getBlue();
			this.colorData[colorIndex + 3] = (byte)color.getAlpha();
			
			this.materialData[i] = box.material;
		}
		
		this.state = EState.READY_TO_UPLOAD;
	}
	
	public void uploadDataToGpu()
	{
		GpuDevice gpuDevice = RenderSystem.getDevice();
		CommandEncoder commandEncoder = gpuDevice.createCommandEncoder();
		
		if (this.colorTexture == null
			|| this.colorTexture.getWidth(0) < this.uploadedBoxCount)
		{
			// TODO USAGE_COPY_DST = 1
			// TODO USAGE_TEXTURE_BINDING = 4
			int usage = 1 | 4 | 8 | 32 | 128;
			String texturePrefix = "DhGenericTexture_";
			
			this.colorTexture = gpuDevice.createTexture(texturePrefix + "Color",
				usage,
				TextureFormat.RGBA8,
				this.uploadedBoxCount, 1,
				1, 1
			);
			
			this.scaleTexture = gpuDevice.createTexture(texturePrefix + "Scale",
				usage,
				TextureFormat.RGBA8,
				this.uploadedBoxCount, 1,
				1, 1
			);
			
			this.chunkPosXTexture = gpuDevice.createTexture(texturePrefix + "ChunkXPos",
				usage,
				TextureFormat.DEPTH32,
				this.uploadedBoxCount, 1,
				1, 1
			);
			this.chunkPosYTexture = gpuDevice.createTexture(texturePrefix + "ChunkYPos",
				usage,
				TextureFormat.DEPTH32,
				this.uploadedBoxCount, 1,
				1, 1
			);
			this.chunkPosZTexture = gpuDevice.createTexture(texturePrefix + "ChunkZPos",
				usage,
				TextureFormat.DEPTH32,
				this.uploadedBoxCount, 1,
				1, 1
			);
			
			this.subChunkPosTexture = gpuDevice.createTexture(texturePrefix + "SubChunk",
				usage,
				TextureFormat.RGBA8,
				this.uploadedBoxCount, 1,
				1, 1
			);
			
			//this.materialTexture = gpuDevice.createTexture(texturePrefix + "Material",
			//	usage,
			//	TextureFormat.RED8I, // TODO not valid?
			//	this.uploadedBoxCount, 1,
			//	1, 1
			//);
		}
		
		{
			// color
			{
				ByteBuffer colorBuffer = ByteBuffer.allocateDirect(this.colorData.length * Float.BYTES);
				colorBuffer.asFloatBuffer();
				
				for (int i = 0; i < this.colorData.length; i++)
				{
					colorBuffer.put(this.colorData[i]);
				}
				colorBuffer.rewind();
				
				commandEncoder.writeToTexture(
					this.colorTexture,
					colorBuffer,
					NativeImage.Format.RGBA, // holds bytes
					0, //mipLevel
					0, // depthOrLayer
					0, // destX
					0, // destY
					this.uploadedBoxCount, // width
					1 // height
				);
			}
			
			// scale
			{
				ByteBuffer scaleBuffer = ByteBuffer.allocateDirect(this.scaleData.length * Float.BYTES);
				scaleBuffer.asFloatBuffer();
				
				for (int i = 0; i < this.scaleData.length; i++)
				{ scaleBuffer.putFloat(this.scaleData[i]); }
				scaleBuffer.rewind();
				
				commandEncoder.writeToTexture(
					this.scaleTexture,
					scaleBuffer,
					NativeImage.Format.RGB, // holds bytes
					0, //mipLevel
					0, // depthOrLayer
					0, // destX
					0, // destY
					this.uploadedBoxCount, // width
					1 // height
				);
			}
			
			
			//for (int i = 0; i < this.chunkPosData.length; i++)
			//{ buffer.putInt(this.chunkPosData[i]); }
			//
			//for (int i = 0; i < this.subChunkPosData.length; i++)
			//{ buffer.putFloat(this.subChunkPosData[i]); }
			//
			//for (int i = 0; i < this.colorData.length; i++)
			//{ buffer.putFloat(this.colorData[i]); }
			//
			//for (int i = 0; i < this.materialData.length; i++)
			//{ buffer.putInt(this.materialData[i]); }
			//
			//buffer.rewind();
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
			if (this.colorTexture != null)
			{
				this.colorTexture.close();
			}
		});
	}
	
	//endregion
	
	
	
}
