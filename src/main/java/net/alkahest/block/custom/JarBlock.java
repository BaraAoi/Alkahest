package net.alkahest.block.custom;

import net.alkahest.block.entity.JarBlockEntity;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.JarContentsComponent;
import net.alkahest.item.custom.JarItem;
import net.alkahest.registry.ModDataComponents;
import net.alkahest.registry.ModItems;
import net.alkahest.registry.ModBlocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.alkahest.registry.ModBlockEntities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import java.util.List;

public class JarBlock extends Block implements BlockEntityProvider {
    public static final IntProperty LEVEL = IntProperty.of("level", 0, 10);
    private static final VoxelShape SHAPE = Block.createCuboidShape(3, 0, 3, 13, 16, 13);
    public JarBlock(Settings settings) { super(settings); setDefaultState(getStateManager().getDefaultState().with(LEVEL, 0)); }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(LEVEL); }
    // Also repairs the display of jars saved before the level property existed.
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return !world.isClient && type == ModBlockEntities.JAR ? (w, p, s, be) -> ((JarBlockEntity)be).updateLevel() : null;
    }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new JarBlockEntity(pos, state); }
    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return SHAPE; }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return SHAPE; }
    @Override protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (stack.isOf(ModBlocks.ALCHEMY_PIPE.asItem())) {
            if (!world.isClient && !((BlockItem)stack.getItem()).place(new ItemPlacementContext(player, hand, stack, hit)).isAccepted())
                player.sendMessage(Text.translatable("message.alkahest.pipe_no_space"), true);
            return ItemActionResult.success(world.isClient);
        }
        // A normal click with the charger places it on top instead of treating it as jar input.
        if (stack.isOf(ModBlocks.ESSENCE_CHARGER.asItem())) {
            if (!world.isClient) {
                var top = new BlockHitResult(Vec3d.ofCenter(pos).add(0, .5, 0), Direction.UP, pos, false);
                if (!world.isAir(pos.up()) || !((BlockItem)stack.getItem()).place(new ItemPlacementContext(player, hand, stack, top)).isAccepted())
                    player.sendMessage(Text.translatable("message.alkahest.charger_no_space_on_jar"), true);
            }
            return ItemActionResult.success(world.isClient);
        }
        if (world.isClient) return ItemActionResult.SUCCESS;
        if (!(world.getBlockEntity(pos) instanceof JarBlockEntity jar)) return ItemActionResult.FAIL;
        var current = jar.contents();
        if (!JarItem.allowed(current)) { player.sendMessage(Text.translatable("message.alkahest.jar_invalid"), true); return ItemActionResult.SUCCESS; }
        if (stack.isOf(ModItems.CRYSTALLIZATION_POWDER)) {
            crystallizeJar(world, pos, player, stack, jar, current);
            return ItemActionResult.SUCCESS;
        }
        JarContentsComponent addition = null;
        boolean fromJar = stack.getItem() instanceof JarItem;
        if (fromJar) {
            addition = JarItem.contents(stack);
            if (!JarItem.allowed(addition)) { player.sendMessage(Text.translatable("message.alkahest.jar_invalid"), true); return ItemActionResult.SUCCESS; }
            if (addition.total() == 0 && current.total() > 0) {
                if (!JarItem.singleType(current)) {
                    player.sendMessage(Text.translatable("message.alkahest.jar_single_type"), true);
                } else {
                    JarItem.set(stack, current);
                    jar.setContents(JarContentsComponent.EMPTY);
                    world.playSound(null, pos, SoundEvents.ITEM_BOTTLE_FILL, SoundCategory.BLOCKS, .7f, 1.0f);
                    player.sendMessage(Text.translatable("message.alkahest.jar_extracted", current.total()), true);
                }
                return ItemActionResult.SUCCESS;
            }
        } else {
            for (var entry : ModItems.CRYSTALS.entrySet()) if (stack.isOf(entry.getValue())) {
                float stability = stack.getOrDefault(ModDataComponents.ESSENCE_STABILITY, 1.0f);
                addition = new JarContentsComponent(List.of(new EssenceStack(entry.getKey(), 1, stability)));
                break;
            }
        }
        if (addition == null || addition.total() == 0) { describe(player, current); return ItemActionResult.SUCCESS; }
        if (!JarItem.compatible(current, addition)) {
            player.sendMessage(Text.translatable("message.alkahest.jar_single_type"), true);
            return ItemActionResult.SUCCESS;
        }
        int before = current.total(), amount = addition.total();
        jar.setContents(current.merge(addition, JarItem.CAPACITY));
        if (fromJar) JarItem.set(stack, JarContentsComponent.EMPTY);
        else stack.decrementUnlessCreative(1, player);
        if (before + amount > JarItem.CAPACITY) {
            int lost = before + amount - JarItem.CAPACITY;
            net.alkahest.atmosphere.AtmosphereEvents.emit(world, pos, addition.contents().getFirst().type(), lost);
            ((ServerWorld) world).spawnParticles(ParticleTypes.SMOKE, pos.getX()+.5, pos.getY()+.8, pos.getZ()+.5, 12, .15, .1, .15, .02);
            world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, .7f, 1.1f);
            player.sendMessage(Text.translatable("message.alkahest.jar_overflow"), true);
        } else describe(player, jar.contents());
        return ItemActionResult.SUCCESS;
    }
    /** Converts one valid stored type 1:1, preserving its stability and every undelivered unit. */
    private static void crystallizeJar(World world, BlockPos pos, PlayerEntity player, ItemStack powder,
                                       JarBlockEntity jar, JarContentsComponent contents) {
        if (contents.total() == 0) { player.sendMessage(Text.translatable("message.alkahest.jar_crystal_empty"), true); return; }
        if (!JarItem.singleType(contents)) { player.sendMessage(Text.translatable("message.alkahest.jar_single_type"), true); return; }
        EssenceStack essence = contents.contents().getFirst();
        var crystal = ModItems.CRYSTALS.get(essence.type());
        if (crystal == null) { player.sendMessage(Text.translatable("message.alkahest.jar_invalid"), true); return; }
        int remaining = essence.amount();
        while (remaining > 0) {
            int count = Math.min(remaining, new ItemStack(crystal).getMaxCount());
            ItemStack output = new ItemStack(crystal, count);
            output.set(ModDataComponents.ESSENCE_STABILITY, essence.stability());
            // Vanilla's creative insert silently deletes overflow; don't let that erase stored essence.
            if (!player.isCreative() || hasRoomFor(player, output)) player.getInventory().insertStack(output);
            int delivered = count - output.getCount();
            if (!output.isEmpty()) {
                ItemEntity drop = new ItemEntity(world, player.getX(), player.getY() + .5, player.getZ(), output, 0, .05, 0);
                drop.addCommandTag(net.alkahest.block.entity.CauldronBlockEntity.RETURNED_ITEM_TAG);
                drop.setToDefaultPickupDelay();
                if (world.spawnEntity(drop)) delivered += output.getCount();
            }
            remaining -= delivered;
            if (delivered < count) break; // Any output rejected by the world remains in the jar.
        }
        int produced = essence.amount() - remaining;
        if (produced == 0) { player.sendMessage(Text.translatable("message.alkahest.jar_crystal_no_space"), true); return; }
        jar.setContents(remaining == 0 ? JarContentsComponent.EMPTY :
            new JarContentsComponent(List.of(new EssenceStack(essence.type(), remaining, essence.stability()))));
        if (!player.isCreative()) powder.decrement(1);
        world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, .8f, 1.2f);
        player.sendMessage(Text.translatable("message.alkahest.jar_crystallized", produced), true);
    }

    private static boolean hasRoomFor(PlayerEntity player, ItemStack output) {
        int room = 0;
        // Main inventory/hotbar are the slots used for ordinary insertion; ignoring offhand is conservative.
        for (int i = 0; i < 36; i++) {
            ItemStack slot = player.getInventory().getStack(i);
            if (slot.isEmpty()) room += output.getMaxCount();
            else if (ItemStack.areItemsAndComponentsEqual(slot, output)) room += Math.max(0, slot.getMaxCount() - slot.getCount());
            if (room >= output.getCount()) return true;
        }
        return false;
    }

    /** Sneak-use crystals in bulk: never consume beyond free capacity or create a mixed jar. */
    public static ActionResult fillCrystalStack(World world, BlockPos pos, PlayerEntity player, Hand hand) {
        ItemStack held = player.getStackInHand(hand);
        EssenceType type = null;
        for (var entry : ModItems.CRYSTALS.entrySet()) if (held.isOf(entry.getValue())) { type = entry.getKey(); break; }
        if (type == null) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        if (!(world.getBlockEntity(pos) instanceof JarBlockEntity jar)) return ActionResult.FAIL;
        var current = jar.contents();
        if (!JarItem.allowed(current)) { player.sendMessage(Text.translatable("message.alkahest.jar_invalid"), true); return ActionResult.SUCCESS; }
        float stability = held.getOrDefault(ModDataComponents.ESSENCE_STABILITY, 1.0f);
        var one = new JarContentsComponent(List.of(new EssenceStack(type, 1, stability)));
        if (!JarItem.compatible(current, one)) {
            player.sendMessage(Text.translatable("message.alkahest.jar_single_type"), true); return ActionResult.SUCCESS;
        }
        int count = Math.min(held.getCount(), JarItem.CAPACITY - current.total());
        if (count <= 0) { player.sendMessage(Text.translatable("message.alkahest.jar_bulk_full"), true); return ActionResult.SUCCESS; }
        var addition = new JarContentsComponent(List.of(new EssenceStack(type, count, stability)));
        jar.setContents(current.merge(addition, JarItem.CAPACITY));
        if (!player.isCreative()) held.decrement(count);
        player.sendMessage(Text.translatable("message.alkahest.jar_bulk_added", count, jar.contents().total()), true);
        return ActionResult.SUCCESS;
    }
    private static void describe(PlayerEntity player, JarContentsComponent contents) {
        player.sendMessage(Text.translatable("message.alkahest.jar_total", contents.total()), false);
        if (!JarItem.singleType(contents)) player.sendMessage(Text.translatable("message.alkahest.jar_legacy"), false);
        for (EssenceStack e : contents.contents())
            player.sendMessage(Text.literal("◆ " + e.type().displayName() + ": " + e.amount() + " · " + Math.round(e.stability() * 100) + "%").styled(style -> style.withColor(e.type().color())), false);
    }
    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient && world.getBlockEntity(pos) instanceof JarBlockEntity jar) {
            if (player.isSneaking() && player.getMainHandStack().isEmpty()) {
                jar.setPipeSource(!jar.pipeSource());
                player.sendMessage(Text.translatable(jar.pipeSource()
                    ? "message.alkahest.jar_pipe_source_on" : "message.alkahest.jar_pipe_source_off"), true);
            } else {
                describe(player, jar.contents());
                if (jar.pipeSource()) player.sendMessage(Text.translatable("message.alkahest.jar_pipe_source_on"), false);
            }
        }
        return ActionResult.success(world.isClient);
    }
}
