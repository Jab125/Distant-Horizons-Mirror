package com.seibel.distanthorizons.neoforge.mixins.client;

#if MC_VER == MC_1_20_1 || MC_VER == MC_1_21_1
import com.seibel.distanthorizons.common.wrappers.block.LTColorCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
#else
#endif

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class MixinClientLevel
{
#if MC_VER == MC_1_20_1 || MC_VER == MC_1_21_1
	@Inject(method = "unload", at = @At("HEAD"))
	private void onChunkUnload(LevelChunk chunk, CallbackInfo ci) {
		ChunkPos pos = chunk.getPos();
		LTColorCache.removeChunk(pos);
	}
#else
#endif
}
