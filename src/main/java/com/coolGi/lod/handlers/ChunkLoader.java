/*
 *    This file is part of the Distant Horizon mod (formerly the LOD Mod),
 *    licensed under the GNU GPL v3 License.
 *
 *    Copyright (C) 2020  James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.coolGi.lod.handlers;

import java.io.File;

import com.coolGi.lod.util.LodUtil;
import com.coolGi.lod.wrappers.MinecraftWrapper;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.server.ServerWorld;

/**
 * 
 * @author ??
 * @version ??
 */
public class ChunkLoader
{
	public static ProtoChunk getChunkFromFile(ChunkPos pos){
		
		ClientLevel clientWorld = MinecraftWrapper.INSTANCE.getClientWorld();
		if (clientWorld == null)
			return null;
		ServerLevel serverWorld = LodUtil.getServerWorldFromDimension(clientWorld.dimensionType());
		try
		{
			File file = new File(serverWorld.getChunkSource().getDataStorage().dataFolder.getParent() + File.separatorChar + "region", "r." + (pos.x >> 5) + "." + (pos.z >> 5) + ".mca");
			if(!file.exists())
				return  null;
			ProtoChunk loadedChunk = ChunkSerializer.read(
					serverWorld,
					serverWorld.getStructureManager(),
					serverWorld.getPoiManager(),
					pos,
					serverWorld.getChunkSource().chunkMap.read(pos)
			);
			boolean emptyChunk = true;
			for(int i = 0; i < 16; i++){
				for(int j = 0; j < 16; j++){
					emptyChunk &= loadedChunk.isYSpaceEmpty(i,j);
				}
			}
			if(emptyChunk)
				return null;
			else
				return loadedChunk;
		}
		catch (Exception e)
		{
			return null;
		}
	}
}
