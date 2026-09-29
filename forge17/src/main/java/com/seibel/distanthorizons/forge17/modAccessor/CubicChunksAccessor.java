package com.seibel.distanthorizons.forge17.modAccessor;

import com.seibel.distanthorizons.common.wrappers.modAccessor.ICubicChunksCommonAccessor;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.jetbrains.annotations.Nullable;

import com.cardinalstar.cubicchunks.api.CCAPI;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.server.chunkio.CubeInitLevel;
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
		if (!isCubicWorld(world))
		{
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

	@Override
	public boolean areAllCubesReady(Chunk chunk)
	{
		if (!isCubicWorld(chunk.worldObj))
		{
			return true;
		}

		for (int i = 0; i < 16; i++)
		{
			if (!isCubeReady(((IColumn) chunk).getLoadedCube(i)))
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean isCubicWorld(@Nullable World world)
	{
		// dummy worlds aren't initialized by CC
		return world != null
			&& world.getChunkProvider() instanceof ICubeProvider;
	}

	private static boolean isCubeReady(@Nullable ICube cube) { return cube != null && cube.isInitializedToLevel(CubeInitLevel.Lit); }

	@Nullable
	private static ICube getCube(World world, Chunk chunk, int cubeY)
	{
		// the column still holds cubes while they are being unloaded and saved,
		// unlike the world's cube cache, which would try to load them again
		ICube loadedCube = ((IColumn) chunk).getLoadedCube(cubeY);

		if (world instanceof WorldServer)
		{
			if (isCubeReady(loadedCube))
			{
				return loadedCube;
			}

			// load missing cubes from disk, or fully generate them (terrain, population and lighting)
			// if they don't exist yet
			return CCAPI.getCube(world, chunk.xPosition, cubeY, chunk.zPosition, ICubeProviderServer.Requirement.LIGHT);
		}
		else
		{
			// the client can't load cubes, it only has what the server sent
			return loadedCube;
		}
	}
}
