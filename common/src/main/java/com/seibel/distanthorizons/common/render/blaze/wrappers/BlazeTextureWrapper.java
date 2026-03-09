package com.seibel.distanthorizons.common.render.blaze.wrappers;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.ColorUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;

import java.util.OptionalDouble;

public class BlazeTextureWrapper
{
	public static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	
	private static final GpuDevice GPU_DEVICE = RenderSystem.getDevice();
	private static final CommandEncoder COMMAND_ENCODER = GPU_DEVICE.createCommandEncoder();
	
	
	public final String name;
	public final TextureFormat textureFormat;
	
	public GpuTexture texture = null;
	public GpuTextureView textureView = null;
	public GpuSampler textureSampler = null;
	
	
	
	public static BlazeTextureWrapper createDepth(String name) { return new BlazeTextureWrapper(name, TextureFormat.DEPTH32); }
	public static BlazeTextureWrapper createColor(String name) { return new BlazeTextureWrapper(name, TextureFormat.RGBA8); }
	
	private BlazeTextureWrapper(String name, TextureFormat textureFormat)
	{
		this.name = name;
		this.textureFormat = textureFormat;
	}
	
	
	
	public boolean isEmpty() { return this.texture == null; }
	
	public void trySetup()
	{
		this.tryCreateTexture();
		this.tryCreateSampler();
	}
	private void tryCreateTexture()
	{
		int viewWidth = MC_RENDER.getTargetFramebufferViewportWidth();
		int textureWidth = (this.texture != null) ? this.texture.getWidth(0) : -1;
		
		int viewHeight = MC_RENDER.getTargetFramebufferViewportHeight();
		int textureHeight = (this.texture != null) ? this.texture.getHeight(0) : -1;
		
		if (this.texture == null
			|| textureWidth != viewWidth
			|| textureHeight != viewHeight)
		{
			if (this.texture != null)
			{
				this.texture.close();
				this.textureView.close();
			}
			
			// TODO USAGE_TEXTURE_BINDING = 4
			int usage = 4 | 8 | 32 | 128;
			this.texture = GPU_DEVICE.createTexture(this.name,
				usage,
				this.textureFormat,
				viewWidth, viewHeight,
				/*depthOrLayers*/ 1,  /*mipLevels*/ 1
			);
			this.textureView = GPU_DEVICE.createTextureView(this.texture);
		}
	}
	private void tryCreateSampler()
	{
		if (this.textureSampler == null)
		{
			this.textureSampler = GPU_DEVICE.createSampler(
				AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, // U,V
				FilterMode.LINEAR, FilterMode.LINEAR, // minFilter, magFilter
				1, // maxAnisotropy 
				OptionalDouble.empty() // maxLod
			);
		}
	}
	
	
	/** @see ColorUtil#argbToInt */
	public void clearColor(int clearArgbColor) 
	{
		if (this.texture != null)
		{
			COMMAND_ENCODER.clearColorTexture(this.texture, clearArgbColor);
		}
	}
	public void clearDepth(float depth) 
	{
		if (this.texture != null)
		{
			COMMAND_ENCODER.clearDepthTexture(this.texture, depth);
		}
	}
	
	
	
}
