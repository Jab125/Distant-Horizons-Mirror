package com.seibel.distanthorizons.common.renderTest.helpers;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;

import java.util.OptionalDouble;

public class McTextureViewWrapper
{
	public static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	private static final GpuDevice GPU_DEVICE = RenderSystem.getDevice();
	private static final CommandEncoder COMMAND_ENCODER = GPU_DEVICE.createCommandEncoder();
	
	
	
	public GpuTextureView textureView = null;
	public GpuSampler textureSampler = null;
	
	
	
	public void trySetup(GpuTexture texture)
	{
		this.tryRecreateTextureView(texture);
		this.tryCreateSampler();
	}
	private void tryRecreateTextureView(GpuTexture texture)
	{
		if (this.textureView == null
			|| this.textureView.texture() != texture)
		{
			if (this.textureView != null)
			{
				this.textureView.close();
			}
			
			this.textureView = GPU_DEVICE.createTextureView(texture);
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
	
	
	
}
