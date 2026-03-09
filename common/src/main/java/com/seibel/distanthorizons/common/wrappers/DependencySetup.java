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

package com.seibel.distanthorizons.common.wrappers;

import com.seibel.distanthorizons.api.interfaces.render.IDhApiCustomRenderObjectFactory;
import com.seibel.distanthorizons.common.render.blaze.BlazeDebugWireframeRenderer;
import com.seibel.distanthorizons.common.render.blaze.McLodRenderer;
import com.seibel.distanthorizons.common.render.blaze.postProcessing.BlazeDhFarFadeRenderer;
import com.seibel.distanthorizons.common.render.blaze.postProcessing.BlazeDhFogRenderer;
import com.seibel.distanthorizons.common.render.blaze.postProcessing.BlazeDhSsaoRenderer;
import com.seibel.distanthorizons.common.render.blaze.postProcessing.BlazeVanillaFadeRenderer;
import com.seibel.distanthorizons.common.render.blaze.test.BlazeDhTestRenderer;
import com.seibel.distanthorizons.common.render.nativeGl.generic.GenericRenderObjectFactory;
import com.seibel.distanthorizons.common.wrappers.gui.ClassicConfigGUI;
import com.seibel.distanthorizons.common.wrappers.gui.LangWrapper;
import com.seibel.distanthorizons.common.wrappers.level.KeyedClientLevelManager;
import com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftServerWrapper;
import com.seibel.distanthorizons.core.level.IKeyedClientLevelManager;
import com.seibel.distanthorizons.core.render.renderer.AbstractDebugWireframeRenderer;
import com.seibel.distanthorizons.core.wrapperInterfaces.config.IConfigGui;
import com.seibel.distanthorizons.core.wrapperInterfaces.config.ILangWrapper;
import com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftClientWrapper;
import com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftRenderWrapper;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.wrapperInterfaces.IVersionConstants;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftSharedWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.*;

/**
 * Binds all necessary dependencies, so we
 * can access them in Core. <br>
 * This needs to be called before any Core classes
 * are loaded.
 *
 * @author James Seibel
 * @author Ran
 * @version 12-1-2021
 */
public class DependencySetup
{
	
	public static void createSharedBindings()
	{
		SingletonInjector.INSTANCE.bind(ILangWrapper.class, LangWrapper.INSTANCE);
		SingletonInjector.INSTANCE.bind(IVersionConstants.class, VersionConstants.INSTANCE);
		SingletonInjector.INSTANCE.bind(IWrapperFactory.class, WrapperFactory.INSTANCE);
		SingletonInjector.INSTANCE.bind(IKeyedClientLevelManager.class, KeyedClientLevelManager.INSTANCE);
		SingletonInjector.INSTANCE.bind(IDhApiCustomRenderObjectFactory.class, GenericRenderObjectFactory.INSTANCE);
	}
	
	public static void createServerBindings()
	{ SingletonInjector.INSTANCE.bind(IMinecraftSharedWrapper.class, MinecraftServerWrapper.INSTANCE); }
	
	public static void createClientBindings()
	{
		SingletonInjector.INSTANCE.bind(IMinecraftClientWrapper.class, MinecraftClientWrapper.INSTANCE);
		SingletonInjector.INSTANCE.bind(IMinecraftSharedWrapper.class, MinecraftClientWrapper.INSTANCE);
		SingletonInjector.INSTANCE.bind(IMinecraftRenderWrapper.class, MinecraftRenderWrapper.INSTANCE);
		SingletonInjector.INSTANCE.bind(IConfigGui.class, ClassicConfigGUI.CONFIG_CORE_INTERFACE);
		
		SingletonInjector.INSTANCE.bind(AbstractDebugWireframeRenderer.class, BlazeDebugWireframeRenderer.INSTANCE);
	}
	
	public static void createRenderBindings()
	{
		SingletonInjector.INSTANCE.bind(IMcTestRenderer.class, BlazeDhTestRenderer.INSTANCE);
		SingletonInjector.INSTANCE.bind(IMcVanillaFadeRenderer.class, BlazeVanillaFadeRenderer.INSTANCE);
		SingletonInjector.INSTANCE.bind(IMcLodRenderer.class, McLodRenderer.INSTANCE);
		SingletonInjector.INSTANCE.bind(IMcSsaoRenderer.class, BlazeDhSsaoRenderer.INSTANCE);
		SingletonInjector.INSTANCE.bind(IMcFogRenderer.class, BlazeDhFogRenderer.INSTANCE);
		SingletonInjector.INSTANCE.bind(IMcFarFadeRenderer.class, BlazeDhFarFadeRenderer.INSTANCE);
	}
	
}
