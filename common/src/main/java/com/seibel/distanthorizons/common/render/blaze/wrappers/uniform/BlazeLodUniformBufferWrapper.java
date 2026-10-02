package com.seibel.distanthorizons.common.render.blaze.wrappers.uniform;

#if MC_VER <= MC_1_21_10
public class BlazeLodUniformBufferWrapper {}

#else

import com.seibel.distanthorizons.api.interfaces.render.renderDef.objects.IDhApiTerrainBufferContainer;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.util.math.DhVec3f;
import com.seibel.distanthorizons.api.interfaces.render.renderDef.objects.IDhApiTerrainContainerUniformBufferWrapper;

public class BlazeLodUniformBufferWrapper extends BlazeUniformBufferWrapper implements IDhApiTerrainContainerUniformBufferWrapper
{
	
	private boolean uploaded = false;
	
	
	
	//=============//
	// constructor //
	//=============//
	//region
	
	public BlazeLodUniformBufferWrapper() { super(BlazeLodUniformBufferWrapper.class.getName()); }
	
	//endregion
	
	
	
	//========//
	// upload //
	//========//
	//region
	
	@Override
	public void tryUpload(IDhApiTerrainBufferContainer apiBufferContainer)
	{
		if (this.uploaded)
		{
			return;
		}
		
		LodBufferContainer bufferContainer = (LodBufferContainer)apiBufferContainer;
		
		DhVec3f modelOffset = new DhVec3f(
			(float) (bufferContainer.minCornerBlockPos.getX()),
			(float) (bufferContainer.minCornerBlockPos.getY()),
			(float) (bufferContainer.minCornerBlockPos.getZ()));
		
		// upload data //
		this
			.putVec3f(modelOffset.x, modelOffset.y, modelOffset.z) // uModelOffset
			.finishAndUpload();
		
		this.uploaded = true;
	}
	
	//endregion
	
	
	
}
#endif