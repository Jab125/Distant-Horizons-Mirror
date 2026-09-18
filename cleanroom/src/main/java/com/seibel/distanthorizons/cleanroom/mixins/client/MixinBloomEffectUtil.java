package com.seibel.distanthorizons.cleanroom.mixins.client;

import com.seibel.distanthorizons.common.commonMixins.IFramebufferDepthTexture;
import gregtech.client.utils.BloomEffectUtil;
import gregtech.client.utils.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = BloomEffectUtil.class, remap = false)
public class MixinBloomEffectUtil
{
	@Redirect(method = "renderBloomInternal", at = @At(value = "INVOKE", target = "Lgregtech/client/utils/RenderUtil;hookDepthBuffer(Lnet/minecraft/client/shader/Framebuffer;I)V"))
	private static void distantHorizons$hookDepthTextureInstead(Framebuffer fbo, int depthBuffer)
	{
		int dhDepthTex = ((IFramebufferDepthTexture) Minecraft.getMinecraft().getFramebuffer()).distantHorizons$getDistantHorizonsDepthTexture();
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
