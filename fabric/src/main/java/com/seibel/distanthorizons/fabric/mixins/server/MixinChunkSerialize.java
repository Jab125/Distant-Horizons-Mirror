package com.seibel.distanthorizons.fabric.mixins.server;

#if MC_VER == MC_1_20_1 || MC_VER == MC_1_21_1
import com.seibel.distanthorizons.common.wrappers.block.LTColorCache;
import com.seibel.distanthorizons.core.config.Config;
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

#if MC_VER == MC_1_21_1
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
#else
#endif

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkSerializer.class)
public class MixinChunkSerialize
{
#if MC_VER == MC_1_20_1 || MC_VER == MC_1_21_1
	
	private static final Logger LOGGER = LogManager.getLogger();
	
	private static void extractLTColor(BlockPos pos, CompoundTag contentTag) {
		try {
			CompoundTag tilesTag = contentTag.getCompound("tiles");
			if (!tilesTag.isEmpty()) {
				String firstTileId = tilesTag.getAllKeys().iterator().next();
				LTColorCache.put(pos, firstTileId);
			} else {
				ListTag childrenList = contentTag.getList("children", 10);
				for (int j = 0; j < childrenList.size(); j++) {
					CompoundTag wrapper = childrenList.getCompound(j);
					if (wrapper.contains("tiles", 10)) {
						CompoundTag tiles = wrapper.getCompound("tiles");
						for (String tileId : tiles.getAllKeys()) {
							if (tiles.get(tileId) instanceof ListTag) {
								LTColorCache.put(pos, tileId);
								//LOGGER.warn("Extracted LT tile id from wrapper.tiles: " + tileId);
								return;
							}
						}
					}
				}
				LOGGER.warn("Failed to get any usable LT info at " + pos);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
#else
#endif


#if MV_VER == MC_1_20_1
	@Inject(method = "write", at = @At("HEAD"))
	private static void onChunkWrite(ServerLevel level, ChunkAccess chunk, CallbackInfoReturnable<CompoundTag> cir) {
		if (chunk instanceof LevelChunk levelChunk && Config.Common.LodBuilding.convertLTBlock.get()) {
			levelChunk.getBlockEntities().forEach((pos, be) -> {
				CompoundTag beTag = be.saveWithFullMetadata();
				if ("littletiles:tiles".equals(beTag.getString("id"))) {
					CompoundTag contentTag = beTag.getCompound("content");
					extractLTColor(pos, contentTag);
				}
			});
		}
	}
#else
#endif


#if MV_VER == MC_1_21_1
	@Inject(method = "write", at = @At("HEAD"))
	private static void onChunkWrite(ServerLevel level, ChunkAccess chunk, CallbackInfoReturnable<CompoundTag> cir) {
		if (chunk instanceof LevelChunk levelChunk && Config.Common.LodBuilding.convertLTBlock.get()) {
			levelChunk.getBlockEntities().forEach((pos, be) -> {
				CompoundTag beTag = be.saveWithFullMetadata(level.registryAccess());
				if ("littletiles:tiles".equals(beTag.getString("id"))) {
					CompoundTag contentTag = beTag.getCompound("content");
					extractLTColor(pos, contentTag);
				}
			});
		}
	}
#else
#endif
	
#if MC_VER == MC_1_20_1
	
	@Inject(method = "read", at = @At("RETURN"))
	private static void onChunkRead(ServerLevel level, PoiManager poiManager, ChunkPos chunkPos, CompoundTag tag, CallbackInfoReturnable<ProtoChunk> cir) {
		ChunkAccess chunk = cir.getReturnValue();
		if (chunk instanceof ProtoChunk && Config.Common.LodBuilding.convertLTBlock.get()) {
			ListTag beList = tag.getList("block_entities", 10);
			for (int i = 0; i < beList.size(); i++) {
				CompoundTag beTag = beList.getCompound(i);
				if ("littletiles:tiles".equals(beTag.getString("id"))) {
					BlockPos pos = BlockEntity.getPosFromTag(beTag);
					CompoundTag contentTag = beTag.getCompound("content");
					extractLTColor(pos, contentTag);
				}
			}
		}
	}
#else
#endif
	
	#if MC_VER == MC_1_21_1
	@Inject(method = "read", at = @At("RETURN"))
	private static void onChunkRead(ServerLevel level, PoiManager poiManager, RegionStorageInfo regionStorageInfo, ChunkPos chunkPos, CompoundTag tag, CallbackInfoReturnable<ProtoChunk> cir) {
		ChunkAccess chunk = cir.getReturnValue();
		if (chunk instanceof ProtoChunk && Config.Common.LodBuilding.convertLTBlock.get()) {
			ListTag beList = tag.getList("block_entities", 10);
			for (int i = 0; i < beList.size(); i++) {
				CompoundTag beTag = beList.getCompound(i);
				if ("littletiles:tiles".equals(beTag.getString("id"))) {
					BlockPos pos = BlockEntity.getPosFromTag(beTag);
					CompoundTag contentTag = beTag.getCompound("content");
					extractLTColor(pos, contentTag);
				}
			}
		}
	}
#else
#endif
}