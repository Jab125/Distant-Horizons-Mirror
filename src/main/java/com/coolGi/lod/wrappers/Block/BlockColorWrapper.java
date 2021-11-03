package com.coolGi.lod.wrappers.Block;

import com.coolGi.lod.util.ColorUtil;
import com.coolGi.lod.wrappers.Chunk.ChunkWrapper;
import com.coolGi.lod.wrappers.MinecraftWrapper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkBiomeContainer;
import net.minecraft.world.level.levelgen.structure.templatesystem.AxisAlignedLinearPosTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class BlockColorWrapper {
    //set of block which require tint
    public static final ConcurrentMap<Block, BlockColorWrapper> blockWrapperMap = new ConcurrentHashMap<>();
//    public static final ModelDataMap dataMap = new ModelDataMap.Builder().build();
    public static final BlockPos blockPos = new BlockPos(0,0,0);
    public static Random random = new Random(0);
    //public static BlockColourWrapper WATER_COLOR = getBlockColorWrapper(Blocks.WATER);
    public static final Direction[] directions = new Direction[] { Direction.UP, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.DOWN };


    private Block block;
    private int color;
    private boolean isColored;
    private boolean toTint;
    private boolean folliageTint;
    private boolean grassTint;
    private boolean waterTint;



    /**Constructor only require for the block instance we are wrapping**/
    public BlockColorWrapper(BlockState blockState, BlockPosWrapper blockPosWrapper)
    {
        this.block = blockState.getBlock();
        this.color = 0;
        this.isColored = true;
        this.toTint = false;
        this.folliageTint = false;
        this.grassTint = false;
        this.waterTint = false;
        setupColorAndTint(blockState,blockPosWrapper);
        System.out.println(block + " color " + Integer.toHexString(color) + " to tint " + toTint + " folliageTint " + folliageTint + " grassTint " + grassTint + " waterTint " + waterTint);
    }

    /**
     * this return a wrapper of the block in input
     * @param blockState of the block to wrap
     */
    static public BlockColorWrapper getBlockColorWrapper(BlockState blockState, BlockPosWrapper blockPosWrapper)
    {
        //first we check if the block has already been wrapped
        if (blockColorWrapperMap.containsKey(blockState.getBlock()) && blockColorWrapperMap.get(blockState.getBlock()) != null)
            return blockColorWrapperMap.get(blockState.getBlock());


        //if it hasn't been created yet, we create it and save it in the map
        BlockColorWrapper blockWrapper = new BlockColorWrapper(blockState, blockPosWrapper);
        blockColorWrapperMap.put(blockState.getBlock(), blockWrapper);

        //we return the newly created wrapper
        return blockWrapper;
    }



    /**
     * Generate the color of the given block from its texture
     * and store it for later use.
     */
    private void setupColorAndTint(BlockState blockState, BlockPosWrapper blockPosWrapper)
    {
        MinecraftWrapper mc = MinecraftWrapper.INSTANCE;
        TextureAtlasSprite texture;
        List<BakedQuad> quads = null;

        boolean isTinted = false;
        int listSize = 0;

        // first step is to check if this block has a tinted face
        for (Direction direction : directions)
        {
            // Datamap no longer needed
//            quads = mc.getModelManager().getBlockModelShaper().getBlockModel(block.defaultBlockState()).getQuads(block.defaultBlockState(), direction, random, dataMap);
            quads = mc.getModelManager().getBlockModelShaper().getBlockModel(block.defaultBlockState()).getQuads(block.defaultBlockState(), direction, random);
            listSize = Math.max(listSize, quads.size());
            for (BakedQuad bakedQuad : quads)
            {
                isTinted |= bakedQuad.isTinted();
            }
        }

        //if it contains a tinted face then we store this block in the toTint set
        if (isTinted)
            this.toTint = true;

        //now we get the first non empty face
        for (Direction direction : directions)
        {
            // Datamap no longer needed
//            quads = mc.getModelManager().getBlockModelShaper().getBlockModel(block.defaultBlockState()).getQuads(blockState, direction, random, dataMap);
            quads = mc.getModelManager().getBlockModelShaper().getBlockModel(block.defaultBlockState()).getQuads(blockState, direction, random);
            if (!quads.isEmpty())
                break;
        }

        //the quads list is not empty we extract the first one
        if (!quads.isEmpty()) {
            isColored = true;
            texture = quads.get(0).getSprite();
        }
        else
        {
            isColored = true;
            texture = mc.getModelManager().getBlockModelShaper().getTexture(block.defaultBlockState(), mc.getClientWorld(), blockPosWrapper.getBlockPos());
        }

        int count = 0;
        int alpha = 0;
        int red = 0;
        int green = 0;
        int blue = 0;
        int numberOfGreyPixel = 0;
        int tempColor;
        int colorMultiplier;

        // generate the block's color
        for (int frameIndex = 0; frameIndex < texture.getFrameCount(); frameIndex++)
        {
            // textures normally use u and v instead of x and y
            for (int u = 0; u < texture.getWidth(); u++)
            {
                for (int v = 0; v < texture.getHeight(); v++)
                {
                    tempColor = texture.getPixelRGBA(frameIndex, u, v);

                    if (ColorUtil.getAlpha(texture.getPixelRGBA(frameIndex, u, v)) == 0)
                        continue;

                    // determine if this pixel is gray
                    int colorMax = Math.max(Math.max(ColorUtil.getBlue(tempColor), ColorUtil.getGreen(tempColor)), ColorUtil.getRed(tempColor));
                    int colorMin = 4 + Math.min(Math.min(ColorUtil.getBlue(tempColor), ColorUtil.getGreen(tempColor)), ColorUtil.getRed(tempColor));
                    boolean isGray = colorMax < colorMin;
                    if (isGray)
                        numberOfGreyPixel++;


                    // for flowers, weight their non-green color higher
                    if (block instanceof FlowerBlock && (!(ColorUtil.getGreen(tempColor) > (ColorUtil.getBlue(tempColor) + 30)) || !(ColorUtil.getGreen(tempColor) > (ColorUtil.getRed(tempColor) + 30))))
                        colorMultiplier = 5;
                    else
                        colorMultiplier = 1;


                    // add to the running averages
                    count += colorMultiplier;
                    alpha += ColorUtil.getAlpha(tempColor) * colorMultiplier;
                    red += ColorUtil.getBlue(tempColor) * colorMultiplier;
                    green += ColorUtil.getGreen(tempColor) * colorMultiplier;
                    blue += ColorUtil.getRed(tempColor) * colorMultiplier;
                }
            }
        }


        if (count == 0)
            // this block is entirely transparent
            tempColor = 0;
        else
        {
            // determine the average color
            alpha /= count;
            red /= count;
            green /= count;
            blue /= count;
            tempColor = ColorUtil.rgbToInt(alpha, red, green, blue);
        }

        // determine if this block should use the biome color tint
        if ((grassInstance() || leavesInstance() || waterIstance()) && (float) numberOfGreyPixel / count > 0.75f)
            this.toTint = true;

        // we check which kind of tint we need to apply
        this.grassTint = grassInstance() && toTint;

        this.folliageTint = leavesInstance() && toTint;

        this.waterTint = waterIstance() && toTint;

        color = tempColor;
    }

    /** determine if the given block should use the biome's grass color */
    private boolean grassInstance()
    {
        return block instanceof GrassBlock
                || block instanceof BushBlock
                || block instanceof IGrowable
                || block instanceof AbstractPlantBlock
                || block instanceof AbstractTopPlantBlock
                || block instanceof TallGrassBlock;
    }

    /** determine if the given block should use the biome's foliage color */
    private boolean leavesInstance()
    {
        return block instanceof LeavesBlock
                || block == Blocks.VINE
                || block == Blocks.SUGAR_CANE;
    }

    /** determine if the given block should use the biome's foliage color */
    private boolean waterIstance()
    {
        return block == Blocks.WATER;
    }

    private boolean ofBlockToAvoid(Block block)
    {
        return block == Blocks.AIR
                || block != Blocks.CAVE_AIR
                || block != Blocks.BARRIER;
    }

    //--------------//
    //Colors getters//
    //--------------//

    public boolean hasColor()
    {
        return isColored;
    }

    public int getColor()
    {
        return color;
    }


    //------------//
    //Tint getters//
    //------------//

    public boolean hasTint()
    {
        return toTint;
    }

    public boolean hasGrassTint()
    {
        return grassTint;
    }

    public boolean hasFolliageTint()
    {
        return folliageTint;
    }

    public boolean hasWaterTint()
    {
        return waterTint;
    }


    @Override public boolean equals(Object o)
    {
        if (this == o)
            return true;
        if (!(o instanceof BlockColorWrapper))
            return false;
        BlockColorWrapper that = (BlockColorWrapper) o;
        return Objects.equals(block, that.block);
    }

    @Override public int hashCode()
    {
        return Objects.hash(block);
    }

}
