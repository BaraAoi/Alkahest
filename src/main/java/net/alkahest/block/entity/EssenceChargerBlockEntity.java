package net.alkahest.block.entity;

import dev.emi.trinkets.api.TrinketsApi;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.JarContentsComponent;
import net.alkahest.item.custom.AlkahestAmuletItem;
import net.alkahest.item.custom.CrystalWandItem;
import net.alkahest.registry.ModBlockEntities;
import net.alkahest.registry.ModDataComponents;
import net.alkahest.registry.ModItems;
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

/** A jar directly below supplies the charger; old installations above are supported. */
public class EssenceChargerBlockEntity extends BlockEntity {
    public EssenceChargerBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.ESSENCE_CHARGER, pos, state); }

    public static void tick(World world, BlockPos pos) {
        if (!(world instanceof ServerWorld server)) return;
        // Reconcile older worlds whose charger block state did not have the mounted property.
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof net.alkahest.block.custom.EssenceChargerBlock) {
            boolean mounted = world.getBlockState(pos.down()).isOf(net.alkahest.registry.ModBlocks.JAR);
            if (state.get(net.alkahest.block.custom.EssenceChargerBlock.MOUNTED) != mounted)
                world.setBlockState(pos, state.with(net.alkahest.block.custom.EssenceChargerBlock.MOUNTED, mounted), net.minecraft.block.Block.NOTIFY_ALL);
        }
        if (world.getTime() % 20 != 0) return;
        JarBlockEntity jar = findJar(world, pos);
        if (jar == null) return;
        var bounds = Box.of(Vec3d.ofCenter(pos), 64, 64, 64);
        var result = chargeNearby(jar, pos, world.getEntitiesByClass(PlayerEntity.class, bounds, p -> true));
        if (result != null) beam(server, pos, result.player(), result.essence());
    }

    /** Prefer the jar below; only fall back to the old arrangement if there is none below. */
    public static JarBlockEntity findJar(World world, BlockPos pos) {
        if (world.getBlockEntity(pos.down()) instanceof JarBlockEntity below) return below;
        if (world.getBlockEntity(pos.up()) instanceof JarBlockEntity above) return above;
        return null;
    }

    public record ChargeTarget(PlayerEntity player, EssenceType essence) { }

    public static ChargeTarget chargeNearby(JarBlockEntity jar, BlockPos charger, Iterable<? extends PlayerEntity> players) {
        Box bounds = Box.of(Vec3d.ofCenter(charger), 64, 64, 64);
        for (PlayerEntity player : players) {
            if (!player.isAlive() || player.isSpectator() || !bounds.intersects(player.getBoundingBox())) continue;
            EssenceType consumed = chargeFirst(jar, player);
            if (consumed != null) return new ChargeTarget(player, consumed);
        }
        return null;
    }

    private static EssenceType requiredEssenceFor(ItemStack stack) {
        if (stack.getItem() instanceof CrystalWandItem wand) return wand.essenceType();
        if (stack.getItem() instanceof AlkahestAmuletItem amulet) return amulet.rechargeType();
        return null;
    }

    private static EssenceType applyJarCharge(JarBlockEntity jar, JarContentsComponent current, ItemStack stack) {
        EssenceType needed = requiredEssenceFor(stack);
        if (needed == null) return null;
        int charge = stack.getOrDefault(ModDataComponents.ESSENCE_CHARGE, 0);
        if (charge >= 100) return null;
        EssenceStack source = current.contents().stream().filter(e -> e.type() == needed).findFirst().orElse(null);
        if (source == null) return null;
        List<EssenceStack> next = new ArrayList<>(current.contents());
        next.remove(source);
        if (source.amount() > 1) next.add(new EssenceStack(source.type(), source.amount() - 1, source.stability()));
        jar.setContents(new JarContentsComponent(next));
        int updated = Math.min(100, charge + 10);
        stack.set(ModDataComponents.ESSENCE_CHARGE, updated);
        if (updated >= 100 && stack.getItem() instanceof AlkahestAmuletItem) {
            stack.set(ModDataComponents.AMULET_USES_STEP, 0);
        }
        return source.type();
    }

    /** One successful charge consumes exactly one matching unit, or nothing on rejection. */
    public static EssenceType chargeFirst(JarBlockEntity jar, PlayerEntity player) {
        JarContentsComponent current = jar.contents();
        if (!net.alkahest.item.custom.JarItem.allowed(current)) return null;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            EssenceType used = applyJarCharge(jar, current, stack);
            if (used != null) {
                player.getInventory().markDirty();
                return used;
            }
        }
        var trinkets = TrinketsApi.getTrinketComponent(player).orElse(null);
        if (trinkets != null) {
            for (var equipped : trinkets.getEquipped(ModItems.ALKAHEST_AMULET)) {
                ItemStack stack = equipped.getRight();
                EssenceType used = applyJarCharge(jar, current, stack);
                if (used != null) {
                    equipped.getLeft().inventory().setStack(equipped.getLeft().index(), stack);
                    return used;
                }
            }
        }
        return null;
    }

    private static void beam(ServerWorld world, BlockPos pos, PlayerEntity player, EssenceType essence) {
        int color = essence.color();
        var dust = new DustParticleEffect(new Vector3f(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f), .8f);
        Vec3d start = Vec3d.ofCenter(pos).add(0, -.18, 0), end = player.getPos().add(0, 1, 0);
        int steps = Math.max(2, Math.min(48, (int)(start.distanceTo(end) * 2)));
        for (int i = 0; i <= steps; i++) {
            Vec3d at = start.lerp(end, (double)i / steps);
            world.spawnParticles(dust, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }
}
