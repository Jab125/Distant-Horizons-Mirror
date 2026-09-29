package com.seibel.distanthorizons.forge112.mixins.client.lumenized;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftRenderWrapper;
import gregtech.client.utils.BloomEffectUtil;
import gregtech.client.utils.RenderUtil;
import net.minecraft.client.shader.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//Intellisense will show errors since both gregtech and lumenized share same import path
@Mixin(value = BloomEffectUtil.class, remap = false)
public class MixinBloomEffectUtil
{
	@WrapOperation(method = "renderBloomBlockLayer", at = @At(value = "INVOKE", target = "Lgregtech/client/utils/RenderUtil;hookDepthBuffer(Lnet/minecraft/client/shader/Framebuffer;I)V"))
	private static void distantHorizons$hookDepthTextureInstead(Framebuffer fbo, int depthBuffer, Operation<Void> original)
	{
		int dhDepthTex = MinecraftRenderWrapper.INSTANCE.getGlDepthTextureId();
		
		if (dhDepthTex != -1)
		{
			RenderUtil.hookDepthTexture(fbo, dhDepthTex);
		}
		else
		{
			original.call(fbo, depthBuffer);
		}
	}
	
}
