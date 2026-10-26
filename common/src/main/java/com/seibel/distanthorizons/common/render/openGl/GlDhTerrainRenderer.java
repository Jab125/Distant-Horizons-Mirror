package com.seibel.distanthorizons.common.render.openGl;

import com.seibel.distanthorizons.api.interfaces.render.renderDef.objects.IDhApiTerrainBufferContainer;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.util.IDhApiReadOnlyList;
import com.seibel.distanthorizons.common.render.openGl.terrain.GlDhTerrainShaderProgram;
import com.seibel.distanthorizons.core.render.RenderParam;
import com.seibel.distanthorizons.api.interfaces.render.renderDef.IDhApiTerrainRenderer;

import java.util.List;
import static com.seibel.distanthorizons.lwjgl.LWJGLServiceProvider.LWJGL;

public class GlDhTerrainRenderer implements IDhApiTerrainRenderer
{
	public static final GlDhTerrainRenderer INSTANCE = new GlDhTerrainRenderer();
	
	private GlDhTerrainShaderProgram terrainShaderProgram = null;
	
	
	
	//=============//
	// constructor //
	//=============//
	//region
	
	private GlDhTerrainRenderer() {}
	
	//endregion
	
	
	
	//=========//
	// getters //
	//=========//
	//region
	
	/** must be called on the render thread the first time so GL can run it's setup */
	public GlDhTerrainShaderProgram getTerrainShaderProgram()
	{
		if (this.terrainShaderProgram == null)
		{
			this.terrainShaderProgram = new GlDhTerrainShaderProgram();
		}
		
		return this.terrainShaderProgram;
	}
	
	//endregion
	
	
	
	//========//
	// render //
	//========//
	//region
	
	@Override 
	public void render(
		DhApiRenderParam apiRenderEventParam, boolean opaquePass,
		IDhApiReadOnlyList<? extends IDhApiTerrainBufferContainer> bufferContainers)
	{
		RenderParam renderEventParam = (RenderParam) apiRenderEventParam;
		#if MC_VER > MC_26_2_0
		if (opaquePass) com.seibel.distanthorizons.common.render.NativeTransparencyRenderer.capture(renderEventParam, bufferContainers);
		if (!opaquePass && com.seibel.distanthorizons.common.render.NativeTransparencyRenderer.isActive()) return;
		#endif
		
		this.getTerrainShaderProgram();
		
		this.terrainShaderProgram.tryInit();
		#if MC_VER > MC_26_2_0
		int coverageSampler = org.lwjgl.opengl.GL30.glGetIntegeri(org.lwjgl.opengl.GL33.GL_SAMPLER_BINDING, 3);
		try
		{
			this.terrainShaderProgram.render(renderEventParam, opaquePass, bufferContainers);
		}
		finally
		{
			LWJGL.glBindSampler(3, coverageSampler);
		}
		#else
		this.terrainShaderProgram.render(renderEventParam, opaquePass, bufferContainers);
		#endif
	}
	
	//endregion
	
	
	
}
