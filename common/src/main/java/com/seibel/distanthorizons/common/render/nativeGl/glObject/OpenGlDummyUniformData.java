package com.seibel.distanthorizons.common.render.nativeGl.glObject;

import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.objects.ILodContainerUniformBufferWrapper;

public class OpenGlDummyUniformData implements ILodContainerUniformBufferWrapper
{
	@Override public void createUniformData(LodBufferContainer bufferContainer) { }
	@Override public void tryUpload() { }
	@Override public void upload() { }
	@Override public void close() { }
	
}
