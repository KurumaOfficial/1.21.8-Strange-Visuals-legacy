package ru.strange.client.utils.player;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import ru.strange.client.utils.Helper;

import java.util.ArrayList;
import java.util.List;

public class PlayerUtil implements Helper {
    public static boolean isBlockSolid(final double x, final double y, final double z) {
        BlockPos pos = BlockPos.ofFloored(x, y, z);
        return mc.world.getBlockState(pos).isFullCube(mc.world, pos);
    }
}
