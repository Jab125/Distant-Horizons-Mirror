package com.seibel.distanthorizons.common.wrappers.block;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPosMutable;
import com.seibel.distanthorizons.core.util.ColorUtil;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;

import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import com.seibel.distanthorizons.core.logging.DhLogger;

#if MC_VER >= MC_1_18_2
import net.minecraft.core.Holder;
#endif


public abstract class AbstractDhTintGetter implements BlockAndTintGetter
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	
	protected final BiomeWrapper biomeWrapper;
	
	protected final int smoothingRadiusInBlocks;
	protected final FullDataSourceV2 fullDataSource;
	protected final IClientLevelWrapper clientLevelWrapper;
	
	#if MC_VER < MC_1_18_2
	public static final ConcurrentMap<String, Biome> BIOME_BY_RESOURCE_STRING = new ConcurrentHashMap<>();
	#else
	public static final ConcurrentMap<String, Holder<Biome>> BIOME_BY_RESOURCE_STRING = new ConcurrentHashMap<>();
    #endif
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public AbstractDhTintGetter(BiomeWrapper biomeWrapper, FullDataSourceV2 fullDataSource, IClientLevelWrapper clientLevelWrapper)
	{
		this.biomeWrapper = biomeWrapper;
		this.fullDataSource = fullDataSource;
		this.clientLevelWrapper = clientLevelWrapper;
		this.smoothingRadiusInBlocks = Config.Client.Advanced.Graphics.Quality.lodBiomeBlending.get();
	}
	
	
	
	//================//
	// shared methods //
	//================//
	
	@Override
	public int getBlockTint(BlockPos blockPos, ColorResolver colorResolver)
	{
		// determine how wide this data source is so we can determine
		// if blending should be used
		byte dataSourceDetailLevel = DhSectionPos.getDetailLevel(this.fullDataSource.getPos());
		// convert from section detail level to absolute detail level
		dataSourceDetailLevel = (byte)(dataSourceDetailLevel - DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL);
		int dataSourceLodWidthInBlocks = DhSectionPos.getDetailLevelWidthInBlocks(dataSourceDetailLevel);
		
		// don't do any smoothing if smoothing is disabled or if the LOD
		// is to large for block-based smoothing to show up
		if (this.smoothingRadiusInBlocks == 0
			|| dataSourceLodWidthInBlocks > this.smoothingRadiusInBlocks)
		{
			return colorResolver.getColor(unwrapClientBiome(this.biomeWrapper.biome, this.clientLevelWrapper), blockPos.getX(), blockPos.getZ());
		}
		
		
		// use a rolling average to calculate the color
		int dataPointCount = 0;
		int rollingRed = 0;
		int rollingGreen = 0;
		int rollingBlue = 0;
		
		int xMin = blockPos.getX() - this.smoothingRadiusInBlocks;
		int xMax = blockPos.getX() + this.smoothingRadiusInBlocks;
		
		int zMin = blockPos.getZ() - this.smoothingRadiusInBlocks;
		int zMax = blockPos.getZ() + this.smoothingRadiusInBlocks;
		
		DhBlockPosMutable mutableBlockPos = new DhBlockPosMutable(0, blockPos.getY(), 0);
		for (int x = xMin; x < xMax; x++)
		{
			for (int z = zMin; z < zMax; z++)
			{
				mutableBlockPos.setX(x);
				mutableBlockPos.setZ(z);
				
				// this can return the same position/datapoint for larger LODs duplicating work,
				// however for small smoothing ranges that isn't a big deal and for large LODs
				// we ignore smoothing anyway
				long dataPoint = this.fullDataSource.getAtBlockPos(mutableBlockPos);
				if (dataPoint == FullDataPointUtil.EMPTY_DATA_POINT)
				{
					continue;
				}
				
				// get the color for this nearby position
				int id = FullDataPointUtil.getId(dataPoint);
				BiomeWrapper biomeWrapper = (BiomeWrapper) this.fullDataSource.mapping.getBiomeWrapper(id);
				int color = colorResolver.getColor(unwrapClientBiome(biomeWrapper.biome, this.clientLevelWrapper), mutableBlockPos.getX(), mutableBlockPos.getZ());
				
				
				// rolling average
				rollingRed += ColorUtil.getRed(color);
				rollingGreen += ColorUtil.getGreen(color);
				rollingBlue += ColorUtil.getBlue(color);
				
				dataPointCount++;
			}
		}
		
		
		// if no data was present (rarely possible)
		// just use the default center's color
		if (dataPointCount == 0)
		{
			return colorResolver.getColor(unwrapClientBiome(this.biomeWrapper.biome, this.clientLevelWrapper), blockPos.getX(), blockPos.getZ());
		}
		
		int colorInt = ColorUtil.argbToInt(
				255, // blending often ignores alpha, having it always 255 prevents multiplication issues later
				rollingRed / dataPointCount,
				rollingGreen / dataPointCount,
				rollingBlue / dataPointCount);
		return colorInt;
	}
	
	protected static Biome unwrapClientBiome(#if MC_VER >= MC_1_18_2 Holder<Biome> #else Biome #endif biome, IClientLevelWrapper clientLevelWrapper)
	{
		BiomeWrapper biomeWrapper = (BiomeWrapper)BiomeWrapper.getBiomeWrapper(biome, clientLevelWrapper);
		
		String biomeString = biomeWrapper.getSerialString();
		if (biomeString == null
				|| biomeString.isEmpty()
				|| biomeString.equals(BiomeWrapper.EMPTY_BIOME_STRING))
		{
			// default to "plains" for empty/invalid biomes
			biomeString = "minecraft:plains";
		}
		
		return unwrapBiome(getClientBiome(biomeString));
	}
	
	protected static Biome unwrapBiome(#if MC_VER >= MC_1_18_2 Holder<Biome> #else Biome #endif biome)
	{
		#if MC_VER >= MC_1_18_2
		return biome.value();
		#else
		return biome;
		#endif
	}
	
	/**
	 * <p>Previously, this class might have immediately unwrapped the Holder like this:</p>
	 * <pre>{@code
	 * // Inside constructor (OLD WAY - PROBLEMATIC):
	 * Holder<Biome> biomeHolder = getTheHolderFromSomewhere();
	 * this.biome = biomeHolder.value(); // <-- PROBLEM HERE
	 * }</pre>
	 *
	 * <p>This approach is problematic because the {@link net.minecraft.core.Holder} system,
	 * particularly {@code Holder.Reference}, is designed for <strong>late binding</strong>. Here's why storing
	 * the Holder itself is now necessary:</p>
	 * <ol>
	 *   <li>A {@code Holder.Reference<Biome>} might be created initially just with a
	 *       {@link net.minecraft.resources.ResourceKey} (like {@code minecraft:plains}), but its actual
	 *       {@link net.minecraft.core.Holder#value() value()} (the {@code Biome} object itself) might be {@code null}
	 *       at construction time.</li>
	 *   <li>Later, during game loading, registry population, or potentially due to modifications by other mods
	 *       (e.g., Polytone), the system calls internal binding methods (like {@code bindValue(Biome)})
	 *       on the {@code Holder} instance. This sets or <strong>updates</strong> the internal reference to the
	 *       actual {@code Biome} object.</li>
	 *   <li>Crucially, the binding process might assign a completely <strong>new</strong> {@code Biome} object
	 *       instance to the {@code Holder} reference, replacing any previous one.</li>
	 * </ol>
	 *
	 * <p>If we unwrapped the {@code Holder} using {@code .value()} within the constructor (the old way),
	 * our class's internal {@code biome} field would permanently store a reference to whatever {@code Biome}
	 * object the {@code Holder} pointed to *at that exact moment*. It would have no link back to the
	 * {@code Holder} and would be unaware if the {@code Holder} was later updated to point to a different
	 * (or the initially missing) {@code Biome} object. This would lead to using stale or even {@code null} data.</p>
	 *
	 * <p>By storing the {@code Holder<Biome>} itself, this class can call {@link net.minecraft.core.Holder#value()}
	 * whenever the biome information is needed, ensuring it always retrieves the most current {@code Biome}
	 * instance associated with the holder at that time.</p>
	 */
	private static #if MC_VER < MC_1_18_2 Biome #else Holder<Biome> #endif getClientBiome(String biomeResourceString)
	{
		// cache the client biomes so we don't have to re-parse the resource location every time
		return BIOME_BY_RESOURCE_STRING.compute(biomeResourceString,
				(resourceString, existingBiome) ->
				{
					if (existingBiome != null)
					{
						return existingBiome;
					}
					
					ClientLevel clientLevel = Minecraft.getInstance().level;
					if (clientLevel == null)
					{
						// shouldn't happen, but just in case
						throw new IllegalStateException("Attempted to get client biome when no client level was loaded.");
					}
					
					BiomeWrapper.BiomeDeserializeResult result;
					try
					{
						result = BiomeWrapper.deserializeBiome(resourceString, clientLevel.registryAccess());
					}
					catch (Exception e)
					{
						LOGGER.warn("Unable to deserialize client biome ["+resourceString+"], using fallback...");
						
						try
						{
							result = BiomeWrapper.deserializeBiome(BiomeWrapper.PLAINS_RESOURCE_LOCATION_STRING, clientLevel.registryAccess());
						}
						catch (IOException ex)
						{
							// should never happen, if it does this log will explode, but just in case
							LOGGER.error("Unable to deserialize fallback client biome ["+BiomeWrapper.PLAINS_RESOURCE_LOCATION_STRING+"], returning NULL.");
							return null;
						}
					}
					
					if (result.success)
					{
						existingBiome = result.biome;
					}
					
					return existingBiome;
				});
	}
	
	
	
}
