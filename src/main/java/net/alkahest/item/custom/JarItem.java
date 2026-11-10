package net.alkahest.item.custom;

import net.alkahest.essence.JarContentsComponent;
import net.alkahest.registry.ModDataComponents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.ClickType;
import net.minecraft.text.Text;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.block.Block;

/** A portable normal jar. Right click pours the cursor/held jar into the other jar. */
public class JarItem extends BlockItem {
    public static final int CAPACITY = 100;
    public JarItem(Block block, Settings settings) { super(block, settings.maxCount(1)); }
    public static JarContentsComponent contents(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.JAR_CONTENTS, JarContentsComponent.EMPTY);
    }
    public static boolean allowed(JarContentsComponent contents) {
        return contents.total() <= CAPACITY && contents.contents().stream().noneMatch(s -> s.type() == net.alkahest.essence.EssenceType.VACUUM);
    }
    /** Legacy mixed jars stay readable, but no transfer may create or add to a mixture. */
    public static boolean singleType(JarContentsComponent contents) { return contents.contents().size() <= 1; }
    public static boolean compatible(JarContentsComponent target, JarContentsComponent addition) {
        if (!allowed(target) || !allowed(addition) || !singleType(target) || !singleType(addition)) return false;
        return target.total() == 0 || addition.total() == 0 ||
            target.contents().getFirst().type() == addition.contents().getFirst().type();
    }
    public static void set(ItemStack stack, JarContentsComponent contents) {
        if (contents.total() == 0) stack.remove(ModDataComponents.JAR_CONTENTS);
        else stack.set(ModDataComponents.JAR_CONTENTS, contents);
    }
    /** Excess evaporates, source jar itself remains. Reject invalid data without changing either item. */
    public static boolean pour(ItemStack source, ItemStack target) {
        if (!(source.getItem() instanceof JarItem) || !(target.getItem() instanceof JarItem) || source == target) return false;
        var from = contents(source); var into = contents(target);
        if (from.total() == 0 || !compatible(into, from)) return false;
        set(target, into.merge(from, CAPACITY)); set(source, JarContentsComponent.EMPTY);
        return true;
    }
    private static boolean validSlot(Slot slot, PlayerEntity player, ClickType click) {
        return click == ClickType.RIGHT && slot.inventory == player.getInventory() && slot.canTakeItems(player);
    }
    private static void overflowFeedback(PlayerEntity player, boolean overflow) {
        if (overflow && !player.getWorld().isClient) {
            player.sendMessage(Text.translatable("message.alkahest.jar_overflow"), true);
            player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, .7f, 1.1f);
            if (player.getWorld() instanceof net.minecraft.server.world.ServerWorld world)
                world.spawnParticles(net.minecraft.particle.ParticleTypes.SMOKE, player.getX(), player.getY() + 1, player.getZ(), 12, .2, .2, .2, .02);
        }
    }
    private static boolean tryInventoryPour(ItemStack source, ItemStack target, Slot slot, ClickType click, PlayerEntity player) {
        if (!validSlot(slot, player, click) || !(source.getItem() instanceof JarItem) || !(target.getItem() instanceof JarItem) || source == target) return false;
        var from = contents(source); var into = contents(target);
        if (from.total() == 0) return false;
        if (!compatible(into, from)) {
            if (!player.getWorld().isClient) player.sendMessage(Text.translatable("message.alkahest.jar_single_type"), true);
            return true; // Do not let vanilla rearrange or consume either jar on a refused pour.
        }
        boolean overflow = from.total() + into.total() > CAPACITY;
        if (!pour(source, target)) return false;
        overflowFeedback(player, overflow); slot.markDirty(); return true;
    }
    @Override public boolean onStackClicked(ItemStack stack, Slot slot, ClickType click, PlayerEntity player) {
        return tryInventoryPour(stack, slot.getStack(), slot, click, player);
    }
    @Override public boolean onClicked(ItemStack stack, ItemStack cursor, Slot slot, ClickType click, PlayerEntity player, StackReference reference) {
        return tryInventoryPour(cursor, stack, slot, click, player);
    }

}
