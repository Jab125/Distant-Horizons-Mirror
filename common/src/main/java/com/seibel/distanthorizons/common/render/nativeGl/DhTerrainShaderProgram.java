/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.common.render.nativeGl;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiRenderPass;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiFramebuffer;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiShaderProgram;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.*;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiTextureCreatedParam;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3f;
import com.seibel.distanthorizons.common.render.nativeGl.glObject.GLProxy;
import com.seibel.distanthorizons.common.render.nativeGl.glObject.buffer.GLVertexBuffer;
import com.seibel.distanthorizons.common.render.nativeGl.glObject.buffer.QuadElementBuffer;
import com.seibel.distanthorizons.common.render.nativeGl.glObject.shader.ShaderProgram;
import com.seibel.distanthorizons.common.render.nativeGl.glObject.texture.*;
import com.seibel.distanthorizons.common.render.nativeGl.glObject.vertexAttribute.AbstractVertexAttribute;
import com.seibel.distanthorizons.common.render.nativeGl.glObject.vertexAttribute.VertexAttributePostGL43;
import com.seibel.distanthorizons.common.render.nativeGl.glObject.vertexAttribute.VertexAttributePreGL43;
import com.seibel.distanthorizons.common.render.nativeGl.glObject.vertexAttribute.VertexPointer;
import com.seibel.distanthorizons.common.render.nativeGl.postProcessing.apply.DhApplyShader;
import com.seibel.distanthorizons.common.render.nativeGl.util.vertexFormat.LodVertexFormat;
import com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftGLWrapper;
import com.seibel.distanthorizons.common.wrappers.misc.LightMapWrapper;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodQuadBuilder;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.render.RenderBufferHandler;
import com.seibel.distanthorizons.core.render.RenderParams;
import com.seibel.distanthorizons.core.util.RenderUtil;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.core.util.math.Vec3d;
import com.seibel.distanthorizons.core.util.math.Vec3f;
import com.seibel.distanthorizons.core.util.objects.SortedArraySet;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IProfilerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.AbstractOptifineAccessor;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IIrisAccessor;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.objects.IVertexBufferWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.renderPass.IDhTerrainRenderer;
import com.seibel.distanthorizons.coreapi.DependencyInjection.ApiEventInjector;
import com.seibel.distanthorizons.coreapi.DependencyInjection.OverrideInjector;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL32;

/**
 * Handles rendering the normal LOD terrain.
 * @see LodQuadBuilder 
 */
public class DhTerrainShaderProgram extends ShaderProgram implements IDhApiShaderProgram, IDhTerrainRenderer
{
	public final AbstractVertexAttribute vao;
	
	// Uniforms
	public int uCombinedMatrix = -1;
	public int uModelOffset = -1;
	public int uWorldYOffset = -1;
	
	public int uMircoOffset = -1;
	public int uEarthRadius = -1;
	public int uLightMap = -1;
	
	// fragment shader uniforms
	public int uClipDistance = -1;
	public int uDitherDhRendering = -1;
	
	// Noise Uniforms
	public int uNoiseEnabled = -1;
	public int uNoiseSteps = -1;
	public int uNoiseIntensity = -1;
	public int uNoiseDropoff = -1;
	
	// Debug Uniform
	public int uIsWhiteWorld = -1;
	
	
	
	//=============//
	// constructor //
	//=============//
	//region
	
	// This will bind  AbstractVertexAttribute
	public DhTerrainShaderProgram()
	{
		super(
			"shaders/standard.vert",
			"shaders/flat_shaded.frag",
			new String[]{"vPosition", "color"}
		);
		
		this.uCombinedMatrix = this.getUniformLocation("uCombinedMatrix");
		this.uModelOffset = this.getUniformLocation("uModelOffset");
		this.uWorldYOffset = this.getUniformLocation("uWorldYOffset");
		this.uDitherDhRendering = this.getUniformLocation("uDitherDhRendering");
		this.uMircoOffset = this.getUniformLocation("uMircoOffset");
		this.uEarthRadius = this.getUniformLocation("uEarthRadius");
		
		this.uLightMap = this.getUniformLocation("uLightMap");
		
		// Fog/Clip Uniforms
		this.uClipDistance = this.getUniformLocation("uClipDistance");
		
		// Noise Uniforms
		this.uNoiseEnabled = this.getUniformLocation("uNoiseEnabled");
		this.uNoiseSteps = this.getUniformLocation("uNoiseSteps");
		this.uNoiseIntensity = this.getUniformLocation("uNoiseIntensity");
		this.uNoiseDropoff = this.getUniformLocation("uNoiseDropoff");
		
		// Debug Uniform
		this.uIsWhiteWorld = this.getUniformLocation("uIsWhiteWorld");
		
		
		if (GLProxy.getInstance().vertexAttributeBufferBindingSupported)
		{
			this.vao = new VertexAttributePostGL43(); // also binds AbstractVertexAttribute
		}
		else
		{
			this.vao = new VertexAttributePreGL43(); // also binds AbstractVertexAttribute
		}
		this.vao.bind();
		
		// short: x, y, z, meta
		//      meta: byte skylight, byte blocklight, byte microOffset
		this.vao.setVertexAttribute(0, 0, VertexPointer.addUnsignedShortsPointer(4, false, true));
		// byte: r, g, b, a
		this.vao.setVertexAttribute(0, 1, VertexPointer.addUnsignedBytesPointer(4, true, false));
		// byte: iris material ID, normal index, 2 spacers
		this.vao.setVertexAttribute(0, 2, VertexPointer.addUnsignedBytesPointer(4, true, true));
		
		try
		{
			int vertexByteCount = LodVertexFormat.DH_VERTEX_FORMAT.getByteSize();
			this.vao.completeAndCheck(vertexByteCount);
		}
		catch (RuntimeException e)
		{
			System.out.println(LodVertexFormat.DH_VERTEX_FORMAT);
			throw e;
		}
		
	}
	
	//endregion
	
	
	
	//=========//
	// methods //
	//=========//
	//region
	
	@Override
	public void bind()
	{
		super.bind();
		this.vao.bind();
	}
	@Override
	public void unbind()
	{
		super.unbind();
		this.vao.unbind();
	}
	
	@Override
	public void free()
	{
		this.vao.free();
		super.free();
	}
	
	@Override
	public void bindVertexBuffer(int vbo) { this.vao.bindBufferToAllBindingPoints(vbo); }
	
	@Override
	public void fillUniformData(DhApiRenderParam renderParameters)
	{
		Mat4f combinedMatrix = new Mat4f(renderParameters.dhProjectionMatrix);
		combinedMatrix.multiply(renderParameters.dhModelViewMatrix);
		
		super.bind();

		// uniforms
		this.setUniform(this.uCombinedMatrix, combinedMatrix);
		this.setUniform(this.uMircoOffset, 0.01f); // 0.01 block offset
		
		this.setUniform(this.uLightMap, LightMapWrapper.GL_BOUND_INDEX);
		
		this.setUniform(this.uWorldYOffset, (float) renderParameters.worldYOffset);
		
		this.setUniform(this.uDitherDhRendering, Config.Client.Advanced.Graphics.Quality.ditherDhFade.get());
		
		float curveRatio = Config.Client.Advanced.Graphics.Experimental.earthCurveRatio.get();
		if (curveRatio < -1.0f || curveRatio > 1.0f)
		{
			curveRatio = /*6371KM*/ 6371000.0f / curveRatio;
		}
		else
		{
			// disable curvature if the config value is between -1 and 1
			curveRatio = 0.0f;
		}
		this.setUniform(this.uEarthRadius, curveRatio);
		
		// Noise Uniforms
		this.setUniform(this.uNoiseEnabled, Config.Client.Advanced.Graphics.NoiseTexture.enableNoiseTexture.get());
		this.setUniform(this.uNoiseSteps, Config.Client.Advanced.Graphics.NoiseTexture.noiseSteps.get());
		this.setUniform(this.uNoiseIntensity, Config.Client.Advanced.Graphics.NoiseTexture.noiseIntensity.get());
		this.setUniform(this.uNoiseDropoff, Config.Client.Advanced.Graphics.NoiseTexture.noiseDropoff.get());
		
		// Debug
		this.setUniform(this.uIsWhiteWorld, Config.Client.Advanced.Debugging.enableWhiteWorld.get());
		
		// Clip Uniform
		float dhNearClipDistance = RenderUtil.getNearClipPlaneInBlocks();
		if (!Config.Client.Advanced.Debugging.lodOnlyMode.get())
		{
			// this added value prevents the near clip plane and discard circle from touching, which looks bad
			dhNearClipDistance += 16f;
		}
		this.setUniform(this.uClipDistance, dhNearClipDistance);
	}
	
	@Override
	public void setModelOffsetPos(DhApiVec3f modelOffsetPos) { this.setUniform(this.uModelOffset, new Vec3f(modelOffsetPos)); }
	
	@Override
	public int getId() { return this.id; }
	
	/** The base DH render program should always render */
	@Override
	public boolean overrideThisFrame() { return true; }
	
	//endregion
	
	
	
	
	@Override
	public void runRenderPassSetup(RenderParams renderParams) { OpenGlRenderState.INSTANCE.runRenderPassSetup(renderParams); }
	
	@Override
	public void runRenderPassCleanup(RenderParams renderParams)  { OpenGlRenderState.INSTANCE.runRenderPassCleanup(renderParams); }
	
	
	@Override 
	public void render(RenderParams renderEventParam, boolean opaquePass, SortedArraySet<LodBufferContainer> bufferContainers, IProfilerWrapper profiler)
	{
		OpenGlRenderState.INSTANCE.renderLodTerrain(bufferContainers, renderEventParam, opaquePass);
	}
	@Override 
	public void applyToMcTexture()
	{
	}
	@Override 
	public void clearDepth()
	{
	}
	@Override 
	public void clearColor()
	{
	}
	
	
	public static class OpenGlRenderState
	{
		public static final DhLogger LOGGER = new DhLoggerBuilder()
			.fileLevelConfig(Config.Common.Logging.logRendererEventToFile)
			.build();
		
		public static final DhLogger RATE_LIMITED_LOGGER = new DhLoggerBuilder()
			.fileLevelConfig(Config.Common.Logging.logRendererEventToFile)
			.maxCountPerSecond(4)
			.build();
		
		public static final OpenGlRenderState INSTANCE = new OpenGlRenderState();
		
		
		private static final IMinecraftClientWrapper MC = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
		private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
		private static final MinecraftGLWrapper GLMC = MinecraftGLWrapper.INSTANCE;
		private static final IIrisAccessor IRIS_ACCESSOR = ModAccessorInjector.INSTANCE.get(IIrisAccessor.class);
		
		
		// these ID's either what any render is currently using (since only one renderer can be active at a time), or just used previously
		private int activeFramebufferId = -1;
		private int activeColorTextureId = -1;
		private int activeDepthTextureId = -1;
		private int textureWidth;
		private int textureHeight;
		
		
		private IDhApiShaderProgram lodRenderProgram = null;
		public QuadElementBuffer quadIBO = null;
		private boolean renderObjectsCreated = false;
		
		// framebuffer and texture ID's for this renderer
		private IDhApiFramebuffer framebuffer;
		/** will be null if MC's framebuffer is being used since MC already has a color texture */
		@Nullable
		private DhColorTexture nullableColorTexture;
		private DHDepthTexture depthTexture;
		/**
		 * If true the {@link OpenGlRenderState#framebuffer} is the same as MC's.
		 * This should only be true in the case of Optifine so LODs won't be overwritten when shaders are enabled.
		 */
		private boolean usingMcFramebuffer = false;
		
		
		private IDhApiShaderProgram lodShaderProgramThisFrame;
		
		
		//
		//
		//
		
		public void runRenderPassSetup(RenderParams renderParams)
		{
			boolean firstPass = 
				(renderParams.renderPass == EDhApiRenderPass.OPAQUE
				|| renderParams.renderPass == EDhApiRenderPass.OPAQUE_AND_TRANSPARENT);
			
			if (!this.renderObjectsCreated)
			{
				boolean setupSuccess = this.createRenderObjects();
				if (!setupSuccess)
				{
					// shouldn't normally happen, but just in case
					return;
				}
				
				this.renderObjectsCreated = true;
			}
			
			
			this.setGLState(renderParams, firstPass);
			
			this.quadIBO.bind();
			renderParams.lightmap.bind();
			
			this.lodShaderProgramThisFrame = this.lodRenderProgram;
			IDhApiShaderProgram lodShaderProgramOverride = OverrideInjector.INSTANCE.get(IDhApiShaderProgram.class);
			if (lodShaderProgramOverride != null && this.lodShaderProgramThisFrame.overrideThisFrame())
			{
				this.lodShaderProgramThisFrame = lodShaderProgramOverride;
			}
			
		}
		
		public void runRenderPassCleanup(RenderParams renderParams)
		{
			boolean runningDeferredPass = (renderParams.renderPass == EDhApiRenderPass.TRANSPARENT);
			
			
			if (!runningDeferredPass)
			{
				//===================//
				// optifine clean up //
				//===================//
				
				if (this.usingMcFramebuffer)
				{
					// If MC's framebuffer is being used the depth needs to be cleared to prevent rendering on top of MC.
					// This should only happen when Optifine shaders are being used.
					GL32.glClear(GL32.GL_DEPTH_BUFFER_BIT);
				}
				
				
				
				//=============================//
				// Apply to the MC Framebuffer //
				//=============================//
				
				boolean cancelApplyShader = ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeApplyShaderRenderEvent.class, renderParams);
				if (!cancelApplyShader)
				{
					//profiler.popPush("LOD Apply");
					
					// Copy the LOD framebuffer to Minecraft's framebuffer
					DhApplyShader.INSTANCE.render(renderParams.partialTicks);
				}
			}
			
			
			renderParams.lightmap.unbind();
			this.quadIBO.unbind();
			this.lodShaderProgramThisFrame.unbind();
		}
		
		
		
		//=================//
		// Setup Functions //
		//=================//
		//region
		
		private void setGLState(
			DhApiRenderParam renderEventParam,
			boolean firstPass)
		{
			//===================//
			// framebuffer setup //
			//===================//
			
			// get the active framebuffer
			IDhApiFramebuffer framebuffer = this.framebuffer;
			IDhApiFramebuffer framebufferOverride = OverrideInjector.INSTANCE.get(IDhApiFramebuffer.class);
			if (framebufferOverride != null && framebufferOverride.overrideThisFrame())
			{
				framebuffer = framebufferOverride;
			}
			this.activeFramebufferId = framebuffer.getId();
			framebuffer.bind();
			
			
			
			//==========//
			// bindings //
			//==========//
			
			// by default draw everything as triangles
			GL32.glPolygonMode(GL32.GL_FRONT_AND_BACK, GL32.GL_FILL);
			GLMC.enableFaceCulling();
			
			GLMC.glBlendFunc(GL32.GL_SRC_ALPHA, GL32.GL_ONE_MINUS_SRC_ALPHA);
			GLMC.glBlendFuncSeparate(GL32.GL_SRC_ALPHA, GL32.GL_ONE_MINUS_SRC_ALPHA, GL32.GL_ONE, GL32.GL_ZERO);
			
			GL32.glDisable(GL32.GL_SCISSOR_TEST);
			
			// Enable depth test and depth mask
			GLMC.enableDepthTest();
			GLMC.glDepthFunc(GL32.GL_LESS);
			GLMC.enableDepthMask();
			
			// This is required for MC versions 1.21.5+
			// due to MC updating the lightmap by changing the viewport size
			GL32.glViewport(0, 0, this.textureWidth, this.textureHeight);
			
			this.lodRenderProgram.bind();
			
			
			
			//==========//
			// uniforms //
			//==========//

			IDhApiShaderProgram shaderProgramOverride = OverrideInjector.INSTANCE.get(IDhApiShaderProgram.class);
			if (shaderProgramOverride != null)
			{
				shaderProgramOverride.fillUniformData(renderEventParam);
			}

			this.lodRenderProgram.fillUniformData(renderEventParam);
			
			
			
			//===============//
			// texture setup //
			//===============//
			
			// resize the textures if needed
			if (MC_RENDER.getTargetFramebufferViewportWidth() != this.textureWidth
				|| MC_RENDER.getTargetFramebufferViewportHeight() != this.textureHeight)
			{
				// just resizing the textures doesn't work when Optifine is present,
				// so recreate the textures with the new size instead
				this.createAndBindTextures();
			}
			
			
			// set the active textures
			this.activeDepthTextureId = this.depthTexture.getTextureId();
			
			if (this.nullableColorTexture != null)
			{
				this.activeColorTextureId = this.nullableColorTexture.getTextureId();
			}
			else
			{
				// get MC's color texture 
				this.activeColorTextureId = GL32.glGetFramebufferAttachmentParameteri(GL32.GL_FRAMEBUFFER, GL32.GL_COLOR_ATTACHMENT0, GL32.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
			}
			
			
			// needs to be fired after all the textures have been created/bound
			boolean clearTextures = !ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeTextureClearEvent.class, renderEventParam);
			if (clearTextures)
			{
				GL32.glClearDepth(1.0);
				
				float[] clearColorValues = new float[4];
				GL32.glGetFloatv(GL32.GL_COLOR_CLEAR_VALUE, clearColorValues);
				GL32.glClearColor(clearColorValues[0], clearColorValues[1], clearColorValues[2], 1.0f);
				
				if (this.usingMcFramebuffer && framebufferOverride == null)
				{
					// Due to using MC/Optifine's framebuffer we need to re-bind the depth texture,
					// otherwise we'll be writing to MC/Optifine's depth texture which causes rendering issues
					framebuffer.addDepthAttachment(this.depthTexture.getTextureId(), EDhDepthBufferFormat.DEPTH32F.isCombinedStencil());
					
					
					// don't clear the color texture, that removes the sky 
					GL32.glClear(GL32.GL_DEPTH_BUFFER_BIT);
				}
				else if (firstPass)
				{
					GL32.glClear(GL32.GL_COLOR_BUFFER_BIT | GL32.GL_DEPTH_BUFFER_BIT);
				}
			}
		}
		
		private boolean createRenderObjects()
		{
			if (this.renderObjectsCreated)
			{
				LOGGER.warn("Renderer setup called but it has already completed setup!");
				return false;
			}
			
			// GLProxy should have already been created by this point, but just in case create it now
			GLProxy.getInstance();
			
			
			
			LOGGER.info("Setting up renderer");
			this.lodRenderProgram = new DhTerrainShaderProgram();
			
			this.quadIBO = new QuadElementBuffer();
			this.quadIBO.reserve(LodQuadBuilder.getMaxBufferByteSize());
			
			
			// create or get the frame buffer
			if (AbstractOptifineAccessor.optifinePresent())
			{
				// use MC/Optifine's default Framebuffer so shaders won't remove the LODs
				int currentFramebufferId = MC_RENDER.getTargetFramebuffer();
				this.framebuffer = new DhFramebuffer(currentFramebufferId);
				this.usingMcFramebuffer = true;
			}
			else
			{
				// normal use case
				this.framebuffer = new DhFramebuffer();
				this.usingMcFramebuffer = false;
			}
			
			// create and bind the necessary textures
			this.createAndBindTextures();
			
			if(this.framebuffer.getStatus() != GL32.GL_FRAMEBUFFER_COMPLETE)
			{
				// This generally means something wasn't bound, IE missing either the color or depth texture
				LOGGER.warn("Framebuffer ["+this.framebuffer.getId()+"] isn't complete.");
				return false;
			}
			
			
			
			LOGGER.info("Renderer setup complete");
			return true;
		}
		
		@SuppressWarnings( "deprecation" ) // done to ignore DhApiColorDepthTextureCreatedEvent
		private void createAndBindTextures()
		{
			int oldWidth = this.textureWidth;
			int oldHeight = this.textureHeight;
			this.textureWidth = MC_RENDER.getTargetFramebufferViewportWidth();
			this.textureHeight = MC_RENDER.getTargetFramebufferViewportHeight();
			
			DhApiTextureCreatedParam textureCreatedParam = new DhApiTextureCreatedParam(
				oldWidth, oldHeight,
				this.textureWidth, this.textureHeight
			);
			
			
			// DhApiColorDepthTextureCreatedEvent needs to be kept around since old versions of Iris need it
			ApiEventInjector.INSTANCE.fireAllEvents(DhApiColorDepthTextureCreatedEvent.class, new DhApiColorDepthTextureCreatedEvent.EventParam(textureCreatedParam));
			ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeColorDepthTextureCreatedEvent.class, textureCreatedParam);
			
			
			// also update the framebuffer override if present
			IDhApiFramebuffer framebufferOverride = OverrideInjector.INSTANCE.get(IDhApiFramebuffer.class);
			
			
			this.depthTexture = new DHDepthTexture(this.textureWidth, this.textureHeight, EDhDepthBufferFormat.DEPTH32F);
			this.framebuffer.addDepthAttachment(this.depthTexture.getTextureId(), EDhDepthBufferFormat.DEPTH32F.isCombinedStencil());
			if (framebufferOverride != null)
			{
				framebufferOverride.addDepthAttachment(this.depthTexture.getTextureId(), EDhDepthBufferFormat.DEPTH32F.isCombinedStencil());
			}
			
			
			// if we are using MC's frame buffer, a color texture is already present and shouldn't need to be bound
			if (!this.usingMcFramebuffer)
			{
				this.nullableColorTexture = DhColorTexture.builder()
					.setDimensions(this.textureWidth, this.textureHeight)
					.setInternalFormat(EDhInternalTextureFormat.RGBA8)
					.setPixelType(EDhPixelType.UNSIGNED_BYTE)
					.setPixelFormat(EDhPixelFormat.RGBA)
					.build();
				
				this.framebuffer.addColorAttachment(0, this.nullableColorTexture.getTextureId());
				if (framebufferOverride != null)
				{
					framebufferOverride.addColorAttachment(0, this.nullableColorTexture.getTextureId());
				}
			}
			else
			{
				this.nullableColorTexture = null;
			}
			
			
			ApiEventInjector.INSTANCE.fireAllEvents(DhApiAfterColorDepthTextureCreatedEvent.class, textureCreatedParam);
		}
		
		//endregion
		
		
		
		//===============//
		// LOD rendering //
		//===============//
		//region
		
		public void renderLodTerrain(SortedArraySet<LodBufferContainer> bufferContainers, RenderParams renderEventParam, boolean opaquePass)
		{
			IDhApiShaderProgram shaderProgram = this.lodShaderProgramThisFrame;
			
			//=======================//
			// debug wireframe setup //
			//=======================//
			
			boolean renderWireframe = Config.Client.Advanced.Debugging.renderWireframe.get();
			if (renderWireframe)
			{
				GL32.glPolygonMode(GL32.GL_FRONT_AND_BACK, GL32.GL_LINE);
				GLMC.disableFaceCulling();
			}
			else
			{
				GL32.glPolygonMode(GL32.GL_FRONT_AND_BACK, GL32.GL_FILL);
				GLMC.enableFaceCulling();
			}
			
			if (!opaquePass)
			{
				GLMC.enableBlend();
				GLMC.enableDepthTest();
				GL32.glBlendEquation(GL32.GL_FUNC_ADD);
				GLMC.glBlendFuncSeparate(GL32.GL_SRC_ALPHA, GL32.GL_ONE_MINUS_SRC_ALPHA, GL32.GL_ONE, GL32.GL_ONE_MINUS_SRC_ALPHA);
			}
			else
			{
				GLMC.disableBlend();
			}
			
			
			
			
			//===========//
			// rendering //
			//===========//
			
			ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeRenderPassEvent.class, renderEventParam);
			
			if (IRIS_ACCESSOR != null)
			{
				// done to fix a bug with Iris where face culling isn't properly set or reverted in the MC state manager
				// which causes Sodium to render some water chunks with their normal inverted
				// https://github.com/IrisShaders/Iris/issues/2582
				// https://github.com/IrisShaders/Iris/blob/1.21.9/common/src/main/java/net/irisshaders/iris/compat/dh/LodRendererEvents.java#L346
				GLMC.enableFaceCulling();
			}
			
			
			if (bufferContainers != null)
			{
				for (int lodIndex = 0; lodIndex < bufferContainers.size(); lodIndex++)
				{
					LodBufferContainer bufferContainer = bufferContainers.get(lodIndex);
					this.setShaderProgramMvmOffset(bufferContainer.minCornerBlockPos, shaderProgram, renderEventParam);
					
					IVertexBufferWrapper[] vertexBuffers = (opaquePass ? bufferContainer.vbos : bufferContainer.vbosTransparent);
					for (int vboIndex = 0; vboIndex < vertexBuffers.length; vboIndex++)
					{
						GLVertexBuffer vbo = (GLVertexBuffer) vertexBuffers[vboIndex];
						if (vbo == null)
						{
							continue;
						}
						
						if (vbo.getVertexCount() == 0)
						{
							continue;
						}
						
						vbo.bind();
						shaderProgram.bindVertexBuffer(vbo.getId());
						GL32.glDrawElements(
							GL32.GL_TRIANGLES,
							(int)(vbo.getVertexCount() * 1.5),
							this.quadIBO.getType(), 0);
						vbo.unbind();
					}
				}
			}
			
			
			
			//=========================//
			// debug wireframe cleanup //
			//=========================//
			
			if (renderWireframe)
			{
				// default back to GL_FILL since all other rendering uses it 
				GL32.glPolygonMode(GL32.GL_FRONT_AND_BACK, GL32.GL_FILL);
				GLMC.enableFaceCulling();
			}
			
		}
		
		/**
		 * the MVM offset is needed so LODs can be rendered anywhere in the MC world
		 * without running into floating point percision loss.
		 */
		private void setShaderProgramMvmOffset(DhBlockPos pos, IDhApiShaderProgram shaderProgram, RenderParams renderEventParam) throws IllegalStateException
		{
			Vec3d camPos = renderEventParam.exactCameraPosition;
			Vec3f modelPos = new Vec3f(
				(float) (pos.getX() - camPos.x),
				(float) (pos.getY() - camPos.y),
				(float) (pos.getZ() - camPos.z));
			
			shaderProgram.bind();
			shaderProgram.setModelOffsetPos(modelPos);
			
			ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeBufferRenderEvent.class, new DhApiBeforeBufferRenderEvent.EventParam(renderEventParam, modelPos));
		}
		
		//endregion
		
		
		
		//===============//
		// API functions //
		//===============//
		//region
		
		/** @return -1 if no frame buffer has been bound yet */
		public int getActiveFramebufferId() { return this.activeFramebufferId; }
		
		/** @return -1 if no texture has been bound yet */
		public int getActiveColorTextureId() { return this.activeColorTextureId; }
		
		/** @return -1 if no texture has been bound yet */
		public int getActiveDepthTextureId() { return this.activeDepthTextureId; }
		
		//endregion
		
		
	}
	
}
