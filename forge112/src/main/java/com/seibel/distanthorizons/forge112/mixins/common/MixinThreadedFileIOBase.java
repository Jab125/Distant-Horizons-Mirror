package com.seibel.distanthorizons.forge112.mixins.common;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.storage.ThreadedFileIOBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ThreadedFileIOBase.class)
public class MixinThreadedFileIOBase
{
	@WrapOperation(method = "processQueue", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;sleep(J)V", remap = false))
	private void reduceSleep(long millis, Operation<Void> original) throws InterruptedException
	{
		// 0ms between chunks, 5ms when idle
		original.call(millis == 25L ? 5L : 0L);
	}
}
