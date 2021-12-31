package com.seibel.lod.common.clouds;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedList;
import java.util.List;

public final class NoiseCloudHandler {

    public static List<CloudTexture> cloudTextures = new LinkedList<CloudTexture>() {};

    private static long cloudIdx = -1;
    private static long timeIdx = -1;
    private static long lastTime = -1;

    public static void update() {
        Minecraft client = Minecraft.getInstance();
        assert client.level != null;
        long time = client.level.getGameTime();
        if (time > lastTime) {
            lastTime = time;
            updateSkyCover(time);

            long update = time / 600;
            if (update > timeIdx) {
                timeIdx = update;
                for (CloudTexture cloudTexture : cloudTextures) {
                    if (cloudTexture.cloudsTexture.getPixels() != null) {
                        cloudTexture.updateImage(time);
                    }
                }
            }

            for (CloudTexture cloudTexture : cloudTextures) {
                if (cloudTexture.cloudsTexture.getPixels() != null) {
                    cloudTexture.updatePixels();
                }
            }
        }
    }

    public static void updateSkyCover(long time) {
        long idx = time / 12000;

        if (idx > cloudIdx) {
            cloudIdx = idx;
        }
    }

    public static void initCloudTextures(ResourceLocation defaultCloud) {
        CloudTexture defaultCloudTexture = new CloudTexture(defaultCloud);

        cloudTextures.add(defaultCloudTexture);
    }
}
