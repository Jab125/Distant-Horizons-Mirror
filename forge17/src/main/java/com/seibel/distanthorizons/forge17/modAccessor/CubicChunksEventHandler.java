package com.seibel.distanthorizons.forge17.modAccessor;

import com.seibel.distanthorizons.common.commonMixins.MixinChunkMapCommon;
import net.minecraft.world.WorldServer;

import com.cardinalstar.cubicchunks.api.event.CubeEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Separate from ForgeServerProxy so the Cubic Chunks classes are only loaded when it is present.
 */
public class CubicChunksEventHandler
{
	/**
	 * Cubic Chunks unloads all of a column's cubes before saving the column,
	 * so the column save can't be used to capture unloaded columns. <br>
	 * Instead save the column when one of its cubes is saved, while the other cubes are still loaded.
	 */
	@SubscribeEvent
	public void cubeSaveEvent(CubeEvent.DataSave event)
	{
		if (!(event.world instanceof WorldServer))
		{
			return;
		}

		MixinChunkMapCommon.onChunkSave((WorldServer) event.world, event.cube.getColumn());
	}
}
