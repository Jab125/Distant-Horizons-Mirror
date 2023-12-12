/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020-2023 James Seibel
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

package com.seibel.distanthorizons.common.wrappers.block;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;

import net.minecraft.client.Minecraft;

#if MC_VER == MC_1_16_5 || MC_VER == MC_1_17_1
import net.minecraft.core.Registry;
#elif MC_VER == MC_1_18_2 || MC_VER == MC_1_19_2
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.data.BuiltinRegistries;
#else
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
#endif

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
#if MC_VER >= MC_1_18_2
import net.minecraft.world.level.biome.Biomes;
#endif


/** This class wraps the minecraft BlockPos.Mutable (and BlockPos) class */
public class BiomeWrapper implements IBiomeWrapper
{
	private static final Logger LOGGER = LogManager.getLogger();
	
	#if MC_VER < MC_1_18_2
	public static final ConcurrentMap<Biome, BiomeWrapper> WRAPPER_BY_BIOME = new ConcurrentHashMap<>();
	#else
	public static final ConcurrentMap<Holder<Biome>, BiomeWrapper> WRAPPER_BY_BIOME = new ConcurrentHashMap<>();
    #endif
	
	public static final String EMPTY_STRING = "EMPTY";
	public static final BiomeWrapper EMPTY_WRAPPER = new BiomeWrapper(null, null);
	
	/** keep track of broken biomes so we don't log every time */
	private static final HashSet<String> brokenResourceLocationStrings = new HashSet<>();
	
	/** 
	 * Only display this warning once, otherwise the log may be spammed <br> 
	 * This is a known issue when joining Hypixel. 
	 */
	private static boolean emptyStringWarningLogged = false;
	private static boolean emptyLevelSerializeFailLogged = false; 
	
	
	
	// properties //
	
	#if MC_VER < MC_1_18_2
	public final Biome biome;
	#else
	public final Holder<Biome> biome;
    #endif
	
	/** technically final, but since it requires a method call to generate it can't be marked as such */
	private String serialString = null;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	static public IBiomeWrapper getBiomeWrapper(#if MC_VER < MC_1_18_2 Biome #else Holder<Biome> #endif biome, ILevelWrapper levelWrapper)
	{
		if (biome == null)
		{
			return EMPTY_WRAPPER;
		}
		
		
		if (WRAPPER_BY_BIOME.containsKey(biome))
		{
			return WRAPPER_BY_BIOME.get(biome);
		}
		else
		{
			BiomeWrapper newWrapper = new BiomeWrapper(biome, levelWrapper);
			WRAPPER_BY_BIOME.put(biome, newWrapper);
			return newWrapper;
		}
	}
	
	private BiomeWrapper(#if MC_VER < MC_1_18_2 Biome #else Holder<Biome> #endif biome, ILevelWrapper levelWrapper)
	{
		this.biome = biome;
		this.serialString = this.serialize(levelWrapper);
		LOGGER.trace("Created BiomeWrapper ["+this.serialString+"] for ["+biome+"]");
	}
	
	
	
	//=========//
	// methods //
	//=========//
	
	@Override
	public String getName()
	{
		if (this == EMPTY_WRAPPER)
		{
			return EMPTY_STRING;
		}
		
        #if MC_VER < MC_1_18_2
		return biome.toString();
        #else
		return this.biome.unwrapKey().orElse(Biomes.THE_VOID).registry().toString();
        #endif
	}
	
	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		else if (obj == null || this.getClass() != obj.getClass())
		{
			return false;
		}
		
		BiomeWrapper that = (BiomeWrapper) obj;
		// the serialized value is used so we can test the contents instead of the references
		return Objects.equals(this.getSerialString(), that.getSerialString());
	}
	
	@Override
	public int hashCode() { return Objects.hash(this.getSerialString()); }
	
	@Override
	public String getSerialString() { return this.serialString; }
	
	@Override
	public Object getWrappedMcObject() { return this.biome; }
	
	@Override
	public String toString() { return this.getSerialString(); }
	
	
	
	//=======================//
	// serialization methods //
	//=======================//
	
	public String serialize(ILevelWrapper levelWrapper)
	{
		if (this.serialString != null)
		{
			return this.serialString;
		}
		
		
		// we can't generate a serial string if the level is null
		if (levelWrapper == null)
		{
			if (!emptyLevelSerializeFailLogged)
			{
				emptyLevelSerializeFailLogged = true;
				LOGGER.warn("Unable to serialize biome: ["+this.biome+"] because the passed in level wrapper is null. Future errors won't be logged.");
			}
			
			return EMPTY_STRING;
		}
		
		
		
		// generate the serial string //
		
		net.minecraft.core.RegistryAccess registryAccess = Minecraft.getInstance().level.registryAccess();
		
		ResourceLocation resourceLocation;
		#if MC_VER == MC_1_16_5 || MC_VER == MC_1_17_1
		resourceLocation = registryAccess.registryOrThrow(Registry.BIOME_REGISTRY).getKey(this.biome);
		#elif MC_VER == MC_1_18_2 || MC_VER == MC_1_19_2
		resourceLocation = registryAccess.registryOrThrow(Registry.BIOME_REGISTRY).getKey(this.biome.value());
		#else
		resourceLocation = registryAccess.registryOrThrow(Registries.BIOME).getKey(this.biome.value());
		#endif
		
		if (resourceLocation == null)
		{
			String biomeName;
			#if MC_VER == MC_1_16_5 || MC_VER == MC_1_17_1
			biomeName = this.biome.toString();
			#else
			biomeName = this.biome.value().toString();
			#endif
			
			LOGGER.warn("unable to serialize: " + biomeName);
			// shouldn't normally happen, but just in case
			this.serialString = "";
		}
		else
		{
			this.serialString = resourceLocation.getNamespace() + ":" + resourceLocation.getPath();
		}
		
		return this.serialString;
	}
	
	public static IBiomeWrapper deserialize(String resourceLocationString, ILevelWrapper levelWrapper) throws IOException
	{
		if (resourceLocationString.equals(EMPTY_STRING))
		{
			if (!emptyStringWarningLogged)
			{
				emptyStringWarningLogged = true;
				LOGGER.warn("[" + EMPTY_STRING + "] biome string deserialized. This may mean the level was null when a save was attempted, a file saving error, or a biome saving error. Future errors will not be logged.");
			}
			return EMPTY_WRAPPER;
		}
		else if (resourceLocationString.trim().isEmpty() || resourceLocationString.equals(""))
		{
			LOGGER.warn("Null biome string deserialized.");
			return EMPTY_WRAPPER;
		}
		
		
		
		// parse the resource location
		int separatorIndex = resourceLocationString.indexOf(":");
		if (separatorIndex == -1)
		{
			throw new IOException("Unable to parse resource location string: [" + resourceLocationString + "].");
		}
		ResourceLocation resourceLocation = new ResourceLocation(resourceLocationString.substring(0, separatorIndex), resourceLocationString.substring(separatorIndex + 1));
		
		
		try
		{
			Level level = (Level)levelWrapper.getWrappedMcObject();
			net.minecraft.core.RegistryAccess registryAccess = level.registryAccess();
			
			boolean success;
			#if MC_VER == MC_1_16_5 || MC_VER == MC_1_17_1
			Biome biome = registryAccess.registryOrThrow(Registry.BIOME_REGISTRY).get(resourceLocation);
			success = (biome != null);
			#elif MC_VER == MC_1_18_2 || MC_VER == MC_1_19_2
			Biome unwrappedBiome = registryAccess.registryOrThrow(Registry.BIOME_REGISTRY).get(resourceLocation);
			success = (unwrappedBiome != null);
			Holder<Biome> biome = new Holder.Direct<>(unwrappedBiome);
			#else
			Biome unwrappedBiome = registryAccess.registryOrThrow(Registries.BIOME).get(resourceLocation);
			success = (unwrappedBiome != null);
			Holder<Biome> biome = new Holder.Direct<>(unwrappedBiome);
			#endif
			
			
			
			if (!success)
			{
				if (!brokenResourceLocationStrings.contains(resourceLocationString))
				{
					brokenResourceLocationStrings.add(resourceLocationString);
					LOGGER.warn("Unable to deserialize biome from string: [" + resourceLocationString + "]");
				}
				return EMPTY_WRAPPER;
			}
			
			return getBiomeWrapper(biome, levelWrapper);
		}
		catch (Exception e)
		{
			throw new IOException("Failed to deserialize the string [" + resourceLocationString + "] into a BiomeWrapper: " + e.getMessage(), e);
		}
	}
	
}
