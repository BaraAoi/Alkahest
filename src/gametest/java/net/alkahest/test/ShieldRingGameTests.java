package net.alkahest.test;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketsApi;
import net.alkahest.block.entity.EssenceChargerBlockEntity;
import net.alkahest.block.entity.JarBlockEntity;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.JarContentsComponent;
import net.alkahest.item.custom.ShieldRingItem;
import net.alkahest.registry.ModBlocks;
import net.alkahest.registry.ModDataComponents;
import net.alkahest.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import java.util.List;

public class ShieldRingGameTests implements FabricGameTest {
    private static void ticks(ShieldRingItem ring, ItemStack stack, SlotReference ref, net.minecraft.entity.player.PlayerEntity player, int count) {
        for (int i=0; i<count; i++) ring.tick(stack,ref,player);
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void chargingAndRegenerationIgnoreHunger(TestContext c) {
        var player=c.createMockPlayer(GameMode.SURVIVAL);
        player.getHungerManager().setFoodLevel(0);
        var inv=TrinketsApi.getTrinketComponent(player).orElseThrow().getInventory().get("hand").get("ring");
        var ref=new SlotReference(inv,0); var ring=new ItemStack(ModItems.RING_SHIELD);
        c.assertTrue(dev.emi.trinkets.TrinketSlot.canInsert(ring,ref,player),"Shield ring fits in hand/ring");
        inv.setStack(0,ring);
        if (inv.size()>1) c.assertFalse(dev.emi.trinkets.TrinketSlot.canInsert(new ItemStack(ModItems.RING_SHIELD),new SlotReference(inv,1),player),"Cannot equip two shield rings");
        c.setBlockState(3,3,3,ModBlocks.JAR);
        var jar=(JarBlockEntity)c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(3,3,3)));
        jar.setContents(new JarContentsComponent(List.of(new EssenceStack(EssenceType.TERRA,2,.5f))));
        c.assertTrue(EssenceChargerBlockEntity.chargeFirst(jar,player)==null && jar.contents().total()==2,"Terra cannot charge shield ring");
        jar.setContents(new JarContentsComponent(List.of(new EssenceStack(EssenceType.ORDO,10,.5f))));
        for (int i=0;i<10;i++) c.assertTrue(EssenceChargerBlockEntity.chargeFirst(jar,player)==EssenceType.ORDO,"Equipped ring charges with Ordo");
        c.assertTrue(ring.get(ModDataComponents.ESSENCE_CHARGE)==10 && jar.contents().total()==0,"Each Ordo is one charge");
        var item=(ShieldRingItem)ring.getItem();
        ticks(item,ring,ref,player,120);
        c.assertTrue(ShieldRingItem.points(ring)==0,"Six seconds must pass before regeneration begins");
        ticks(item,ring,ref,player,9); c.assertTrue(ShieldRingItem.points(ring)==0,"No early restoration");
        ticks(item,ring,ref,player,1); c.assertTrue(ShieldRingItem.points(ring)==1 && ring.get(ModDataComponents.ESSENCE_CHARGE)==9,"First HP costs one charge");
        ticks(item,ring,ref,player,10); c.assertTrue(ShieldRingItem.points(ring)==2 && ring.get(ModDataComponents.ESSENCE_CHARGE)==8,"Exactly two HP per second, even at zero hunger");
        ticks(item,ring,ref,player,40); c.assertTrue(ShieldRingItem.points(ring)==6 && ring.get(ModDataComponents.ESSENCE_CHARGE)==4,"Shield caps at six");
        ticks(item,ring,ref,player,100); c.assertTrue(ring.get(ModDataComponents.ESSENCE_CHARGE)==4,"A full shield consumes nothing");
        var ops=c.getWorld().getRegistryManager().getOps(net.minecraft.nbt.NbtOps.INSTANCE);
        var restored=ItemStack.CODEC.parse(ops,ItemStack.CODEC.encodeStart(ops,ring).getOrThrow()).getOrThrow();
        c.assertTrue(ShieldRingItem.points(restored)==6 && restored.get(ModDataComponents.ESSENCE_CHARGE)==4,"Shield and charge survive saving");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void realDamageStopsRecoveryAndAbsorbsBeforeHealth(TestContext c) {
        var player=c.createMockPlayer(GameMode.SURVIVAL);
        var inv=TrinketsApi.getTrinketComponent(player).orElseThrow().getInventory().get("hand").get("ring");
        var ring=new ItemStack(ModItems.RING_SHIELD); inv.setStack(0,ring); var ref=new SlotReference(inv,0);
        ring.set(ModDataComponents.SHIELD_POINTS,6f); ring.set(ModDataComponents.SHIELD_DELAY,120);
        ring.set(ModDataComponents.ESSENCE_CHARGE,10);
        float health=player.getHealth();
        c.assertTrue(player.damage(c.getWorld().getDamageSources().generic(),2f),"Damage is applied through the game engine");
        c.assertTrue(Math.abs(player.getHealth()-health)<.001f && Math.abs(ShieldRingItem.points(ring)-4f)<.001f,"The separate shield absorbs 2 HP, not the player's health");
        c.assertTrue(ring.get(ModDataComponents.SHIELD_DELAY)==0,"A real hit resets the six-second timer");
        var item=(ShieldRingItem)ring.getItem();
        ticks(item,ring,ref,player,120); c.assertTrue(ShieldRingItem.points(ring)==4,"No recovery during six seconds after the hit");
        ticks(item,ring,ref,player,10); c.assertTrue(ShieldRingItem.points(ring)==5 && ring.get(ModDataComponents.ESSENCE_CHARGE)==9,"Recovery begins after the delay");
        ring.set(ModDataComponents.ESSENCE_CHARGE,0);
        ticks(item,ring,ref,player,50); c.assertTrue(ShieldRingItem.points(ring)==5,"No charge means no regeneration, but remaining shield persists");
        inv.setStack(0,ItemStack.EMPTY);
        c.assertTrue(ShieldRingItem.absorb(player,3f)==3f,"An unequipped ring cannot protect");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void shieldDoesNotOverwritePotionAbsorption(TestContext c) {
        var player=c.createMockPlayer(GameMode.SURVIVAL);
        var inv=TrinketsApi.getTrinketComponent(player).orElseThrow().getInventory().get("hand").get("ring");
        var ring=new ItemStack(ModItems.RING_SHIELD); inv.setStack(0,ring); ring.set(ModDataComponents.SHIELD_POINTS,6f);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION,600,0));
        float potion=player.getAbsorptionAmount(), health=player.getHealth();
        c.assertTrue(potion>=4f,"Vanilla absorption is present");
        c.assertTrue(player.damage(c.getWorld().getDamageSources().generic(),8f),"Real damage goes through vanilla and the ring");
        c.assertTrue(ShieldRingItem.points(ring)==0 && Math.abs(player.getAbsorptionAmount()-(potion-2f))<.001f && player.getHealth()==health,
            "Ring absorbs six, then vanilla absorption absorbs the rest without resetting it");
        c.complete();
    }
}
