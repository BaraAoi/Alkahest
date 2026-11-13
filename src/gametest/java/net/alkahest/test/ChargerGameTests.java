package net.alkahest.test;

import net.alkahest.block.entity.EssenceChargerBlockEntity;
import net.alkahest.block.entity.JarBlockEntity;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.JarContentsComponent;
import net.alkahest.item.custom.JarItem;
import net.alkahest.registry.ModBlocks;
import net.alkahest.registry.ModDataComponents;
import net.alkahest.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import java.util.List;

public class ChargerGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void preciseChargeAndRejections(TestContext context) {
        context.setBlockState(3, 3, 3, ModBlocks.JAR);
        BlockPos pos = context.getAbsolutePos(new BlockPos(3, 3, 3));
        var jar = (JarBlockEntity) context.getWorld().getBlockEntity(pos);
        jar.setContents(new JarContentsComponent(List.of(new EssenceStack(EssenceType.ORDO, 2, .25f), new EssenceStack(EssenceType.AQUA, 3, .4f))));
        var player = context.createMockPlayer(GameMode.SURVIVAL);
        var wand = new ItemStack(ModItems.WANDS.get(EssenceType.ORDO));
        wand.set(ModDataComponents.ESSENCE_CHARGE, 85); player.getInventory().setStack(0, wand);
        context.assertTrue(EssenceChargerBlockEntity.chargeFirst(jar, player) == EssenceType.ORDO, "Matching wand must charge");
        context.assertTrue(wand.get(ModDataComponents.ESSENCE_CHARGE) == 95 && jar.contents().total() == 4, "One unit restores ten");
        EssenceChargerBlockEntity.chargeFirst(jar, player);
        context.assertTrue(wand.get(ModDataComponents.ESSENCE_CHARGE) == 100 && jar.contents().total() == 3, "Charge is capped and consumes one unit");
        context.assertTrue(EssenceChargerBlockEntity.chargeFirst(jar, player) == null && jar.contents().total() == 3, "Full wand must not consume essence");
        wand.set(ModDataComponents.ESSENCE_CHARGE, 50);
        context.assertTrue(EssenceChargerBlockEntity.chargeFirst(jar, player) == null && jar.contents().total() == 3, "Wrong essence must not be consumed");
        var aqua = jar.contents().contents().get(0);
        context.assertTrue(aqua.type() == EssenceType.AQUA && Math.abs(aqua.stability() - .4f) < .001, "Other essence and stability are preserved");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void placementAndNearbySelection(TestContext context) {
        BlockPos pos = context.getAbsolutePos(new BlockPos(3, 2, 3));
        context.setBlockState(3, 2, 3, ModBlocks.ESSENCE_CHARGER);
        // A mock player is not tracked by the world; inject it into the selection scan.
        var player = context.createMockPlayer(GameMode.CREATIVE);
        player.setPosition(pos.getX() + 2.5, pos.getY(), pos.getZ() + .5);
        ItemStack jarItem = new ItemStack(ModBlocks.JAR);
        JarItem.set(jarItem, new JarContentsComponent(List.of(new EssenceStack(EssenceType.ORDO, 3, .5f))));
        player.setStackInHand(Hand.MAIN_HAND, jarItem);
        var hit = new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(pos), net.minecraft.util.math.Direction.NORTH, pos, false);
        context.getWorld().getBlockState(pos).onUseWithItem(jarItem, context.getWorld(), player, Hand.MAIN_HAND, hit);
        var placed = context.getWorld().getBlockEntity(pos.up());
        context.assertTrue(placed instanceof JarBlockEntity, "Right click with jar places it above charger");
        var jar = (JarBlockEntity) placed;
        context.assertTrue(jar.contents().total() == 3, "Placed jar retains contents");
        player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        ItemStack wand = new ItemStack(ModItems.WANDS.get(EssenceType.ORDO));
        wand.set(ModDataComponents.ESSENCE_CHARGE, 60); player.getInventory().setStack(0, wand);
        var result = EssenceChargerBlockEntity.chargeNearby(jar, pos, List.of(player));
        context.assertTrue(result != null && result.player() == player && result.essence() == EssenceType.ORDO, "Nearby scan selects a matching player and type");
        context.assertTrue(wand.get(ModDataComponents.ESSENCE_CHARGE) == 70 && jar.contents().total() == 2, "Scan charges a wand and consumes one unit");
        player.setPosition(pos.getX() + 48.5, pos.getY(), pos.getZ());
        context.assertTrue(EssenceChargerBlockEntity.chargeNearby(jar, pos, List.of(player)) == null, "Player beyond the cube is ignored");
        context.assertTrue(wand.get(ModDataComponents.ESSENCE_CHARGE) == 70 && jar.contents().total() == 2, "Out of range must not consume essence");
        context.getWorld().breakBlock(pos, true);
        context.assertTrue(context.getWorld().getBlockEntity(pos.up()) == placed, "Breaking charger must not destroy or duplicate its jar");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void chargerMountsOnJarAndRelinks(TestContext context) {
        BlockPos jarPos = context.getAbsolutePos(new BlockPos(3, 2, 3));
        context.setBlockState(3, 2, 3, ModBlocks.JAR);
        var jar = (JarBlockEntity) context.getWorld().getBlockEntity(jarPos);
        jar.setContents(new JarContentsComponent(List.of(new EssenceStack(EssenceType.ORDO, 4, .5f))));
        var player = context.createMockPlayer(GameMode.SURVIVAL);
        player.setPosition(jarPos.getX() + 2.5, jarPos.getY(), jarPos.getZ() + .5);
        var chargers = new ItemStack(ModBlocks.ESSENCE_CHARGER, 2);
        player.setStackInHand(Hand.MAIN_HAND, chargers);
        var hit = new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(jarPos), net.minecraft.util.math.Direction.NORTH, jarPos, false);
        context.getWorld().getBlockState(jarPos).onUseWithItem(chargers, context.getWorld(), player, Hand.MAIN_HAND, hit);
        BlockPos chargerPos = jarPos.up();
        context.assertTrue(context.getWorld().getBlockState(chargerPos).isOf(ModBlocks.ESSENCE_CHARGER), "Clicking the jar with a charger places it above, even on the side");
        context.assertTrue(chargers.getCount() == 1, "Placing consumes exactly one charger");
        context.assertTrue(context.getWorld().getBlockState(chargerPos).get(net.alkahest.block.custom.EssenceChargerBlock.MOUNTED), "Placed charger displays the mounted shape");
        context.assertTrue(EssenceChargerBlockEntity.findJar(context.getWorld(), chargerPos) == jar, "Charger automatically links to the jar below");
        context.getWorld().getBlockState(jarPos).onUseWithItem(chargers, context.getWorld(), player, Hand.MAIN_HAND, hit);
        context.assertTrue(chargers.getCount() == 1 && jar.contents().total() == 4, "Blocked placement must not consume items or contents");
        var wand = new ItemStack(ModItems.WANDS.get(EssenceType.ORDO));
        wand.set(ModDataComponents.ESSENCE_CHARGE, 40); player.getInventory().setStack(0, wand);
        context.assertTrue(EssenceChargerBlockEntity.chargeNearby(EssenceChargerBlockEntity.findJar(context.getWorld(), chargerPos), chargerPos, List.of(player)) != null, "Linked jar supplies nearby wand");
        context.assertTrue(jar.contents().total() == 3 && wand.get(ModDataComponents.ESSENCE_CHARGE) == 50, "Linked charging consumes the correct jar");
        context.getWorld().breakBlock(jarPos, true);
        context.assertTrue(EssenceChargerBlockEntity.findJar(context.getWorld(), chargerPos) == null, "Removing the jar detaches it");
        context.assertFalse(context.getWorld().getBlockState(chargerPos).get(net.alkahest.block.custom.EssenceChargerBlock.MOUNTED), "Removed jar updates appearance");
        context.getWorld().setBlockState(jarPos, ModBlocks.JAR.getDefaultState());
        context.assertTrue(EssenceChargerBlockEntity.findJar(context.getWorld(), chargerPos) == context.getWorld().getBlockEntity(jarPos), "Replacement jar links automatically");
        context.assertTrue(context.getWorld().getBlockState(chargerPos).get(net.alkahest.block.custom.EssenceChargerBlock.MOUNTED), "Replacement jar updates appearance");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void prefersJarBelowAndKeepsLegacySetup(TestContext context) {
        BlockPos pos = context.getAbsolutePos(new BlockPos(3, 2, 3));
        context.setBlockState(3, 2, 3, ModBlocks.ESSENCE_CHARGER);
        context.setBlockState(3, 3, 3, ModBlocks.JAR);
        var above = (JarBlockEntity) context.getWorld().getBlockEntity(pos.up());
        context.assertTrue(EssenceChargerBlockEntity.findJar(context.getWorld(), pos) == above, "0.1.14 jar above still works");
        context.setBlockState(3, 1, 3, ModBlocks.JAR);
        var below = (JarBlockEntity) context.getWorld().getBlockEntity(pos.down());
        context.assertTrue(EssenceChargerBlockEntity.findJar(context.getWorld(), pos) == below, "Jar below has priority, even if empty");
        context.getWorld().breakBlock(pos.down(), true);
        context.assertTrue(EssenceChargerBlockEntity.findJar(context.getWorld(), pos) == above, "Legacy jar is used again when lower jar disappears");
        context.complete();
    }

    private static net.alkahest.block.entity.TransmutatorCoreBlockEntity tower(TestContext c, int x, int y, int z, int charge, boolean wake) {
        c.setBlockState(x,y,z,ModBlocks.TRANSMUTATOR_CORE);
        c.setBlockState(x,y+1,z,ModBlocks.TRANSMUTATOR_CHAMBER);
        c.setBlockState(x,y+2,z,ModBlocks.TRANSMUTATOR_CROWN);
        var core=(net.alkahest.block.entity.TransmutatorCoreBlockEntity)c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(x,y,z)));
        var amulet=new ItemStack(ModItems.ALKAHEST_AMULET); amulet.set(ModDataComponents.ESSENCE_CHARGE,charge);
        core.setStack(net.alkahest.block.entity.TransmutatorCoreBlockEntity.AMULET,amulet);
        if (wake) core.wake();
        return core;
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void rechargesAwakenedMachineWithoutResettingProgress(TestContext c) {
        int y=200;
        c.setBlockState(2,y-1,3,ModBlocks.JAR);
        BlockPos charger=c.getAbsolutePos(new BlockPos(2,y,3));
        var jar=(JarBlockEntity)c.getWorld().getBlockEntity(charger.down());
        jar.setContents(new JarContentsComponent(List.of(new EssenceStack(EssenceType.ORDO,4,.55f))));
        var core=tower(c,5,y,3,35,true);
        core.setStack(net.alkahest.block.entity.TransmutatorCoreBlockEntity.INPUT,new ItemStack(net.minecraft.item.Items.COPPER_INGOT));
        for(int n=0;n<12;n++) net.alkahest.block.entity.TransmutatorCoreBlockEntity.tick(c.getWorld(),core.getPos(),core);
        int before=core.progress();
        c.assertTrue(before>0,"The machine is processing");
        var farther=tower(c,7,y,3,10,true);
        boolean[] loadedBefore=new boolean[25]; int index=0;
        for(int cx=(charger.getX()-32)>>4; cx<=(charger.getX()+32)>>4; cx++)
            for(int cz=(charger.getZ()-32)>>4; cz<=(charger.getZ()+32)>>4; cz++)
                loadedBefore[index++]=c.getWorld().getChunkManager().isChunkLoaded(cx,cz);
        c.assertTrue(EssenceChargerBlockEntity.chargeMachineNearby(c.getWorld(),jar,charger)==core,"The nearest awakened machine is selected");
        c.assertTrue(farther.getStack(net.alkahest.block.entity.TransmutatorCoreBlockEntity.AMULET).get(ModDataComponents.ESSENCE_CHARGE)==10,
            "A more distant machine is not charged in the same cycle");
        index=0;
        for(int cx=(charger.getX()-32)>>4; cx<=(charger.getX()+32)>>4; cx++)
            for(int cz=(charger.getZ()-32)>>4; cz<=(charger.getZ()+32)>>4; cz++)
                c.assertTrue(loadedBefore[index++]==c.getWorld().getChunkManager().isChunkLoaded(cx,cz),"Scanning does not load chunks");
        c.getWorld().setBlockState(farther.getPos(),net.minecraft.block.Blocks.AIR.getDefaultState());
        var amulet=core.getStack(net.alkahest.block.entity.TransmutatorCoreBlockEntity.AMULET);
        c.assertTrue(amulet.get(ModDataComponents.ESSENCE_CHARGE)==45 && jar.contents().total()==3,"One Ordo becomes ten charge");
        c.assertTrue(core.progress()==before,"Charging does not restart processing");
        var replica=new net.alkahest.block.entity.TransmutatorCoreBlockEntity(core.getPos(),core.getCachedState());
        replica.readComponentlessNbt(core.toInitialChunkDataNbt(c.getWorld().getRegistryManager()),c.getWorld().getRegistryManager());
        c.assertTrue(replica.getStack(net.alkahest.block.entity.TransmutatorCoreBlockEntity.AMULET).get(ModDataComponents.ESSENCE_CHARGE)==45
            && core.toUpdatePacket()!=null,"Saved and client data include the charge");
        amulet.set(ModDataComponents.ESSENCE_CHARGE,95); amulet.set(ModDataComponents.AMULET_USES_STEP,3);
        EssenceChargerBlockEntity.chargeMachineNearby(c.getWorld(),jar,charger);
        c.assertTrue(amulet.get(ModDataComponents.ESSENCE_CHARGE)==100 && amulet.get(ModDataComponents.AMULET_USES_STEP)==0,
            "The charge is capped and a full amulet clears partial-use debt");
        c.assertTrue(EssenceChargerBlockEntity.chargeMachineNearby(c.getWorld(),jar,charger)==null && jar.contents().total()==2,
            "A full amulet cannot waste Ordo");
        amulet.set(ModDataComponents.ESSENCE_CHARGE,0); core.sleep();
        c.assertTrue(EssenceChargerBlockEntity.chargeMachineNearby(c.getWorld(),jar,charger)==null && jar.contents().total()==2,
            "Dormant towers do not draw charge");
        core.wake(); c.getWorld().setBlockState(core.getPos().up(2),net.minecraft.block.Blocks.AIR.getDefaultState());
        c.assertTrue(EssenceChargerBlockEntity.chargeMachineNearby(c.getWorld(),jar,charger)==null && jar.contents().total()==2,
            "An incomplete structure cannot draw charge");
        for (int dx=-1; dx<=1; dx++) for (int dz=-1; dz<=1; dz++) {
            if (dx!=0 || dz!=0) c.getWorld().setBlockState(core.getPos().add(dx,0,dz),ModBlocks.TRANSMUTATOR_PILLAR.getDefaultState());
        }
        c.assertTrue(EssenceChargerBlockEntity.chargeMachineNearby(c.getWorld(),jar,charger)==core
            && amulet.get(ModDataComponents.ESSENCE_CHARGE)==10 && jar.contents().total()==1,
            "A fully assembled legacy platform remains supported");
        c.getWorld().setBlockState(core.getPos().add(1,0,0),net.minecraft.block.Blocks.AIR.getDefaultState());
        var far=tower(c,2,y+33,3,0,true);
        c.assertTrue(EssenceChargerBlockEntity.chargeMachineNearby(c.getWorld(),jar,charger)==null
            && far.getStack(net.alkahest.block.entity.TransmutatorCoreBlockEntity.AMULET).get(ModDataComponents.ESSENCE_CHARGE)==0,
            "A tower outside the cube is not charged");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE, tickLimit=100)
    public void playerPriorityAndActualChargerTick(TestContext c) {
        int y=280;
        c.setBlockState(2,y-1,3,ModBlocks.JAR); c.setBlockState(2,y,3,ModBlocks.ESSENCE_CHARGER);
        BlockPos pos=c.getAbsolutePos(new BlockPos(2,y,3));
        var jar=(JarBlockEntity)c.getWorld().getBlockEntity(pos.down());
        jar.setContents(new JarContentsComponent(List.of(new EssenceStack(EssenceType.ORDO,4,.5f))));
        var core=tower(c,5,y,3,10,true);
        var player=c.createMockPlayer(GameMode.SURVIVAL); player.setPosition(pos.getX()+1,pos.getY(),pos.getZ());
        var wand=new ItemStack(ModItems.WANDS.get(EssenceType.ORDO)); wand.set(ModDataComponents.ESSENCE_CHARGE,90);
        player.getInventory().setStack(0,wand);
        c.assertTrue(EssenceChargerBlockEntity.chargeCycle(c.getWorld(),jar,pos,List.of(player)),"The cycle finds a target");
        c.assertTrue(wand.get(ModDataComponents.ESSENCE_CHARGE)==100 && jar.contents().total()==3
            && core.getStack(net.alkahest.block.entity.TransmutatorCoreBlockEntity.AMULET).get(ModDataComponents.ESSENCE_CHARGE)==10,
            "A player is charged first and only one unit is spent");
        c.runAtTick(25,()->{
            c.assertTrue(core.getStack(net.alkahest.block.entity.TransmutatorCoreBlockEntity.AMULET).get(ModDataComponents.ESSENCE_CHARGE)>=20,
                "The actual charger tick finds and charges the machine");
            c.assertTrue(jar.contents().total()<=2,"Actual charging consumes matching Ordo");
            c.complete();
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void wrongEssenceAndEmptySlotNeverConsumeJar(TestContext c) {
        int y=350;
        c.setBlockState(2,y-1,3,ModBlocks.JAR); BlockPos pos=c.getAbsolutePos(new BlockPos(2,y,3));
        var jar=(JarBlockEntity)c.getWorld().getBlockEntity(pos.down());
        var original=new JarContentsComponent(List.of(new EssenceStack(EssenceType.AQUA,7,.4f)));
        jar.setContents(original);
        var core=tower(c,5,y,3,0,true);
        c.assertTrue(EssenceChargerBlockEntity.chargeMachineNearby(c.getWorld(),jar,pos)==null && jar.contents().equals(original),
            "A jar without Ordo is untouched");
        jar.setContents(new JarContentsComponent(List.of(new EssenceStack(EssenceType.ORDO,2,.4f))));
        core.removeStack(net.alkahest.block.entity.TransmutatorCoreBlockEntity.AMULET);
        c.assertTrue(EssenceChargerBlockEntity.chargeMachineNearby(c.getWorld(),jar,pos)==null && jar.contents().total()==2,
            "An empty machine slot cannot consume Ordo");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void terraRingChargeAndStoredUsage(TestContext c) {
        c.setBlockState(3,3,3,ModBlocks.JAR);
        var jar=(JarBlockEntity)c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(3,3,3)));
        var player=c.createMockPlayer(GameMode.SURVIVAL);
        var ring=new ItemStack(ModItems.RING_TERRA);
        var item=(net.alkahest.item.custom.TerraRingItem)ring.getItem();
        c.assertTrue(ring.getOrDefault(ModDataComponents.ESSENCE_CHARGE,-1)==0,"A new ring is empty");
        player.getInventory().setStack(0,ring);
        jar.setContents(new JarContentsComponent(List.of(new EssenceStack(EssenceType.ORDO,2,.5f))));
        c.assertTrue(EssenceChargerBlockEntity.chargeFirst(jar,player)==null && jar.contents().total()==2,"Wrong essence is rejected");
        jar.setContents(new JarContentsComponent(List.of(new EssenceStack(EssenceType.TERRA,3,.5f))));
        c.assertTrue(EssenceChargerBlockEntity.chargeFirst(jar,player)==EssenceType.TERRA && ring.get(ModDataComponents.ESSENCE_CHARGE)==1 && jar.contents().total()==2,"One Terra is one ring charge");
        for(int i=0;i<1200;i++) item.tick(ring,null,player);
        c.assertTrue(ring.get(ModDataComponents.ESSENCE_CHARGE)==1 && ring.get(ModDataComponents.RING_ACTIVE_TICKS)==1200 && player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.HASTE),"Haste and partial usage accrue only while worn");
        var ops=c.getWorld().getRegistryManager().getOps(net.minecraft.nbt.NbtOps.INSTANCE);
        var encoded=ItemStack.CODEC.encodeStart(ops,ring).getOrThrow();
        var restored=ItemStack.CODEC.parse(ops,encoded).getOrThrow();
        c.assertTrue(restored.get(ModDataComponents.RING_ACTIVE_TICKS)==1200 && restored.get(ModDataComponents.ESSENCE_CHARGE)==1,"Charge and fractional use survive serialization");
        for(int i=0;i<1199;i++) item.tick(restored,null,player);
        c.assertTrue(restored.get(ModDataComponents.ESSENCE_CHARGE)==1 && restored.get(ModDataComponents.RING_ACTIVE_TICKS)==2399,"No early charge consumption");
        item.tick(restored,null,player);
        c.assertTrue(restored.get(ModDataComponents.ESSENCE_CHARGE)==0 && restored.get(ModDataComponents.RING_ACTIVE_TICKS)==0,"Exactly 2400 equipped ticks consume one Terra");
        player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.HASTE);
        item.tick(restored,null,player);
        c.assertFalse(player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.HASTE),"An empty ring grants no effect");
        restored.set(ModDataComponents.ESSENCE_CHARGE,100); player.getInventory().setStack(0,restored);
        c.assertTrue(EssenceChargerBlockEntity.chargeFirst(jar,player)==null && jar.contents().total()==2,"A full ring wastes no essence");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void equippedRingSlotAndCharger(TestContext c) {
        var player=c.createMockCreativeServerPlayerInWorld();
        var component=dev.emi.trinkets.api.TrinketsApi.getTrinketComponent(player).orElseThrow();
        var ringInventory=component.getInventory().get("hand").get("ring");
        c.assertTrue(ringInventory.size()>=1,"Trinkets exposes the ring slot");
        var ring=new ItemStack(ModItems.RING_TERRA);
        var ref=new dev.emi.trinkets.api.SlotReference(ringInventory,0);
        c.assertTrue(dev.emi.trinkets.TrinketSlot.canInsert(ring,ref,player),"The ring is allowed in its slot");
        c.assertFalse(dev.emi.trinkets.TrinketSlot.canInsert(new ItemStack(ModItems.RING_BLANK),ref,player),"The blank is not wearable");
        ringInventory.setStack(0,ring);
        c.setBlockState(3,3,3,ModBlocks.JAR);
        var jar=(JarBlockEntity)c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(3,3,3)));
        jar.setContents(new JarContentsComponent(List.of(new EssenceStack(EssenceType.TERRA,2,.5f))));
        c.assertTrue(EssenceChargerBlockEntity.chargeFirst(jar,player)==EssenceType.TERRA && ring.get(ModDataComponents.ESSENCE_CHARGE)==1,"Equipped ring is charged");
        // Mock server players are not scheduled like connected clients; explicitly tick the entity.
        player.tick();
        c.assertTrue(player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.HASTE) && ring.getOrDefault(ModDataComponents.RING_ACTIVE_TICKS,0)>0,"Trinkets ticks the equipped ring");
        ringInventory.setStack(0,ItemStack.EMPTY);
        for (int i=0;i<8;i++) player.tick();
        c.assertFalse(player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.HASTE),"Effect expires after removing the ring");
        c.complete();
    }

}
