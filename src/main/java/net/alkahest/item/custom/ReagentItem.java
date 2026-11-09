package net.alkahest.item.custom;

import net.alkahest.entity.ThrownReagentEntity;
import net.alkahest.registry.ModEntities;
import net.alkahest.registry.ModItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.ClickType;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Throwables and the two early inventory-mixing recipes. */
public class ReagentItem extends Item {
    public ReagentItem(Settings settings) { super(settings); }

    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!world.isClient) {
            ThrownReagentEntity projectile = new ThrownReagentEntity(ModEntities.THROWN_REAGENT, world);
            projectile.setOwner(player);
            projectile.setPosition(player.getX(), player.getEyeY() - 0.1, player.getZ());
            projectile.setItem(stack.copyWithCount(1));
            projectile.setVelocity(player, player.getPitch(), player.getYaw(), 0, 1.0f, 1.0f);
            if (!world.spawnEntity(projectile)) return TypedActionResult.fail(stack);
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_SNOWBALL_THROW,
                SoundCategory.PLAYERS, 0.5f, 0.8f + world.random.nextFloat() * 0.4f);
            stack.decrementUnlessCreative(1, player);
            player.incrementStat(Stats.USED.getOrCreateStat(this));
        }
        return TypedActionResult.success(stack, world.isClient);
    }

    private static Item mixture(ItemStack reagent, ItemStack ingredient) {
        if (reagent.isOf(ModItems.FIRE_DUST) && ingredient.isOf(Items.SLIME_BALL)) return ModItems.NAPALM;
        if (reagent.isOf(ModItems.CAUSTIC_SLIME) && ingredient.isOf(Items.GUNPOWDER)) return ModItems.ACID_BOMB;
        return null;
    }

    /** The clicked slot must belong to the player and permit taking items, not a machine or output slot. */
    private static boolean mix(ItemStack reagent, ItemStack ingredient, Slot slot, PlayerEntity player, ClickType click) {
        if (click != ClickType.RIGHT || slot.inventory != player.getInventory() || !slot.canTakeItems(player)) return false;
        Item resultItem = mixture(reagent, ingredient);
        if (resultItem == null || reagent.isEmpty() || ingredient.isEmpty()) return false;
        reagent.decrement(1);
        ingredient.decrement(1);
        slot.markDirty();
        ItemStack result = new ItemStack(resultItem);
        if (!player.giveItemStack(result) && !player.getWorld().isClient) player.dropItem(result, false);
        if (!player.getWorld().isClient) player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.5f, 1.2f);
        return true;
    }
    @Override public boolean onStackClicked(ItemStack stack, Slot slot, ClickType click, PlayerEntity player) {
        return mix(stack, slot.getStack(), slot, player, click);
    }
    @Override public boolean onClicked(ItemStack stack, ItemStack cursor, Slot slot, ClickType click,
                                       PlayerEntity player, StackReference cursorStackReference) {
        return mix(stack, cursor, slot, player, click);
    }
}
