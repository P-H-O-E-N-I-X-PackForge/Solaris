package net.phoenixvine.solaris.client.waypoint;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

public final class WaypointPlacement {

    private WaypointPlacement() {}

    /**
     * The Y of the block to put a waypoint on at (x, z). Open-sky dimensions use the surface
     * heightmap. Under a ceiling (the Nether) that heightmap is the bedrock roof, which left
     * waypoints floating far above the player, so there it finds the floor nearest {@code hintY}
     * (the player's own Y) instead, falling back to the hint when the column isn't loaded.
     */
    public static int groundY(Level level, int x, int z, int hintY) {
        if (!level.dimensionType().hasCeiling()) {
            return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        }
        if (!level.hasChunk(x >> 4, z >> 4)) return hintY;

        int min = level.getMinBuildHeight();
        int max = level.getMaxBuildHeight() - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, Mth.clamp(hintY, min, max), z);

        for (int i = 0; i < 48 && pos.getY() < max && !level.getBlockState(pos).isAir(); i++) pos.move(0, 1, 0);
        for (int i = 0; i < 128 && pos.getY() > min && level.getBlockState(pos.below()).isAir(); i++) {
            pos.move(0, -1, 0);
        }
        return pos.getY() - 1;
    }
}
