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

#if MC_VER > MC_26_2_0
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.seibel.distanthorizons.common.render.NativeTransparencyRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = DefaultChunkRenderer.class, remap = false)
public class MixinSodiumUniforms
{
	@ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Ljava/lang/System;currentTimeMillis()J"), remap = false)
	private long useCompleteNativeSections(long time)
	{
		if (!NativeTransparencyRenderer.requiresSharedOit())
		{
			return time;
		}
		return time + (long) Math.ceil(Minecraft.getInstance().options.chunkSectionFadeInTime().get() * 1000.0) + 1L;
	}
}
#else
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Entity.class)
public class MixinSodiumUniforms { }
#endif
