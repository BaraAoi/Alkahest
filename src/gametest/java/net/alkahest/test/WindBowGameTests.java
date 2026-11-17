package net.alkahest.test;

import net.alkahest.block.entity.EssenceChargerBlockEntity;
import net.alkahest.block.entity.JarBlockEntity;
import net.alkahest.entity.WindGustEntity;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.JarContentsComponent;
import net.alkahest.item.custom.WindBowItem;
import net.alkahest.registry.ModBlocks;
import net.alkahest.registry.ModDataComponents;
import net.alkahest.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import java.util.List;

public class WindBowGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void firesWithoutArrowsAndConsumesOneAerPerTwoShots(TestContext c) {
        var world=c.getWorld(); var player=c.createMockPlayer(GameMode.SURVIVAL);
        var p=c.getAbsolutePos(new BlockPos(3,128,3)); player.setPosition(p.getX()+.5,p.getY(),p.getZ()+.5);
        var stack=new ItemStack(ModItems.BOW_AER); player.setStackInHand(Hand.MAIN_HAND,stack);
        var bow=(WindBowItem)stack.getItem(); stack.set(ModDataComponents.ESSENCE_CHARGE,1);
        c.assertTrue(bow.use(world,player,Hand.MAIN_HAND).getResult().isAccepted(),"Charged bow starts drawing without arrows");
        bow.onStoppedUsing(stack,world,player,WindBowItem.MAX_USE-3);
        c.assertTrue(stack.get(ModDataComponents.ESSENCE_CHARGE)==1 && stack.get(ModDataComponents.WIND_BOW_SHOT_STEP)==0,"A cancelled draw costs nothing");
        bow.onStoppedUsing(stack,world,player,WindBowItem.MAX_USE-20);
        c.assertTrue(stack.get(ModDataComponents.ESSENCE_CHARGE)==1 && stack.get(ModDataComponents.WIND_BOW_SHOT_STEP)==1,"First shot stores half the cost");
        var ops=world.getRegistryManager().getOps(net.minecraft.nbt.NbtOps.INSTANCE);
        var restored=ItemStack.CODEC.parse(ops,ItemStack.CODEC.encodeStart(ops,stack).getOrThrow()).getOrThrow();
        c.assertTrue(restored.get(ModDataComponents.WIND_BOW_SHOT_STEP)==1,"Partial shot cost survives serialization");
        bow.onStoppedUsing(stack,world,player,WindBowItem.MAX_USE-20);
        c.assertTrue(stack.get(ModDataComponents.ESSENCE_CHARGE)==0 && stack.get(ModDataComponents.WIND_BOW_SHOT_STEP)==0,"Second shot consumes exactly one Aer");
        var gusts=world.getEntitiesByClass(WindGustEntity.class,Box.of(Vec3d.ofCenter(p),10,10,10),e -> true);
        c.assertTrue(gusts.size()==2,"Both real releases spawn a gust");
        bow.onStoppedUsing(stack,world,player,WindBowItem.MAX_USE-20);
        c.assertTrue(world.getEntitiesByClass(WindGustEntity.class,Box.of(Vec3d.ofCenter(p),10,10,10),e -> true).size()==2,"An empty bow fires nothing");
        gusts.forEach(WindGustEntity::discard);
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void aerChargerAndMatrixRecipe(TestContext c) {
        c.setBlockState(3,3,3,ModBlocks.JAR);
        var jar=(JarBlockEntity)c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(3,3,3)));
        var player=c.createMockPlayer(GameMode.SURVIVAL); var stack=new ItemStack(ModItems.BOW_AER);
        player.getInventory().setStack(0,stack);
        jar.setContents(new JarContentsComponent(List.of(new EssenceStack(EssenceType.ORDO,3,.5f))));
        c.assertTrue(EssenceChargerBlockEntity.chargeFirst(jar,player)==null && jar.contents().total()==3,"Wrong essence is rejected");
        jar.setContents(new JarContentsComponent(List.of(new EssenceStack(EssenceType.AER,3,.5f))));
        c.assertTrue(EssenceChargerBlockEntity.chargeFirst(jar,player)==EssenceType.AER && stack.get(ModDataComponents.ESSENCE_CHARGE)==1 && jar.contents().total()==2,"One Aer grants one bow charge");
        var recipe=net.alkahest.matrix.MatrixRecipe.all().stream().filter(r->r.output().isOf(ModItems.BOW_AER)).findFirst().orElseThrow();
        c.assertTrue(recipe.base()==net.minecraft.item.Items.BOW && recipe.components().size()==6 && recipe.components().stream().filter(i->i==ModItems.STABLE_MATERIAL).count()==2
            && recipe.components().stream().filter(i->i==ModItems.INFUSED_MATERIALS.get(EssenceType.AER)).count()==2
            && recipe.components().contains(net.minecraft.item.Items.NETHERITE_INGOT) && recipe.cost().get(EssenceType.AER)==30 && recipe.cost().get(EssenceType.MOTUS)==15,"Bow has the complete Matrix recipe");
        c.complete();
    }
    private static ZombieEntity zombie(TestContext c, Vec3d p) {
        var z=EntityType.ZOMBIE.create(c.getWorld());
        if (z==null) throw new IllegalStateException("zombie unavailable");
        z.setPosition(p.x,p.y,p.z); z.setAiDisabled(true); z.setNoGravity(true); c.getWorld().spawnEntity(z); return z;
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void impactSplitsOnceAndTargetsOtherHostiles(TestContext c) {
        var world=c.getWorld(); var p=Vec3d.ofBottomCenter(c.getAbsolutePos(new BlockPos(3,148,2)));
        var shooter=c.createMockPlayer(GameMode.SURVIVAL); shooter.setPosition(p);
        var first=zombie(c,p.add(0,0,3)); var left=zombie(c,p.add(-3,0,5)); var right=zombie(c,p.add(3,0,5));
        var gust=new WindGustEntity(world,shooter,false); gust.setPosition(p.add(0,1,0)); gust.setVelocity(0,0,1.1); world.spawnEntity(gust);
        for (int i=0;i<10 && !gust.isRemoved();i++) gust.tick();
        c.assertTrue(gust.isRemoved() && first.getHealth()<8f,"A real primary collision deals substantially increased damage");
        var children=world.getEntitiesByClass(WindGustEntity.class,Box.of(p.add(0,1,3),20,20,20),WindGustEntity::child);
        c.assertTrue(children.size()==2,"Exactly two child gusts spawn");
        c.assertTrue(children.stream().noneMatch(e -> first.getUuid().equals(e.targetId())),"Children never target the original victim");
        c.assertTrue(children.stream().anyMatch(e -> left.getUuid().equals(e.targetId())) && children.stream().anyMatch(e -> right.getUuid().equals(e.targetId())),"Children choose distinct nearby enemies when possible");
        for (var child:children) for(int i=0;i<20 && !child.isRemoved();i++) child.tick();
        c.assertTrue(left.getHealth()<14f && right.getHealth()<14f,"Both child gusts deal increased damage to their own other targets");
        c.assertTrue(world.getEntitiesByClass(WindGustEntity.class,Box.of(p,20,20,20),e->!e.isRemoved()).isEmpty(),"Child impacts cannot split again");
        first.discard();left.discard();right.discard();c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void homingRespectsWallsAndIgnoresOriginalVictim(TestContext c) {
        var p=Vec3d.ofBottomCenter(c.getAbsolutePos(new BlockPos(3,168,2)));
        var shooter=c.createMockPlayer(GameMode.SURVIVAL); shooter.setPosition(p);
        var victim=zombie(c,p.add(0,0,3)); var hidden=zombie(c,p.add(0,0,6));
        var gust=new WindGustEntity(c.getWorld(),shooter,false); gust.setPosition(p.add(0,1,0)); gust.setVelocity(0,0,1);
        c.setBlockState(3,169,6,net.minecraft.block.Blocks.STONE);
        gust.exclude(victim);
        c.assertTrue(gust.nearbyTargets(10,false).isEmpty(),"Original target and enemies behind a wall are not selected");
        c.setBlockState(3,169,6,net.minecraft.block.Blocks.AIR);
        c.assertTrue(gust.nearbyTargets(10,false).contains(hidden),"Visible alternative is selectable");
        var tag=gust.writeNbt(new net.minecraft.nbt.NbtCompound());
        var copy=new WindGustEntity(c.getWorld(),shooter,true); copy.readNbt(tag);
        c.assertTrue(copy.nearbyTargets(10,false).stream().noneMatch(t -> t==victim),"Excluded victim survives saving");
        victim.discard();hidden.discard();c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void noOtherEnemyMeansNoSeekingChildren(TestContext c) {
        var world=c.getWorld(); var p=Vec3d.ofBottomCenter(c.getAbsolutePos(new BlockPos(3,188,2)));
        var shooter=c.createMockPlayer(GameMode.SURVIVAL); shooter.setPosition(p);
        var first=zombie(c,p.add(0,0,3));
        var gust=new WindGustEntity(world,shooter,false); gust.setPosition(p.add(0,1,0)); gust.setVelocity(0,0,1.1); world.spawnEntity(gust);
        for(int i=0;i<10 && !gust.isRemoved();i++) gust.tick();
        c.assertTrue(gust.isRemoved() && first.getHealth()<first.getMaxHealth(),"Main gust hits its target");
        var fragments=world.getEntitiesByClass(WindGustEntity.class,Box.of(p,20,20,20),WindGustEntity::child);
        c.assertTrue(fragments.size()==2 && fragments.stream().allMatch(e -> e.targetId()==null),"No other enemy: two untargeted fragments fan out visibly");
        float health=first.getHealth();
        for (var child:fragments) for(int i=0;i<8 && !child.isRemoved();i++) child.tick();
        c.assertTrue(fragments.stream().allMatch(WindGustEntity::isRemoved) && first.getHealth()==health,"Untargeted fragments dissipate without hitting the original victim");
        first.discard(); c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void aWallStopsTheRealGust(TestContext c) {
        var world=c.getWorld(); var p=Vec3d.ofBottomCenter(c.getAbsolutePos(new BlockPos(3,208,2)));
        var shooter=c.createMockPlayer(GameMode.SURVIVAL); shooter.setPosition(p);
        c.setBlockState(3,209,4,net.minecraft.block.Blocks.STONE);
        var target=zombie(c,p.add(0,0,6));
        var gust=new WindGustEntity(world,shooter,false); gust.setPosition(p.add(0,1,0)); gust.setVelocity(0,0,1.1); world.spawnEntity(gust);
        for(int i=0;i<10 && !gust.isRemoved();i++) gust.tick();
        c.assertTrue(gust.isRemoved() && target.getHealth()==target.getMaxHealth(),"A wall stops the gust and protects the target behind it");
        c.assertTrue(world.getEntitiesByClass(WindGustEntity.class,Box.of(p,20,20,20),WindGustEntity::child).isEmpty(),"A block collision does not create fragments");
        target.discard(); c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void gustHomesOnAndDestroysEndCrystal(TestContext c) {
        var world=c.getWorld(); var p=Vec3d.ofBottomCenter(c.getAbsolutePos(new BlockPos(3,228,2)));
        var shooter=c.createMockPlayer(GameMode.SURVIVAL); shooter.setPosition(p);
        var crystal=net.minecraft.entity.EntityType.END_CRYSTAL.create(world);
        if (crystal==null) throw new IllegalStateException("crystal unavailable");
        crystal.setPosition(p.add(2,0,12)); world.spawnEntity(crystal);
        var gust=new WindGustEntity(world,shooter,false); gust.setPosition(p.add(0,1,0)); gust.setVelocity(0,0,1.1); world.spawnEntity(gust);
        gust.tick();
        c.assertTrue(crystal.getUuid().equals(gust.targetId()),"Crystal is an actual homing target despite not being a living entity");
        for(int i=0;i<30 && !gust.isRemoved();i++) gust.tick();
        c.assertTrue(gust.isRemoved() && crystal.isRemoved(),"A real gust hit destroys the crystal through vanilla damage");
        world.getEntitiesByClass(WindGustEntity.class,Box.of(p,20,20,32),WindGustEntity::child).forEach(WindGustEntity::discard);
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void gustHomesOnDragonAndDamagesItsRealBodyPart(TestContext c) {
        var world=c.getWorld(); var p=Vec3d.ofBottomCenter(c.getAbsolutePos(new BlockPos(3,248,2)));
        var shooter=c.createMockPlayer(GameMode.SURVIVAL); shooter.setPosition(p);
        var dragon=net.minecraft.entity.EntityType.ENDER_DRAGON.create(world);
        if (dragon==null) throw new IllegalStateException("dragon unavailable");
        dragon.setPosition(p.add(0,0,8)); world.spawnEntity(dragon);
        var parts=dragon.getBodyParts();
        for (int i=0;i<parts.length;i++) parts[i].setPosition(p.add(i==0?0:10,0,i==0?8:18));
        var gust=new WindGustEntity(world,shooter,false); gust.setPosition(p.add(0,.5,0)); gust.setVelocity(0,0,1.1); world.spawnEntity(gust);
        gust.tick();
        c.assertTrue(dragon.getUuid().equals(gust.targetId()),"Dragon parent is acquired even though its collision uses parts");
        for(int i=0;i<25 && !gust.isRemoved();i++) gust.tick();
        c.assertTrue(gust.isRemoved() && dragon.getHealth()<dragon.getMaxHealth(),"A real collision with the dragon head reduces its health");
        var children=world.getEntitiesByClass(WindGustEntity.class,Box.of(p.add(0,0,8),24,24,24),WindGustEntity::child);
        c.assertTrue(children.size()==2 && children.stream().noneMatch(child -> dragon.getUuid().equals(child.targetId())),"Two fragments never retarget another part of the same dragon");
        var saved=children.getFirst().writeNbt(new net.minecraft.nbt.NbtCompound());
        var restored=new WindGustEntity(world,shooter,true); restored.readNbt(saved);
        c.assertFalse(restored.nearbyTargets(20,false).contains(dragon),"Dragon exclusion survives fragment serialization");
        children.forEach(WindGustEntity::discard); dragon.discard(); c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void gustAcquiresLongRangeCrystalButNotThroughWalls(TestContext c) {
        var world=c.getWorld(); var p=Vec3d.ofBottomCenter(c.getAbsolutePos(new BlockPos(3,268,2)));
        var shooter=c.createMockPlayer(GameMode.SURVIVAL); shooter.setPosition(p);
        var crystal=net.minecraft.entity.EntityType.END_CRYSTAL.create(world);
        if (crystal==null) throw new IllegalStateException("crystal unavailable");
        crystal.setPosition(p.add(0,0,30)); world.spawnEntity(crystal);
        var gust=new WindGustEntity(world,shooter,false); gust.setPosition(p.add(0,1,0)); gust.setVelocity(0,0,1);
        c.assertTrue(gust.nearbyTargets(WindGustEntity.MAIN_RANGE,true).contains(crystal),"Crystal beyond the old 20-block range is acquired");
        c.setBlockState(3,269,5,net.minecraft.block.Blocks.STONE);
        c.assertFalse(gust.nearbyTargets(WindGustEntity.MAIN_RANGE,true).contains(crystal),"Targeting does not pass through solid walls");
        crystal.discard(); c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void splitGustsCanSeekAndDestroyAnEndCrystal(TestContext c) {
        var world=c.getWorld(); var p=Vec3d.ofBottomCenter(c.getAbsolutePos(new BlockPos(3,288,2)));
        var shooter=c.createMockPlayer(GameMode.SURVIVAL); shooter.setPosition(p);
        var first=zombie(c,p.add(0,0,3));
        var crystal=net.minecraft.entity.EntityType.END_CRYSTAL.create(world);
        if (crystal==null) throw new IllegalStateException("crystal unavailable");
        crystal.setPosition(p.add(4,0,4)); world.spawnEntity(crystal);
        var gust=new WindGustEntity(world,shooter,false); gust.setPosition(p.add(0,1,0)); gust.setVelocity(0,0,1.1); world.spawnEntity(gust);
        for(int i=0;i<10 && !gust.isRemoved();i++) gust.tick();
        var fragments=world.getEntitiesByClass(WindGustEntity.class,Box.of(p,20,20,20),WindGustEntity::child);
        c.assertTrue(fragments.size()==2 && fragments.stream().allMatch(e->crystal.getUuid().equals(e.targetId())),"Fragments seek an End Crystal as the other target");
        for(var child:fragments) for(int i=0;i<20 && !child.isRemoved();i++) child.tick();
        c.assertTrue(crystal.isRemoved(),"A secondary gust destroys a crystal");
        fragments.forEach(WindGustEntity::discard); first.discard(); c.complete();
    }

}
