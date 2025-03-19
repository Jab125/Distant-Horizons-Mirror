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

package com.seibel.distanthorizons.common.wrappers.misc;

import com.mojang.blaze3d.platform.NativeImage;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.ILightMapWrapper;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL32;

import java.nio.ByteBuffer;

public class LightMapWrapper implements ILightMapWrapper
{
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	private int textureId = 0;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public LightMapWrapper() { }
	
	
	
	//==================//
	// lightmap syncing //
	//==================//
	
	public void uploadLightmap(NativeImage image)
	{
		int currentTexture = GLMC.getActiveTexture();
		if (this.textureId == 0)
		{
			this.createLightmap(image);
		}
		else
		{
			GLMC.glBindTexture(this.textureId);
		}
		image.upload(0, 0, 0, false);
		
		// getActiveTexture() may return textures that aren't valid and attempting to bind them will
		// throw a GL error in MC 1.21.1
		if (GL32.glIsTexture(currentTexture))
		{
			GLMC.glBindTexture(currentTexture);
		}
	}
	private void createLightmap(NativeImage image)
	{
		this.textureId = GLMC.glGenTextures();
		GLMC.glBindTexture(this.textureId);
		GL32.glTexImage2D(GL32.GL_TEXTURE_2D, 0, image.format().glFormat(), image.getWidth(), image.getHeight(),
				0, image.format().glFormat(), GL32.GL_UNSIGNED_BYTE, (ByteBuffer) null);
	}
	
	public void setLightmapId(int minecraftLightmapTetxureId)
	{
		// just use the MC texture ID
		this.textureId = minecraftLightmapTetxureId;
	}
	
	
	
	//==============//
	// lightmap use //
	//==============//
	
	@Override
	public void bind()
	{
		GLMC.glActiveTexture(GL32.GL_TEXTURE0);
		GLMC.glBindTexture(this.textureId);
	}
	
	@Override
	public void unbind() { GLMC.glBindTexture(0); }
	
}

