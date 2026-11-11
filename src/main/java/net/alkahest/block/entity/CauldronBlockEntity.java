package net.alkahest.block.entity;

import net.alkahest.block.custom.AlchemyCauldronBlock;
import net.alkahest.entity.DissolvedEntity;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.ItemEssenceMap;
import net.alkahest.item.custom.AlkahestAmuletItem;
import net.alkahest.item.custom.CrystalWandItem;
import net.alkahest.recipe.CauldronEssenceRecipes;
import net.alkahest.registry.ModBlockEntities;
import net.alkahest.registry.ModBlocks;
import net.alkahest.registry.ModDataComponents;
import net.alkahest.registry.ModEntities;
import net.alkahest.registry.ModItems;
import net.alkahest.research.EarlyResearch;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class CauldronBlockEntity extends BlockEntity {
    public static final int MAX_PER_TYPE = 256;
    public static final float RESIDUE_STABILITY = 0.05f;
    private static final String RETURNED_ITEM_TAG = "alkahest_cauldron_returned";

    /** Exact dissolved cost of 1x alchemical_moss + 1x ender_pearl + all 6 Prima crystals. */
    public static final Map<EssenceType, Integer> ALKAHEST_BREW_COST = Map.ofEntries(
        Map.entry(EssenceType.IGNIS, 1),
        Map.entry(EssenceType.AQUA, 1),
        Map.entry(EssenceType.TERRA, 1),
        Map.entry(EssenceType.AER, 1),
        Map.entry(EssenceType.ORDO, 3),
        Map.entry(EssenceType.PERDITIO, 3),
        Map.entry(EssenceType.HERBA, 3),
        Map.entry(EssenceType.VITIUM, 2),
        Map.entry(EssenceType.SPIRITUS, 6),
        Map.entry(EssenceType.MOTUS, 6)
    );

    private static final List<EssenceType> PRIMA_TYPES = List.of(
        EssenceType.IGNIS, EssenceType.AQUA, EssenceType.TERRA,
        EssenceType.AER, EssenceType.ORDO, EssenceType.PERDITIO
    );

    private static final EssenceType[][] ANTIPODE_PAIRS = {
        {EssenceType.IGNIS, EssenceType.AQUA},
        {EssenceType.TERRA, EssenceType.AER},
        {EssenceType.ORDO, EssenceType.PERDITIO}
    };

    private final EnumMap<EssenceType, Integer> essences = new EnumMap<>(EssenceType.class);
    // Only used to safely eject items left in cauldrons saved in <= 0.1.24; no items are ever stored here anymore.
    private final DefaultedList<ItemStack> legacyItems = DefaultedList.ofSize(6, ItemStack.EMPTY);
    private boolean water;
    private boolean dirtyWater;
    private boolean mossStabilized;
    private int evaporation;

    public CauldronBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CAULDRON, pos, state);
    }

    public Map<EssenceType, Integer> essences() {
        return Map.copyOf(essences);
    }

    public boolean hasWater() {
        return water;
    }

    public boolean isDirtyWater() {
        return water && dirtyWater;
    }

    public boolean isMossStabilized() {
        return water && mossStabilized;
    }

    public boolean isHeated() {
        if (world == null) return false;
        BlockState below = world.getBlockState(pos.down());
        return below.isOf(Blocks.FIRE) || below.isOf(Blocks.SOUL_FIRE) || below.isOf(Blocks.LAVA)
            || below.isOf(Blocks.MAGMA_BLOCK) || CampfireBlock.isLitCampfire(below);
    }

    /** Weighted color of dissolved essences, or -1 when the cauldron has no dissolved essences. */
    public int essenceColor() {
        if (essences.isEmpty()) return -1;
        long r = 0, g = 0, b = 0, total = 0;
        for (var entry : essences.entrySet()) {
            int amount = entry.getValue();
            if (amount <= 0) continue;
            int color = entry.getKey().color();
            r += (long) ((color >> 16) & 255) * amount;
            g += (long) ((color >> 8) & 255) * amount;
            b += (long) (color & 255) * amount;
            total += amount;
        }
        if (total <= 0) return -1;
        return (int) (((r / total) << 16) | ((g / total) << 8) | (b / total));
    }

    public void fill(PlayerEntity player, Hand hand) {
        if (water && !dirtyWater) {
            message(player, "message.alkahest.cauldron_full");
            return;
        }
        boolean cleaning = water && dirtyWater;
        dirtyWater = false;
        setWater(true);
        evaporation = 0;
        if (!player.isCreative()) player.setStackInHand(hand, new ItemStack(Items.BUCKET));
        if (world != null) {
            world.playSound(null, pos, SoundEvents.ITEM_BUCKET_EMPTY, SoundCategory.BLOCKS, 0.8f, 1.0f);
        }
        markDirty();
        message(player, cleaning ? "message.alkahest.cauldron_cleaned" : "message.alkahest.cauldron_filled");
    }

    public boolean useWand(PlayerEntity player, Hand hand, CrystalWandItem wand) {
        if (wand.essenceType() == EssenceType.AQUA && (!water || dirtyWater)) {
            boolean cleaning = water && dirtyWater;
            dirtyWater = false;
            setWater(true);
            evaporation = 0;
            wand.consumeUse(player, hand);
            if (world != null) {
                world.playSound(null, pos, SoundEvents.ITEM_BUCKET_EMPTY, SoundCategory.BLOCKS, 0.8f, 1.15f);
            }
            markDirty();
            message(player, cleaning ? "message.alkahest.cauldron_cleaned" : "message.alkahest.cauldron_filled");
            return true;
        }
        describeEssences(player);
        return false;
    }

    public boolean useAmulet(PlayerEntity player, Hand hand, AlkahestAmuletItem amulet) {
        return useAmulet(player, player.getStackInHand(hand));
    }

    public boolean useAmulet(PlayerEntity player, ItemStack stack) {
        if (!(stack.getItem() instanceof AlkahestAmuletItem amulet)) return false;
        if (!water || dirtyWater) {
            if (!amulet.canConsumeUse(stack, player)) {
                message(player, "message.alkahest.amulet_depleted");
                return false;
            }
            boolean cleaning = water && dirtyWater;
            dirtyWater = false;
            setWater(true);
            evaporation = 0;
            amulet.consumeUseStack(player, stack);
            if (world != null) {
                world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.8f, 1.2f);
            }
            markDirty();
            message(player, cleaning ? "message.alkahest.cauldron_cleaned" : "message.alkahest.cauldron_filled");
            return true;
        }
        describeEssences(player);
        return false;
    }

    public static EnumMap<EssenceType, Integer> essencesOf(ItemStack stack) {
        return essencesOf(stack, null);
    }
    public static EnumMap<EssenceType, Integer> essencesOf(ItemStack stack, World world) {
        var additions = new EnumMap<EssenceType, Integer>(EssenceType.class);
        if (!ItemEssenceMap.safeToConsume(stack)) return additions;
        for (var entry : ModItems.CRYSTALS.entrySet()) {
            if (stack.isOf(entry.getValue())) {
                if (entry.getKey() != EssenceType.VACUUM) additions.put(entry.getKey(), 1);
                return additions;
            }
        }
        for (var value : ItemEssenceMap.get(stack, world)) {
            if (value.type() == EssenceType.VACUUM || !ModItems.CRYSTALS.containsKey(value.type())) {
                additions.clear();
                return additions;
            }
            additions.merge(value.type(), value.amount(), Integer::sum);
        }
        return additions;
    }

    /** Adds a single mapped item atomically; the caller owns and consumes the original item. */
    public boolean dissolveOne(ItemStack stack) {
        if (world == null || world.isClient || !water || !isHeated() || stack.isEmpty()) return false;
        var additions = essencesOf(stack, world);
        if (additions.isEmpty()) return false;
        if (additions.entrySet().stream().anyMatch(e -> essences.getOrDefault(e.getKey(), 0) + e.getValue() > MAX_PER_TYPE)) {
            return false;
        }
        additions.forEach((type, amount) -> essences.merge(type, amount, Integer::sum));
        if (stack.isOf(ModBlocks.ALCHEMICAL_MOSS.asItem())) {
            mossStabilized = true;
        }
        evaporation = 0;
        syncEssences();
        if (world instanceof ServerWorld serverWorld) {
            if (stack.isOf(ModItems.SALT)) {
                for (ServerPlayerEntity researcher : serverWorld.getEntitiesByClass(
                        ServerPlayerEntity.class, new Box(pos).expand(6.0), p -> !p.isSpectator())) {
                    EarlyResearch.discoverBinding(researcher, world, pos);
                }
            } else if (stack.isOf(ModBlocks.ALCHEMICAL_MOSS.asItem())) {
                for (ServerPlayerEntity researcher : serverWorld.getEntitiesByClass(
                        ServerPlayerEntity.class, new Box(pos).expand(6.0), p -> !p.isSpectator())) {
                    EarlyResearch.discoverAlkahest(researcher, world, pos);
                }
            }
        }
        return true;
    }

    private void deductEssences(Map<EssenceType, Integer> cost) {
        cost.forEach((type, amount) -> {
            int rest = essences.getOrDefault(type, 0) - amount;
            if (rest <= 0) essences.remove(type);
            else essences.put(type, rest);
        });
    }

    private void playDissolveFeedback() {
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.playSound(null, pos, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.BLOCKS, 0.35f, 1.25f);
            serverWorld.spawnParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 0.95, pos.getZ() + 0.5,
                8, 0.2, 0.05, 0.2, 0.04);
        }
    }

    private void playBrewFeedback() {
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.playSound(null, pos, SoundEvents.BLOCK_BREWING_STAND_BREW, SoundCategory.BLOCKS, 0.85f, 1.1f);
            serverWorld.spawnParticles(ParticleTypes.WITCH, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5,
                16, 0.25, 0.15, 0.25, 0.03);
        }
    }

    private void triggerBrewResearch(ServerPlayerEntity researcher, ItemStack output) {
        if (researcher == null || world == null) return;
        if (output.isOf(ModItems.PURIFIED_WATER)) EarlyResearch.discoverReactive(researcher, world, pos);
        if (output.isOf(ModItems.HERBAL_PASTE)) EarlyResearch.discoverRaw(researcher, world, pos);
    }

    private boolean hasAllPrima() {
        return PRIMA_TYPES.stream().allMatch(t -> essences.getOrDefault(t, 0) >= 1);
    }

    private boolean hasAnyAntipodePair() {
        for (EssenceType[] pair : ANTIPODE_PAIRS) {
            if (essences.getOrDefault(pair[0], 0) > 0 && essences.getOrDefault(pair[1], 0) > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAllAlkahestEssences() {
        return ALKAHEST_BREW_COST.entrySet().stream()
            .allMatch(e -> essences.getOrDefault(e.getKey(), 0) >= e.getValue());
    }

    private void explodeIntoDissolved(ServerWorld serverWorld, PlayerEntity actor) {
        essences.clear();
        water = false;
        dirtyWater = false;
        mossStabilized = false;
        evaporation = 0;
        markDirty();

        serverWorld.removeBlock(pos, false);
        serverWorld.createExplosion(
            null,
            pos.getX() + 0.5,
            pos.getY() + 0.5,
            pos.getZ() + 0.5,
            3.0f,
            World.ExplosionSourceType.NONE
        );
        serverWorld.spawnParticles(ParticleTypes.WITCH,
            pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 35, 0.45, 0.45, 0.45, 0.08);

        DissolvedEntity mutant = new DissolvedEntity(ModEntities.DISSOLVED, serverWorld);
        mutant.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, serverWorld.random.nextFloat() * 360.0f, 0.0f);
        serverWorld.spawnEntity(mutant);

        if (actor != null) {
            message(actor, "message.alkahest.cauldron_exploded");
        }
    }

    private void annihilateOpposites(PlayerEntity actor) {
        for (EssenceType[] pair : ANTIPODE_PAIRS) {
            int a = essences.getOrDefault(pair[0], 0);
            int b = essences.getOrDefault(pair[1], 0);
            int cancel = Math.min(a, b);
            if (cancel > 0) {
                deductEssences(Map.of(pair[0], cancel, pair[1], cancel));
            }
        }
        dirtyWater = true;
        syncWaterState();
        syncEssences();
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.playSound(null, pos, SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 0.95f, 0.85f);
            serverWorld.spawnParticles(ParticleTypes.LARGE_SMOKE,
                pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 28, 0.28, 0.2, 0.28, 0.03);
        }
        if (actor != null) {
            message(actor, "message.alkahest.cauldron_annihilation");
        }
    }

    /** Unified right-click interaction: brews with a ready catalyst, tests an active hypothesis, or dissolves essences directly. */
    public boolean useItem(PlayerEntity player, Hand hand) {
        ItemStack held = player.getStackInHand(hand);
        if (held.isEmpty()) {
            describeEssences(player);
            return false;
        }
        if (held.isOf(Items.WATER_BUCKET)) {
            fill(player, hand);
            return true;
        }
        if (held.getItem() instanceof CrystalWandItem wand) {
            return useWand(player, hand, wand);
        }
        if (held.getItem() instanceof AlkahestAmuletItem amulet) {
            return useAmulet(player, hand, amulet);
        }
        if (held.isOf(ModItems.CRYSTALLIZATION_POWDER)) {
            return crystallizeWith(player, hand);
        }
        if (!water) {
            message(player, "message.alkahest.need_water");
            return false;
        }
        if (!isHeated()) {
            message(player, "message.alkahest.need_heat");
            return false;
        }

        // Catalyst recipes consume the held stack too; never destroy stored contents through that path.
        if (!ItemEssenceMap.safeToConsume(held)) return false;

        // Binary Alkahest synthesis & instability reactions when striking with a Diamond lattice
        if (held.isOf(Items.DIAMOND) && world instanceof ServerWorld serverWorld) {
            if (mossStabilized) {
                if (hasAllAlkahestEssences()) {
                    deductEssences(ALKAHEST_BREW_COST);
                    if (!player.isCreative()) held.decrement(1);
                    mossStabilized = false;
                    setWater(false);
                    evaporation = 0;
                    syncEssences();
                    serverWorld.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.0f, 1.15f);
                    serverWorld.playSound(null, pos, SoundEvents.BLOCK_BREWING_STAND_BREW, SoundCategory.BLOCKS, 0.9f, 1.2f);
                    serverWorld.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING,
                        pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 32, 0.3, 0.25, 0.3, 0.08);
                    serverWorld.spawnParticles(ParticleTypes.END_ROD,
                        pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 18, 0.25, 0.2, 0.25, 0.04);
                    ItemStack vial = new ItemStack(ModItems.ALKAHEST_VIAL);
                    if (!player.giveItemStack(vial)) {
                        ItemEntity dropped = player.dropItem(vial, false);
                        if (dropped != null) dropped.addCommandTag(RETURNED_ITEM_TAG);
                    }
                    message(player, "message.alkahest.alkahest_brewed");
                    return true;
                }
                List<String> missing = new ArrayList<>();
                for (EssenceType type : EssenceType.values()) {
                    int need = ALKAHEST_BREW_COST.getOrDefault(type, 0);
                    int have = essences.getOrDefault(type, 0);
                    if (need > have) missing.add(type.displayName() + " ×" + (need - have));
                }
                player.sendMessage(Text.translatable("message.alkahest.cauldron_missing_essences", String.join(", ", missing)), true);
                return false;
            } else if (hasAllPrima()) {
                if (!player.isCreative()) held.decrement(1);
                explodeIntoDissolved(serverWorld, player);
                return true;
            } else if (hasAnyAntipodePair()) {
                annihilateOpposites(player);
                return true;
            }
        }

        var recipe = CauldronEssenceRecipes.all().stream()
            .filter(r -> r.matches(held, essences))
            .findFirst()
            .orElse(null);
        if (recipe != null) {
            deductEssences(recipe.cost());
            if (!player.isCreative()) held.decrement(1);
            ItemStack output = new ItemStack(recipe.result(), recipe.count());
            setWater(false);
            evaporation = 0;
            syncEssences();
            playBrewFeedback();
            if (player instanceof ServerPlayerEntity researcher) {
                triggerBrewResearch(researcher, output);
            }
            if (!player.giveItemStack(output)) {
                ItemEntity dropped = player.dropItem(output, false);
                if (dropped != null) dropped.addCommandTag(RETURNED_ITEM_TAG);
            }
            message(player, "message.alkahest.cauldron_success");
            return true;
        }

        var perItem = essencesOf(held, world);
        if (!perItem.isEmpty()) {
            boolean wasSalt = held.isOf(ModItems.SALT);
            boolean wasMoss = held.isOf(ModBlocks.ALCHEMICAL_MOSS.asItem());
            int limit = player.isSneaking() ? held.getCount() : 1;
            int dissolved = 0;
            while (dissolved < limit && dissolveOne(held)) {
                dissolved++;
            }
            if (dissolved == 0) {
                message(player, "message.alkahest.cauldron_essence_full");
                return false;
            }
            if (!player.isCreative()) held.decrement(dissolved);
            if (player instanceof ServerPlayerEntity researcher && world != null) {
                if (wasSalt) EarlyResearch.discoverBinding(researcher, world, pos);
                if (wasMoss) EarlyResearch.discoverAlkahest(researcher, world, pos);
            }
            playDissolveFeedback();
            List<String> parts = new ArrayList<>();
            for (var entry : perItem.entrySet()) {
                parts.add(entry.getKey().displayName() + " +" + (entry.getValue() * dissolved));
            }
            player.sendMessage(Text.translatable("message.alkahest.cauldron_dissolved", String.join(", ", parts)), true);
            return true;
        }

        var catalystRecipe = CauldronEssenceRecipes.all().stream()
            .filter(r -> held.isOf(r.catalyst()))
            .findFirst()
            .orElse(null);
        if (catalystRecipe != null) {
            List<String> missing = new ArrayList<>();
            for (EssenceType type : EssenceType.values()) {
                int need = catalystRecipe.cost().getOrDefault(type, 0);
                int have = essences.getOrDefault(type, 0);
                if (need > have) missing.add(type.displayName() + " ×" + (need - have));
            }
            player.sendMessage(Text.translatable("message.alkahest.cauldron_missing_essences", String.join(", ", missing)), true);
        } else {
            message(player, "message.alkahest.cauldron_cannot_dissolve");
        }
        return false;
    }

    private void spawnOutputAbove(ItemStack output) {
        if (world == null || world.isClient || output.isEmpty()) return;
        ItemEntity result = new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 1.15, pos.getZ() + 0.5, output, 0, 0.2, 0);
        result.addCommandTag(RETURNED_ITEM_TAG);
        result.setToDefaultPickupDelay();
        world.spawnEntity(result);
    }

    /** Dropped items only dissolve into essences (or precipitate residue with crystallization dust); catalysts require right-click. */
    private void collectDroppedIngredients(World world, BlockPos pos) {
        if (!water || !isHeated()) return;
        Box opening = new Box(pos.getX() + 0.1875, pos.getY() + 0.25, pos.getZ() + 0.1875,
                              pos.getX() + 0.8125, pos.getY() + 1.0, pos.getZ() + 0.8125);
        for (ItemEntity entity : world.getEntitiesByClass(ItemEntity.class, opening,
                e -> e.isAlive() && !e.getCommandTags().contains(RETURNED_ITEM_TAG))) {
            if (!water) break;
            if (entity.getX() < opening.minX || entity.getX() > opening.maxX ||
                entity.getZ() < opening.minZ || entity.getZ() > opening.maxZ ||
                entity.getY() < opening.minY || entity.getY() > opening.maxY) continue;
            ItemStack dropped = entity.getStack();
            if (dropped.isOf(ModItems.CRYSTALLIZATION_POWDER)) {
                if (crystallize() > 0) dropped.decrement(1);
            } else {
                boolean dissolvedAny = false;
                while (!dropped.isEmpty() && dissolveOne(dropped)) {
                    dropped.decrement(1);
                    dissolvedAny = true;
                }
                if (dissolvedAny) playDissolveFeedback();
            }
            if (dropped.isEmpty()) entity.discard();
            else entity.setStack(dropped);
        }
    }

    /** Convert all possible residue 1:1 into separately typed 5% crystals; no bank mixtures. */
    public int crystallize() {
        if (world == null || world.isClient) return 0;
        int produced = 0;
        for (EssenceType type : EssenceType.values()) {
            int remaining = essences.getOrDefault(type, 0);
            Item crystal = ModItems.CRYSTALS.get(type);
            if (crystal == null || remaining <= 0) continue;
            while (remaining > 0) {
                int count = Math.min(64, remaining);
                ItemStack result = new ItemStack(crystal, count);
                result.set(ModDataComponents.ESSENCE_STABILITY, RESIDUE_STABILITY);
                ItemEntity output = new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 1.15, pos.getZ() + 0.5, result, 0, 0.18, 0);
                output.addCommandTag(RETURNED_ITEM_TAG);
                output.setToDefaultPickupDelay();
                if (!world.spawnEntity(output)) break;
                remaining -= count;
                produced += count;
                if (remaining == 0) essences.remove(type);
                else essences.put(type, remaining);
            }
        }
        mossStabilized = false;
        if (produced > 0) {
            syncEssences();
            world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.8f, 1.2f);
        }
        return produced;
    }

    public boolean crystallizeWith(PlayerEntity player, Hand hand) {
        if (!water) {
            message(player, "message.alkahest.need_water");
            return false;
        }
        if (!isHeated()) {
            message(player, "message.alkahest.need_heat");
            return false;
        }
        int amount = crystallize();
        if (amount == 0) {
            message(player, "message.alkahest.cauldron_no_residue");
            return false;
        }
        if (!player.isCreative()) player.getStackInHand(hand).decrement(1);
        player.sendMessage(Text.translatable("message.alkahest.cauldron_crystallized", amount), true);
        return true;
    }

    public void describeEssences(PlayerEntity player) {
        if (essences.isEmpty()) {
            message(player, water ? "message.alkahest.cauldron_no_residue" : "message.alkahest.need_water");
            return;
        }
        player.sendMessage(Text.translatable("message.alkahest.cauldron_contents"), false);
        if (mossStabilized) {
            player.sendMessage(Text.translatable("message.alkahest.cauldron_moss_stabilized"), false);
        }
        for (EssenceType type : EssenceType.values()) {
            int amount = essences.getOrDefault(type, 0);
            if (amount > 0) {
                player.sendMessage(Text.literal("◆ " + type.displayName() + ": " + amount)
                    .styled(style -> style.withColor(type.color())), false);
            }
        }
    }

    public void ejectLegacyItems() {
        if (world == null || world.isClient) return;
        boolean changed = false;
        for (int i = 0; i < legacyItems.size(); i++) {
            ItemStack legacy = legacyItems.get(i);
            if (!legacy.isEmpty()) {
                spawnOutputAbove(legacy);
                legacyItems.set(i, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) markDirty();
    }

    private void syncEssences() {
        markDirty();
        if (world != null && !world.isClient) {
            BlockState state = world.getBlockState(pos);
            world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
        }
    }

    @Override
    public BlockEntityUpdateS2CPacket toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    private void setWater(boolean value) {
        water = value;
        if (!water) {
            dirtyWater = false;
            mossStabilized = false;
        }
        syncWaterState();
        markDirty();
    }

    private void syncWaterState() {
        if (world == null || world.isClient) return;
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof AlchemyCauldronBlock &&
            (state.get(AlchemyCauldronBlock.FILLED) != water || state.get(AlchemyCauldronBlock.DIRTY) != (water && dirtyWater))) {
            world.setBlockState(pos, state.with(AlchemyCauldronBlock.FILLED, water)
                .with(AlchemyCauldronBlock.DIRTY, water && dirtyWater), Block.NOTIFY_ALL);
        }
    }

    public static void tick(World world, BlockPos pos, CauldronBlockEntity self) {
        self.syncWaterState();
        self.ejectLegacyItems();
        self.collectDroppedIngredients(world, pos);
        if (self.water && self.isHeated() && self.essences.isEmpty()) {
            if (++self.evaporation >= 400) {
                self.setWater(false);
                self.evaporation = 0;
                ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, new ItemStack(ModItems.SALT, 2));
            }
            self.markDirty();
        } else if (self.evaporation != 0) {
            self.evaporation = 0;
            self.markDirty();
        }
    }

    private void message(PlayerEntity player, String key) {
        player.sendMessage(Text.translatable(key), true);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (!legacyItems.stream().allMatch(ItemStack::isEmpty)) {
            Inventories.writeNbt(nbt, legacyItems, registries);
        }
        nbt.putBoolean("Water", water);
        nbt.putBoolean("DirtyWater", dirtyWater);
        nbt.putBoolean("MossStabilized", mossStabilized);
        nbt.putInt("Evaporation", evaporation);
        NbtCompound stored = new NbtCompound();
        essences.forEach((type, amount) -> stored.putInt(type.asString(), amount));
        nbt.put("Essences", stored);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        legacyItems.clear();
        if (nbt.contains("Items")) {
            Inventories.readNbt(nbt, legacyItems, registries);
        }
        water = nbt.getBoolean("Water");
        dirtyWater = water && nbt.getBoolean("DirtyWater");
        mossStabilized = water && nbt.getBoolean("MossStabilized");
        evaporation = nbt.getInt("Evaporation");
        essences.clear();
        NbtCompound stored = nbt.getCompound("Essences");
        for (EssenceType type : EssenceType.values()) {
            int amount = stored.getInt(type.asString());
            if (amount > 0 && type != EssenceType.VACUUM) {
                essences.put(type, Math.min(amount, MAX_PER_TYPE));
            }
        }
        // Rebuild the client mesh when dissolved essence tint updates without a blockstate change.
        if (world != null && world.isClient) {
            BlockState state = world.getBlockState(pos);
            world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
        }
    }
}
