package com.seibel.distanthorizons.forge17.modAccessor;

import com.seibel.distanthorizons.common.wrappers.modAccessor.ICubicChunksCommonAccessor;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.jetbrains.annotations.Nullable;

import com.cardinalstar.cubicchunks.api.CCAPI;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.world.api.ICubeProviderServer;
import com.cardinalstar.cubicchunks.world.cube.ICubeProvider;

public class CubicChunksAccessor implements ICubicChunksCommonAccessor
{
	@Override
	public String getModName() { return "CubicChunks"; }

	@Override
	@Nullable
	public ExtendedBlockStorage[] getCubeStorages(Chunk chunk)
	{
		World world = chunk.worldObj;
		if (world == null
			|| !(world.getChunkProvider() instanceof ICubeProvider))
		{
			// not a cubic world (IE a dummy world CC doesn't initialize)
			return null;
		}

		ExtendedBlockStorage[] storages = new ExtendedBlockStorage[16];
		for (int i = 0; i < 16; i++)
		{
			ICube cube = getCube(world, chunk, i);
			storages[i] = (cube != null) ? cube.getStorage() : null;
		}
		return storages;
	}

	@Nullable
	private static ICube getCube(World world, Chunk chunk, int cubeY)
	{
		if (world instanceof WorldServer)
		{
			// load missing cubes from disk, or fully generate them (terrain, population and lighting)
			// if they don't exist yet
			return CCAPI.getCube(world, chunk.xPosition, cubeY, chunk.zPosition, ICubeProviderServer.Requirement.LIGHT);
		}
		else
		{
			// the client can't load cubes, it only has what the server sent
			return CCAPI.getLoadedCube(world, chunk.xPosition, cubeY, chunk.zPosition);
		}
	}
}
