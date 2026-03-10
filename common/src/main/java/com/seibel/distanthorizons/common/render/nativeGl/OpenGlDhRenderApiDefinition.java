package com.seibel.distanthorizons.common.render.nativeGl;

import com.seibel.distanthorizons.core.render.renderer.AbstractDebugWireframeRenderer;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.AbstractDhRenderApiDefinition;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.objects.IDhGenericObjectVertexBufferContainer;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.objects.ILodContainerUniformBufferWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.objects.IVertexBufferWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.renderPass.*;

public class OpenGlDhRenderApiDefinition extends AbstractDhRenderApiDefinition
{
	
	@Override public IDhTerrainRenderer getTerrainRenderer() { return null; }
	@Override public IDhSsaoRenderer getSsaoRenderer() { return null; }
	@Override public IDhFogRenderer getFogRenderer() { return null; }
	@Override public IDhFarFadeRenderer getFarFadeRenderer() { return null; }
	@Override public AbstractDebugWireframeRenderer getDebugWireframeRenderer() { return null; }
	
	@Override public IDhVanillaFadeRenderer getVanillaFadeRenderer() { return null; }
	@Override public IDhTestTriangleRenderer getTestTriangleRenderer() { return null; }
	
	@Override public IDhGenericRenderer createGenericRenderer() { return null; }
	
	
	@Override public IVertexBufferWrapper createVboWrapper(String name) { return null; }
	@Override public ILodContainerUniformBufferWrapper createLodContainerUniformWrapper() { return null; }
	@Override public IDhGenericObjectVertexBufferContainer createGenericVboContainer() { return null; }
	
	
	
}
