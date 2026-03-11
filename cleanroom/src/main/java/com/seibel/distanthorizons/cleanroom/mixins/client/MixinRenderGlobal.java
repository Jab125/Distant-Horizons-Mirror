package com.seibel.distanthorizons.cleanroom.mixins.client;

import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderGlobal.class)
public class MixinRenderGlobal
{
	@Shadow private WorldClient world;

	@Inject(method = "renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I", at = @At("HEAD"))
	private void renderChunkLayer(BlockRenderLayer blockLayerIn, double partialTicks, int pass, Entity entityIn, CallbackInfoReturnable<Integer> cir)
	{
		if (blockLayerIn == BlockRenderLayer.SOLID)
		{
			float[] mcProjMatrixRaw = new float[16];
			GL11.glGetFloatv(GL11.GL_PROJECTION_MATRIX, mcProjMatrixRaw);
			ClientApi.RENDER_STATE.mcProjectionMatrix = new Mat4f(mcProjMatrixRaw);
			ClientApi.RENDER_STATE.mcProjectionMatrix.transpose();
			
			float[] mcModelViewRaw = new float[16];
			GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX, mcModelViewRaw);
			ClientApi.RENDER_STATE.mcModelViewMatrix = new Mat4f(mcModelViewRaw);
			ClientApi.RENDER_STATE.mcModelViewMatrix.transpose();
			
			ClientApi.RENDER_STATE.partialTickTime = (float) partialTicks;
			ClientApi.RENDER_STATE.clientLevelWrapper = ClientLevelWrapper.getWrapperIfDifferent(ClientApi.RENDER_STATE.clientLevelWrapper, this.world);
			
			int blendSrc = GL11.glGetInteger(GL11.GL_BLEND_SRC);
			int blendDst = GL11.glGetInteger(GL11.GL_BLEND_DST);
			int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
			
			ClientApi.INSTANCE.renderLods();
			
			GL30.glBindVertexArray(0);
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
			GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
			GL20.glUseProgram(0);
			
			//Restore vanilla states
			GlStateManager.depthFunc(GL11.GL_LEQUAL);
			GlStateManager.bindTexture(boundTexture);
			GlStateManager.tryBlendFuncSeparate(blendSrc, blendDst, GL11.GL_ONE, GL11.GL_ZERO);

		}
	}
}