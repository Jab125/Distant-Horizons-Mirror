package com.seibel.distanthorizons.neoforge.mixins.client;

#if MC_VER == MC_1_20_1 || MC_VER == MC_1_21_1
import com.seibel.distanthorizons.common.wrappers.block.LTColorCache;
import com.seibel.distanthorizons.core.config.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import java.util.function.Consumer;
#else
#endif

import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener
{
	@Shadow
	private ClientLevel level;
	
	@Inject(method = "handleLogin", at = @At("RETURN"))
	void onHandleLoginEnd(CallbackInfo ci)
	{
		ClientApi.INSTANCE.onClientOnlyConnected();
		ClientApi.INSTANCE.clientLevelLoadEvent(ClientLevelWrapper.getWrapper(this.level, true));
	}
	
	#if MC_VER < MC_1_19_4
	@Inject(method = "cleanup", at = @At("HEAD"))
	#else
	@Inject(method = "close", at = @At("HEAD"))
	#endif
	void onCleanupStart(CallbackInfo ci)
	{
		ClientApi.INSTANCE.onClientOnlyDisconnected();
	}
	
	#if MC_VER == MC_1_20_1 || MC_VER == MC_1_21_1
	//init chunk early and unload chunk late, to make sure we don't lost any data
	@Inject(method = "handleBlockEntityData", at = @At("HEAD"))
	private void onReceiveBlockEntity(ClientboundBlockEntityDataPacket packet, CallbackInfo ci) {
		if (Config.Common.LodBuilding.convertLTBlock.get()){
			CompoundTag tag = packet.getTag();
			if (tag != null && "littletiles:tiles".equals(tag.getString("id"))) {
				BlockPos pos = packet.getPos();
				CompoundTag contentTag = tag.getCompound("content");
				LTColorCache.extractLTColor(pos, contentTag);
			}
		}
	}
	
	@Inject(method = "handleLevelChunkWithLight", at = @At("RETURN"))
	private void onChunkLoad(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
		if (Config.Common.LodBuilding.convertLTBlock.get()){
			int chunkX = packet.getX();
			int chunkZ = packet.getZ();
			
			ClientboundLevelChunkPacketData data = packet.getChunkData();
			Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> consumer = data.getBlockEntitiesTagsConsumer(chunkX, chunkZ);
			
			consumer.accept((blockPos, type, tag) -> {
				if (tag != null && "littletiles:tiles".equals(tag.getString("id"))) {
					CompoundTag content = tag.getCompound("content");
					LTColorCache.extractLTColor(blockPos, content);
				}
			});
		}
	}
	#else
	#endif
}
