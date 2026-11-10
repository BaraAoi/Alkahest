package net.alkahest.block.entity;

import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.JarContentsComponent;
import net.alkahest.item.custom.CrystalWandItem;
import net.alkahest.registry.ModBlockEntities;
import net.alkahest.registry.ModDataComponents;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/** Early charger: a real jar above the stand supplies one matching wand per second. */
public class EssenceChargerBlockEntity extends BlockEntity {
    public EssenceChargerBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.ESSENCE_CHARGER, pos, state); }

    public static void tick(World world, BlockPos pos) {
        if (!(world instanceof ServerWorld server) || world.getTime() % 20 != 0 ||
            !(world.getBlockEntity(pos.up()) instanceof JarBlockEntity jar)) return;
        var bounds = Box.of(Vec3d.ofCenter(pos), 64, 64, 64);
        for (PlayerEntity player : world.getEntitiesByClass(PlayerEntity.class, bounds, p -> p.isAlive() && !p.isSpectator())) {
            EssenceType consumed = chargeFirst(jar, player);
            if (consumed != null) { beam(server, pos, player, consumed); return; }
        }
    }
    /** One successful charge consumes exactly one matching unit, or nothing on rejection. */
    public static EssenceType chargeFirst(JarBlockEntity jar, PlayerEntity player) {
        JarContentsComponent current = jar.contents();
        if (!net.alkahest.item.custom.JarItem.allowed(current)) return null;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!(stack.getItem() instanceof CrystalWandItem wand)) continue;
            int charge = stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0);
            if (charge >= 100) continue;
            EssenceStack source = current.contents().stream().filter(e -> e.type() == wand.essenceType()).findFirst().orElse(null);
            if (source == null) continue;
            List<EssenceStack> next = new ArrayList<>(current.contents());
            next.remove(source);
            if (source.amount() > 1) next.add(new EssenceStack(source.type(), source.amount() - 1, source.stability()));
            jar.setContents(new JarContentsComponent(next));
            stack.set(ModDataComponents.ESSENCE_CHARGE, Math.min(100, charge + 10));
            player.getInventory().markDirty();
            return source.type();
        }
        return null;
    }
    private static void beam(ServerWorld world, BlockPos pos, PlayerEntity player, EssenceType essence) {
        int color = essence.color();
        var dust = new DustParticleEffect(new Vector3f(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f), .8f);
        Vec3d start = Vec3d.ofCenter(pos).add(0, .8, 0), end = player.getPos().add(0, 1, 0);
        int steps = Math.max(2, Math.min(48, (int)(start.distanceTo(end) * 2)));
        for (int i = 0; i <= steps; i++) {
            Vec3d at = start.lerp(end, (double)i / steps);
            world.spawnParticles(dust, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }
}
