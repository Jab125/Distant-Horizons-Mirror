package com.seibel.distanthorizons.cleanroom;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.connect.IMixinConnector;
import zone.rong.mixinbooter.service.ModDiscoverer;

public class DistantHorizonsMixinConnector implements IMixinConnector
{
	private static final Logger LOGGER = LogManager.getLogger();
	
	@Override
	public void connect()
	{
		Mixins.addConfiguration("distanthorizons.default.mixin.json");
		if (ModDiscoverer.isModPresent("gregtech"))
		{
			Mixins.addConfiguration("distanthorizons.gregtech.mixin.json");
		}
		if (ModDiscoverer.isModPresent("lumenized"))
		{
			Mixins.addConfiguration("distanthorizons.lumenized.mixin.json");
		}
		if (!isIrisLoaded())
		{
			Mixins.addConfiguration("distanthorizons.iris.mixin.json");
		}
	}
	
	private boolean isIrisLoaded()
	{
		try
		{
			Class.forName("net.irisshaders.iris.api.v0.IrisApi", false, DistantHorizonsMixinConnector.class.getClassLoader());
			LOGGER.info("DistantHorizonsMixins: Iris is detected.");
			return true;
		}
		catch (ClassNotFoundException e)
		{
			LOGGER.info("DistantHorizonsMixins: Iris is not detected.");
			return false;
		}
	}
	
}
