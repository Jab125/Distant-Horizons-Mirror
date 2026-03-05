package com.seibel.distanthorizons.common.renderTest;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.render.renderer.RenderParams;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.core.util.math.Vec3d;
import com.seibel.distanthorizons.core.util.math.Vec3f;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.ILodContainerUniformBufferWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.IUniformBufferWrapper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Supplier;

public class LodContainerUniformBufferWrapper extends AbstractUniformBufferWrapper implements ILodContainerUniformBufferWrapper
{
	
	
	
	
	//========//
	// ??? //
	//========//
	//region
	
	public void createBufferData(RenderParams renderEventParam, LodBufferContainer bufferContainer)
	{
		Vec3d camPos = renderEventParam.exactCameraPosition;
		Vec3f modelOffset = new Vec3f(
			(float) (bufferContainer.minCornerBlockPos.getX() - camPos.x),
			(float) (bufferContainer.minCornerBlockPos.getY() - camPos.y),
			(float) (bufferContainer.minCornerBlockPos.getZ() - camPos.z));
		
		
		Mat4f combinedMatrix = new Mat4f(renderEventParam.dhProjectionMatrix);
		combinedMatrix.multiply(renderEventParam.dhModelViewMatrix);
		
		
		// upload data //
		
		int uniformBufferSize = new Std140SizeCalculator()
			.putVec3() // uModelOffset
			.get();
		
		ByteBuffer buffer = this.getOrCreateBuffer(uniformBufferSize);
		Std140Builder.intoBuffer(buffer)
			.putVec3(modelOffset.x, modelOffset.y, modelOffset.z) // uModelOffset
			.get();
		
	}
	
	//endregion
	
}
