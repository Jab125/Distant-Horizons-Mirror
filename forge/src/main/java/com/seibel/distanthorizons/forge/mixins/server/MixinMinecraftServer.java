package com.seibel.distanthorizons.fabric.mixins.server;

import com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftServerWrapper;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public class MixinMinecraftServer
{
	@Shadow
	private int emptyTicks;
	
	@Inject(method = "tickServer", at = @At("HEAD"))
	private void onTickServer(BooleanSupplier hasTimeLeft, CallbackInfo ci)
	{
		if (MinecraftServerWrapper.INSTANCE.preventAutoPause)
		{
			this.emptyTicks = 0;
		}
	}
	
}
