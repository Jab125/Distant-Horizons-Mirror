package com.seibel.distanthorizons.common.renderTest.helpers;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;

@Deprecated // TODO use Uniform Wrapper instead
public class UniformHandler
{
	
	public static GpuBuffer createBuffer(String uniformName, int size, GpuBuffer vboGpuBuffer)
	{
		GpuDevice gpuDevice = RenderSystem.getDevice();
		
		// create VBO if needed
		if (vboGpuBuffer == null
			|| vboGpuBuffer.size() < size)
		{
			// GpuBuffer.USAGE_UNIFORM = 128
			int usage = 8 | 32 | 128; // is this just using OpenGL VBO flags?, if so I can't find it, supposedly GlDevice on Mojang's side
			vboGpuBuffer = gpuDevice.createBuffer(() -> uniformName, usage, size);
		}
		
		return vboGpuBuffer;
	}
	
}
