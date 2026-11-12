package net.alkahest.test;

import net.alkahest.block.entity.DistilleryBellowsBlockEntity;
import net.alkahest.block.entity.Tier2DistilleryBlockEntity;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.ItemEssenceMap;
import net.alkahest.essence.JarContentsComponent;
import net.alkahest.item.custom.JarItem;
import net.alkahest.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import java.util.List;

public class Tier2DistilleryGameTests implements FabricGameTest {
    private BlockPos corePos(TestContext c) { return c.getAbsolutePos(new BlockPos(3, 2, 3)); }

    private ItemStack savedItem(TestContext c, ItemStack item) {
        var slots=net.minecraft.util.collection.DefaultedList.ofSize(1,ItemStack.EMPTY); slots.set(0,item.copy());
        var nbt=new net.minecraft.nbt.NbtCompound();
        net.minecraft.inventory.Inventories.writeNbt(nbt,slots,c.getWorld().getRegistryManager());
        var loaded=net.minecraft.util.collection.DefaultedList.ofSize(1,ItemStack.EMPTY);
        net.minecraft.inventory.Inventories.readNbt(nbt,loaded,c.getWorld().getRegistryManager());
        return loaded.get(0);
    }

    private Tier2DistilleryBlockEntity core(TestContext c) {
        return (Tier2DistilleryBlockEntity) c.getWorld().getBlockEntity(corePos(c));
    }

    private void build(TestContext c) {
        c.setBlockState(3, 2, 3, ModBlocks.DISTILLERY_TIER2);
        c.setBlockState(3, 3, 3, ModBlocks.DISTILLERY_BELLOWS);
        c.setBlockState(3, 4, 3, ModBlocks.DISTILLERY_BELLOWS);
        c.setBlockState(3, 5, 3, ModBlocks.DISTILLERY_BELLOWS);
    }

    private void distil(TestContext c, int ticks) {
        for (int i = 0; i < ticks; i++) Tier2DistilleryBlockEntity.tick(c.getWorld(), corePos(c), core(c));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void distilsEveryEssenceTypeIntoBellows(TestContext c) {
        build(c);
        Tier2DistilleryBlockEntity core = core(c);
        c.assertTrue(core.bellows().size() == 3, "Three bellows must be detected above the core");
        core.setStack(0, new ItemStack(Items.APPLE, 2));
        distil(c, Tier2DistilleryBlockEntity.DURATION);

        List<EssenceStack> expected = ItemEssenceMap.get(new ItemStack(Items.APPLE));
        int stored = 0;
        for (DistilleryBellowsBlockEntity bellows : core.bellows()) stored += bellows.amount();
        c.assertTrue(stored > 0, "Essence must accumulate in the bellows");
        for (DistilleryBellowsBlockEntity bellows : core.bellows()) {
            if (bellows.empty()) continue;
            EssenceType type = bellows.storedType();
            c.assertTrue(expected.stream().anyMatch(e -> e.type() == type), "Only item essences may be distilled, got " + type);
            c.assertTrue(bellows.stability() >= 0.55f - 1e-4 && bellows.stability() <= 0.70f + 1e-4,
                "Stable-tier stability expected, got " + bellows.stability());
        }
        c.assertTrue(expected.stream().anyMatch(e -> e.type() == EssenceType.HERBA), "Apple must contain Herba");
        c.assertTrue(core.bellows().stream().anyMatch(b -> b.storedType() == EssenceType.HERBA),
            "The tier-2 cube must distil composite Herba instead of only primaries");
        c.assertTrue(core.getStack(0).getCount() == 1, "Exactly one item must be consumed per cycle");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void jarTakesEssenceFromBellows(TestContext c) {
        build(c);
        Tier2DistilleryBlockEntity core = core(c);
        core.setStack(0, new ItemStack(Items.APPLE));
        distil(c, Tier2DistilleryBlockEntity.DURATION);

        DistilleryBellowsBlockEntity bellows = core.bellows().get(0);
        c.assertFalse(bellows.empty(), "The first bellow must be filled");
        int amount = bellows.amount();
        EssenceType type = bellows.storedType();
        float stability = bellows.stability();

        var player = c.createMockPlayer(GameMode.SURVIVAL);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModBlocks.JAR));
        bellows.pourToJar(player, Hand.MAIN_HAND);

        JarContentsComponent jar = JarItem.contents(player.getStackInHand(Hand.MAIN_HAND));
        c.assertTrue(jar.total() == Math.min(amount, JarItem.CAPACITY), "The jar must take the stored essence");
        c.assertTrue(jar.contents().getFirst().type() == type, "The essence type must be preserved");
        c.assertTrue(Math.abs(jar.contents().getFirst().stability() - stability) < 1e-4f, "Stability must be preserved");
        c.assertTrue(bellows.amount() == Math.max(0, amount - JarItem.CAPACITY), "Leftover essence must stay in the bellows");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void withoutBellowsNothingHappens(TestContext c) {
        c.setBlockState(3, 2, 3, ModBlocks.DISTILLERY_TIER2);
        Tier2DistilleryBlockEntity core = core(c);
        core.setStack(0, new ItemStack(Items.APPLE));
        distil(c, Tier2DistilleryBlockEntity.DURATION * 2);
        c.assertTrue(core.status() == Tier2DistilleryBlockEntity.STATUS_NO_BELLOWS, "Missing bellows has a distinct status");
        c.assertTrue(core.progress() == 0, "Without bellows the cube must not progress");
        c.assertTrue(core.pending().isEmpty(), "Nothing may be produced without bellows");
        c.assertTrue(core.getStack(0).getCount() == 1, "The input must not be consumed");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void bellowsHoldOneTypeOnly(TestContext c) {
        c.setBlockState(3, 3, 3, ModBlocks.DISTILLERY_BELLOWS);
        DistilleryBellowsBlockEntity bellows = (DistilleryBellowsBlockEntity)
            c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(3, 3, 3)));
        c.assertTrue(bellows.accept(EssenceType.IGNIS, 10, 0.6f) == 10, "The first type must be accepted");
        c.assertTrue(bellows.accept(EssenceType.AQUA, 10, 0.6f) == 0, "A second type must be rejected");
        c.assertTrue(bellows.amount() == 10 && bellows.storedType() == EssenceType.IGNIS, "Contents must stay untouched");
        c.assertTrue(bellows.accept(EssenceType.IGNIS, 100, 0.6f) == DistilleryBellowsBlockEntity.CAPACITY - 10,
            "Only the free capacity may be filled");
        c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void matchingBellowsFillBeforeEmptyBellows(TestContext c) {
        build(c); var core=core(c); var bellows=core.bellows();
        bellows.get(1).accept(EssenceType.IGNIS,60,.4f);
        bellows.get(2).accept(EssenceType.AQUA,64,.7f);
        core.pending().add(new EssenceStack(EssenceType.IGNIS,4,.8f));
        core.pending().add(new EssenceStack(EssenceType.TERRA,5,.6f));
        core.setStack(0,new ItemStack(Items.APPLE));
        c.assertTrue(core.status()==Tier2DistilleryBlockEntity.STATUS_WORKING,"Dry-run sees space for both types");
        distil(c,1);
        c.assertTrue(core.pending().isEmpty(),"Both queued types delivered instead of blocking");
        c.assertTrue(bellows.get(1).amount()==64 && bellows.get(1).storedType()==EssenceType.IGNIS,"Matching non-empty bellow is filled first");
        c.assertTrue(Math.abs(bellows.get(1).stability()-.425f)<.0001,"Matching liquid uses weighted stability");
        c.assertTrue(bellows.get(0).storedType()==EssenceType.TERRA && bellows.get(0).amount()==5,"Empty bellow is reserved for new type");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void bellowsRetainContentsOnBreakAndPlacement(TestContext c) {
        c.setBlockState(3,2,3,ModBlocks.DISTILLERY_BELLOWS); BlockPos pos=corePos(c); var world=c.getWorld();
        var b=(DistilleryBellowsBlockEntity)world.getBlockEntity(pos); b.accept(EssenceType.HERBA,37,.42f);
        var expected=new JarContentsComponent(List.of(new EssenceStack(EssenceType.HERBA,37,.42f)));
        var saved=b.createNbt(world.getRegistryManager()); var restored=new DistilleryBellowsBlockEntity(pos,b.getCachedState());
        restored.readComponentlessNbt(saved,world.getRegistryManager());
        c.assertTrue(restored.amount()==37 && restored.storedType()==EssenceType.HERBA,"Old-style world NBT remains valid");
        world.breakBlock(pos,true);
        c.runAtTick(5,()->{
            var drops=world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new net.minecraft.util.math.Box(pos).expand(2),e->e.getStack().isOf(ModBlocks.DISTILLERY_BELLOWS.asItem()));
            c.assertTrue(drops.size()==1 && drops.getFirst().getStack().get(net.alkahest.registry.ModDataComponents.DISTILLERY_CONTENTS).equals(expected),"One dropped bellow retains amount, type and stability");
            ItemStack item=savedItem(c,drops.getFirst().getStack()); drops.getFirst().discard();
            c.assertTrue(expected.equals(item.get(net.alkahest.registry.ModDataComponents.DISTILLERY_CONTENTS)),"Bellows item contents serialize in a saved inventory");
            c.assertTrue(!ItemEssenceMap.safeToConsume(item) && !Tier2DistilleryBlockEntity.accepts(item,world),"Stored essence cannot be destroyed by alchemical recycling");
            BlockPos base=c.getAbsolutePos(new BlockPos(5,1,3)); world.setBlockState(base,net.minecraft.block.Blocks.STONE.getDefaultState());
            var player=c.createMockPlayer(GameMode.SURVIVAL); player.setStackInHand(Hand.MAIN_HAND,item);
            var hit=new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(base),net.minecraft.util.math.Direction.UP,base,false);
            var ctx=new net.minecraft.item.ItemPlacementContext(player,Hand.MAIN_HAND,item,hit);
            c.assertTrue(((net.minecraft.item.BlockItem)item.getItem()).place(ctx).isAccepted(),"Place the real dropped bellow");
            var placed=(DistilleryBellowsBlockEntity)world.getBlockEntity(base.up());
            c.assertTrue(placed.amount()==37 && placed.storedType()==EssenceType.HERBA && Math.abs(placed.stability()-.42f)<.0001,"Placed flask restores contents");
            c.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void blockedCoreRetainsPendingAndDropsInputOnce(TestContext c) {
        c.setBlockState(3,2,3,ModBlocks.DISTILLERY_TIER2); var world=c.getWorld(); BlockPos pos=corePos(c);
        var machine=core(c); machine.setStack(0,new ItemStack(Items.APPLE,3));
        var queued=List.of(new EssenceStack(EssenceType.IGNIS,8,.61f),new EssenceStack(EssenceType.AQUA,5,.73f));
        machine.pending().addAll(queued);
        world.breakBlock(pos,true);
        c.runAtTick(5,()->{
            var area=new net.minecraft.util.math.Box(pos).expand(3);
            var drops=world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,area,e->e.getStack().isOf(ModBlocks.DISTILLERY_TIER2.asItem()));
            c.assertTrue(drops.size()==1,"Exactly one core drops"); ItemStack item=savedItem(c,drops.getFirst().getStack());
            var stored=item.get(net.alkahest.registry.ModDataComponents.DISTILLERY_CONTENTS);
            c.assertTrue(stored!=null && stored.contents().equals(queued),"Both queued fractions and their stability survive on core item");
            int apples=world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,area,e->e.getStack().isOf(Items.APPLE)).stream().mapToInt(e->e.getStack().getCount()).sum();
            c.assertTrue(apples==3 && !ItemEssenceMap.safeToConsume(item),"Input drops exactly once and queued product cannot be recycled away");
            drops.getFirst().discard();
            BlockPos base=c.getAbsolutePos(new BlockPos(5,1,3)); world.setBlockState(base,net.minecraft.block.Blocks.STONE.getDefaultState());
            var player=c.createMockPlayer(GameMode.SURVIVAL); player.setStackInHand(Hand.MAIN_HAND,item);
            var hit=new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(base),net.minecraft.util.math.Direction.UP,base,false);
            c.assertTrue(((net.minecraft.item.BlockItem)item.getItem()).place(new net.minecraft.item.ItemPlacementContext(player,Hand.MAIN_HAND,item,hit)).isAccepted(),"Core can be placed again");
            var placed=(Tier2DistilleryBlockEntity)world.getBlockEntity(base.up());
            c.assertTrue(placed.pending().equals(queued) && placed.getStack(0).isEmpty() && placed.status()==Tier2DistilleryBlockEntity.STATUS_NO_BELLOWS,"Queued output restored without duplicating input");
            c.setBlockState(5,3,3,ModBlocks.DISTILLERY_BELLOWS); c.setBlockState(5,4,3,ModBlocks.DISTILLERY_BELLOWS);
            Tier2DistilleryBlockEntity.tick(world,base.up(),placed);
            c.assertTrue(placed.pending().isEmpty() && placed.bellows().stream().mapToInt(DistilleryBellowsBlockEntity::amount).sum()==13,"Queued output resumes into new bellows");
            c.complete();
        });
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void largeBatchDrainsInPortionsWithoutLossOrExtraInputs(TestContext c) {
        c.setBlockState(3,2,3,ModBlocks.DISTILLERY_TIER2);
        c.setBlockState(3,3,3,ModBlocks.DISTILLERY_BELLOWS);
        var core=core(c); var bellows=core.bellows().getFirst(); var world=c.getWorld();
        var input=new ItemStack(Items.DIAMOND_BLOCK,2);
        var expected=new java.util.EnumMap<EssenceType,Integer>(EssenceType.class);
        Tier2DistilleryBlockEntity.outputs(input,world.random,false,world).forEach(e->expected.put(e.type(),e.amount()));
        c.assertTrue(expected.getOrDefault(EssenceType.TERRA,0)>64,"A complex item yields more than one bellow of Terra");
        core.setStack(0,input); distil(c,Tier2DistilleryBlockEntity.DURATION);
        c.assertTrue(core.getStack(0).getCount()==1 && !core.pending().isEmpty() && bellows.amount()==64,
            "First item is processed once; overflow remains queued");
        var stability=new java.util.EnumMap<EssenceType,Float>(EssenceType.class);
        if(!bellows.empty()) stability.put(bellows.storedType(),bellows.stability());
        core.pending().forEach(e->stability.put(e.type(),e.stability()));
        var restored=new Tier2DistilleryBlockEntity(corePos(c),core.getCachedState());
        restored.readComponentlessNbt(core.createNbt(world.getRegistryManager()),world.getRegistryManager());
        c.assertTrue(restored.pending().equals(core.pending()),"Partial batch and its stability survive world NBT");
        var obtained=new java.util.EnumMap<EssenceType,Integer>(EssenceType.class);
        for(int i=0;i<100 && (!core.pending().isEmpty() || !bellows.empty());i++) {
            if(!bellows.empty()) {
                var type=bellows.storedType(); int count=bellows.amount();
                c.assertTrue(Math.abs(bellows.stability()-stability.get(type))<.0001,"Partial output keeps its original stability");
                obtained.merge(type,bellows.drain(count),Integer::sum);
            }
            distil(c,1);
            c.assertTrue(core.getStack(0).getCount()==1,"Blocked queue cannot consume a second item");
        }
        c.assertTrue(core.pending().isEmpty() && bellows.empty() && obtained.equals(expected),
            "Every type is eventually extracted in full exactly once: expected="+expected+" obtained="+obtained);
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void hugeQueuedCoreRemainsPortableAndSafe(TestContext c) {
        c.setBlockState(3,2,3,ModBlocks.DISTILLERY_TIER2); var world=c.getWorld(); BlockPos pos=corePos(c);
        var core=core(c); var expected=List.of(new EssenceStack(EssenceType.IGNIS,500,.6f),new EssenceStack(EssenceType.ORDO,500,.8f));
        core.pending().addAll(expected); world.breakBlock(pos,true);
        c.runAtTick(5,()->{
            var drops=world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new net.minecraft.util.math.Box(pos).expand(2),e->e.getStack().isOf(ModBlocks.DISTILLERY_TIER2.asItem()));
            c.assertTrue(drops.size()==1,"One portable core drops");
            ItemStack item=savedItem(c,drops.getFirst().getStack());
            var stored=item.get(net.alkahest.registry.ModDataComponents.DISTILLERY_CONTENTS);
            c.assertTrue(stored!=null && stored.contents().equals(expected) && stored.total()==1000,"Portable component and inventory NBT preserve a queue above 500");
            c.assertFalse(ItemEssenceMap.safeToConsume(item),"Portable queued essence is protected from recycling");
            drops.getFirst().discard(); BlockPos base=c.getAbsolutePos(new BlockPos(5,1,3)); world.setBlockState(base,net.minecraft.block.Blocks.STONE.getDefaultState());
            var player=c.createMockPlayer(GameMode.SURVIVAL); player.setStackInHand(Hand.MAIN_HAND,item);
            var hit=new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(base),net.minecraft.util.math.Direction.UP,base,false);
            c.assertTrue(((net.minecraft.item.BlockItem)item.getItem()).place(new net.minecraft.item.ItemPlacementContext(player,Hand.MAIN_HAND,item,hit)).isAccepted(),"Core places again");
            var placed=(Tier2DistilleryBlockEntity)world.getBlockEntity(base.up());
            c.assertTrue(placed.pending().equals(expected),"Large portable queue restores exactly");
            c.setBlockState(5,3,3,ModBlocks.DISTILLERY_BELLOWS);
            Tier2DistilleryBlockEntity.tick(world,base.up(),placed);
            c.assertTrue(placed.bellows().getFirst().amount()==64 && placed.pending().stream().mapToInt(EssenceStack::amount).sum()==936,
                "Large restored queue releases partially and retains the remainder");
            c.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void gelumAndSnowballsKeepTheirPreviousYield(TestContext c) {
        var world=c.getWorld();
        var snow=Tier2DistilleryBlockEntity.outputs(new ItemStack(Items.SNOWBALL),world.random,false,world);
        c.assertTrue(snow.stream().anyMatch(e->e.type()==EssenceType.AQUA && e.amount()==1)
            && snow.stream().anyMatch(e->e.type()==EssenceType.GELUM && e.amount()==1),"Snowball yield unchanged");
        var ice=Tier2DistilleryBlockEntity.outputs(new ItemStack(Items.BLUE_ICE),world.random,false,world);
        c.assertTrue(ice.stream().anyMatch(e->e.type()==EssenceType.GELUM && e.amount()==8),"Existing Gelum limit remains unchanged");
        c.complete();
    }
}
