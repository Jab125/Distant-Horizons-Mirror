package com.seibel.distanthorizons.fabric.testing;

import com.mojang.logging.LogUtils;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.override.worldGenerator.IDhApiWorldGenerator;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiLevelLoadEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.fabric.FabricServerProxy;
import net.minecraft.server.level.ServerLevel;

public class TestWorldGenBindingEvent extends DhApiLevelLoadEvent
{
	private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
	
	@Override
	public void onLevelLoad(DhApiEventParam<DhApiLevelLoadEvent.EventParam> event)
	{
		LOGGER.info("DH Level: ["+event.value.levelWrapper.getDimensionType()+"] loaded.");
		
		// Note: whenever you use a wrapper method on a new Minecraft version it is recommended that you
		// call wrapper.getClass() to determine which object the API will return before you try casting it.
		ServerLevel level = (ServerLevel) event.value.levelWrapper.getWrappedMcObject();
		
		// override the core DH world generator for this level
		IDhApiWorldGenerator exampleWorldGen = new TestWorldGenerator(level);
		DhApi.worldGenOverrides.registerWorldGeneratorOverride(event.value.levelWrapper, exampleWorldGen);
	}
}
