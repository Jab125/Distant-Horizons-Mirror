package com.seibel.distanthorizons.forge.mixins.server;

#if MC_VER == MC_1_20_1
import com.seibel.distanthorizons.common.wrappers.block.LTColorCache;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
#else
#endif

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;


@Mixin(ChunkSerializer.class)
public class MixinChunkSerialize
{
	#if MC_VER == MC_1_20_1
	
	private static final Logger LOGGER = LogManager.getLogger();
	@Inject(method = "write", at = @At("HEAD"))
	private static void onChunkWrite(ServerLevel level, ChunkAccess chunk, CallbackInfoReturnable<CompoundTag> cir) {
		if (chunk instanceof LevelChunk levelChunk) {
			levelChunk.getBlockEntities().forEach((pos, be) -> {
				CompoundTag beTag = be.saveWithFullMetadata();
				String id = beTag.getString("id");
				if (id.equals("littletiles:tiles")) {
					try{
						CompoundTag contentTag = beTag.getCompound("content");
						CompoundTag tilesTag = contentTag.getCompound("tiles");
						Set<String> tileKeys = tilesTag.getAllKeys();
						if (!tileKeys.isEmpty())
						{
							String firstTileId = tileKeys.iterator().next();
							LTColorCache.put(pos, firstTileId);
						}
					}catch (Exception e){
						e.printStackTrace();
					}
				}
			});
		}
	}
	
	@Inject(method = "read", at = @At("RETURN"))
	private static void onChunkRead(ServerLevel level, PoiManager poiManager, ChunkPos chunkPos, CompoundTag tag, CallbackInfoReturnable<ProtoChunk> cir) {
		
		ChunkAccess chunk = cir.getReturnValue();
		
		if (chunk instanceof ProtoChunk) {
			// In main ProtoChunk, BlockEntities have not yet been deserialized into objects;
			// need to read from raw NBT data.
			ListTag beList = tag.getList("block_entities", 10);// 10: Each entry is a CompoundTag
			
			for (int i = 0; i < beList.size(); i++) {
				CompoundTag beTag = beList.getCompound(i);
				String id = beTag.getString("id");
				BlockPos pos = BlockEntity.getPosFromTag(beTag);
				if (id.equals("littletiles:tiles")) {
					try{
						CompoundTag contentTag = beTag.getCompound("content");
						CompoundTag tilesTag = contentTag.getCompound("tiles");
						Set<String> tileKeys = tilesTag.getAllKeys();
						if (!tileKeys.isEmpty())
						{
							String firstTileId = tileKeys.iterator().next();
							LTColorCache.put(pos, firstTileId);
							LOGGER.warn("LTColorCache.getCacheSize()="+LTColorCache.getCacheSize());
						}
					}catch (Exception e){
						e.printStackTrace();
					}
				}
			}
		}else if (chunk instanceof LevelChunk levelChunk) {
			// Under normal circumstances, this branch shouldn't be reached
		}
	}
	#else
	#endif
}