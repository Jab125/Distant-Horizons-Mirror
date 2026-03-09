package com.seibel.distanthorizons.common.render.blaze.helpers;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;

@Deprecated // TODO use Uniform Wrapper instead
public class UniformHandler
{
	private static final GpuDevice GPU_DEVICE = RenderSystem.getDevice();
	private static final CommandEncoder COMMAND_ENCODER = GPU_DEVICE.createCommandEncoder();
	
	
	public static GpuBuffer createBuffer(String uniformName, int size, GpuBuffer vboGpuBuffer)
	{
		// create VBO if needed
		if (vboGpuBuffer == null
			|| vboGpuBuffer.size() < size)
		{
			// GpuBuffer.USAGE_UNIFORM = 128
			int usage = GpuBuffer.USAGE_COPY_DST 
				| GpuBuffer.USAGE_VERTEX
				| GpuBuffer.USAGE_UNIFORM;
			vboGpuBuffer = GPU_DEVICE.createBuffer(() -> uniformName, usage, size);
		}
		
		return vboGpuBuffer;
	}
	
	
	
}
