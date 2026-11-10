package net.alkahest.block.entity;

import net.alkahest.block.custom.AlchemyPipeBlock;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.JarContentsComponent;
import net.alkahest.item.custom.JarItem;
import net.alkahest.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Early pipeline: transfer atomically between jars and bellows, never buffering or loading chunks. */
public class AlchemyPipeBlockEntity extends BlockEntity {
    private static final int MAX_SEGMENTS = 64;
    private EssenceType filter;
    public AlchemyPipeBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.ALCHEMY_PIPE,pos,state); }
    public EssenceType filter() { return filter; }
    public void nextFilter() {
        var all=EssenceType.values();
        int next = filter == null ? 0 : filter.ordinal()+1;
        filter = next >= all.length || all[next] == EssenceType.VACUUM ? null : all[next];
        markDirty();
        if (world != null && !world.isClient) {
            BlockState state = world.getBlockState(pos);
            world.updateListeners(pos, state, state, net.minecraft.block.Block.NOTIFY_LISTENERS);
        }
    }
    @Override public net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket toUpdatePacket() {
        return net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket.create(this);
    }
    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registry) { return createNbt(registry); }
    public void describe(net.minecraft.entity.player.PlayerEntity player) {
        Direction out = world.getBlockState(pos).get(AlchemyPipeBlock.FACING);
        player.sendMessage(Text.translatable("message.alkahest.pipe_status", Text.translatable("direction.alkahest."+out.asString()),
            filter == null ? Text.translatable("message.alkahest.pipe_all") : Text.literal(filter.displayName())),true);
    }
    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registry) {
        super.writeNbt(nbt,registry); if (filter != null) nbt.putString("Filter",filter.asString());
    }
    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registry) {
        super.readNbt(nbt,registry); filter=null;
        for (EssenceType t:EssenceType.values()) if (t != EssenceType.VACUUM && t.asString().equals(nbt.getString("Filter"))) filter=t;
    }
    public static void tick(World world, BlockPos pos, BlockState state) {
        if (world.getTime() % 10 != 0) return;
        Direction out=state.get(AlchemyPipeBlock.FACING);
        BlockPos input=pos.offset(out.getOpposite());
        if (!world.isChunkLoaded(input)) return;
        BlockEntity source = world.getBlockEntity(input);
        if (!isPort(source) || portContents(source).total() == 0) return;
        transfer(world,pos,source);
    }

    /** Jars and distillation bellows both act as pipe endpoints. */
    private static boolean isPort(BlockEntity be) {
        return be instanceof JarBlockEntity || be instanceof DistilleryBellowsBlockEntity;
    }

    /** Bellows are a single-type container, so they are read as a one-entry jar. */
    private static JarContentsComponent portContents(BlockEntity be) {
        if (be instanceof JarBlockEntity jar) return jar.contents();
        if (be instanceof DistilleryBellowsBlockEntity bellows) {
            return bellows.empty() ? JarContentsComponent.EMPTY
                : new JarContentsComponent(List.of(new EssenceStack(bellows.storedType(), bellows.amount(), bellows.stability())));
        }
        return JarContentsComponent.EMPTY;
    }

    /** Room left for {@code type}; zero blocks the transfer. */
    private static int portFree(BlockEntity be, EssenceType type) {
        if (be instanceof JarBlockEntity jar) {
            JarContentsComponent contents = jar.contents();
            if (!JarItem.allowed(contents) || !JarItem.singleType(contents)) return 0;
            if (contents.total() > 0 && contents.contents().getFirst().type() != type) return 0;
            return JarItem.CAPACITY - contents.total();
        }
        if (be instanceof DistilleryBellowsBlockEntity bellows) {
            if (bellows.empty()) return DistilleryBellowsBlockEntity.CAPACITY;
            if (bellows.storedType() != type) return 0;
            return DistilleryBellowsBlockEntity.CAPACITY - bellows.amount();
        }
        return 0;
    }

    private static void portAdd(BlockEntity be, EssenceStack unit) {
        if (be instanceof JarBlockEntity jar) {
            jar.setContents(jar.contents().merge(new JarContentsComponent(List.of(unit)), JarItem.CAPACITY));
        } else if (be instanceof DistilleryBellowsBlockEntity bellows) {
            bellows.accept(unit.type(), unit.amount(), unit.stability());
        }
    }

    private static void portRemove(BlockEntity be, EssenceStack stack) {
        if (be instanceof JarBlockEntity jar) {
            List<EssenceStack> remainder = new ArrayList<>(jar.contents().contents());
            remainder.remove(stack);
            if (stack.amount() > 1) remainder.add(new EssenceStack(stack.type(), stack.amount() - 1, stack.stability()));
            jar.setContents(new JarContentsComponent(remainder));
        } else if (be instanceof DistilleryBellowsBlockEntity bellows) {
            bellows.drain(1);
        }
    }
    /** Walk a directed route once, then atomically move one matching unit to a jar with room. */
    public static boolean transfer(World world, BlockPos start, BlockEntity source) {
        if (world.isClient || !world.isChunkLoaded(start) || !isPort(source)) return false;
        JarContentsComponent sourceContents = portContents(source);
        if (!JarItem.allowed(sourceContents) || sourceContents.total() == 0) return false;
        BlockState first = world.getBlockState(start);
        if (!(first.getBlock() instanceof AlchemyPipeBlock) || !start.offset(first.get(AlchemyPipeBlock.FACING).getOpposite()).equals(source.getPos())) return false;
        Set<BlockPos> visited=new HashSet<>();
        List<EssenceType> required=new ArrayList<>();
        BlockPos cursor=start;
        for (int i=0; i<MAX_SEGMENTS; i++) {
            if (!world.isChunkLoaded(cursor) || !visited.add(cursor) || !(world.getBlockEntity(cursor) instanceof AlchemyPipeBlockEntity pipe)) return false;
            if (pipe.filter != null) required.add(pipe.filter);
            BlockState state=world.getBlockState(cursor);
            if (!(state.getBlock() instanceof AlchemyPipeBlock)) return false;
            BlockPos next=cursor.offset(state.get(AlchemyPipeBlock.FACING));
            if (!world.isChunkLoaded(next)) return false;
            BlockEntity target = world.getBlockEntity(next);
            if (isPort(target)) {
                if (target == source) return false;
                JarContentsComponent targetContents = portContents(target);
                EssenceType occupied = targetContents.total() == 0 ? null : targetContents.contents().getFirst().type();
                var item=sourceContents.contents().stream().filter(e -> e.type() != EssenceType.VACUUM &&
                    required.stream().allMatch(f -> f == e.type()) &&
                    (occupied == null || occupied == e.type())).findFirst().orElse(null);
                if (item == null || portFree(target, item.type()) <= 0) return false;
                // Both updates run synchronously on the server tick; no contents are held by pipes.
                portAdd(target, new EssenceStack(item.type(), 1, item.stability()));
                portRemove(source, item);
                return true;
            }
            cursor=next;
        }
        return false;
    }
}
