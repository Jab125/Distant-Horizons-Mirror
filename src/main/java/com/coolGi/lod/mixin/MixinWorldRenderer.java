/*
 *    This file is part of the Distant Horizon mod (formerly the LOD Mod),
 *    licensed under the GNU GPL v3 License.
 *
 *    Copyright (C) 2020  James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.coolGi.lod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.coolGi.lod.LodMain;
import com.coolGi.lod.config.LodConfig;

import net.minecraft.client.renderer.RenderType;

/**
 * This class is used to mix in my rendering code
 * before Minecraft starts rendering blocks.
 * If this wasn't done, and we used Forge's
 * render last event, the LODs would render on top
 * of the normal terrain.
 * 
 * @author James Seibel
 * @version 9-19-2021
 */
@Mixin(LevelRenderer.class)
public class MixinWorldRenderer
{
	private static float previousPartialTicks = 0;

	@Inject(at = @At("RETURN"), method = "renderSky", cancellable = false)
	private void renderSky(PoseStack poseStack, float f, CallbackInfo ci) {
		// get the partial ticks since renderBlockLayer doesn't
		// have access to them
		previousPartialTicks = f;
	}

	@Inject(at = @At("HEAD"), method = "renderChunkLayer", cancellable = false)
	private void renderLayer(RenderType renderType, PoseStack poseStack, double d, double e, double f, CallbackInfo ci)
	{
		// only render if LODs are enabled and
		// only render before solid blocks
		if (LodConfig.CLIENT.advancedModOptions.debugging.drawLods.get() && renderType.equals(RenderType.solid()))
			LodMain.client_proxy.renderLods(poseStack, previousPartialTicks);
	}
}
