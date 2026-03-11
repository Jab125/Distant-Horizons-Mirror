package com.seibel.distanthorizons.cleanroom.mixins.common;

import com.seibel.distanthorizons.common.commonMixins.MixinChunkMapCommon;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkProviderServer.class)
public class MixinChunkProviderServer
{
	@Shadow 
	@Final 
	public WorldServer world;
	
	@Inject(method = "saveChunkData", at = @At("RETURN"))
	private void onSaveChunkData(Chunk chunk, CallbackInfo ci)
	{
		MixinChunkMapCommon.onChunkSave(world, chunk);
	}
}
