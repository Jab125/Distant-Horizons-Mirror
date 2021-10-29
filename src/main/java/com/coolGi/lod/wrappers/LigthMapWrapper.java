package com.coolGi.lod.wrappers;

import com.mojang.blaze3d.platform.NativeImage;

public class LigthMapWrapper {
    static NativeImage lightMap = null;

    public static void setLightMap(NativeImage lightMap)
    {
        lightMap = lightMap;
    }

    public static int getLightValue(int skyLight, int blockLight)
    {
        return lightMap.getPixelRGBA(skyLight, blockLight);
    }
}
