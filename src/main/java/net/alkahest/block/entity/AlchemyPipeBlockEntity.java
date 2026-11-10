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

/** Early pipeline: transfer atomically between jars, never buffering or loading chunks. */
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
    }
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
        if (!world.isChunkLoaded(input) || !(world.getBlockEntity(input) instanceof JarBlockEntity source)) return;
        transfer(world,pos,source);
    }
    /** Walk a directed route once, then atomically move one matching unit to a jar with room. */
    public static boolean transfer(World world, BlockPos start, JarBlockEntity source) {
        if (world.isClient || !world.isChunkLoaded(start) || !JarItem.allowed(source.contents()) || source.contents().total() == 0) return false;
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
            if (world.getBlockEntity(next) instanceof JarBlockEntity target) {
                if (target == source || !JarItem.allowed(target.contents()) || target.contents().total() >= JarItem.CAPACITY) return false;
                var item=source.contents().contents().stream().filter(e -> e.type() != EssenceType.VACUUM && required.stream().allMatch(f -> f == e.type())).findFirst().orElse(null);
                if (item == null) return false;
                List<EssenceStack> remainder=new ArrayList<>(source.contents().contents());
                remainder.remove(item);
                if (item.amount()>1) remainder.add(new EssenceStack(item.type(),item.amount()-1,item.stability()));
                JarContentsComponent addition=new JarContentsComponent(List.of(new EssenceStack(item.type(),1,item.stability())));
                // Both updates run synchronously on the server tick; no contents are held by pipes.
                target.setContents(target.contents().merge(addition,JarItem.CAPACITY));
                source.setContents(new JarContentsComponent(remainder));
                return true;
            }
            cursor=next;
        }
        return false;
    }
}
