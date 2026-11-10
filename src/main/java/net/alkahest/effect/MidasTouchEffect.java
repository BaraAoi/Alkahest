package net.alkahest.effect;

import net.alkahest.registry.ModEffects;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Early Midas effect: nearby Matrices run 20% faster, without stacking per player. */
public final class MidasTouchEffect extends StatusEffect {
    public static final int RADIUS = 8;
    public static final int DURATION = 3600;

    public MidasTouchEffect() { super(StatusEffectCategory.BENEFICIAL, 0xE8B747); }

    public static boolean nearby(World world, BlockPos pos) {
        return nearby(world.getPlayers(), pos);
    }

    /** Shared with tests; use a sphere around the centre of the Matrix core. */
    public static boolean nearby(Iterable<? extends PlayerEntity> players, BlockPos pos) {
        for (PlayerEntity player : players) {
            if (player.isAlive() && !player.isSpectator() && player.hasStatusEffect(ModEffects.MIDAS_TOUCH)
                && player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= RADIUS * RADIUS)
                return true;
        }
        return false;
    }

    /** One extra progress tick per five world ticks = 20% more work, never stacked. */
    public static int progressStep(boolean nearby, long worldTime) {
        return nearby && Math.floorMod(worldTime, 5) == 0 ? 2 : 1;
    }
}
