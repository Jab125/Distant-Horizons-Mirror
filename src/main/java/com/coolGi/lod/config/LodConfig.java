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

package com.coolGi.lod.config;

import com.coolGi.lod.enums.*;
import me.shedaniel.autoconfig.annotation.*;
import com.coolGi.lod.ModInfo;

/**
 * This handles any configuration the user has access to.
 * @author coolGi2007
 * @version 10-24-2021
 */
//@Mod.EventBusSubscriber
@Config(name = ModInfo.MODID)
public class LodConfig
{
    // Since the original config system uses forge stuff, that means we have to rewrite the whole config system

    @ConfigEntry.Gui.Excluded
    @ConfigEntry.Category("lod.debug")
    public int ConfVersion = 1;

    public class Client {
        public class Graphics {
            public class QualityOption {
                @ConfigEntry.Category("lod.Graphics.QualityOption")
                public static HorizontalQuality drawResolution;

                @ConfigEntry.Category("lod.Graphics.QualityOption")
                @ConfigEntry.BoundedDiscrete(min = 32, max = 1024)
                public static int lodChunkRenderDistance = 64;

                @ConfigEntry.Category("lod.Graphics.QualityOption")
                public static VerticalQuality verticalQuality = VerticalQuality.MEDIUM;

                @ConfigEntry.Category("lod.Graphics.QualityOption")
                public static HorizontalScale horizontalScale = HorizontalScale.MEDIUM;

                @ConfigEntry.Category("lod.Graphics.QualityOption")
                public static HorizontalQuality horizontalQuality = HorizontalQuality.MEDIUM;
            }

            public class FogQualityOption {
                @ConfigEntry.Category("lod.Graphics.FogQualityOption")
                public static FogDistance fogDistance = FogDistance.FAR;

                @ConfigEntry.Category("lod.Graphics.FogQualityOption")
                public static FogDrawOverride fogDrawOverride = FogDrawOverride.FANCY;

                @ConfigEntry.Category("lod.Graphics.FogQualityOption")
                public static boolean disableVanillaFog = true;
            }

            public class AdvancedGraphicsOption {
                @ConfigEntry.Category("lod.Graphics.AdvancedGraphicsOption")
                public static LodTemplate lodTemplate = LodTemplate.CUBIC;

                @ConfigEntry.Category("lod.Graphics.AdvancedGraphicsOption")
                public static boolean disableDirectionalCulling = false;

                @ConfigEntry.Category("lod.Graphics.AdvancedGraphicsOption")
                public static boolean alwaysDrawAtMaxQuality = false;

                @ConfigEntry.Category("lod.Graphics.AdvancedGraphicsOption")
                public static VanillaOverdraw vanillaOverdraw = VanillaOverdraw.DYNAMIC;

                @ConfigEntry.Category("lod.Graphics.AdvancedGraphicsOption")
                public static GpuUploadMethod gpuUploadMethod = GpuUploadMethod.BUFFER_STORAGE;

                @ConfigEntry.Category("lod.Graphics.AdvancedGraphicsOption")
                public static boolean useExtendedNearClipPlane = false;
            }
        }

        public class WorldGenerator {
            @ConfigEntry.Category("lod.WorldGenerator")
            public static GenerationPriority generationPriority = GenerationPriority.FAR_FIRST;

            @ConfigEntry.Category("lod.WorldGenerator")
            public static DistanceGenerationMode distanceGenerationMode = DistanceGenerationMode.SURFACE;

            @ConfigEntry.Category("lod.WorldGenerator")
            public static boolean allowUnstableFeatureGeneration = false;

            @ConfigEntry.Category("lod.WorldGenerator")
            public static BlockToAvoid blockToAvoid = BlockToAvoid.BOTH;

//            @ConfigEntry.Category("lod.WorldGenerator")
//            public static boolean useExperimentalPreGenLoading = false;
        }

        public class AdvancedModOptions {

        }

        public class Debug {
//            @ConfigEntry.Gui.Excluded
            @ConfigEntry.Category("lod.Debug")
            @ConfigEntry.Gui.Tooltip
            public static boolean drawLods = true;

//            @ConfigEntry.Gui.Excluded
            @ConfigEntry.Category("lod.Debug")
            @ConfigEntry.Gui.Tooltip
            public static DebugMode debugMode = DebugMode.OFF;

//            @ConfigEntry.Gui.Excluded
            @ConfigEntry.Category("lod.Debug")
            @ConfigEntry.Gui.Tooltip
            public static boolean enableDebugKeybindings = false;
        }
    }

	// CONFIG STRUCTURE
	// 	-> Client
	//		|
	//		|-> Graphics
	//		|		|-> QualityOption
	//		|		|-> FogQualityOption
	//		|		|-> AdvancedGraphicsOption
	//		|
	//		|-> World Generation
	//		|
	//		|-> Advanced Mod Option
	//				|-> Threads
	//				|-> Buffers
	//				|-> Debugging

}
