package net.alkahest.block.custom;

import net.alkahest.block.entity.JarBlockEntity;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.JarContentsComponent;
import net.alkahest.item.custom.JarItem;
import net.alkahest.registry.ModDataComponents;
import net.alkahest.registry.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
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
    private static final VoxelShape SHAPE = Block.createCuboidShape(3, 0, 3, 13, 13, 13);
    public JarBlock(Settings settings) { super(settings); }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new JarBlockEntity(pos, state); }
    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return SHAPE; }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return SHAPE; }
    @Override protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (world.isClient) return ItemActionResult.SUCCESS;
        if (!(world.getBlockEntity(pos) instanceof JarBlockEntity jar)) return ItemActionResult.FAIL;
        var current = jar.contents();
        if (!JarItem.allowed(current)) { player.sendMessage(Text.translatable("message.alkahest.jar_invalid"), true); return ItemActionResult.SUCCESS; }
        JarContentsComponent addition = null;
        boolean fromJar = stack.getItem() instanceof JarItem;
        if (fromJar) {
            addition = JarItem.contents(stack);
            if (!JarItem.allowed(addition)) { player.sendMessage(Text.translatable("message.alkahest.jar_invalid"), true); return ItemActionResult.SUCCESS; }
        } else {
            for (var entry : ModItems.CRYSTALS.entrySet()) if (stack.isOf(entry.getValue())) {
                float stability = stack.getOrDefault(ModDataComponents.ESSENCE_STABILITY, 1.0f);
                addition = new JarContentsComponent(List.of(new EssenceStack(entry.getKey(), 1, stability)));
                break;
            }
        }
        if (addition == null || addition.total() == 0) { describe(player, current); return ItemActionResult.SUCCESS; }
        int before = current.total(), amount = addition.total();
        jar.setContents(current.merge(addition, JarItem.CAPACITY));
        if (fromJar) JarItem.set(stack, JarContentsComponent.EMPTY);
        else stack.decrementUnlessCreative(1, player);
        if (before + amount > JarItem.CAPACITY) {
            ((ServerWorld) world).spawnParticles(ParticleTypes.SMOKE, pos.getX()+.5, pos.getY()+.8, pos.getZ()+.5, 12, .15, .1, .15, .02);
            world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, .7f, 1.1f);
            player.sendMessage(Text.translatable("message.alkahest.jar_overflow"), true);
        } else describe(player, jar.contents());
        return ItemActionResult.SUCCESS;
    }
    private static void describe(PlayerEntity player, JarContentsComponent contents) {
        player.sendMessage(Text.translatable("message.alkahest.jar_total", contents.total()), false);
        for (EssenceStack e : contents.contents())
            player.sendMessage(Text.literal("◆ " + e.type().displayName() + ": " + e.amount() + " · " + Math.round(e.stability() * 100) + "%").styled(style -> style.withColor(e.type().color())), false);
    }
    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient && world.getBlockEntity(pos) instanceof JarBlockEntity jar) describe(player, jar.contents());
        return ActionResult.success(world.isClient);
    }
}
