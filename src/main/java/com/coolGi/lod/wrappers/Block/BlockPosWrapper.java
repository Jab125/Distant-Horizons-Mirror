package com.coolGi.lod.wrappers.Block;

import net.minecraft.core.BlockPos;

import java.util.Objects;

public class BlockPosWrapper {
    private BlockPos.MutableBlockPos blockPos;


    public BlockPosWrapper()
    {
        this.blockPos = new BlockPos.MutableBlockPos(0,0,0);
    }

    public void set(int x, int y, int z)
    {
        blockPos.set(x, y, z);
    }

    public int getX()
    {
        return blockPos.getX();
    }

    public int getY()
    {
        return blockPos.getY();
    }

    public int getZ()
    {
        return blockPos.getZ();
    }

    public BlockPos.MutableBlockPos getBlockPos()
    {
        return blockPos;
    }

    @Override public boolean equals(Object o)
    {
        return blockPos.equals(o);
    }

    @Override public int hashCode()
    {
        return Objects.hash(blockPos);
    }


}
