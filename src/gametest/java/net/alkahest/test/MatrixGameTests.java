package net.alkahest.test;

import net.alkahest.block.entity.JarBlockEntity;
import net.alkahest.block.entity.MatrixCoreBlockEntity;
import net.alkahest.block.custom.MatrixCoreBlock;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.JarContentsComponent;
import net.alkahest.registry.ModBlocks;
import net.alkahest.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/** The 3x3x3 Matrix: eight pillars on the corners, eighteen bricks on the shell, essence by air. */
public class MatrixGameTests implements FabricGameTest {
    private static final int CX = 3, CY = 2, CZ = 3;

    private static JarContentsComponent of(EssenceType type, int amount) {
        return new JarContentsComponent(List.of(new EssenceStack(type, amount, 0.5f)));
    }

    private static BlockPos build(TestContext c) {
        c.setBlockState(CX, CY, CZ, ModBlocks.MATRIX_CORE);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    boolean corner = dx != 0 && dy != 0 && dz != 0;
                    c.setBlockState(CX + dx, CY + dy, CZ + dz,
                        corner ? ModBlocks.MATRIX_PILLAR : ModBlocks.ALCHEMICAL_BRICK);
                }
            }
        }
        return c.getAbsolutePos(new BlockPos(CX, CY, CZ));
    }

    private static MatrixCoreBlockEntity core(TestContext c) {
        return (MatrixCoreBlockEntity) c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(CX, CY, CZ)));
    }

    private static void wake(TestContext c) {
        MatrixCoreBlockEntity core = core(c);
        core.setAwake(c.getWorld(), c.getAbsolutePos(new BlockPos(CX, CY, CZ)), true);
    }

    private static JarBlockEntity jar(TestContext c, int x, int y, int z, EssenceType type, int amount) {
        c.setBlockState(x, y, z, ModBlocks.JAR);
        JarBlockEntity jar = (JarBlockEntity) c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(x, y, z)));
        jar.setContents(of(type, amount));
        return jar;
    }

    private static void pedestal(TestContext c, int x, int y, int z, ItemStack stack) {
        c.setBlockState(x, y, z, ModBlocks.MATRIX_PEDESTAL);
        var pedestal = (net.alkahest.block.entity.MatrixPedestalBlockEntity)
            c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(x, y, z)));
        pedestal.setHeld(stack.copy());
    }

    private static void start(TestContext c) {
        var player = c.createMockPlayer(net.minecraft.world.GameMode.CREATIVE);
        c.assertTrue(core(c).startWith(new ItemStack(ModItems.ALKAHEST_AMULET), player), "The focus starts one synthesis");
    }

    private static void run(World world, BlockPos pos, int ticks) {
        MatrixCoreBlockEntity core = (MatrixCoreBlockEntity) world.getBlockEntity(pos);
        for (int i = 0; i < ticks; i++) MatrixCoreBlockEntity.tick(world, pos, core);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void upgradedRingsPreserveStackStateInRealMatrix(TestContext c) {
        var pos=build(c); wake(c); var core=core(c); var player=c.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        var cases=List.of(ModItems.RING_TERRA,ModItems.RING_SHIELD,ModItems.RING_SHIELD_2,ModItems.RING_SHIELD_3);
        var outputs=List.of(ModItems.RING_TERRA_2,ModItems.RING_SHIELD_2,ModItems.RING_SHIELD_3,ModItems.RING_SHIELD_4);
        var coords=List.of(new BlockPos(1,1,3),new BlockPos(5,1,3),new BlockPos(3,1,5),new BlockPos(1,1,1),new BlockPos(5,1,1),new BlockPos(1,1,5));
        for(int n=0;n<cases.size();n++) {
            var base=new ItemStack(cases.get(n)); base.set(net.alkahest.registry.ModDataComponents.ESSENCE_CHARGE,73);
            base.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, net.minecraft.text.Text.literal("Kept name"));
            if(n==0) base.set(net.alkahest.registry.ModDataComponents.RING_ACTIVE_TICKS,1234);
            else { base.set(net.alkahest.registry.ModDataComponents.SHIELD_POINTS,1.5f); base.set(net.alkahest.registry.ModDataComponents.SHIELD_DELAY,87); }
            core.setStack(MatrixCoreBlockEntity.BASE,base);
            var expectedOutput=outputs.get(n);
            var recipe=net.alkahest.matrix.MatrixRecipe.all().stream().filter(r -> r.output().isOf(expectedOutput)).findFirst().orElseThrow();
            c.assertTrue(recipe.components().contains(ModItems.STABLE_MATERIAL),"Every upgrade needs Pure Effir");
            for(int j=0;j<recipe.components().size();j++) { var q=coords.get(j); pedestal(c,q.getX(),q.getY(),q.getZ(),new ItemStack(recipe.components().get(j))); }
            var prices=new java.util.ArrayList<>(recipe.cost().entrySet());
            var a=prices.get(0); var b=prices.get(1);
            jar(c,0,2,3,a.getKey(),a.getValue()); jar(c,6,2,3,b.getKey(),b.getValue());
            c.assertTrue(core.startWith(new ItemStack(ModItems.ALKAHEST_AMULET),player),"Upgrade starts with all components");
            run(c.getWorld(),pos,MatrixCoreBlockEntity.DURATION*2+10);
            var result=core.removeStack(MatrixCoreBlockEntity.OUTPUT);
            c.assertTrue(result.isOf(outputs.get(n)) && result.get(net.alkahest.registry.ModDataComponents.ESSENCE_CHARGE)==73
                && result.getName().getString().equals("Kept name"),"Upgrade preserves charge and custom name");
            if(n==0) c.assertTrue(result.get(net.alkahest.registry.ModDataComponents.RING_ACTIVE_TICKS)==1234,"Terra partial usage persists");
            else c.assertTrue(result.get(net.alkahest.registry.ModDataComponents.SHIELD_POINTS)==1.5f
                && result.get(net.alkahest.registry.ModDataComponents.SHIELD_DELAY)==87,"Shield state and damage delay persist");
            for(var q:coords) { var be=c.getWorld().getBlockEntity(c.getAbsolutePos(q)); if(be instanceof net.alkahest.block.entity.MatrixPedestalBlockEntity ped) c.assertTrue(ped.isEmpty(),"All pedestal ingredients consumed"); }
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void shieldRingUsesSeparateMatrixRecipe(TestContext c) {
        var pos=build(c); wake(c); var core=core(c); var player=c.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        core.setStack(MatrixCoreBlockEntity.BASE,new ItemStack(ModItems.RING_BLANK));
        pedestal(c,1,1,3,new ItemStack(ModItems.INFUSED_MATERIALS.get(EssenceType.ORDO)));
        pedestal(c,5,1,3,new ItemStack(ModItems.CRYSTALS.get(EssenceType.ORDO)));
        c.assertFalse(core.startWith(new ItemStack(ModItems.ALKAHEST_AMULET),player),"Shield is required");
        pedestal(c,1,1,1,new ItemStack(Items.SHIELD));
        var recipe=core.recipe();
        c.assertTrue(recipe!=null && recipe.output().isOf(ModItems.RING_SHIELD) && recipe.components().size()==3
            && recipe.cost().size()==2 && recipe.cost().get(EssenceType.ORDO)==30 && recipe.cost().get(EssenceType.METALLUM)==15,"Exact shield recipe and price");
        var ordo=jar(c,0,2,3,EssenceType.ORDO,30); var metal=jar(c,6,2,3,EssenceType.METALLUM,15);
        c.assertTrue(core.startWith(new ItemStack(ModItems.ALKAHEST_AMULET),player),"The shield recipe starts without research gates");
        run(c.getWorld(),pos,MatrixCoreBlockEntity.DURATION*2+10);
        var result=core.getStack(MatrixCoreBlockEntity.OUTPUT);
        c.assertTrue(result.isOf(ModItems.RING_SHIELD) && result.getOrDefault(net.alkahest.registry.ModDataComponents.ESSENCE_CHARGE,-1)==0,"Result is an empty shield ring");
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.BASE).isEmpty() && ordo.contents().total()==0 && metal.contents().total()==0,"Base and essences consumed");
        for(var q:List.of(new BlockPos(1,1,3),new BlockPos(5,1,3),new BlockPos(1,1,1)))
            c.assertTrue(((net.alkahest.block.entity.MatrixPedestalBlockEntity)c.getWorld().getBlockEntity(c.getAbsolutePos(q))).isEmpty(),"Each component consumed");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void terraRingRequiresAllMaterialsAndPaysEssence(TestContext c) {
        var pos=build(c); wake(c); var core=core(c); var player=c.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        core.setStack(MatrixCoreBlockEntity.BASE,new ItemStack(ModItems.RING_BLANK));
        pedestal(c,1,1,3,new ItemStack(ModItems.INFUSED_MATERIALS.get(EssenceType.TERRA)));
        pedestal(c,5,1,3,new ItemStack(ModItems.CRYSTALS.get(EssenceType.TERRA)));
        c.assertFalse(core.startWith(new ItemStack(ModItems.WANDS.get(EssenceType.ORDO)),player),"A wand cannot start a ring");
        var recipe=core.recipe();
        c.assertTrue(recipe!=null && recipe.output().isOf(ModItems.RING_TERRA) && recipe.components().size()==2
            && recipe.cost().size()==2 && recipe.cost().get(EssenceType.TERRA)==30 && recipe.cost().get(EssenceType.ORDO)==10,"Matching recipe has its exact ingredients and cost");
        c.assertFalse(core.startWith(new ItemStack(ModItems.WANDS.get(EssenceType.ORDO)),player),"A wand cannot start a ring");
        var terra=jar(c,0,2,3,EssenceType.TERRA,30); var ordo=jar(c,6,2,3,EssenceType.ORDO,10);
        c.assertTrue(core.startWith(new ItemStack(ModItems.ALKAHEST_AMULET),player),"Amulet starts the recipe without research gating");
        run(c.getWorld(),pos,MatrixCoreBlockEntity.DURATION*2+10);
        var result=core.getStack(MatrixCoreBlockEntity.OUTPUT);
        c.assertTrue(result.isOf(ModItems.RING_TERRA) && result.getOrDefault(net.alkahest.registry.ModDataComponents.ESSENCE_CHARGE,-1)==0,"Synthesized ring starts empty");
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.BASE).isEmpty() && terra.contents().total()==0 && ordo.contents().total()==0,"Base and exact essence consumed");
        for(var q:List.of(new BlockPos(1,1,3),new BlockPos(5,1,3)))
            c.assertTrue(((net.alkahest.block.entity.MatrixPedestalBlockEntity)c.getWorld().getBlockEntity(c.getAbsolutePos(q))).isEmpty(),"Each pedestal ingredient consumed");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stableMaterialCombinesSixInfusions(TestContext c) {
        var pos = build(c); wake(c); var core = core(c); var world = c.getWorld();
        var player = c.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(ModItems.STABILIZING_REAGENT));
        var types = List.of(EssenceType.IGNIS, EssenceType.AQUA, EssenceType.TERRA,
            EssenceType.AER, EssenceType.ORDO, EssenceType.PERDITIO);
        var ped = List.of(new BlockPos(1,1,3),new BlockPos(5,1,3),new BlockPos(3,1,5),
            new BlockPos(1,1,1),new BlockPos(5,1,1),new BlockPos(1,1,5));
        var jars = List.of(new BlockPos(0,2,3),new BlockPos(6,2,3),new BlockPos(3,2,0),
            new BlockPos(3,2,6),new BlockPos(0,2,0),new BlockPos(6,2,6));
        for (int i=0; i<5; i++) pedestal(c, ped.get(i).getX(),ped.get(i).getY(),ped.get(i).getZ(), new ItemStack(ModItems.INFUSED_MATERIALS.get(types.get(i))));
        c.assertFalse(core.startWith(new ItemStack(ModItems.ALKAHEST_AMULET), player), "Missing sixth infusion prevents synthesis");
        var p=ped.get(5); pedestal(c,p.getX(),p.getY(),p.getZ(),new ItemStack(ModItems.INFUSED_MATERIALS.get(types.get(5))));
        var recipe=core.recipe();
        c.assertTrue(recipe != null && recipe.output().isOf(ModItems.STABLE_MATERIAL) && recipe.components().size()==6, "All six materials match");
        c.assertFalse(recipe.components().contains(ModItems.SPIRIT_CLOTH), "Spirit cloth is not an ingredient");
        c.assertTrue(types.stream().allMatch(t -> recipe.cost().getOrDefault(t,0)==8) && recipe.cost().size()==6, "Exactly eight of each primal essence");
        c.assertFalse(core.startWith(new ItemStack(ModItems.WANDS.get(EssenceType.ORDO)), player), "Only amulet synthesis can start with a wand");
        for (int i=0; i<types.size(); i++) { var q=jars.get(i); jar(c,q.getX(),q.getY(),q.getZ(),types.get(i),8); }
        c.assertTrue(core.startWith(new ItemStack(ModItems.ALKAHEST_AMULET), player), "Amulet starts stable material synthesis");
        run(world,pos,MatrixCoreBlockEntity.DURATION*2+10);
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).isOf(ModItems.STABLE_MATERIAL), "Stable material is created");
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.BASE).isEmpty(), "Reagent consumed");
        for (var q : ped) c.assertTrue(((net.alkahest.block.entity.MatrixPedestalBlockEntity) world.getBlockEntity(c.getAbsolutePos(q))).isEmpty(), "Infusion consumed");
        for (var q : jars) c.assertTrue(((JarBlockEntity) world.getBlockEntity(c.getAbsolutePos(q))).contents().total()==0, "Each essence paid");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void amuletRequiresEverySeparatePedestal(TestContext c) {
        BlockPos pos = build(c); wake(c); var core = core(c);
        var player = c.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        var wand = new ItemStack(ModItems.WANDS.get(EssenceType.ORDO));
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(ModItems.IRON_CHAIN));
        pedestal(c, 1, 1, 3, new ItemStack(ModItems.ALKAHEST_VIAL));
        pedestal(c, 5, 1, 3, new ItemStack(ModItems.CRYSTALS.get(EssenceType.ORDO), 2));
        pedestal(c, 3, 1, 5, new ItemStack(Items.GOLD_INGOT, 2));
        pedestal(c, 1, 1, 1, new ItemStack(Items.DIAMOND));
        c.assertFalse(core.startWith(wand, player), "Two-item stacks on single pedestals do not count twice");
        pedestal(c, 5, 1, 1, new ItemStack(ModItems.CRYSTALS.get(EssenceType.ORDO)));
        c.assertFalse(core.startWith(wand, player), "Missing second gold is rejected");
        pedestal(c, 1, 1, 5, new ItemStack(Items.GOLD_INGOT));
        c.assertTrue(core.startWith(wand, player), "All six pedestals allow start");
        run(c.getWorld(), pos, MatrixCoreBlockEntity.DURATION * 2);
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).isEmpty(), "No essence means no amulet");
        c.assertFalse(core.getStack(MatrixCoreBlockEntity.BASE).isEmpty(), "Missing essence preserves the base");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void firstAmuletIsMadeWithAWandInTheMatrix(TestContext c) {
        BlockPos pos = build(c); var world = c.getWorld(); var core = core(c);
        var player = c.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        var wand = new ItemStack(ModItems.WANDS.get(EssenceType.ORDO));
        var before = wand.getOrDefault(net.alkahest.registry.ModDataComponents.ESSENCE_CHARGE, 0);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(ModItems.IRON_CHAIN));
        pedestal(c, 1, 1, 3, new ItemStack(ModItems.ALKAHEST_VIAL));
        pedestal(c, 5, 1, 3, new ItemStack(ModItems.CRYSTALS.get(EssenceType.ORDO)));
        pedestal(c, 3, 1, 5, new ItemStack(Items.GOLD_INGOT));
        // Two identical pieces require two distinct pedestals each; stacks cannot stand in for them.
        pedestal(c, 1, 1, 1, new ItemStack(ModItems.CRYSTALS.get(EssenceType.ORDO)));
        pedestal(c, 5, 1, 1, new ItemStack(Items.GOLD_INGOT));
        pedestal(c, 1, 1, 5, new ItemStack(Items.DIAMOND));
        var ordo = jar(c, 0, 2, 3, EssenceType.ORDO, 16);
        var metal = jar(c, 6, 2, 3, EssenceType.METALLUM, 16);
        var lux = jar(c, 3, 2, 0, EssenceType.LUX, 8);
        MatrixCoreBlockEntity.interactNearby(world, pos, player, wand);
        c.assertTrue(core.awake() && !core.started(), "First wand click wakes, but does not start the Matrix");
        MatrixCoreBlockEntity.interactNearby(world, pos, player, wand);
        c.assertTrue(core.started(), "Second wand click starts the amulet recipe");
        run(world, pos, MatrixCoreBlockEntity.DURATION * 2 + 10);
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).isOf(ModItems.ALKAHEST_AMULET), "The synthesis produces an amulet");
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.BASE).isEmpty(), "Necklace base consumed");
        for (var p : List.of(new BlockPos(1,1,3), new BlockPos(5,1,3), new BlockPos(3,1,5), new BlockPos(1,1,1), new BlockPos(5,1,1), new BlockPos(1,1,5)))
            c.assertTrue(((net.alkahest.block.entity.MatrixPedestalBlockEntity) world.getBlockEntity(c.getAbsolutePos(p))).isEmpty(), "Components consumed");
        c.assertTrue(ordo.contents().total() == 0 && metal.contents().total() == 0 && lux.contents().total() == 0, "All essence costs paid");
        c.assertTrue(wand.getOrDefault(net.alkahest.registry.ModDataComponents.ESSENCE_CHARGE, 0) == before, "Bootstrap wand use is free");
        core.setStack(MatrixCoreBlockEntity.OUTPUT, ItemStack.EMPTY);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.STRING));
        c.assertFalse(core.startWith(wand, player), "A wand cannot start other Matrix recipes");
        var input = net.minecraft.recipe.input.CraftingRecipeInput.create(3, 3, List.of(
            ItemStack.EMPTY, new ItemStack(Items.STRING), ItemStack.EMPTY,
            new ItemStack(Items.GOLD_INGOT), new ItemStack(ModItems.CRYSTALS.get(EssenceType.ORDO)), new ItemStack(Items.GOLD_INGOT),
            ItemStack.EMPTY, new ItemStack(ModItems.ALKAHEST_VIAL), ItemStack.EMPTY));
        var match = world.getServer().getRecipeManager().getFirstMatch(net.minecraft.recipe.RecipeType.CRAFTING, input, world);
        c.assertFalse(match.isPresent() && match.get().value().craft(input, world.getRegistryManager()).isOf(ModItems.ALKAHEST_AMULET), "The old workbench recipe no longer makes an amulet");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void atmosphericOverloadSlowsButNeverBlocksSynthesis(TestContext c) {
        BlockPos pos = build(c); wake(c); var world = c.getWorld(); var core = core(c);
        var air = net.alkahest.atmosphere.AtmosphereState.of(world);
        air.add(world, pos, EssenceType.ORDO, 65); air.add(world, pos, EssenceType.AQUA, 65);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.STRING));
        jar(c, CX + 3, CY, CZ, EssenceType.ORDO, 4);
        jar(c, CX - 3, CY, CZ, EssenceType.AQUA, 2);
        double before = air.levels(world, pos).get(EssenceType.ORDO);
        start(c);
        run(world, pos, MatrixCoreBlockEntity.DURATION);
        c.assertTrue(core.progress() < MatrixCoreBlockEntity.DURATION, "Overload slows the actual synthesis");
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).isEmpty(), "No early free output");
        var saved = core.createNbt(world.getRegistryManager());
        var clientCopy = new MatrixCoreBlockEntity(pos, core.getCachedState());
        clientCopy.readComponentlessNbt(core.toUpdatePacket().getNbt(), world.getRegistryManager());
        c.assertTrue(clientCopy.atmosphereStability() == core.atmosphereStability() && core.atmosphereStability() < 100,
            "Monocle receives server-calculated stability");
        core.readComponentlessNbt(saved, world.getRegistryManager());
        run(world, pos, MatrixCoreBlockEntity.DURATION * 2 + 10);
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).isOf(ModItems.BINDING_SALT),
            "Even severely overloaded, the paid recipe eventually succeeds after save/reload");
        c.assertTrue(air.levels(world, pos).get(EssenceType.ORDO) > before + .39, "Completed Matrix recipe emits its actual working byproduct");
        // The GameTest structures share a world; do not leave artificial overload for another test.
        air.spend(world, pos, EssenceType.ORDO, 80);
        air.spend(world, pos, EssenceType.AQUA, 80);
        c.complete();
    }

    /** Reproduce client update packets on already-populated replicas, including omitted empty slots. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void emptyUpdatesClearClientReplicas(TestContext c) {
        BlockPos pos = build(c);
        var registry = c.getWorld().getRegistryManager();
        var source = core(c);
        var replica = new MatrixCoreBlockEntity(pos, source.getCachedState());
        source.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.COAL));
        replica.readComponentlessNbt(source.toUpdatePacket().getNbt(), registry);
        c.assertTrue(replica.getStack(MatrixCoreBlockEntity.BASE).isOf(Items.COAL), "Replica receives the base");
        source.setStack(MatrixCoreBlockEntity.BASE, ItemStack.EMPTY);
        source.setStack(MatrixCoreBlockEntity.OUTPUT, new ItemStack(Items.DIAMOND));
        replica.readComponentlessNbt(source.toUpdatePacket().getNbt(), registry);
        c.assertTrue(replica.getStack(MatrixCoreBlockEntity.BASE).isEmpty(), "Consumed base is removed on client");
        c.assertTrue(replica.getStack(MatrixCoreBlockEntity.OUTPUT).isOf(Items.DIAMOND), "Replica receives product");
        source.setStack(MatrixCoreBlockEntity.OUTPUT, ItemStack.EMPTY);
        replica.readComponentlessNbt(source.toUpdatePacket().getNbt(), registry);
        c.assertTrue(replica.isEmpty(), "Taken product no longer floats on client");

        pedestal(c, CX + 3, CY, CZ, new ItemStack(Items.GOLD_INGOT));
        BlockPos pedestalPos = c.getAbsolutePos(new BlockPos(CX + 3, CY, CZ));
        var pedestal = (net.alkahest.block.entity.MatrixPedestalBlockEntity) c.getWorld().getBlockEntity(pedestalPos);
        var replicaPedestal = new net.alkahest.block.entity.MatrixPedestalBlockEntity(pedestalPos, pedestal.getCachedState());
        replicaPedestal.readComponentlessNbt(pedestal.toUpdatePacket().getNbt(), registry);
        c.assertTrue(replicaPedestal.held().isOf(Items.GOLD_INGOT), "Replica receives the pedestal item");
        pedestal.takeOne();
        replicaPedestal.readComponentlessNbt(pedestal.toUpdatePacket().getNbt(), registry);
        c.assertTrue(replicaPedestal.isEmpty(), "Taken pedestal item no longer floats on client");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void eachSynthesisRequiresFocusAndPersists(TestContext c) {
        BlockPos pos = build(c);
        wake(c);
        var core = core(c);
        var player = c.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.STRING, 2));
        JarBlockEntity ordo = jar(c, CX + 3, CY, CZ, EssenceType.ORDO, 10);
        jar(c, CX - 3, CY, CZ, EssenceType.AQUA, 10);
        run(c.getWorld(), pos, 40);
        c.assertTrue(core.progress() == 0 && ordo.contents().total() == 10, "Awakening alone starts nothing");
        c.assertTrue(core.status() == MatrixCoreBlockEntity.STATUS_NEEDS_START, "HUD asks for a focus");
        var amulet = new ItemStack(ModItems.ALKAHEST_AMULET);
        MatrixCoreBlockEntity.interactNearby(c.getWorld(), pos, player, amulet);
        c.assertTrue(core.started() && core.getStack(MatrixCoreBlockEntity.BASE).isOf(Items.STRING), "Amulet starts without replacing base");
        var saved = core.createNbt(c.getWorld().getRegistryManager());
        var replica = new MatrixCoreBlockEntity(pos, core.getCachedState());
        replica.readComponentlessNbt(saved, c.getWorld().getRegistryManager());
        c.assertTrue(replica.started(), "Running state survives reload and sync");
        run(c.getWorld(), pos, MatrixCoreBlockEntity.DURATION + 4);
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).isOf(ModItems.BINDING_SALT), "One batch completes");
        c.assertFalse(core.started(), "The focus must start every batch");
        int remaining = ordo.contents().total();
        run(c.getWorld(), pos, 40);
        c.assertTrue(ordo.contents().total() == remaining, "A second base cannot auto-start");
        core.setStack(MatrixCoreBlockEntity.OUTPUT, ItemStack.EMPTY);
        var stone = new ItemStack(ModItems.PHILOSOPHERS_STONE);
        MatrixCoreBlockEntity.interactNearby(c.getWorld(), pos, player, stone);
        c.assertTrue(core.started() && stone.getCount() == 1, "Stone starts without being inserted or consumed");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void focusCannotStartInvalidOrUnchargedRecipe(TestContext c) {
        build(c);
        wake(c);
        var core = core(c);
        var player = c.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        var amulet = new ItemStack(ModItems.ALKAHEST_AMULET);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.GLASS_PANE));
        c.assertFalse(core.startWith(amulet, player), "Missing components reject start");
        c.assertTrue(amulet.getOrDefault(net.alkahest.registry.ModDataComponents.AMULET_USES_STEP, 0) == 0,
            "A rejected start costs no charge");
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.STRING));
        amulet.set(net.alkahest.registry.ModDataComponents.ESSENCE_CHARGE, 0);
        c.assertFalse(core.startWith(amulet, player), "Empty amulet cannot start a synthesis");
        c.assertFalse(core.started(), "The Matrix remains idle");
        c.complete();
    }

    /** A lone core is nothing; the cube needs four bricks in every middle layer and pillars on the corners. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void cubeNeedsPillarsAndBricks(TestContext c) {
        c.setBlockState(CX, CY, CZ, ModBlocks.MATRIX_CORE);
        BlockPos pos = c.getAbsolutePos(new BlockPos(CX, CY, CZ));
        var core = (MatrixCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        c.assertFalse(core.formed(), "A lone core is not a Matrix");
        build(c);
        c.assertTrue(core.formed(), "Eight pillars and eighteen bricks assemble the cube");
        c.setBlockState(CX + 1, CY, CZ, Blocks.STONE);
        c.assertFalse(core.formed(), "A stray stone in the shell breaks the cube");
        c.setBlockState(CX + 1, CY, CZ, ModBlocks.ALCHEMICAL_BRICK);
        c.assertTrue(core.formed(), "Repairing the shell restores the cube");
        c.complete();
    }

    /** A newly built cube does nothing at all until a wand wakes it up. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void matrixStaysDormantUntilAwoken(TestContext c) {
        BlockPos pos = build(c);
        var core = core(c);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.STRING));
        jar(c, CX + 3, CY, CZ, EssenceType.ORDO, 10);
        jar(c, CX - 3, CY, CZ, EssenceType.AQUA, 10);
        c.assertFalse(core.awake(), "A fresh Matrix is dormant");
        c.assertTrue(core.status() == MatrixCoreBlockEntity.STATUS_ASLEEP, "The core reports that it is asleep");
        run(c.getWorld(), pos, MatrixCoreBlockEntity.DURATION + 20);
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).isEmpty(), "A dormant cube never works");
        c.assertFalse(c.getWorld().getBlockState(pos).get(net.alkahest.block.custom.MatrixCoreBlock.ACTIVE),
            "A dormant cube does not glow");
        wake(c);
        c.assertTrue(c.getWorld().getBlockState(pos).get(net.alkahest.block.custom.MatrixCoreBlock.AWAKE),
            "The wand switches the cube on");
        start(c);
        run(c.getWorld(), pos, MatrixCoreBlockEntity.DURATION + 4);
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).getItem() == ModItems.BINDING_SALT,
            "An awakened cube works");
        c.complete();
    }

    /** Awakening hides the rough shell but keeps the blocks where they are. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void awakeningHidesTheShell(TestContext c) {
        BlockPos pos = build(c);
        BlockPos corner = c.getAbsolutePos(new BlockPos(CX + 1, CY + 1, CZ + 1));
        BlockPos brick = c.getAbsolutePos(new BlockPos(CX + 1, CY, CZ));
        c.assertFalse(c.getWorld().getBlockState(corner)
            .get(net.alkahest.block.custom.MatrixPillarBlock.REVEALED), "A dormant cube shows its pyramids");
        wake(c);
        c.assertTrue(c.getWorld().getBlockState(corner)
            .get(net.alkahest.block.custom.MatrixPillarBlock.REVEALED), "Awakening hides the corner pyramids");
        c.assertTrue(c.getWorld().getBlockState(brick)
            .get(net.alkahest.block.custom.AlchemicalBrickBlock.REVEALED), "Awakening hides the bricks");
        c.assertTrue(c.getWorld().getBlockState(brick).getBlock() == ModBlocks.ALCHEMICAL_BRICK,
            "Hidden blocks are still there and can be broken");
        c.setBlockState(CX + 1, CY, CZ, Blocks.AIR);
        MatrixCoreBlockEntity.tick(c.getWorld(), pos, core(c));
        c.assertFalse(core(c).awake(), "Breaking the shell puts the Matrix back to its initial state");
        c.assertFalse(c.getWorld().getBlockState(corner)
            .get(net.alkahest.block.custom.MatrixPillarBlock.REVEALED), "The remaining pyramids come back");
        c.complete();
    }

    /** Waking an already awakened cube changes nothing. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void awakeningHappensOnlyOnce(TestContext c) {
        BlockPos pos = build(c);
        wake(c);
        wake(c);
        c.assertTrue(core(c).awake(), "The Matrix stays awake");
        c.assertTrue(core(c).status() != MatrixCoreBlockEntity.STATUS_UNFORMED, "The cube is still whole");
        MatrixCoreBlockEntity.tick(c.getWorld(), pos, core(c));
        c.assertTrue(core(c).awake(), "A second activation does not break anything");
        c.complete();
    }

    /** Parts for the synthesis lie on the pedestals around the cube, not in the interface. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void pedestalPartsArePulledIntoTheSynthesis(TestContext c) {
        BlockPos pos = build(c);
        wake(c);
        var core = core(c);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.GLASS_PANE));
        pedestal(c, CX + 3, CY, CZ, new ItemStack(Items.GOLD_INGOT));
        jar(c, CX - 3, CY, CZ, EssenceType.ORDO, 12);
        jar(c, CX - 3, CY, CZ + 1, EssenceType.LUX, 12);
        start(c);
        run(c.getWorld(), pos, MatrixCoreBlockEntity.DURATION + 4);
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).getItem() == ModItems.ALCHEMISTS_MONOCLE,
            "The pane and the golden ingot from the pedestal make a monocle");
        var used = (net.alkahest.block.entity.MatrixPedestalBlockEntity)
            c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(CX + 3, CY, CZ)));
        c.assertTrue(used.isEmpty(), "The pedestal gives its item away");
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.BASE).isEmpty(), "The base is spent as well");
        c.complete();
    }

    /** Use the real block interactions and server ticker, with a diagonal pedestal below the core. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
    public void diamondUsesTwoClickedPedestalsOnRealTicks(TestContext c) {
        BlockPos pos = build(c);
        wake(c);
        var core = core(c);
        var player = c.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        ItemStack coal = new ItemStack(Items.COAL);
        MatrixCoreBlockEntity.interactNearby(c.getWorld(), pos, player, coal);
        c.assertTrue(coal.isEmpty(), "A real core click inserts the base");
        c.setBlockState(7, CY - 1, 7, ModBlocks.MATRIX_PEDESTAL);
        c.setBlockState(0, CY - 1, CZ, ModBlocks.MATRIX_PEDESTAL);
        BlockPos goldPos = c.getAbsolutePos(new BlockPos(7, CY - 1, 7));
        BlockPos saltPos = c.getAbsolutePos(new BlockPos(0, CY - 1, CZ));
        ItemStack gold = new ItemStack(Items.GOLD_INGOT);
        ItemStack salt = new ItemStack(ModItems.SALT);
        for (var entry : java.util.Map.of(goldPos, gold, saltPos, salt).entrySet()) {
            BlockPos target = entry.getKey();
            var hit = new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(target),
                net.minecraft.util.math.Direction.UP, target, false);
            c.getWorld().getBlockState(target).onUseWithItem(entry.getValue(), c.getWorld(), player,
                net.minecraft.util.Hand.MAIN_HAND, hit);
        }
        c.assertTrue(gold.isEmpty() && salt.isEmpty(), "Actual clicks place both ingredients");
        c.assertTrue(MatrixCoreBlockEntity.forPedestal(c.getWorld(), goldPos) == core,
            "Diagonal pedestal one level below is linked to the Matrix");
        c.assertTrue(core.recipe() != null && core.recipe().output().isOf(Items.DIAMOND),
            "Both components are recognised before work starts");
        jar(c, 0, CY, 0, EssenceType.METALLUM, 8);
        jar(c, 6, CY, 0, EssenceType.LUX, 6);
        jar(c, 0, CY, 6, EssenceType.ORDO, 4);
        MatrixCoreBlockEntity.interactNearby(c.getWorld(), pos, player, new ItemStack(ModItems.ALKAHEST_AMULET));
        c.assertTrue(core.started(), "Real amulet interaction starts the synthesis");
        // Atmosphere can legitimately slow a recipe to half speed without blocking it.
        c.runAtTick(340, () -> {
            c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).isOf(Items.DIAMOND),
                "The natural server ticker completes the diamond synthesis");
            var goldPedestal = (net.alkahest.block.entity.MatrixPedestalBlockEntity) c.getWorld().getBlockEntity(goldPos);
            var saltPedestal = (net.alkahest.block.entity.MatrixPedestalBlockEntity) c.getWorld().getBlockEntity(saltPos);
            c.assertTrue(goldPedestal.isEmpty() && saltPedestal.isEmpty(), "Both real pedestal items were absorbed");
            c.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void removedBottleRecipesCannotStart(TestContext c) {
        build(c); wake(c);
        var core = core(c);
        var removed = List.of(ModItems.MIDAS_ELIXIR, ModItems.NAPALM, ModItems.ACID_BOMB,
            ModItems.MIASMA, ModItems.LIGHTNING_IN_A_BOTTLE, ModItems.FROST_FLASK, ModItems.PURIFYING_FLASK);
        for (var recipe : net.alkahest.matrix.MatrixRecipe.all())
            c.assertFalse(removed.contains(recipe.output().getItem()), "Bottle products must not be Matrix outputs");
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.GLASS_BOTTLE));
        pedestal(c, 7, CY - 1, 7, new ItemStack(ModItems.CAUSTIC_SLIME));
        c.assertFalse(core.startWith(new ItemStack(ModItems.ALKAHEST_AMULET),
            c.createMockPlayer(net.minecraft.world.GameMode.CREATIVE)), "Old bottle and slime recipe cannot start");
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.BASE).isOf(Items.GLASS_BOTTLE), "Rejected recipe preserves inputs");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void changedRecipeCancelsWithoutConsumingItems(TestContext c) {
        BlockPos pos = build(c); wake(c);
        var core = core(c);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.GLASS_PANE));
        pedestal(c, 7, CY - 1, 7, new ItemStack(Items.GOLD_INGOT));
        var part = (net.alkahest.block.entity.MatrixPedestalBlockEntity) c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(7, CY - 1, 7)));
        jar(c, 0, CY, 0, EssenceType.ORDO, 12);
        start(c); run(c.getWorld(), pos, 50);
        int buffered = core.stored(EssenceType.ORDO);
        c.assertTrue(buffered > 0, "Essence was reserved");
        var registry = c.getWorld().getRegistryManager();
        var oldSave = core.createNbt(registry); oldSave.remove("StartedRecipe");
        var legacy = new MatrixCoreBlockEntity(pos, core.getCachedState());
        legacy.readComponentlessNbt(oldSave, registry);
        c.assertFalse(legacy.started(), "Old saves without identity stop instead of guessing");
        c.assertTrue(legacy.getStack(MatrixCoreBlockEntity.BASE).isOf(Items.GLASS_PANE) && legacy.stored(EssenceType.ORDO) == buffered,
            "Migration retains base and reserved essence");
        oldSave.putBoolean("Started", true);
        oldSave.putString("StartedRecipe", "removed-old-bottle-recipe");
        var removedRecipeSave = new MatrixCoreBlockEntity(pos, core.getCachedState());
        removedRecipeSave.setWorld(c.getWorld());
        removedRecipeSave.readComponentlessNbt(oldSave, registry);
        MatrixCoreBlockEntity.tick(c.getWorld(), pos, removedRecipeSave);
        c.assertFalse(removedRecipeSave.started(), "An in-flight removed recipe cancels on load");
        c.assertTrue(removedRecipeSave.getStack(MatrixCoreBlockEntity.BASE).isOf(Items.GLASS_PANE)
            && removedRecipeSave.stored(EssenceType.ORDO) == buffered, "Removed recipe migration retains inputs and buffer");
        part.takeOne(); run(c.getWorld(), pos, 1);
        c.assertFalse(core.started(), "Changing ingredients cancels synthesis");
        c.assertTrue(core.stored(EssenceType.ORDO) == buffered && core.getStack(MatrixCoreBlockEntity.BASE).isOf(Items.GLASS_PANE),
            "Cancellation preserves reserved essence and base");
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).isEmpty(), "No output created");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void midasElixirDrinkingAndNearbyRange(TestContext c) {
        var player = c.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        var item = (net.alkahest.item.custom.MidasElixirItem) ModItems.MIDAS_ELIXIR;
        ItemStack elixir = new ItemStack(ModItems.MIDAS_ELIXIR);
        c.assertTrue(item.getMaxUseTime(elixir, player) == 32 && item.getUseAction(elixir) == net.minecraft.util.UseAction.DRINK,
            "The elixir uses the normal 32 tick drinking animation");
        ItemStack returned = item.finishUsing(elixir, c.getWorld(), player);
        c.assertTrue(returned.isOf(Items.GLASS_BOTTLE) && elixir.isEmpty(), "Survival drinking returns one empty bottle");
        var effect = player.getStatusEffect(net.alkahest.registry.ModEffects.MIDAS_TOUCH);
        c.assertTrue(effect != null && effect.getDuration() == 3600, "Midas Touch lasts three minutes");
        BlockPos pos = c.getAbsolutePos(new BlockPos(CX, CY, CZ));
        player.setPosition(pos.getX() + 8.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        c.assertTrue(net.alkahest.effect.MidasTouchEffect.nearby(List.of(player), pos), "The eight-block boundary counts");
        player.setPosition(pos.getX() + 8.51, pos.getY() + 0.5, pos.getZ() + 0.5);
        c.assertFalse(net.alkahest.effect.MidasTouchEffect.nearby(List.of(player), pos), "Outside the radius gives no bonus");
        player.removeStatusEffect(net.alkahest.registry.ModEffects.MIDAS_TOUCH);
        player.setPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        c.assertFalse(net.alkahest.effect.MidasTouchEffect.nearby(List.of(player), pos), "Players without the effect do not boost");
        var spectator = c.createMockPlayer(net.minecraft.world.GameMode.SPECTATOR);
        spectator.setPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        spectator.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.alkahest.registry.ModEffects.MIDAS_TOUCH, 3600));
        c.assertFalse(net.alkahest.effect.MidasTouchEffect.nearby(List.of(spectator), pos), "Spectators do not boost");
        var creative = c.createMockPlayer(net.minecraft.world.GameMode.CREATIVE);
        ItemStack creativeElixir = new ItemStack(ModItems.MIDAS_ELIXIR);
        c.assertTrue(item.finishUsing(creativeElixir, c.getWorld(), creative) == creativeElixir && creativeElixir.getCount() == 1,
            "Creative drinking does not consume the elixir");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void midasProgressIsTwentyPercentFaster(TestContext c) {
        int normal = 0, boosted = 0, normalTicks = 0, boostedTicks = 0, prefillCrossings = 0;
        while (normal < MatrixCoreBlockEntity.DURATION) {
            normal += net.alkahest.effect.MidasTouchEffect.progressStep(false, normalTicks++);
        }
        while (boosted < MatrixCoreBlockEntity.DURATION) {
            int previous = boosted;
            boosted = Math.min(MatrixCoreBlockEntity.DURATION,
                boosted + net.alkahest.effect.MidasTouchEffect.progressStep(true, boostedTicks++));
            if (previous / 10 != boosted / 10) prefillCrossings++;
        }
        c.assertTrue(normalTicks == 160 && boostedTicks == 133, "Midas gives one extra progress step per five ticks");
        c.assertTrue(prefillCrossings == 16, "Accelerated steps never skip a ten-progress essence request");
        c.complete();
    }

    /** String becomes three binding salts, paid for with essence taken straight out of the jars. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void synthesisesBindingSaltFromJars(TestContext c) {
        BlockPos pos = build(c);
        wake(c);
        var core = core(c);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.STRING, 1));
        JarBlockEntity ordo = jar(c, CX + 3, CY, CZ, EssenceType.ORDO, 10);
        JarBlockEntity aqua = jar(c, CX - 3, CY, CZ, EssenceType.AQUA, 10);
        start(c);
        run(c.getWorld(), pos, MatrixCoreBlockEntity.DURATION + 4);

        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).getItem() == ModItems.BINDING_SALT,
            "The Matrix yields binding salt");
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).getCount() == 3, "Three salts are made at once");
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.BASE).isEmpty(), "The string is spent");
        c.assertTrue(ordo.contents().total() == 6, "Four ordo left the jar");
        c.assertTrue(aqua.contents().total() == 8, "Two aqua left the jar");
        c.complete();
    }

    /** Without the extra part on any pedestal the base just waits inside the core. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void waitsForTheMissingPart(TestContext c) {
        BlockPos pos = build(c);
        wake(c);
        var core = core(c);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.GLASS_PANE));
        jar(c, CX + 3, CY, CZ, EssenceType.ORDO, 12);
        jar(c, CX - 3, CY, CZ, EssenceType.LUX, 12);
        c.assertTrue(core.status() == MatrixCoreBlockEntity.STATUS_NO_RECIPE, "The Matrix asks for the missing part");
        run(c.getWorld(), pos, MatrixCoreBlockEntity.DURATION + 4);
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).isEmpty(), "Nothing is made without the gold");
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.BASE).getCount() == 1, "The pane stays");
        c.complete();
    }

    /** Essence trickles in while the synthesis runs, so the sparks are visible the whole time. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void essenceCrossesTheAirFromJars(TestContext c) {
        BlockPos pos = build(c);
        wake(c);
        var core = core(c);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.STRING));
        JarBlockEntity ordo = jar(c, CX + 4, CY + 1, CZ, EssenceType.ORDO, 10);
        start(c);
        run(c.getWorld(), pos, 60);
        c.assertTrue(ordo.contents().total() < 10, "The jar is already giving essence away mid-synthesis");
        c.assertTrue(core.stored(EssenceType.ORDO) > 0, "The core keeps what flew in");
        c.assertTrue(c.getWorld().getBlockState(pos).get(MatrixCoreBlock.ACTIVE), "A working Matrix glows");
        c.complete();
    }

    /** No essence around means no synthesis and no losses. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void withoutEssenceNothingHappens(TestContext c) {
        // The shared GameTest world may contain jars in adjacent structures, within the 12-block
        // pickup radius. Build this negative test well above the other structures instead.
        int y = CY + 32;
        c.setBlockState(CX, y, CZ, ModBlocks.MATRIX_CORE);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dy == 0 && dz == 0) continue;
            c.setBlockState(CX + dx, y + dy, CZ + dz,
                dx != 0 && dy != 0 && dz != 0 ? ModBlocks.MATRIX_PILLAR : ModBlocks.ALCHEMICAL_BRICK);
        }
        BlockPos pos = c.getAbsolutePos(new BlockPos(CX, y, CZ));
        var core = (MatrixCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        core.setAwake(c.getWorld(), pos, true);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.STRING, 2));
        c.assertTrue(core.startWith(new ItemStack(ModItems.ALKAHEST_AMULET),
            c.createMockPlayer(net.minecraft.world.GameMode.CREATIVE)), "Focus starts synthesis");
        run(c.getWorld(), pos, MatrixCoreBlockEntity.DURATION * 2);
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).isEmpty(), "No essence, no product");
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.BASE).getCount() == 2, "The base is untouched");
        c.assertTrue(core.status() == MatrixCoreBlockEntity.STATUS_NO_ESSENCE, "The core reports the missing essence");
        c.complete();
    }

    /** A full output slot stops the cube instead of destroying the result. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fullOutputBlocksTheCube(TestContext c) {
        BlockPos pos = build(c);
        wake(c);
        var core = core(c);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.STRING));
        core.setStack(MatrixCoreBlockEntity.OUTPUT, new ItemStack(Items.STONE, 64));
        jar(c, CX + 3, CY, CZ, EssenceType.ORDO, 10);
        jar(c, CX - 3, CY, CZ, EssenceType.AQUA, 10);
        run(c.getWorld(), pos, MatrixCoreBlockEntity.DURATION + 4);
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.OUTPUT).getCount() == 64, "The full slot is left alone");
        c.assertTrue(core.status() == MatrixCoreBlockEntity.STATUS_BLOCKED, "The core reports the blocked output");
        c.complete();
    }

    /** Breaking the shell stops the machine at once and does not eat the base item. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void brokenShellStopsTheSynthesis(TestContext c) {
        BlockPos pos = build(c);
        wake(c);
        var core = core(c);
        core.setStack(MatrixCoreBlockEntity.BASE, new ItemStack(Items.STRING));
        jar(c, CX + 3, CY, CZ, EssenceType.ORDO, 10);
        jar(c, CX - 3, CY, CZ, EssenceType.AQUA, 10);
        start(c);
        run(c.getWorld(), pos, 40);
        c.assertTrue(core.progress() > 0, "The synthesis started");
        c.setBlockState(CX, CY + 1, CZ, Blocks.AIR);
        run(c.getWorld(), pos, 2);
        c.assertTrue(core.progress() == 0, "Breaking a brick resets the progress");
        c.assertFalse(c.getWorld().getBlockState(pos).get(MatrixCoreBlock.ACTIVE), "The cube stops glowing");
        c.assertFalse(core.awake(), "The Matrix falls back to its dormant form");
        c.assertTrue(core.getStack(MatrixCoreBlockEntity.BASE).getCount() == 1, "The base survives");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void newSynthesisRecipesHaveDistinctInputsAndTwoPedestalItems(TestContext c) {
        var recipes = net.alkahest.matrix.MatrixRecipe.all();
        for (var output : List.of(Items.ARROW, Items.POWERED_RAIL,
                Items.SEA_LANTERN, Items.MAGMA_BLOCK, Items.SOUL_LANTERN, Items.RAIL)) {
            var matching = recipes.stream().filter(r -> r.output().isOf(output)).toList();
            c.assertTrue(matching.size() == 1, "Exactly one new recipe exists for " + output);
            var recipe = matching.getFirst();
            c.assertTrue(recipe.components().size() >= 2, "Every new Matrix recipe uses multiple pedestal ingredients");
            var given = recipe.components().stream().map(ItemStack::new).toList();
            c.assertTrue(net.alkahest.matrix.MatrixRecipe.find(new ItemStack(recipe.base()), given) == recipe,
                "The complete recipe has an unambiguous match");
            c.assertTrue(net.alkahest.matrix.MatrixRecipe.find(new ItemStack(recipe.base()), given.subList(0, 1)) == null,
                "A missing ingredient cannot start this recipe");
        }
        c.complete();
    }

}
