package net.alkahest.block.custom;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.joml.Vector3f;

public class AlchemicalMossBlock extends Block {
    private static final VoxelShape SHAPE = Block.createCuboidShape(1.0, 0.0, 1.0, 15.0, 6.0, 15.0);
    private static final DustParticleEffect SPORE = new DustParticleEffect(new Vector3f(0.62f, 0.30f, 0.94f), 0.7f);

    public AlchemicalMossBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        BlockPos belowPos = pos.down();
        BlockState below = world.getBlockState(belowPos);
        return below.isIn(BlockTags.DIRT)
            || below.isIn(BlockTags.BASE_STONE_OVERWORLD)
            || below.isOf(Blocks.MOSS_BLOCK)
            || below.isOf(Blocks.AMETHYST_BLOCK)
            || below.isSideSolidFullSquare(world, belowPos, Direction.UP);
    }

    @Override
    protected BlockState getStateForNeighborUpdate(
        BlockState state,
        Direction direction,
        BlockState neighborState,
        WorldAccess world,
        BlockPos pos,
        BlockPos neighborPos
    ) {
        if (direction == Direction.DOWN && !state.canPlaceAt(world, pos)) {
            return Blocks.AIR.getDefaultState();
        }
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (random.nextInt(7) == 0) {
            double x = pos.getX() + 0.2 + random.nextDouble() * 0.6;
            double y = pos.getY() + 0.15 + random.nextDouble() * 0.35;
            double z = pos.getZ() + 0.2 + random.nextDouble() * 0.6;
            world.addParticle(SPORE, x, y, z, 0.0, 0.015, 0.0);
        }
    }
}
