package com.seibel.distanthorizons.forge.mixins.server;

#if MC_VER == MC_1_20_1
import com.seibel.distanthorizons.common.wrappers.block.LTColorCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
#else
#endif

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
public class MixinLevelChunk {
	#if MC_VER == MC_1_20_1
	@Inject(method = "setLoaded", at = @At("HEAD"))
	private void onChunkUnload(boolean loaded, CallbackInfo ci) {
		if (!loaded) {
			try{
				ChunkPos chunkPos = ((LevelChunk)(Object)this).getPos();
				LTColorCache.removeChunk(chunkPos);
			}catch (Exception e){
				e.printStackTrace();
			}
		}
	}
	#else
	#endif
}

