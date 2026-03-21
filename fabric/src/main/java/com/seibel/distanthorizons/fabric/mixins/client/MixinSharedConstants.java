package com.seibel.distanthorizons.fabric.mixins.client;

import com.seibel.distanthorizons.coreapi.ModInfo;
import net.minecraft.SharedConstants;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SharedConstants.class)
public abstract class MixinSharedConstants
{
	@Mutable
	@Shadow @Final public static boolean IS_RUNNING_IN_IDE;
	
	@Inject(method = "<clinit>", at = @At("TAIL"))
	private static void setIsRunningInIde(CallbackInfo ci) 
	{
		// run extra validation for dev builds
		IS_RUNNING_IN_IDE = ModInfo.IS_DEV_BUILD;
	}
	
}
