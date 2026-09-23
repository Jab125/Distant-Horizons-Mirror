/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.forge112;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.jetbrains.annotations.Nullable;
import zone.rong.mixinbooter.IEarlyMixinLoader;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@IFMLLoadingPlugin.Name("DistantHorizons")
@IFMLLoadingPlugin.MCVersion("1.12.2")
public class DistantHorizonsLoadingPlugin implements IFMLLoadingPlugin, IEarlyMixinLoader
{
	static
	{
		isCleanroomEnviroment();
	}
	
	// We must do it this way since forge refuses to load our mod -> classic dependency doesn't work
	private static void isCleanroomEnviroment()
	{
		// If it's java 8 we are running forge, Cleanroom has hard dependency on java 25
		if (Integer.parseInt(System.getProperty("java.version").split("\\.")[1]) == 8)
		{
			final String message = "\n"
				+"=================================================================\n"
				+"=================================================================\n"
				+"=================================================================\n"
				+"\n"
				+ " Distant Horizons (Cleanroom build) cannot run here.\n"
				+ " Fix: Switch this instance to Cleanroom if you want to play with DH (or wait for Forge release that is WIP).\n"
				+ " https://cleanroommc.com/\n"
				+ "\n"
				+ "=================================================================\n"
				+ "=================================================================\n"
				+ "=================================================================\n";
			
			System.err.println(message);
			
			// Print at end of log so user sees it
			CleanroomErrorReporter.printOnShutdown(message);
			
			throw new RuntimeException("Distant Horizons: wrong loader/Java version, see message above.");
		}
	}
	
	@Override
	public List<String> getMixinConfigs()
	{
		return Collections.singletonList("DistantHorizons.forge.mixins.json");
	}
	
	@Override
	public String[] getASMTransformerClass()
	{
		return new String[0];
	}
	
	@Override
	public String getModContainerClass()
	{
		return null;
	}
	
	@Override
	public @Nullable String getSetupClass()
	{
		return null;
	}
	
	@Override
	public void injectData(Map<String, Object> map)
	{
		
	}
	
	@Override
	public String getAccessTransformerClass()
	{
		return null;
	}
	
}
