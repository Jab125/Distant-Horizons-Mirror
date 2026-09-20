package com.seibel.distanthorizons.cleanroom.mixins.client;

import com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftRenderWrapper;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import gregtech.client.utils.BloomEffectUtil;
import gregtech.client.utils.RenderUtil;
import net.minecraft.client.shader.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = BloomEffectUtil.class, remap = false)
public class MixinBloomEffectUtil
{
	@Redirect(method = "renderBloomInternal", at = @At(value = "INVOKE", target = "Lgregtech/client/utils/RenderUtil;hookDepthBuffer(Lnet/minecraft/client/shader/Framebuffer;I)V"))
	private static void distantHorizons$hookDepthTextureInstead(Framebuffer fbo, int depthBuffer)
	{
		int dhDepthTex = MinecraftRenderWrapper.INSTANCE.getGlDepthTextureId();
		
		if (dhDepthTex != -1)
		{
			RenderUtil.hookDepthTexture(fbo, dhDepthTex);
		}
		else
		{
			RenderUtil.hookDepthBuffer(fbo, depthBuffer);
		}
	}
	
}
