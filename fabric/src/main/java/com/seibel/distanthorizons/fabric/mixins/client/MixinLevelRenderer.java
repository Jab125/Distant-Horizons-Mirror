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

package com.seibel.distanthorizons.fabric.mixins.client;

import com.seibel.distanthorizons.common.render.VanillaTerrainCoverage;

#if MC_VER < MC_1_19_4
import com.seibel.distanthorizons.core.util.math.DhMat4f;
import com.seibel.distanthorizons.core.util.math.DhVec3f;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import org.lwjgl.opengl.GL33;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
#elif MC_VER < MC_1_21_6
import com.seibel.distanthorizons.core.util.math.DhMat4f;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
#elif MC_VER < MC_1_21_9
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.seibel.distanthorizons.core.util.math.DhMat4f;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
#elif MC_VER <= MC_1_21_11
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
#elif MC_VER <= MC_26_2_0
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.renderer.state.level.CameraRenderState;
#elif MC_VER <= MC_26_3_0
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.LevelTargetBundle;
import org.spongepowered.asm.mixin.Final;
import java.util.Optional;
import java.util.OptionalDouble;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IIrisAccessor;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
#endif


import com.mojang.blaze3d.systems.RenderSystem;
import com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftRenderWrapper;
import com.seibel.distanthorizons.common.wrappers.McObjectConverter;
import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.coreapi.ModInfo;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import com.seibel.distanthorizons.core.logging.DhLogger;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;



@Mixin(LevelRenderer.class)
public class MixinLevelRenderer
{
	#if MC_VER > MC_26_2_0
	@com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = "extractSectionDrawGroups",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/OptionsRenderState;chunkSectionFadeInTime:D"))
	private double useCompleteNativeSections(double fadeTime)
	{
		return com.seibel.distanthorizons.common.render.NativeTransparencyRenderer.requiresSharedOit() ? 0.0 : fadeTime;
	}
	#endif

	#if MC_VER <= MC_26_1_2
    @Shadow
    private ClientLevel level;
    #else
	#endif
	
	@Unique
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	
	
	//===========//
	// Pre MC 26 //
	//===========//
	//region
	#if MC_VER <= MC_1_21_11
	
	#if MC_VER < MC_1_17_1
    @Inject(at = @At("HEAD"),
			method = "renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDD)V",
			cancellable = true)
	private void renderChunkLayer(RenderType renderType, PoseStack matrixStackIn, double xIn, double yIn, double zIn, CallbackInfo callback)
	#elif MC_VER < MC_1_19_4
    @Inject(at = @At("HEAD"),
            method = "renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLcom/mojang/math/Matrix4f;)V",
            cancellable = true)
    private void renderChunkLayer(RenderType renderType, PoseStack modelViewMatrixStack, double cameraXBlockPos, double cameraYBlockPos, double cameraZBlockPos, Matrix4f projectionMatrix, CallbackInfo callback)
	#elif MC_VER < MC_1_20_2
    @Inject(at = @At("HEAD"),
            method = "renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V",
            cancellable = true)
    private void renderChunkLayer(RenderType renderType, PoseStack modelViewMatrixStack, double cameraXBlockPos, double cameraYBlockPos, double cameraZBlockPos, Matrix4f projectionMatrix, CallbackInfo callback)
    #elif MC_VER < MC_1_20_6
	@Inject(at = @At("HEAD"),
			method = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V",
			cancellable = true)
	private void renderChunkLayer(RenderType renderType, PoseStack modelViewMatrixStack, double camX, double camY, double camZ, Matrix4f projectionMatrix, CallbackInfo callback)
	#elif MC_VER < MC_1_21_6
	@Inject(at = @At("HEAD"),
			method = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
			cancellable = true)
	private void renderChunkLayer(RenderType renderType, double x, double y, double z, Matrix4f projectionMatrix, Matrix4f frustumMatrix, CallbackInfo callback)
	#elif MC_VER < MC_1_21_9
	@Inject(at = @At("HEAD"), method = "prepareChunkRenders", cancellable = true)
	private void prepareChunkRenders(Matrix4fc projectionMatrix, double d, double e, double f, CallbackInfoReturnable<ChunkSectionsToRender> callback)
	#else
	@Inject(at = @At("HEAD"), method = "renderLevel")
	private void renderLevel(
			GraphicsResourceAllocator resourceAllocator, DeltaTracker deltaTracker,
			boolean renderBlockOutline, Camera camera,
			Matrix4f positionMatrix, Matrix4f projectionMatrix, Matrix4f idkMatrix, GpuBufferSlice gpuBufferSlice,
			Vector4f skyColor, boolean thinFog, CallbackInfo callback)
    #endif
    {
		#if MC_VER <= MC_1_16_5
	    // get the matrices from the OpenGL fixed pipeline
	    float[] mcProjMatrixRaw = new float[16];
	    GL33.glGetFloatv(GL33.GL_PROJECTION_MATRIX, mcProjMatrixRaw);
	    ClientApi.RENDER_STATE.mcProjectionMatrix = new DhMat4f(mcProjMatrixRaw);
	    ClientApi.RENDER_STATE.mcProjectionMatrix.transpose();
	    
	    ClientApi.RENDER_STATE.mcModelViewMatrix = McObjectConverter.convert(matrixStackIn.last().pose());
		
		#elif MC_VER <= MC_1_20_4
		// get the matrices directly from MC
		ClientApi.RENDER_STATE.mcModelViewMatrix = McObjectConverter.convert(modelViewMatrixStack.last().pose());
		ClientApi.RENDER_STATE.mcProjectionMatrix = McObjectConverter.convert(projectionMatrix);
		#elif MC_VER < MC_1_21_9
		// MC combined the model view and projection matricies
	    ClientApi.RENDER_STATE.mcModelViewMatrix = McObjectConverter.convert(projectionMatrix);
	    ClientApi.RENDER_STATE.mcProjectionMatrix = new DhMat4f();
	    ClientApi.RENDER_STATE.mcProjectionMatrix.setIdentity();
		#else
		ClientApi.RENDER_STATE.mcModelViewMatrix = McObjectConverter.convert(positionMatrix);
		ClientApi.RENDER_STATE.mcProjectionMatrix = McObjectConverter.convert(projectionMatrix);
		#endif
		
		ClientApi.RENDER_STATE.partialTickTime = MinecraftRenderWrapper.INSTANCE.getPartialTickTime();
		ClientApi.RENDER_STATE.clientLevelWrapper = ClientLevelWrapper.getWrapperIfDifferent(ClientApi.RENDER_STATE.clientLevelWrapper, this.level);
	    
	    #if MC_VER < MC_1_21_6
	    if (renderType.equals(RenderType.translucent())) 
		{
		    ClientApi.INSTANCE.renderDeferredLodsForShaders();
	    }
		#elif MC_VER < MC_1_21_9
	    // rendering handled via Fabric Api render event
		#else
		// handled here and in MixinChunkSectionsToRender
	    #endif
    }
	
	
	
	#if MC_VER < MC_1_21_9
	// rendering handled via Fabric Api render event
	#else
	@Inject(at = @At("HEAD"), method = "prepareChunkRenders")
	private void prepareChunkRenders(Matrix4fc modelViewMatrix, double d, double e, double f, CallbackInfoReturnable<ChunkSectionsToRender> callback)
	{
		ClientApi.RENDER_STATE.mcModelViewMatrix = McObjectConverter.convert(modelViewMatrix);
		ClientApi.RENDER_STATE.clientLevelWrapper = ClientLevelWrapper.getWrapperIfDifferent(ClientApi.RENDER_STATE.clientLevelWrapper, this.level);
		
		// only crash during development
		if (ModInfo.IS_DEV_BUILD)
		{
			ClientApi.RENDER_STATE.canRenderOrThrow();
		}
		
		ClientApi.INSTANCE.renderLods();
		
	}
	
	#endif
	#endif
	//endregion
	
	
	
	//===========//
	// MC 26.1.2 //
	//===========//
	//region
	
	#if MC_VER <= MC_1_21_11
	// see code above
	#elif MC_VER <= MC_26_1_2
	@Inject(at = @At("HEAD"), method = "prepareChunkRenders")
	private void prepareChunkRenders(final Matrix4fc modelViewMatrix, CallbackInfoReturnable<ChunkSectionsToRender> callback)
	{
		ClientApi.RENDER_STATE.clientLevelWrapper = ClientLevelWrapper.getWrapperIfDifferent(ClientApi.RENDER_STATE.clientLevelWrapper, this.level);
	}
	
	
	@Inject(at = @At("HEAD"), method = "renderLevel")
	public void renderLevel(
		final GraphicsResourceAllocator resourceAllocator, final DeltaTracker deltaTracker,
		final boolean renderBlockOutline, final CameraRenderState camera,
		final Matrix4fc modelViewMatrix, final GpuBufferSlice terrainFog,
		final Vector4f fogColor, final boolean shouldRenderSky,
		final ChunkSectionsToRender chunkSectionsToRender,
		CallbackInfo callback)
	{
		ClientApi.RENDER_STATE.mcModelViewMatrix = McObjectConverter.convert(modelViewMatrix);
		
		ClientApi.RENDER_STATE.partialTickTime = MinecraftRenderWrapper.INSTANCE.getPartialTickTime();
		
	}
	
	#endif
	
	//endregion
	
	
	
	//==============//
	// MC 26.3 plus //
	//==============//
	//region
	
	#if MC_VER <= MC_26_2_0
	// handled in game renderer
	#else
	
	@Unique
	private static boolean firstTimeSetupComplete = false;
	@Unique
	private static IIrisAccessor irisAccessor;
	
	
	@Shadow @Final private LevelTargetBundle targets;
	@Shadow private void executeOit(ChunkSectionsToRender chunks, FeatureRenderDispatcher.PreparedFrame features) { throw new AssertionError(); }

	@Inject(at = @At("HEAD"), method = "prepareTranslucents")
	private void prepareTranslucents(
		CallbackInfo callback)
	
	{
		if (!firstTimeSetupComplete)
		{
			irisAccessor = ModAccessorInjector.INSTANCE.get(IIrisAccessor.class);
			firstTimeSetupComplete = true;
		}
		
		
		
		ClientApi.RENDER_STATE.mcModelViewMatrix = McObjectConverter.convert(RenderSystem.getModelViewStack());
		
		VanillaTerrainCoverage.update();

		// if shaders are active then we need to render in Sodium's render pass
		// if blaze3D is active we need to render here
		if (irisAccessor != null
			&& irisAccessor.isShaderPackInUse())
		{
			return;
		}
		
		ClientApi.RENDER_STATE.canRenderOrThrow();
		ClientApi.INSTANCE.renderLods();
	}
	
	@Inject(at = @At("HEAD"), method = "executeOutline")
	private void executeOutline(
		final FeatureRenderDispatcher.PreparedFrame featureFrame,
		CallbackInfo callback)
	{
		if (irisAccessor != null
			&& irisAccessor.isShaderPackInUse())
		{
			return;
		}
		
		ClientApi.INSTANCE.renderDeferredLodsForShaders();
		ClientApi.INSTANCE.renderFadeTransparent();
	}
	
	@Inject(at = @At("HEAD"), method = "executeOit")
	private void beforeOit(ChunkSectionsToRender chunks, FeatureRenderDispatcher.PreparedFrame features, CallbackInfo callback)
	{
		var main = this.targets.main.get();
		if (com.seibel.distanthorizons.common.render.NativeReferenceRenderer.isNeeded())
		{
			com.seibel.distanthorizons.common.render.NativeReferenceRenderer.begin(main, features);
			com.seibel.distanthorizons.common.render.NativeReferenceRenderer.clearTransparency(this.targets);
			this.executeOit(chunks, features);
			com.seibel.distanthorizons.common.render.NativeReferenceRenderer.end(main);
			com.seibel.distanthorizons.common.render.NativeReferenceRenderer.clearTransparency(this.targets);
		}
		ClientApi.INSTANCE.renderFadeOpaque();
		com.seibel.distanthorizons.common.render.NativeTransparencyRenderer.drawOpaque(main);
	}

	@Inject(at = @At("RETURN"), method = "executeOit")
	private void afterOit(ChunkSectionsToRender chunks, FeatureRenderDispatcher.PreparedFrame features, CallbackInfo callback)
	{
		com.seibel.distanthorizons.common.render.NativeReferenceRenderer.apply(this.targets.main.get(),
			com.seibel.distanthorizons.common.render.NativeTransparencyRenderer.getFrameParams());
	}

	@WrapOperation(method = "executeOit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderOit(Lcom/mojang/renderpearl/api/textures/GpuSampler;Lnet/minecraft/client/renderer/oit/OitStage;Lnet/minecraft/client/renderer/oit/OitRenderPassProvider$Parameters;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Lcom/mojang/renderpearl/api/textures/GpuTextureView;)V"))
	private void renderSharedTransparency(ChunkSectionsToRender chunks, com.mojang.renderpearl.api.textures.GpuSampler sampler,
		net.minecraft.client.renderer.oit.OitStage stage, net.minecraft.client.renderer.oit.OitRenderPassProvider.Parameters targets,
		com.mojang.renderpearl.api.textures.GpuTextureView atlas, com.mojang.renderpearl.api.textures.GpuTextureView lightmap,
		Operation<Void> original)
	{
		if (!com.seibel.distanthorizons.common.render.NativeReferenceRenderer.isRendering())
			original.call(chunks, sampler, stage, targets, atlas, lightmap);
		com.seibel.distanthorizons.common.render.NativeTransparencyRenderer.draw(stage, targets, atlas, lightmap);
	}

	@WrapOperation(method = "lambda$addMainPass$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;executeClassicTransparency(Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
	private void fadeBeforeClassicTransparency(LevelRenderer renderer, ChunkSectionsToRender chunks, FeatureRenderDispatcher.PreparedFrame features, RenderPass pass, Operation<Void> original, @Local LocalRef<RenderPass> passReference)
	{
		if (ClientApi.shouldRenderOpaqueFade())
		{
			pass.close();
			passReference.set(null);
			ClientApi.INSTANCE.renderFadeOpaque();
			var target = this.targets.main.get();
			pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
				() -> "DH vanilla transparency", target.getColorTextureView(), Optional.empty(),
				target.getDepthTextureView(), OptionalDouble.empty());
			// The enclosing render scope closes the replacement pass.
			passReference.set(pass);
			RenderSystem.bindDefaultUniforms(pass);
		}
		original.call(renderer, chunks, features, pass);
	}
	
	#endif
	
	//endregion
	
	
	
}
