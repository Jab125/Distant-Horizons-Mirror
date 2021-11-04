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

package com.seibel.lod;


import com.seibel.lod.proxy.ClientProxy;
import net.fabricmc.api.ClientModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Initialize and setup the Mod.
 * <br>
 * If you are looking for the real start of the mod
 * check out the ClientProxy.
 *
 * @author coolGi2007
 * @author James Seibel
 * @version 10-24-2021
 */
//@Mod(ModInfo.ID)
public class LodMain implements ClientModInitializer
{
	public static final Logger LOGGER = LogManager.getLogger(ModInfo.MODID);

	public static LodMain instance;
	
	public static ClientProxy client_proxy;
	

	// For initialising config on forge
//	private void init(final FMLCommonSetupEvent event)
//	{
//		ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, LodConfig.CLIENT_SPEC);
//	}
	
	
	public LodMain()
	{
		// Register the methods
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::init);
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientStart);
		
		// Register ourselves for server and other game events we are interested in
		MinecraftForge.EVENT_BUS.register(this);
	}

	// Client start for forge
//	private void onClientStart(final FMLClientSetupEvent event)
//	{
//		client_proxy = new ClientProxy();
//		MinecraftForge.EVENT_BUS.register(client_proxy);
//	}
	
	
	// No server mod
//	@SubscribeEvent
//	public void onServerStarting(FMLServerStartingEvent event)
//	{
//		// this is called when the server starts
//	}

//	@Override
//	public void onInitialize() {
//		LodMain.LOGGER.info(ModInfo.READABLE_NAME + ", Version: " + ModInfo.RELEASETYPE + " " + ModInfo.VERSION);
//	}

	@Override
	public void onInitializeClient() {
		LodMain.LOGGER.info(ModInfo.READABLE_NAME + ", Version: " + ModInfo.RELEASETYPE + " " + ModInfo.VERSION);

		client_proxy = new ClientProxy();
		MinecraftForge.EVENT_BUS.register(client_proxy);
	}
}
