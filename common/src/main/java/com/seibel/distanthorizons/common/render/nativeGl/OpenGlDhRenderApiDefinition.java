package com.seibel.distanthorizons.common.render.nativeGl;

import com.seibel.distanthorizons.common.render.nativeGl.generic.OpenGlGenericObjectRenderer;
import com.seibel.distanthorizons.common.render.nativeGl.generic.OpenGlGenericObjectVertexContainer;
import com.seibel.distanthorizons.common.render.nativeGl.glObject.OpenGlDummyUniformData;
import com.seibel.distanthorizons.common.render.nativeGl.glObject.buffer.GLVertexBuffer;
import com.seibel.distanthorizons.common.render.nativeGl.postProcessing.fade.DhFarFadeRenderer;
import com.seibel.distanthorizons.common.render.nativeGl.postProcessing.fade.VanillaFadeRenderer;
import com.seibel.distanthorizons.common.render.nativeGl.postProcessing.fog.DhFogRenderer;
import com.seibel.distanthorizons.common.render.nativeGl.postProcessing.ssao.DhSSAORenderer;
import com.seibel.distanthorizons.common.render.nativeGl.test.GlTestTriangleRenderer;
import com.seibel.distanthorizons.core.render.renderer.AbstractDebugWireframeRenderer;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.AbstractDhRenderApiDefinition;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.objects.IDhGenericObjectVertexBufferContainer;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.objects.ILodContainerUniformBufferWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.objects.IVertexBufferWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.renderPass.*;

public class OpenGlDhRenderApiDefinition extends AbstractDhRenderApiDefinition
{
	
	public String getApiName() { return "OpenGL"; }
	
	@Override public IDhMetaRenderer getMetaRenderer() { return OpenGlDhMetaRenderer.INSTANCE; }
	@Override public IDhTerrainRenderer getTerrainRenderer() { return DhTerrainShaderProgram.INSTANCE; } // TODO how to support Iris?
	@Override public IDhSsaoRenderer getSsaoRenderer() { return DhSSAORenderer.INSTANCE; }
	@Override public IDhFogRenderer getFogRenderer() { return DhFogRenderer.INSTANCE; }
	@Override public IDhFarFadeRenderer getFarFadeRenderer() { return DhFarFadeRenderer.INSTANCE; }
	@Override public AbstractDebugWireframeRenderer getDebugWireframeRenderer() { return OpenGlDebugWireframeRenderer.INSTANCE; }
	
	@Override public IDhVanillaFadeRenderer getVanillaFadeRenderer() { return VanillaFadeRenderer.INSTANCE; }
	@Override public IDhTestTriangleRenderer getTestTriangleRenderer() { return GlTestTriangleRenderer.INSTANCE; }
	
	@Override public IDhGenericRenderer createGenericRenderer() { return OpenGlGenericObjectRenderer.INSTANCE; }
	
	
	@Override public IVertexBufferWrapper createVboWrapper(String name) { return new GLVertexBuffer(); }
	@Override public ILodContainerUniformBufferWrapper createLodContainerUniformWrapper() { return new OpenGlDummyUniformData(); }
	@Override public IDhGenericObjectVertexBufferContainer createGenericVboContainer() { return new OpenGlGenericObjectVertexContainer(); }
	
	
	
}
