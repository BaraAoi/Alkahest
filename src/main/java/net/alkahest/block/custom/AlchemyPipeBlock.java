package net.alkahest.block.custom;

import net.alkahest.block.entity.AlchemyPipeBlockEntity;
import net.alkahest.registry.ModBlockEntities;
import net.alkahest.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

public class AlchemyPipeBlock extends Block implements BlockEntityProvider {
    /** ACTIVE drives the working animation; optional filters live in the block entity. */
    public static final BooleanProperty ACTIVE = BooleanProperty.of("active");
    public static final BooleanProperty NORTH=Properties.NORTH, SOUTH=Properties.SOUTH, EAST=Properties.EAST,
        WEST=Properties.WEST, UP=Properties.UP, DOWN=Properties.DOWN;
    private static final VoxelShape CORE = Block.createCuboidShape(6,6,6,10,10,10);
    public AlchemyPipeBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(ACTIVE, false)
            .with(NORTH,false).with(SOUTH,false).with(EAST,false).with(WEST,false).with(UP,false).with(DOWN,false));
    }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> b) { b.add(ACTIVE,NORTH,SOUTH,EAST,WEST,UP,DOWN); }
    public static BooleanProperty side(Direction d) { return switch (d) {
        case NORTH -> NORTH; case SOUTH -> SOUTH; case EAST -> EAST; case WEST -> WEST; case UP -> UP; case DOWN -> DOWN;
    }; }
    private static boolean connects(BlockState state) { return state.isOf(ModBlocks.ALCHEMY_PIPE) || state.isOf(ModBlocks.JAR)
        || state.isOf(ModBlocks.DISTILLERY_BELLOWS) || state.isOf(ModBlocks.TRANSMUTATOR_CORE)
        || state.isOf(ModBlocks.STABILIZER_RECEIVER); }
    @Override public BlockState getPlacementState(ItemPlacementContext context) {
        BlockPos placed = context.getBlockPos();
        var state = getDefaultState();
        for (Direction d : Direction.values()) {
            state = state.with(side(d), connects(context.getWorld().getBlockState(placed.offset(d))));
        }
        return state;
    }
    @Override protected BlockState getStateForNeighborUpdate(BlockState state, Direction d, BlockState neighbor, WorldAccess world, BlockPos pos, BlockPos other) {
        return state.with(side(d), connects(neighbor));
    }
    @Override protected VoxelShape getOutlineShape(BlockState s, BlockView world, BlockPos pos, ShapeContext ctx) {
        VoxelShape shape = CORE;
        if (s.get(NORTH)) shape = VoxelShapes.union(shape,Block.createCuboidShape(6,6,0,10,10,6));
        if (s.get(SOUTH)) shape = VoxelShapes.union(shape,Block.createCuboidShape(6,6,10,10,10,16));
        if (s.get(WEST)) shape = VoxelShapes.union(shape,Block.createCuboidShape(0,6,6,6,10,10));
        if (s.get(EAST)) shape = VoxelShapes.union(shape,Block.createCuboidShape(10,6,6,16,10,10));
        if (s.get(DOWN)) shape = VoxelShapes.union(shape,Block.createCuboidShape(6,0,6,10,6,10));
        if (s.get(UP)) shape = VoxelShapes.union(shape,Block.createCuboidShape(6,10,6,10,16,10));
        return shape;
    }
    @Override protected VoxelShape getCollisionShape(BlockState s, BlockView world, BlockPos pos, ShapeContext ctx) { return getOutlineShape(s,world,pos,ctx); }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new AlchemyPipeBlockEntity(pos,state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return !world.isClient && type == ModBlockEntities.ALCHEMY_PIPE ? (w,p,s,be) -> AlchemyPipeBlockEntity.tick(w,p,s) : null;
    }
    @Override protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        // Allow a second pipe to be attached by an ordinary click, not only while sneaking.
        if (stack.isOf(ModBlocks.ALCHEMY_PIPE.asItem())) {
            if (!world.isClient && !((BlockItem)stack.getItem()).place(new ItemPlacementContext(player,hand,stack,hit)).isAccepted())
                player.sendMessage(Text.translatable("message.alkahest.pipe_no_space"), true);
            return ItemActionResult.success(world.isClient);
        }
        // Normal wand or amulet click cycles the exit face. Sneak-use remains the filter.
        if (!player.isSneaking() && (stack.getItem() instanceof net.alkahest.item.custom.CrystalWandItem
            || stack.getItem() instanceof net.alkahest.item.custom.AlkahestAmuletItem)) {
            if (!world.isClient && world.getBlockEntity(pos) instanceof AlchemyPipeBlockEntity pipe)
                pipe.cycleDirection(player);
            return ItemActionResult.success(world.isClient);
        }
        // Select a filter directly with a crystal, without consuming it.
        for (var entry : net.alkahest.registry.ModItems.CRYSTALS.entrySet()) {
            if (entry.getKey() != net.alkahest.essence.EssenceType.VACUUM && stack.isOf(entry.getValue())) {
                if (!world.isClient && world.getBlockEntity(pos) instanceof AlchemyPipeBlockEntity pipe)
                    pipe.selectFilter(entry.getKey(), player);
                return ItemActionResult.success(world.isClient);
            }
        }
        return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient && world.getBlockEntity(pos) instanceof AlchemyPipeBlockEntity pipe) {
            if (player.isSneaking() && player.getMainHandStack().isEmpty()) pipe.cycleValve(player);
            else pipe.describe(player);
        }
        return ActionResult.success(world.isClient);
    }
}
