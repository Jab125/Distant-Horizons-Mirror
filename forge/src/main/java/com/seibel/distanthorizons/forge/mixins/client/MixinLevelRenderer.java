/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020-2023 James Seibel
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

package com.seibel.distanthorizons.forge.mixins.client;

import com.mojang.blaze3d.vertex.PoseStack;
#if MC_VER < MC_1_19_4
import com.mojang.math.Matrix4f;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
#else
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
#endif
import com.seibel.distanthorizons.common.wrappers.McObjectConverter;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

#if MC_VER < MC_1_17_1
import org.lwjgl.opengl.GL15;
#endif


/**
 * This class is used to mix in DH's rendering code
 * before Minecraft starts rendering blocks.
 * If this wasn't done, and we used Forge's
 * render last event, the LODs would render on top
 * of the normal terrain. <br><br>
 *
 * This is also the mixin for rendering the clouds
 */
@Mixin(LevelRenderer.class)
public class MixinLevelRenderer
{
	@Nullable
	@Shadow //# if MC_VER >= MC_1_20_4 (remap = false) # endif
	private ClientLevel level;
	@Unique
	private static float previousPartialTicks = 0;
	
	// TODO: Is there any reason why this is here? Can it be deleted?
	public MixinLevelRenderer()
	{
		throw new NullPointerException("Null cannot be cast to non-null type.");
	}
	
	#if MC_VER < MC_1_17_1
	@Inject(at = @At("RETURN"), method = "renderSky(Lcom/mojang/blaze3d/vertex/PoseStack;F)V")
	private void renderSky(PoseStack matrixStackIn, float partialTicks, CallbackInfo callback)
	#else
	@Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
	public void renderClouds(PoseStack poseStack, Matrix4f projectionMatrix, float partialTicks, double cameraX, double cameraY, double cameraZ, CallbackInfo ci) 
	#endif
	{
		// FIXME this is only called when clouds are enabled and vanilla render distance is far enough
		//  not having the parital ticks doesn't appear to be critical currently, but might cause weird issues down the line
		
		// get the partial ticks since renderBlockLayer doesn't
		// have access to them
		previousPartialTicks = partialTicks;
	}
	
	
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
    #elif MC_VER < MC_1_20_4
    @Inject(at = @At("HEAD"),
            method = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V",
            cancellable = true)
    private void renderChunkLayer(RenderType renderType, PoseStack modelViewMatrixStack, double camX, double camY, double camZ, Matrix4f projectionMatrix, CallbackInfo callback)
    #else
	@Inject(at = @At("HEAD"),
			method = "renderSectionLayer",
			cancellable = true)
	private void renderChunkLayer(RenderType renderType, PoseStack modelViewMatrixStack, double camX, double camY, double camZ, Matrix4f projectionMatrix, CallbackInfo callback)
    #endif
	{
		// get MC's model view and projection matrices
		#if MC_VER == MC_1_16_5
		// get the matrices from the OpenGL fixed pipeline
		float[] mcProjMatrixRaw = new float[16];
		GL15.glGetFloatv(GL15.GL_PROJECTION_MATRIX, mcProjMatrixRaw);
		Mat4f mcProjectionMatrix = new Mat4f(mcProjMatrixRaw);
		mcProjectionMatrix.transpose();
		
		Mat4f mcModelViewMatrix = McObjectConverter.Convert(matrixStackIn.last().pose());
		
		#else
		// get the matrices directly from MC
		Mat4f mcModelViewMatrix = McObjectConverter.Convert(modelViewMatrixStack.last().pose());
		Mat4f mcProjectionMatrix = McObjectConverter.Convert(projectionMatrix);
		#endif
		
		
		
		// only render before solid blocks
		if (renderType.equals(RenderType.solid()))
		{
			ClientApi.INSTANCE.renderLods(ClientLevelWrapper.getWrapper(this.level), mcModelViewMatrix, mcProjectionMatrix, Minecraft.getInstance().getFrameTime());
		} 
		else if (renderType.equals(RenderType.translucent())) 
		{
			ClientApi.INSTANCE.renderDeferredLods(ClientLevelWrapper.getWrapper(this.level), mcModelViewMatrix, mcProjectionMatrix, Minecraft.getInstance().getFrameTime());
		}
		
		if (Config.Client.Advanced.Debugging.lodOnlyMode.get())
		{
			callback.cancel();
		}
	}
	
}
