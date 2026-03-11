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

#if MC_VER > MC_1_12_2
import com.mojang.blaze3d.platform.NativeImage;
#endif
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.ILightMapWrapper;
import com.seibel.distanthorizons.core.logging.DhLogger;
import org.lwjgl.opengl.GL32;

#if MC_VER < MC_1_21_3
import java.nio.ByteBuffer;
#else
#endif

public class LightMapWrapper implements ILightMapWrapper
{
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	private int textureId = 0;
	#if MC_VER <= MC_1_12_2
	private int lastTextureId = 0;
	private int lastTextureUnit = GL32.GL_TEXTURE0;
	#endif
	
	
	//==============//
	// constructors //
	//==============//
	//region
	
	public LightMapWrapper() { }
	
	//endregion
	
	
	
	//==================//
	// lightmap syncing //
	//==================//
	//region
	
	#if MC_VER > MC_1_12_2
	public void uploadLightmap(NativeImage image)
	{
		#if MC_VER < MC_1_21_3
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
		#else 
		throw new UnsupportedOperationException("setLightmapId should be used for MC versions after 1.21.3");
		#endif
	}
	private void createLightmap(NativeImage image)
	{
		#if MC_VER < MC_1_21_3
		this.textureId = GLMC.glGenTextures();
		GLMC.glBindTexture(this.textureId);
		GL32.glTexImage2D(GL32.GL_TEXTURE_2D, 0, image.format().glFormat(), image.getWidth(), image.getHeight(),
				0, image.format().glFormat(), GL32.GL_UNSIGNED_BYTE, (ByteBuffer) null);
		#else
		throw new UnsupportedOperationException("setLightmapId should be used for MC versions after 1.21.3");
		#endif
	}
	#endif
	
	public void setLightmapId(int minecraftLightmapTextureId)
	{
		// just use the MC texture ID
		this.textureId = minecraftLightmapTextureId;
	}
	
	//endregion
	
	
	
	//==============//
	// lightmap use //
	//==============//
	//region
	
	@Override
	public void bind()
	{
		#if MC_VER <= MC_1_12_2
		//1.12.2 If we don't bind MC texture back vanilla rendering will break
		lastTextureUnit = GL32.glGetInteger(GL32.GL_ACTIVE_TEXTURE);
		GLMC.glActiveTexture(GL32.GL_TEXTURE0 + ILightMapWrapper.BOUND_INDEX);
		lastTextureId = GL32.glGetInteger(GL32.GL_TEXTURE_BINDING_2D);
		#else
		GLMC.glActiveTexture(GL32.GL_TEXTURE0 + ILightMapWrapper.BOUND_INDEX);
		#endif
		GLMC.glBindTexture(this.textureId);
	}
	
	@Override
	public void unbind()
	{
		#if MC_VER <= MC_1_12_2
		GLMC.glBindTexture(lastTextureId);
		GLMC.glActiveTexture(lastTextureUnit);
		#else
		GLMC.glBindTexture(0); 
		#endif
	}
	
	//endregion
	
	
	
}

