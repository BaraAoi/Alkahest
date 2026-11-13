package net.alkahest.test;

import net.alkahest.block.custom.TransmutatorPillarBlock;
import net.alkahest.block.entity.JarBlockEntity;
import net.alkahest.block.entity.TransmutatorCoreBlockEntity;
import net.alkahest.block.entity.TransmutatorPillarBlockEntity;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.essence.JarContentsComponent;
import net.alkahest.registry.ModBlocks;
import net.alkahest.registry.ModDataComponents;
import net.alkahest.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/** The 3x1x3 Transmutator: a ring of pillars feeds the core with essence taken straight from jars. */
public class TransmutatorGameTests implements FabricGameTest {
    private static final int CX = 3, CY = 2, CZ = 3;

    /** Isolate conservation tests from the neighbouring structures' jars in the shared world. */
    private static BlockPos isolatedPlatform(TestContext c, int y) {
        c.setBlockState(CX, y, CZ, ModBlocks.TRANSMUTATOR_CORE);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx != 0 || dz != 0) c.setBlockState(CX + dx, y, CZ + dz, ModBlocks.TRANSMUTATOR_PILLAR);
        }
        return c.getAbsolutePos(new BlockPos(CX, y, CZ));
    }

    private static int stored(TransmutatorCoreBlockEntity core, EssenceType type) {
        return core.pillars().stream().mapToInt(p -> p.stored(type)).sum();
    }

    private static JarContentsComponent of(EssenceType type, int amount) {
        return new JarContentsComponent(List.of(new EssenceStack(type, amount, 0.4f)));
    }

    /** Builds the core with its eight pillars in one horizontal plane. */
    private static BlockPos build(TestContext c) {
        c.setBlockState(CX, CY, CZ, ModBlocks.TRANSMUTATOR_CORE);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                c.setBlockState(CX + dx, CY, CZ + dz, ModBlocks.TRANSMUTATOR_PILLAR);
            }
        }
        return c.getAbsolutePos(new BlockPos(CX, CY, CZ));
    }

    private static JarBlockEntity jar(TestContext c, int x, int y, int z, EssenceType type, int amount) {
        c.setBlockState(x, y, z, ModBlocks.JAR);
        JarBlockEntity jar = (JarBlockEntity) c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(x, y, z)));
        jar.setContents(of(type, amount));
        return jar;
    }

    private static void installInput(TestContext c, TransmutatorCoreBlockEntity core) {
        core.setStack(TransmutatorCoreBlockEntity.INPUT, new ItemStack(Items.COPPER_INGOT, 2));
        ItemStack amulet = new ItemStack(ModItems.ALKAHEST_AMULET);
        amulet.set(ModDataComponents.ESSENCE_CHARGE, 4);
        core.setStack(TransmutatorCoreBlockEntity.AMULET, amulet);
    }

    private static void run(World world, BlockPos corePos, int ticks) {
        TransmutatorCoreBlockEntity core = (TransmutatorCoreBlockEntity) world.getBlockEntity(corePos);
        for (int i = 0; i < ticks; i++) TransmutatorCoreBlockEntity.tick(world, corePos, core);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void ironRecipeChoiceIsValidatedSavedAndResetsProgress(TestContext c) {
        BlockPos pos=tower(c,CY+32); var world=c.getWorld();
        var core=(TransmutatorCoreBlockEntity)world.getBlockEntity(pos); core.wake();
        var player=c.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        player.setPosition(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);
        var h=(net.alkahest.screen.TransmutatorScreenHandler)core.createMenu(1,player.getInventory(),player);
        c.assertFalse(h.onButtonClick(player,0),"No input cannot change the iron recipe");
        core.setStack(0,new ItemStack(Items.IRON_INGOT,2));
        core.setStack(1,new ItemStack(ModItems.ALKAHEST_AMULET));
        c.assertTrue(core.recipe()==null && core.status()==TransmutatorCoreBlockEntity.STATUS_CHOOSE_RECIPE && h.needsRecipeChoice(), "Ambiguous input waits for an explicit selection");
        run(world,pos,140); c.assertTrue(core.progress()==0 && core.pipeCost().isEmpty() && core.getStack(2).isEmpty(), "No progress, payment or output before selection");
        c.assertTrue(h.onButtonClick(player,1) && core.recipe().output().isOf(Items.GOLD_INGOT), "Gold is selected directly");
        run(world,pos,40); c.assertTrue(core.progress()==40,"Selected recipe makes progress");
        c.assertFalse(h.onButtonClick(player,17),"Unknown button IDs are rejected");
        var spectator=c.createMockPlayer(net.minecraft.world.GameMode.SPECTATOR);
        spectator.setPosition(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);
        c.assertFalse(h.onButtonClick(spectator,2),"Spectators cannot change the recipe");
        c.assertTrue(h.onButtonClick(player,2) && core.progress()==0,"Changing the recipe resets progress");
        c.assertTrue(core.selectedRecipeIndex()==1 && core.recipe().output().isOf(Items.DIAMOND) && core.duration()==100,"Alternate recipe is selected");
        c.assertTrue(h.maxProgress()==100 && h.selectedRecipeIndex()==1 && h.selectedOutput().isOf(Items.DIAMOND)
            && h.requirements().size()==4,"GUI properties and cost match the selected recipe");
        var clientInventory=new net.minecraft.inventory.SimpleInventory(3);
        clientInventory.setStack(0,new ItemStack(Items.IRON_INGOT));
        var clientProperties=new net.minecraft.screen.ArrayPropertyDelegate(4);
        clientProperties.set(1,100); clientProperties.set(3,2);
        var clientScreen=new net.alkahest.screen.TransmutatorScreenHandler(2,player.getInventory(),clientInventory,clientProperties);
        c.assertTrue(clientScreen.selectedOutput().isOf(Items.DIAMOND) && clientScreen.requirements().size()==4
            && clientScreen.maxProgress()==100,"Client mirror displays the selected result, price and duration");
        var rendered=new TransmutatorCoreBlockEntity(pos,core.getCachedState());
        rendered.readComponentlessNbt(core.toUpdatePacket().getNbt(),world.getRegistryManager());
        c.assertTrue(rendered.selectedRecipeIndex()==1 && rendered.duration()==100,"Block update sync carries recipe to the renderer");
        var restored=new TransmutatorCoreBlockEntity(pos,core.getCachedState());
        restored.readComponentlessNbt(core.createNbt(world.getRegistryManager()),world.getRegistryManager());
        c.assertTrue(restored.selectedRecipeIndex()==1 && restored.recipe().output().isOf(Items.DIAMOND),"Choice and input survive save/load");
        core.setStack(2,new ItemStack(Items.GOLD_INGOT));
        c.assertTrue(core.status()==TransmutatorCoreBlockEntity.STATUS_BLOCKED,"Existing gold blocks diamond output without loss");
        c.assertTrue(h.onButtonClick(player,1) && core.getStack(2).isOf(Items.GOLD_INGOT),"Switching back preserves the output");
        c.assertTrue(core.recipe().output().isOf(Items.GOLD_INGOT) && core.duration()==100,"Original gold recipe remains available");
        core.setStack(0,new ItemStack(Items.COPPER_INGOT));
        c.assertFalse(h.onButtonClick(player,0),"Other inputs cannot toggle this choice");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void unselectedRecipeNeverWorksOrConsumesResourcesInEitherMachine(TestContext c) {
        int y=CY+128; var pos=isolatedPlatform(c,y); var world=c.getWorld();
        var core=(TransmutatorCoreBlockEntity)world.getBlockEntity(pos);
        ItemStack amulet=new ItemStack(ModItems.ALKAHEST_AMULET); amulet.set(ModDataComponents.ESSENCE_CHARGE,4);
        core.setStack(0,new ItemStack(Items.IRON_INGOT,2)); core.setStack(1,amulet);
        var ordo=jar(c,1,y,1,EssenceType.ORDO,20); var metal=jar(c,5,y,1,EssenceType.METALLUM,20);
        run(world,pos,250);
        c.assertTrue(core.recipe()==null && core.progress()==0 && core.getStack(2).isEmpty() && core.getStack(0).getCount()==2
            && core.getStack(1).get(ModDataComponents.ESSENCE_CHARGE)==4 && ordo.contents().total()==20 && metal.contents().total()==20,
            "Legacy platform does not prefill or process without a choice");
        var saved=core.createNbt(world.getRegistryManager()); saved.putInt("Progress",99);
        core.readComponentlessNbt(saved,world.getRegistryManager());
        c.assertTrue(core.recipe()==null && core.progress()==0, "Unselected saved progress is reset safely on load");
        c.assertTrue(core.selectRecipe(0), "Explicitly select gold");
        run(world,pos,20); c.assertTrue(core.progress()==20, "Chosen recipe can start");
        c.assertTrue(core.clearRecipeChoice() && core.progress()==0, "Changing choice stops and resets the cycle");
        run(world,pos,120);
        c.assertTrue(core.recipe()==null && core.progress()==0 && core.getStack(2).isEmpty(), "Cannot continue after selection cleared");
        var waiting=core.createNbt(world.getRegistryManager());
        core.readComponentlessNbt(waiting,world.getRegistryManager());
        c.assertTrue(core.recipe()==null, "Waiting state persists on reload");
        c.assertTrue(core.selectRecipe(0), "Gold is selected explicitly");
        var selected=core.createNbt(world.getRegistryManager());
        core.readComponentlessNbt(selected,world.getRegistryManager());
        c.assertTrue(core.recipe()!=null && core.recipe().output().isOf(Items.GOLD_INGOT), "Explicit first choice persists on reload");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void genericRecipeSelectionSupportsThreeAndReordering(TestContext c) {
        var sand = new ItemStack(Items.SAND);
        var a = new net.alkahest.transmutation.TransmutationRecipe("sand_first", sand.copy(), new ItemStack(Items.GLASS), java.util.Map.of(EssenceType.IGNIS, 1), 100);
        var b = new net.alkahest.transmutation.TransmutationRecipe("sand_second", sand.copy(), new ItemStack(Items.CLAY_BALL), java.util.Map.of(EssenceType.AQUA, 2), 120);
        var d = new net.alkahest.transmutation.TransmutationRecipe("sand_third", sand.copy(), new ItemStack(Items.GRAVEL), java.util.Map.of(EssenceType.TERRA, 3), 140);
        var other = new net.alkahest.transmutation.TransmutationRecipe("coal_only", new ItemStack(Items.COAL), new ItemStack(Items.DIAMOND), java.util.Map.of(EssenceType.LUX, 1), 100);
        var all = List.of(a, other, b, d);
        var options = net.alkahest.transmutation.TransmutationRecipe.choices(all, sand);
        c.assertTrue(options.equals(List.of(a,b,d)), "All and only matching inputs appear in registry order");
        c.assertTrue(net.alkahest.transmutation.TransmutationRecipe.selectedIndex(options, null) == -1
            && net.alkahest.transmutation.TransmutationRecipe.selected(options, -1) == null, "Three options need an explicit choice");
        var reordered = List.of(d,a,b);
        int index = net.alkahest.transmutation.TransmutationRecipe.selectedIndex(reordered, b.id());
        c.assertTrue(net.alkahest.transmutation.TransmutationRecipe.selected(reordered,index) == b, "Stable ID survives registry reorder");
        c.assertTrue(net.alkahest.transmutation.TransmutationRecipe.selectedIndex(reordered, "removed_recipe") == -1
            && net.alkahest.transmutation.TransmutationRecipe.selected(reordered, 999) == null, "Missing ID or invalid network index must not start another recipe");
        c.assertTrue(net.alkahest.transmutation.TransmutationRecipe.selectedIndex(List.of(other), null) == 0
            && net.alkahest.transmutation.TransmutationRecipe.choices(all, ItemStack.EMPTY).isEmpty(), "One recipe is automatic, zero has none");
        var ids = new java.util.HashSet<String>();
        for (var recipe : net.alkahest.transmutation.TransmutationRecipe.all())
            c.assertTrue(ids.add(recipe.id()) && !recipe.id().isBlank(), "Built-in recipe IDs must be unique and stable");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void legacyIronChoiceMigratesEvenWithDifferentInputAndKeepsProgress(TestContext c) {
        BlockPos pos=tower(c,CY+32); var world=c.getWorld();
        var core=(TransmutatorCoreBlockEntity)world.getBlockEntity(pos); core.wake();
        core.setStack(0,new ItemStack(Items.IRON_INGOT));
        core.setStack(1,new ItemStack(ModItems.ALKAHEST_AMULET));
        var old=core.createNbt(world.getRegistryManager());
        old.remove("RecipeChoices"); old.putBoolean("IronDiamond",true); old.putInt("Progress",40);
        core.readComponentlessNbt(old,world.getRegistryManager());
        c.assertTrue(core.selectedRecipeIndex()==1 && core.recipe().output().isOf(Items.DIAMOND) && core.progress()==40, "Old iron flag migrates without resetting valid progress");
        core.setStack(0,new ItemStack(Items.COPPER_INGOT));
        c.assertTrue(core.selectedRecipeIndex()==0 && !core.selectRecipe(0), "Another input with one recipe cannot change the selection");
        var saved=core.createNbt(world.getRegistryManager());
        var restored=new TransmutatorCoreBlockEntity(pos,core.getCachedState());
        restored.readComponentlessNbt(saved,world.getRegistryManager());
        restored.setStack(0,new ItemStack(Items.IRON_INGOT));
        c.assertTrue(restored.selectedRecipeIndex()==1 && restored.recipe().output().isOf(Items.DIAMOND), "Iron choice is remembered across another input and save/reload");
        var oldNoInput=core.createNbt(world.getRegistryManager());
        oldNoInput.remove("RecipeChoices"); oldNoInput.putBoolean("IronDiamond",true);
        core.readComponentlessNbt(oldNoInput,world.getRegistryManager());
        core.setStack(0,new ItemStack(Items.IRON_INGOT));
        c.assertTrue(core.selectedRecipeIndex()==1, "Legacy selection migrates even when iron is not currently loaded");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void recipeSelectionSynchronizesThroughScreenProperties(TestContext c) {
        BlockPos pos=tower(c,CY+32); var world=c.getWorld();
        var core=(TransmutatorCoreBlockEntity)world.getBlockEntity(pos); core.wake();
        var player=c.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        player.setPosition(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);
        core.setStack(0,new ItemStack(Items.IRON_INGOT));
        var server=(net.alkahest.screen.TransmutatorScreenHandler)core.createMenu(1,player.getInventory(),player);
        var client=new net.alkahest.screen.TransmutatorScreenHandler(1,player.getInventory());
        client.getSlot(0).setStack(new ItemStack(Items.IRON_INGOT));
        server.addListener(new net.minecraft.screen.ScreenHandlerListener() {
            public void onSlotUpdate(net.minecraft.screen.ScreenHandler handler,int slot,ItemStack stack) { }
            public void onPropertyUpdate(net.minecraft.screen.ScreenHandler handler,int id,int value) { client.setProperty(id,value); }
        });
        server.sendContentUpdates();
        c.assertTrue(client.recipeCount()==2 && client.needsRecipeChoice() && client.selectedOutput().isEmpty() && client.requirements().isEmpty(), "Unselected state syncs to client");
        c.assertTrue(server.onButtonClick(player,2), "Player selects diamond directly"); server.sendContentUpdates();
        c.assertTrue(client.selectedRecipeIndex()==1 && client.selectedOutput().isOf(Items.DIAMOND) && client.requirements().size()==4, "Choice and price sync to open screen");
        c.assertTrue(server.onButtonClick(player,0), "Player requests another choice"); server.sendContentUpdates();
        c.assertTrue(client.needsRecipeChoice() && client.selectedOutput().isEmpty(), "Clearing synchronizes waiting state");
        c.assertTrue(server.onButtonClick(player,1), "Player selects gold directly"); server.sendContentUpdates();
        c.assertTrue(client.selectedRecipeIndex()==0 && client.selectedOutput().isOf(Items.GOLD_INGOT), "Explicit first choice syncs to screen");
        client.getSlot(0).setStack(new ItemStack(Items.SAND));
        c.assertFalse(client.canSelectRecipe(), "Single-recipe input hides the control");
        client.setProperty(3,999);
        c.assertTrue(client.selectedOutput().isOf(Items.GLASS), "Single recipe does not depend on a stale property");
        client.getSlot(0).setStack(new ItemStack(Items.IRON_INGOT));
        c.assertTrue(client.needsRecipeChoice() && client.selectedOutput().isEmpty(), "Invalid network index never picks an unrelated recipe");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void diamondTowerWaitsForFullPriceThenPaysExactlyOnce(TestContext c) {
        int y=CY+80; BlockPos pos=tower(c,y); var world=c.getWorld();
        var core=(TransmutatorCoreBlockEntity)world.getBlockEntity(pos); core.wake();
        core.setStack(0,new ItemStack(Items.IRON_INGOT,2));
        ItemStack amulet=new ItemStack(ModItems.ALKAHEST_AMULET); amulet.set(ModDataComponents.ESSENCE_CHARGE,4);
        core.setStack(1,amulet); c.assertTrue(core.selectRecipe(1),"Select diamond");
        c.setBlockState(CX+1,y,CZ,ModBlocks.ALCHEMY_PIPE); c.setBlockState(CX+2,y,CZ,ModBlocks.ALCHEMY_PIPE);
        BlockPos pipe=c.getAbsolutePos(new BlockPos(CX+1,y,CZ));
        var terra=jar(c,CX+1,y,CZ-1,EssenceType.TERRA,1);
        var lux=jar(c,CX+1,y,CZ+1,EssenceType.LUX,1);
        var ordo=jar(c,CX+2,y,CZ-1,EssenceType.ORDO,1);
        run(world,pos,99);
        c.assertTrue(core.progress()==99 && core.pipeCost().isEmpty(),"Diamond cannot be paid before 100 ticks");
        run(world,pos,1);
        net.alkahest.block.entity.AlchemyPipeBlockEntity.pulse(world,pipe);
        c.assertTrue(core.progress()==100 && terra.contents().total()==1 && lux.contents().total()==1
            && ordo.contents().total()==1,"Missing Metallum prevents any partial payment");
        c.assertTrue(core.getStack(0).getCount()==2 && core.getStack(2).isEmpty()
            && core.getStack(1).get(ModDataComponents.ESSENCE_CHARGE)==4,"Waiting preserves inputs, output and charge");
        var metal=jar(c,CX+2,y,CZ+1,EssenceType.METALLUM,1);
        net.alkahest.block.entity.AlchemyPipeBlockEntity.pulse(world,pipe);
        c.assertTrue(terra.contents().total()==0 && lux.contents().total()==0 && ordo.contents().total()==0 && metal.contents().total()==0,
            "All four full costs are consumed atomically");
        c.assertTrue(core.getStack(2).isOf(Items.DIAMOND) && core.getStack(2).getCount()==1
            && core.getStack(0).getCount()==1 && core.getStack(1).get(ModDataComponents.ESSENCE_CHARGE)==3 && core.progress()==0,
            "One iron and one charge produce exactly one diamond");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void coalDiamondUsesItsReducedPrice(TestContext c) {
        int y=CY+80; BlockPos pos=tower(c,y); var world=c.getWorld();
        var core=(TransmutatorCoreBlockEntity)world.getBlockEntity(pos); core.wake();
        core.setStack(0,new ItemStack(Items.COAL,2));
        ItemStack amulet=new ItemStack(ModItems.ALKAHEST_AMULET); amulet.set(ModDataComponents.ESSENCE_CHARGE,4);
        core.setStack(1,amulet);
        c.setBlockState(CX+1,y,CZ,ModBlocks.ALCHEMY_PIPE);
        BlockPos pipe=c.getAbsolutePos(new BlockPos(CX+1,y,CZ));
        var lux=jar(c,CX+1,y,CZ-1,EssenceType.LUX,3);
        var metal=jar(c,CX+1,y,CZ+1,EssenceType.METALLUM,2);
        run(world,pos,100);
        net.alkahest.block.entity.AlchemyPipeBlockEntity.pulse(world,pipe);
        c.assertTrue(lux.contents().total()==3 && metal.contents().total()==2 && core.getStack(2).isEmpty(),
            "The discounted coal recipe still requires full payment");
        metal.setContents(of(EssenceType.METALLUM,3));
        net.alkahest.block.entity.AlchemyPipeBlockEntity.pulse(world,pipe);
        c.assertTrue(lux.contents().total()==0 && metal.contents().total()==0 && core.getStack(2).isOf(Items.DIAMOND)
            && core.getStack(0).getCount()==1 && core.getStack(1).get(ModDataComponents.ESSENCE_CHARGE)==3,
            "Three of each essence, one coal and one charge buy exactly one diamond");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void legacyPlatformCanPayDiamondRecipe(TestContext c) {
        int y=CY+128; var pos=isolatedPlatform(c,y); var world=c.getWorld();
        var core=(TransmutatorCoreBlockEntity)world.getBlockEntity(pos);
        core.setStack(0,new ItemStack(Items.IRON_INGOT)); core.setStack(1,new ItemStack(ModItems.ALKAHEST_AMULET));
        c.assertTrue(core.selectRecipe(1),"Select diamond");
        var terra=jar(c,1,y,1,EssenceType.TERRA,1); var lux=jar(c,5,y,1,EssenceType.LUX,1);
        var ordo=jar(c,1,y,5,EssenceType.ORDO,1); var metal=jar(c,5,y,5,EssenceType.METALLUM,1);
        var saved=core.createNbt(world.getRegistryManager()); saved.putInt("Progress",2400);
        core.readComponentlessNbt(saved,world.getRegistryManager());
        c.assertTrue(core.progress()==100, "Old long-duration progress clamps to ready, not back to zero");
        run(world,pos,1);
        c.assertTrue(core.getStack(2).isOf(Items.DIAMOND),"Old platform can pay the new price");
        c.assertTrue(terra.contents().total()+lux.contents().total()+ordo.contents().total()+metal.contents().total()==0,
            "Exact new price was consumed from the jars");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void diamondPricesAreBalancedAndAllRecipesGotCheaper(TestContext c) {
        var recipes = net.alkahest.transmutation.TransmutationRecipe.all();
        int[] oldTotals = {2, 2, 4, 3, 5, 5, 6, 8, 4, 2, 3, 3, 5, 7, 5, 5, 3, 120};
        int[] newTotals = {1, 1, 2, 2, 4, 3, 3, 6, 2, 1, 2, 2, 3, 4, 3, 3, 2, 4};
        c.assertTrue(recipes.size() == oldTotals.length, "All 18 recipes still exist");
        for (int i = 0; i < recipes.size(); i++) {
            var recipe = recipes.get(i);
            int cost = net.alkahest.transmutation.TransmutationRecipe.cost(recipe.cost());
            c.assertTrue(cost == newTotals[i] && cost < oldTotals[i], "Each old cost was reduced to the intended price: " + i);
            c.assertTrue(recipe.duration() == 100 && recipe.input().getCount() == 1 && recipe.output().getCount() == 1,
                "Every transmutation still takes five seconds and transforms one item");
        }
        var iron = net.alkahest.transmutation.TransmutationRecipe.find(new ItemStack(Items.IRON_INGOT), true);
        var coal = net.alkahest.transmutation.TransmutationRecipe.find(new ItemStack(Items.COAL));
        c.assertTrue(iron.output().isOf(Items.DIAMOND) && coal.output().isOf(Items.DIAMOND), "Both routes produce a diamond");
        c.assertTrue(iron.cost().equals(java.util.Map.of(EssenceType.TERRA,1, EssenceType.LUX,1, EssenceType.ORDO,1, EssenceType.METALLUM,1)), "Iron has the intended four-unit price");
        c.assertTrue(coal.cost().equals(java.util.Map.of(EssenceType.METALLUM,3, EssenceType.LUX,3)), "Coal has the intended six-unit price");
        c.assertTrue(net.alkahest.transmutation.TransmutationRecipe.cost(iron.cost()) < net.alkahest.transmutation.TransmutationRecipe.cost(coal.cost())
            && iron.duration() == coal.duration(), "Iron uses less essence and is no slower than coal");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void actualHoppersFeedInputAndTakeOnlyOutput(TestContext c) {
        c.setBlockState(CX, CY, CZ, ModBlocks.TRANSMUTATOR_CORE);
        var world = c.getWorld(); var pos = c.getAbsolutePos(new BlockPos(CX, CY, CZ));
        var core = (TransmutatorCoreBlockEntity)world.getBlockEntity(pos);
        var top = pos.up(); var bottom = pos.down();
        world.setBlockState(top, net.minecraft.block.Blocks.HOPPER.getDefaultState());
        world.setBlockState(bottom, net.minecraft.block.Blocks.HOPPER.getDefaultState());
        var feeder = (net.minecraft.block.entity.HopperBlockEntity)world.getBlockEntity(top);
        var collector = (net.minecraft.block.entity.HopperBlockEntity)world.getBlockEntity(bottom);
        feeder.setStack(0, new ItemStack(Items.SAND, 3));
        core.setStack(TransmutatorCoreBlockEntity.AMULET, new ItemStack(ModItems.ALKAHEST_AMULET));
        core.setStack(TransmutatorCoreBlockEntity.OUTPUT, new ItemStack(Items.GLASS, 2));
        c.runAtTick(35, () -> {
            c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.INPUT).getCount() == 3, "Top hopper feeds eligible inputs");
            c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.AMULET).isOf(ModItems.ALKAHEST_AMULET), "Installed amulet stays in place");
            c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isEmpty() && collector.getStack(0).getCount() == 2
                && collector.getStack(0).isOf(Items.GLASS), "Bottom hopper extracts only the output");
            c.assertFalse(core.canInsert(TransmutatorCoreBlockEntity.AMULET, new ItemStack(ModItems.ALKAHEST_AMULET), net.minecraft.util.math.Direction.UP), "Amulet is player-managed");
            c.assertFalse(core.canExtract(TransmutatorCoreBlockEntity.INPUT, new ItemStack(Items.SAND), net.minecraft.util.math.Direction.DOWN), "Input cannot be extracted");
            c.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void matchingInputTopUpsPreserveTransmutatorProgress(TestContext c) {
        BlockPos pos = build(c); var core = (TransmutatorCoreBlockEntity)c.getWorld().getBlockEntity(pos);
        installInput(c, core); run(c.getWorld(), pos, 5); int before = core.progress();
        c.assertTrue(before > 0, "Machine has started");
        core.setStack(TransmutatorCoreBlockEntity.INPUT, new ItemStack(Items.COPPER_INGOT, 5));
        c.assertTrue(core.progress() == before, "Matching input top-up preserves progress");
        core.setStack(TransmutatorCoreBlockEntity.INPUT, new ItemStack(Items.IRON_INGOT));
        c.assertTrue(core.progress() == 0, "Changing input resets progress");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void midasSpeedCrossesEveryTransmutatorPrefillBoundary(TestContext c) {
        for (int phase = 0; phase < 5; phase++) {
            int progress = 0, ticks = 0, requests = 0;
            while (progress < TransmutatorCoreBlockEntity.DURATION) {
                int before = progress;
                progress = Math.min(TransmutatorCoreBlockEntity.DURATION, before +
                    net.alkahest.effect.MidasTouchEffect.progressStep(true, ticks + phase));
                if (before / 10 != progress / 10) requests++;
                ticks++;
            }
            c.assertTrue(ticks >= 83 && ticks <= 84, "The 100 progress ticks finish at Midas speed for every phase");
            c.assertTrue(requests == 10, "Every ten-progress essence request occurs at boosted speed");
        }
        BlockPos pos = build(c);
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        installInput(c, core);
        TransmutatorCoreBlockEntity.tick(c.getWorld(), pos, core);
        c.assertTrue(core.progress() == 1, "Without the effect the machine advances normally");
        c.complete();
    }

    private static BlockPos tower(TestContext c, int y) {
        c.setBlockState(CX, y, CZ, ModBlocks.TRANSMUTATOR_CORE);
        c.setBlockState(CX, y + 1, CZ, ModBlocks.TRANSMUTATOR_CHAMBER);
        c.setBlockState(CX, y + 2, CZ, ModBlocks.TRANSMUTATOR_CROWN);
        return c.getAbsolutePos(new BlockPos(CX, y, CZ));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void towerNeedsAwakeningAndSurvivesReload(TestContext c) {
        int y = CY + 32;
        BlockPos pos = tower(c, y);
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        installInput(c, core);
        run(c.getWorld(), pos, 200);
        c.assertTrue(core.verticalFormed() && !core.awake() && core.progress() == 0, "A new tower is dormant");
        c.assertTrue(core.status() == TransmutatorCoreBlockEntity.STATUS_DORMANT, "The tower asks for a wand");
        var player = c.createMockPlayer(net.minecraft.world.GameMode.CREATIVE);
        ItemStack wand = new ItemStack(ModItems.WANDS.get(EssenceType.ORDO));
        BlockPos crown = pos.up(2);
        var hit = new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(crown), net.minecraft.util.math.Direction.UP, crown, false);
        c.getWorld().getBlockState(crown).onUseWithItem(wand, c.getWorld(), player, net.minecraft.util.Hand.MAIN_HAND, hit);
        c.assertTrue(core.awake() && !core.wake(), "A wand click on the crown awakens the tower once");
        c.assertTrue(c.getWorld().getBlockState(pos.up()).get(net.alkahest.block.custom.TransmutatorAssemblyBlock.AWAKE), "The chamber lights after awakening");
        var replica = new TransmutatorCoreBlockEntity(pos, core.getCachedState());
        replica.readComponentlessNbt(core.createNbt(c.getWorld().getRegistryManager()), c.getWorld().getRegistryManager());
        c.assertTrue(replica.awake() && replica.getStack(TransmutatorCoreBlockEntity.INPUT).getCount() == 2, "Awake state and inventory survive NBT reload");
        c.setBlockState(CX, y + 2, CZ, net.minecraft.block.Blocks.AIR);
        run(c.getWorld(), pos, 1);
        c.assertFalse(core.awake(), "Breaking the crown deactivates the tower");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.INPUT).getCount() == 2, "Breaking a component preserves the core inventory");
        c.setBlockState(CX, y + 2, CZ, ModBlocks.TRANSMUTATOR_CROWN);
        c.assertTrue(core.verticalFormed() && !core.awake(), "Reassembly needs another awakening");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void connectedTowerWaitsForWholePriceAndSpendsExactlyOnce(TestContext c) {
        int y = CY + 48;
        BlockPos pos = tower(c, y);
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        installInput(c, core);
        core.wake();
        c.setBlockState(CX + 1, y, CZ, ModBlocks.ALCHEMY_PIPE);
        BlockPos pipe = c.getAbsolutePos(new BlockPos(CX + 1, y, CZ));
        var metal = jar(c, CX + 1, y, CZ - 1, EssenceType.METALLUM, 60);
        var remoteOrdo = jar(c, CX - 3, y, CZ, EssenceType.ORDO, 20);
        run(c.getWorld(), pos, 150);
        for (int i = 0; i < 200; i++) net.alkahest.block.entity.AlchemyPipeBlockEntity.pulse(c.getWorld(), pipe);
        c.assertTrue(metal.contents().total() == 60 && remoteOrdo.contents().total() == 20, "An absent connected type never drains the other type or a nearby disconnected jar");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isEmpty() && core.getStack(TransmutatorCoreBlockEntity.AMULET).get(ModDataComponents.ESSENCE_CHARGE) == 4,
            "Waiting never spends an item or amulet charge");
        var ordo = jar(c, CX + 1, y, CZ + 1, EssenceType.ORDO, 10);
        net.alkahest.block.entity.AlchemyPipeBlockEntity.pulse(c.getWorld(), pipe);
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isOf(Items.GOLD_INGOT), "Connecting the missing jar resumes the operation");
        c.assertTrue(metal.contents().total() == 58 && ordo.contents().total() == 8 && remoteOrdo.contents().total() == 20,
            "A successful pipe operation spends exactly 2 Metallum and 2 Ordo");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.INPUT).getCount() == 1 && core.getStack(TransmutatorCoreBlockEntity.AMULET).get(ModDataComponents.ESSENCE_CHARGE) == 3,
            "Only one item and one charge are consumed");
        for (int i = 0; i < 20; i++) net.alkahest.block.entity.AlchemyPipeBlockEntity.pulse(c.getWorld(), pipe);
        c.assertTrue(metal.contents().total() == 58 && ordo.contents().total() == 8, "An idle progress cycle does not drain or transfer jars");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sharedNetworkTransfersBellowsToJarsAndFeedsTower(TestContext c) {
        int y = CY + 64;
        BlockPos pos = tower(c, y);
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        installInput(c, core);
        core.wake();
        c.setBlockState(CX + 1, y, CZ, ModBlocks.ALCHEMY_PIPE);
        BlockPos pipe = c.getAbsolutePos(new BlockPos(CX + 1, y, CZ));
        var metal = jar(c, CX + 1, y, CZ - 1, EssenceType.METALLUM, 1);
        var ordo = jar(c, CX + 1, y, CZ + 1, EssenceType.ORDO, 10);
        c.setBlockState(CX + 2, y, CZ, ModBlocks.DISTILLERY_BELLOWS);
        var bellow = (net.alkahest.block.entity.DistilleryBellowsBlockEntity) c.getWorld().getBlockEntity(c.getAbsolutePos(new BlockPos(CX + 2, y, CZ)));
        bellow.accept(EssenceType.METALLUM, 10, .7f);
        run(c.getWorld(), pos, 110);
        net.alkahest.block.entity.AlchemyPipeBlockEntity.pulse(c.getWorld(), pipe);
        c.assertTrue(bellow.amount() == 6 && metal.contents().total() == 3 && ordo.contents().total() == 8,
            "Bellows feed their jar while the tower pays the exact cost in the same network");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isOf(Items.GOLD_INGOT), "Shared distillery and storage network feeds the tower");
        c.assertTrue(metal.contents().contents().size() == 1 && ordo.contents().contents().size() == 1, "Jars remain single-type");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void towerDoesNotDrainWhileBlockedOrDisconnected(TestContext c) {
        int y = CY + 80;
        BlockPos pos = tower(c, y);
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        installInput(c, core);
        core.wake();
        var metal = jar(c, CX + 1, y, CZ - 1, EssenceType.METALLUM, 10);
        var ordo = jar(c, CX + 1, y, CZ + 1, EssenceType.ORDO, 10);
        c.setBlockState(CX + 1, y, CZ, ModBlocks.ALCHEMY_PIPE);
        BlockPos pipe = c.getAbsolutePos(new BlockPos(CX + 1, y, CZ));
        core.setStack(TransmutatorCoreBlockEntity.OUTPUT, new ItemStack(Items.STONE, 64));
        run(c.getWorld(), pos, 200);
        net.alkahest.block.entity.AlchemyPipeBlockEntity.pulse(c.getWorld(), pipe);
        c.assertTrue(metal.contents().total() == 10 && ordo.contents().total() == 10, "A full output never draws essence");
        core.setStack(TransmutatorCoreBlockEntity.OUTPUT, ItemStack.EMPTY);
        run(c.getWorld(), pos, 110);
        c.setBlockState(CX + 1, y, CZ, net.minecraft.block.Blocks.AIR);
        net.alkahest.block.entity.AlchemyPipeBlockEntity.pulse(c.getWorld(), pipe);
        c.assertTrue(metal.contents().total() == 10 && ordo.contents().total() == 10 && core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isEmpty(),
            "A disconnected pipe cannot pay the machine");
        c.setBlockState(CX + 1, y, CZ, ModBlocks.ALCHEMY_PIPE);
        net.alkahest.block.entity.AlchemyPipeBlockEntity.pulse(c.getWorld(), pipe);
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isOf(Items.GOLD_INGOT), "Reconnecting resumes one operation");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void legacyPillarReturnsReservedEssenceWhenBroken(TestContext c) {
        int y = CY + 96;
        BlockPos pos = isolatedPlatform(c, y);
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        installInput(c, core);
        var metal = jar(c, CX + 3, y, CZ, EssenceType.METALLUM, 10);
        jar(c, CX - 3, y, CZ, EssenceType.ORDO, 10);
        run(c.getWorld(), pos, 10);
        var pillar = core.pillars().stream().filter(p -> p.stored(EssenceType.METALLUM) > 0).findFirst().orElseThrow();
        BlockPos pillarPos = pillar.getPos();
        int reserved = pillar.stored(EssenceType.METALLUM);
        c.getWorld().setBlockState(pillarPos, net.minecraft.block.Blocks.AIR.getDefaultState());
        var items = c.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
            new net.minecraft.util.math.Box(pillarPos).expand(2), e -> e.getStack().isOf(ModBlocks.JAR.asItem()));
        int recovered = 0;
        for (var item : items) for (EssenceStack entry : net.alkahest.item.custom.JarItem.contents(item.getStack()).contents())
            if (entry.type() == EssenceType.METALLUM) recovered += entry.amount();
        c.assertTrue(reserved == recovered && metal.contents().total() + recovered == 10, "Upgrading a legacy pillar returns every buffered unit in jars");
        c.assertTrue(pillar.buffer().total() == 0, "Recovered buffer is cleared and cannot duplicate");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void chamberAndCrownHaveWorkingMultiIngredientRecipes(TestContext c) {
        for (int which = 0; which < 2; which++) {
            ItemStack other = which == 0 ? new ItemStack(Items.GLASS) : new ItemStack(Items.AMETHYST_SHARD);
            ItemStack crystal = new ItemStack(ModItems.CRYSTALS.get(which == 0 ? EssenceType.AQUA : EssenceType.ORDO));
            var input = net.minecraft.recipe.input.CraftingRecipeInput.create(2, 2, List.of(
                new ItemStack(ModBlocks.TRANSMUTATOR_PILLAR), other, new ItemStack(Items.GOLD_INGOT), crystal));
            var found = c.getWorld().getServer().getRecipeManager().getFirstMatch(net.minecraft.recipe.RecipeType.CRAFTING, input, c.getWorld());
            c.assertTrue(found.isPresent(), "The tower component crafting recipe is registered");
            ItemStack result = found.get().value().craft(input, c.getWorld().getRegistryManager());
            c.assertTrue(result.isOf((which == 0 ? ModBlocks.TRANSMUTATOR_CHAMBER : ModBlocks.TRANSMUTATOR_CROWN).asItem()),
                "The component recipe makes the correct block");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void pipeCanBePlacedOnTowerCore(TestContext c) {
        int y = CY + 112;
        BlockPos pos = tower(c, y);
        var player = c.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        player.setPosition(pos.getX() + 3, pos.getY(), pos.getZ());
        ItemStack pipes = new ItemStack(ModBlocks.ALCHEMY_PIPE, 4);
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, pipes);
        var hit = new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(pos).add(.5, 0, 0),
            net.minecraft.util.math.Direction.EAST, pos, false);
        c.getWorld().getBlockState(pos).onUseWithItem(pipes, c.getWorld(), player, net.minecraft.util.Hand.MAIN_HAND, hit);
        c.assertTrue(c.getWorld().getBlockState(pos.east()).isOf(ModBlocks.ALCHEMY_PIPE), "A normal pipe click places the segment next to the core");
        c.assertTrue(c.getWorld().getBlockState(pos.east()).get(net.alkahest.block.custom.AlchemyPipeBlock.WEST), "The pipe visually connects to the core");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void animationProgressCheckpointsRoundTrip(TestContext c) {
        BlockPos pos = tower(c, CY + 32);
        var source = (TransmutatorCoreBlockEntity)c.getWorld().getBlockEntity(pos);
        source.wake();
        installInput(c, source);
        var replica = new TransmutatorCoreBlockEntity(pos, source.getCachedState());
        var registry = c.getWorld().getRegistryManager();
        for (int i = 0; i < 45; i++) TransmutatorCoreBlockEntity.tick(c.getWorld(), pos, source);
        replica.readComponentlessNbt(source.toUpdatePacket().getNbt(), registry);
        c.assertTrue(replica.progress() == source.progress() && replica.progress() == 45,
            "Animation receives the actual saved operation progress");
        c.assertTrue(replica.getStack(TransmutatorCoreBlockEntity.AMULET).get(ModDataComponents.ESSENCE_CHARGE) == 4,
            "Visual progress sync preserves the actual installed amulet");
        source.setStack(TransmutatorCoreBlockEntity.INPUT, ItemStack.EMPTY);
        TransmutatorCoreBlockEntity.tick(c.getWorld(), pos, source);
        replica.readComponentlessNbt(source.toUpdatePacket().getNbt(), registry);
        c.assertTrue(replica.progress() == 0 && source.status() == TransmutatorCoreBlockEntity.STATUS_IDLE,
            "Cancellation resets progress rather than leaving the machine at high speed");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void workingAspectColoursFollowRecipeWithoutChangingCosts(TestContext c) {
        for (var recipe : net.alkahest.transmutation.TransmutationRecipe.all()) {
            var aspects = TransmutatorCoreBlockEntity.particleAspects(recipe);
            c.assertTrue(aspects.size() == recipe.cost().size() && aspects.containsAll(recipe.cost().keySet()),
                "Particles use every and only the aspects required by this recipe");
        }
        c.assertTrue(TransmutatorCoreBlockEntity.particleAspects(null).isEmpty(), "No recipe has no aspect particles");
        BlockPos pos = tower(c, CY + 32);
        var core = (TransmutatorCoreBlockEntity)c.getWorld().getBlockEntity(pos);
        core.wake();
        installInput(c, core);
        for (int i = 0; i < 20; i++) TransmutatorCoreBlockEntity.tick(c.getWorld(), pos, core);
        c.assertTrue(core.progress() == 20 && core.status() == TransmutatorCoreBlockEntity.STATUS_WORKING,
            "Visuals do not change normal work progress");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.INPUT).getCount() == 2
            && core.getStack(TransmutatorCoreBlockEntity.AMULET).get(ModDataComponents.ESSENCE_CHARGE) == 4,
            "Visuals do not consume the input or amulet charge");
        core.setStack(TransmutatorCoreBlockEntity.OUTPUT, new ItemStack(Items.STONE));
        TransmutatorCoreBlockEntity.tick(c.getWorld(), pos, core);
        c.assertTrue(core.status() == TransmutatorCoreBlockEntity.STATUS_BLOCKED && core.progress() == 0,
            "Blocked output stops the work that emits aspect particles");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void visibleAmuletUpdatesAndClearsOnClientReplicas(TestContext c) {
        BlockPos pos = tower(c, CY + 32);
        var source = (TransmutatorCoreBlockEntity)c.getWorld().getBlockEntity(pos);
        var replica = new TransmutatorCoreBlockEntity(pos, source.getCachedState());
        var registry = c.getWorld().getRegistryManager();
        ItemStack amulet = new ItemStack(ModItems.ALKAHEST_AMULET);
        amulet.set(ModDataComponents.ESSENCE_CHARGE, 17);
        source.setStack(TransmutatorCoreBlockEntity.AMULET, amulet);
        replica.readComponentlessNbt(source.toUpdatePacket().getNbt(), registry);
        c.assertTrue(replica.getStack(TransmutatorCoreBlockEntity.AMULET).isOf(ModItems.ALKAHEST_AMULET)
            && replica.getStack(TransmutatorCoreBlockEntity.AMULET).get(ModDataComponents.ESSENCE_CHARGE) == 17,
            "The rendered item and its actual charge reach the client");
        source.removeStack(TransmutatorCoreBlockEntity.AMULET);
        replica.readComponentlessNbt(source.toUpdatePacket().getNbt(), registry);
        c.assertTrue(replica.getStack(TransmutatorCoreBlockEntity.AMULET).isEmpty(), "Taking the amulet clears the old client image");
        ItemStack replacement = new ItemStack(ModItems.ALKAHEST_AMULET);
        replacement.set(ModDataComponents.ESSENCE_CHARGE, 0);
        source.setStack(TransmutatorCoreBlockEntity.AMULET, replacement);
        replica.readComponentlessNbt(source.toUpdatePacket().getNbt(), registry);
        c.assertTrue(replica.getStack(TransmutatorCoreBlockEntity.AMULET).get(ModDataComponents.ESSENCE_CHARGE) == 0,
            "A depleted amulet is still rendered and shows zero charge");
        source.clear();
        replica.readComponentlessNbt(source.toUpdatePacket().getNbt(), registry);
        c.assertTrue(replica.getStack(TransmutatorCoreBlockEntity.AMULET).isEmpty(), "Clearing inventory also removes the client image");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void redesignedGuiSlotsRemainUsable(TestContext c) {
        BlockPos pos = tower(c, CY + 32);
        var core = (TransmutatorCoreBlockEntity)c.getWorld().getBlockEntity(pos);
        var player = c.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        var h = new net.alkahest.screen.TransmutatorScreenHandler(1, player.getInventory(), core,
            new net.minecraft.screen.ArrayPropertyDelegate(4));
        c.assertTrue(h.getSlot(0).x == 26 && h.getSlot(0).y == 45 && h.getSlot(1).x == 26 && h.getSlot(1).y == 78,
            "Input and amulet hitboxes align with their new frames");
        c.assertTrue(h.getSlot(2).x == 102 && h.getSlot(2).y == 45 && h.getSlot(3).x == 37 && h.getSlot(3).y == 143 && h.getSlot(30).y == 201,
            "Output, inventory and hotbar hitboxes align with their new frames");
        c.assertFalse(h.getSlot(2).canInsert(new ItemStack(Items.STONE)), "Output still rejects insertion");
        player.getInventory().setStack(9, new ItemStack(Items.COPPER_INGOT, 3));
        c.assertFalse(h.quickMove(player, 3).isEmpty(), "Shift-click still inserts a valid input");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.INPUT).getCount() == 3, "The input stack reaches the core");
        player.getInventory().setStack(10, new ItemStack(ModItems.ALKAHEST_AMULET));
        c.assertFalse(h.quickMove(player, 4).isEmpty(), "Shift-click still inserts the amulet");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.AMULET).isOf(ModItems.ALKAHEST_AMULET), "The amulet reaches its dedicated slot");
        c.assertFalse(h.quickMove(player, 0).isEmpty(), "Input can still be returned to inventory");
        c.complete();
    }

    /** One core alone is not a platform; the eight pillars make it one. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void platformNeedsAllEightPillars(TestContext c) {
        c.setBlockState(CX, CY, CZ, ModBlocks.TRANSMUTATOR_CORE);
        BlockPos pos = c.getAbsolutePos(new BlockPos(CX, CY, CZ));
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        c.assertFalse(core.formed(), "A lone core is not assembled");
        c.setBlockState(CX + 1, CY, CZ, ModBlocks.TRANSMUTATOR_PILLAR);
        c.setBlockState(CX - 1, CY, CZ, ModBlocks.TRANSMUTATOR_PILLAR);
        c.setBlockState(CX, CY, CZ + 1, ModBlocks.TRANSMUTATOR_PILLAR);
        c.assertFalse(core.formed(), "Four pillars are still not enough");
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                c.setBlockState(CX + dx, CY, CZ + dz, ModBlocks.TRANSMUTATOR_PILLAR);
            }
        }
        c.assertTrue(core.formed(), "The full ring of eight pillars assembles the platform");
        c.complete();
    }

    /** Copper becomes gold while the essence flies out of the jars and the amulet pays a charge. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void transmutesCopperIntoGoldUsingJarEssence(TestContext c) {
        BlockPos pos = build(c);
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        installInput(c, core);
        JarBlockEntity metallum = jar(c, CX + 3, CY, CZ, EssenceType.METALLUM, 10);
        JarBlockEntity ordo = jar(c, CX - 3, CY, CZ, EssenceType.ORDO, 10);
        run(c.getWorld(), pos, TransmutatorCoreBlockEntity.DURATION + 4);

        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.OUTPUT).getItem() == Items.GOLD_INGOT,
            "Copper turns into gold");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.INPUT).getCount() == 1,
            "Only one input is spent");
        c.assertTrue(metallum.contents().total() == 8, "Two metallum were taken from the jar");
        c.assertTrue(ordo.contents().total() == 8, "Two ordo were taken from the jar");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.AMULET).get(ModDataComponents.ESSENCE_CHARGE) == 3,
            "The amulet pays one charge for the operation");
        c.complete();
    }

    /** Essence is drawn through the air into the pillars, not through pipes. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void pillarsPullEssenceStraightFromJars(TestContext c) {
        BlockPos pos = build(c);
        JarBlockEntity jar = jar(c, CX, CY + 2, CZ + 4, EssenceType.HERBA, 12);
        var pillar = (TransmutatorPillarBlockEntity) c.getWorld().getBlockEntity(
            c.getAbsolutePos(new BlockPos(CX, CY, CZ + 1)));
        c.assertTrue(pillar.request(c.getWorld(), EssenceType.HERBA, 6), "The pillar reaches a jar four blocks away");
        c.assertTrue(pillar.stored(EssenceType.HERBA) == 6, "The units arrive in the pillar buffer");
        c.assertTrue(jar.contents().total() == 6, "The jar hands over exactly what was asked");
        BlockPos pillarPos = c.getAbsolutePos(new BlockPos(CX, CY, CZ + 1));
        c.assertTrue(c.getWorld().getBlockState(pillarPos).get(TransmutatorPillarBlock.CHARGED),
            "A loaded pillar lights up");
        c.complete();
    }

    /** No essence around means no transmutation, and the input is never eaten. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void waitsWithoutEssenceAndKeepsTheInput(TestContext c) {
        BlockPos pos = build(c);
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        installInput(c, core);
        run(c.getWorld(), pos, TransmutatorCoreBlockEntity.DURATION * 2);
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isEmpty(), "Nothing is produced without essence");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.INPUT).getCount() == 2, "The input is untouched");
        c.assertTrue(core.status() == TransmutatorCoreBlockEntity.STATUS_NO_ESSENCE, "The core reports the missing essence");
        c.complete();
    }

    /** A short jar leaves everything where it was: essence is only spent when the price is whole. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void shortEssenceChangesNothing(TestContext c) {
        BlockPos pos = build(c);
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        installInput(c, core);
        JarBlockEntity metallum = jar(c, CX + 3, CY, CZ, EssenceType.METALLUM, 1);
        run(c.getWorld(), pos, TransmutatorCoreBlockEntity.DURATION + 4);
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isEmpty(), "An incomplete price produces nothing");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.INPUT).getCount() == 2, "The input stays");
        c.assertTrue(metallum.contents().total() == 1, "The essence stays in the jar");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void missingLaterEssenceNeverConsumesEarlierType(TestContext c) {
        int y = CY + 32;
        BlockPos pos = isolatedPlatform(c, y);
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        installInput(c, core);
        var metallum = jar(c, CX + 3, y, CZ, EssenceType.METALLUM, 60);
        run(c.getWorld(), pos, TransmutatorCoreBlockEntity.DURATION + 200);
        c.assertTrue(metallum.contents().total() + stored(core, EssenceType.METALLUM) == 60,
            "Missing Ordo cannot repeatedly destroy Metallum while waiting");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.INPUT).getCount() == 2 && core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isEmpty(),
            "An incomplete price never consumes the input or creates output");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.AMULET).get(ModDataComponents.ESSENCE_CHARGE) == 4,
            "Waiting never spends an amulet charge");
        var ordo = jar(c, CX - 3, y, CZ, EssenceType.ORDO, 10);
        run(c.getWorld(), pos, 1);
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isOf(Items.GOLD_INGOT), "Providing the missing type completes one operation");
        c.assertTrue(metallum.contents().total() + stored(core, EssenceType.METALLUM) == 58 &&
            ordo.contents().total() + stored(core, EssenceType.ORDO) == 8, "Successful operation spends exactly 2 Metallum and 2 Ordo");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.AMULET).get(ModDataComponents.ESSENCE_CHARGE) == 3, "Exactly one charge is spent");
        c.setBlockState(CX + 3, y, CZ, net.minecraft.block.Blocks.AIR);
        c.setBlockState(CX - 3, y, CZ, net.minecraft.block.Blocks.AIR);
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void disappearingEssenceKeepsReservationsUntilPayment(TestContext c) {
        int y = CY + 48;
        BlockPos pos = isolatedPlatform(c, y);
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        installInput(c, core);
        var metallum = jar(c, CX + 3, y, CZ, EssenceType.METALLUM, 20);
        var ordo = jar(c, CX - 3, y, CZ, EssenceType.ORDO, 10);
        run(c.getWorld(), pos, 10);
        int reservedOrdo = stored(core, EssenceType.ORDO);
        c.assertTrue(reservedOrdo == 1 && stored(core, EssenceType.METALLUM) == 1, "Both types are partially reserved");
        int savedOrdo = ordo.contents().total();
        ordo.setContents(JarContentsComponent.EMPTY);
        run(c.getWorld(), pos, 400);
        c.assertTrue(metallum.contents().total() + stored(core, EssenceType.METALLUM) == 20 && stored(core, EssenceType.ORDO) == reservedOrdo,
            "A missing jar never spends or drains earlier reservations");
        ordo.setContents(of(EssenceType.ORDO, savedOrdo));
        run(c.getWorld(), pos, 1);
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isOf(Items.GOLD_INGOT), "Returning the jar resumes the waiting operation");
        c.assertTrue(metallum.contents().total() + stored(core, EssenceType.METALLUM) == 18 &&
            ordo.contents().total() + stored(core, EssenceType.ORDO) == 8, "Only the successful price was spent");
        c.setBlockState(CX + 3, y, CZ, net.minecraft.block.Blocks.AIR);
        c.setBlockState(CX - 3, y, CZ, net.minecraft.block.Blocks.AIR);
        c.complete();
    }

    /** Without a charged amulet the core does not run. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void needsAChargedAmulet(TestContext c) {
        BlockPos pos = build(c);
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        core.setStack(TransmutatorCoreBlockEntity.INPUT, new ItemStack(Items.COPPER_INGOT));
        jar(c, CX + 3, CY, CZ, EssenceType.METALLUM, 10);
        jar(c, CX - 3, CY, CZ, EssenceType.ORDO, 10);
        c.assertTrue(core.status() == TransmutatorCoreBlockEntity.STATUS_NO_AMULET, "The core asks for an amulet");
        run(c.getWorld(), pos, TransmutatorCoreBlockEntity.DURATION + 4);
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isEmpty(), "No amulet, no transmutation");
        c.complete();
    }

    /** A full output slot stops the machine instead of destroying the product. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fullOutputBlocksTheMachine(TestContext c) {
        BlockPos pos = build(c);
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        installInput(c, core);
        core.setStack(TransmutatorCoreBlockEntity.OUTPUT, new ItemStack(Items.STONE, 64));
        jar(c, CX + 3, CY, CZ, EssenceType.METALLUM, 10);
        jar(c, CX - 3, CY, CZ, EssenceType.ORDO, 10);
        run(c.getWorld(), pos, TransmutatorCoreBlockEntity.DURATION + 4);
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.OUTPUT).getItem() == Items.STONE, "The output keeps its item");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.OUTPUT).getCount() == 64, "Nothing is added to a full slot");
        c.assertTrue(core.status() == TransmutatorCoreBlockEntity.STATUS_BLOCKED, "The core reports a blocked output");
        c.complete();
    }

    /** Breaking the platform must not swallow what was inside the core. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void coreDropsItsContentsWhenBroken(TestContext c) {
        BlockPos pos = build(c);
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        installInput(c, core);
        core.dropContents(c.getWorld(), pos);
        c.assertTrue(core.isEmpty(), "The core is emptied when it is taken apart");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void newResourceConversionSpendsExactConnectedEssence(TestContext c) {
        int y = CY + 160;
        BlockPos pos = tower(c, y);
        var core = (TransmutatorCoreBlockEntity)c.getWorld().getBlockEntity(pos);
        installInput(c, core);
        core.setStack(TransmutatorCoreBlockEntity.INPUT, new ItemStack(Items.REDSTONE, 2));
        core.wake();
        c.setBlockState(CX + 1, y, CZ, ModBlocks.ALCHEMY_PIPE);
        BlockPos pipe = c.getAbsolutePos(new BlockPos(CX + 1, y, CZ));
        var lux = jar(c, CX + 1, y, CZ - 1, EssenceType.LUX, 10);
        run(c.getWorld(), pos, 110);
        net.alkahest.block.entity.AlchemyPipeBlockEntity.pulse(c.getWorld(), pipe);
        c.assertTrue(lux.contents().total() == 10 && core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isEmpty(), "Missing Potentia prevents payment");
        var potentia = jar(c, CX + 1, y, CZ + 1, EssenceType.POTENTIA, 10);
        net.alkahest.block.entity.AlchemyPipeBlockEntity.pulse(c.getWorld(), pipe);
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isOf(Items.GLOWSTONE_DUST), "One redstone makes one glowstone dust");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.OUTPUT).getCount() == 1 && core.getStack(TransmutatorCoreBlockEntity.INPUT).getCount() == 1, "One input and one output per operation");
        c.assertTrue(lux.contents().total() == 8 && potentia.contents().total() == 8, "Exactly Lux 2 and Potentia 2 are spent");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.AMULET).get(ModDataComponents.ESSENCE_CHARGE) == 3, "Exactly one amulet charge is spent");
        c.setBlockState(CX + 1, y, CZ - 1, net.minecraft.block.Blocks.AIR);
        c.setBlockState(CX + 1, y, CZ + 1, net.minecraft.block.Blocks.AIR);
        c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void filteredTowerNeverPartiallyPaysAndResumes(TestContext c) {
        int y = CY + 48;
        BlockPos pos = tower(c, y);
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        installInput(c, core); core.wake();
        c.setBlockState(CX + 1, y, CZ, ModBlocks.ALCHEMY_PIPE);
        BlockPos pipe = c.getAbsolutePos(new BlockPos(CX + 1, y, CZ));
        var segment = (net.alkahest.block.entity.AlchemyPipeBlockEntity)c.getWorld().getBlockEntity(pipe);
        var metal = jar(c, CX + 1, y, CZ - 1, EssenceType.METALLUM, 10);
        var ordo = jar(c, CX + 2, y, CZ, EssenceType.ORDO, 10);
        segment.setFilter(EssenceType.METALLUM);
        run(c.getWorld(), pos, 110);
        c.assertTrue(net.alkahest.block.entity.AlchemyPipeBlockEntity.pulse(c.getWorld(), pipe)==0,
            "A blocked part of the price prevents the whole payment");
        c.assertTrue(metal.contents().total()==10 && ordo.contents().total()==10
            && core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isEmpty(), "No partial payment or output");
        segment.setFilter(null);
        net.alkahest.block.entity.AlchemyPipeBlockEntity.pulse(c.getWorld(), pipe);
        c.assertTrue(metal.contents().total()==8 && ordo.contents().total()==8,
            "Removing the filter permits exactly one complete payment");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isOf(Items.GOLD_INGOT), "Operation completes");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void towerCombinesSeveralReachableJarsAndPreservesRemainderStability(TestContext c) {
        int y = CY + 48;
        BlockPos pos = tower(c, y);
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        installInput(c, core); core.wake();
        c.setBlockState(CX + 1, y, CZ, ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(CX + 2, y, CZ, ModBlocks.ALCHEMY_PIPE);
        BlockPos pipe = c.getAbsolutePos(new BlockPos(CX + 1, y, CZ));
        var first = jar(c, CX + 1, y, CZ - 1, EssenceType.METALLUM, 1);
        var second = jar(c, CX + 2, y, CZ - 1, EssenceType.METALLUM, 10);
        second.setContents(new JarContentsComponent(List.of(new EssenceStack(EssenceType.METALLUM,10,.72f))));
        var ordo = jar(c, CX + 1, y, CZ + 1, EssenceType.ORDO, 10);
        run(c.getWorld(), pos, 110);
        net.alkahest.block.entity.AlchemyPipeBlockEntity.pulse(c.getWorld(), pipe);
        c.assertTrue(first.contents().total()==0 && second.contents().total()==9 && ordo.contents().total()==8,
            "The exact price is combined from multiple jars");
        c.assertTrue(Math.abs(second.contents().contents().getFirst().stability()-.72f)<.001f,
            "The remaining essence retains its stability");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isOf(Items.GOLD_INGOT),"Operation completes");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void inaccessibleJarIsExcludedFromWholePriceUntilRouteOpens(TestContext c) {
        int y = CY + 48;
        BlockPos pos = tower(c, y);
        var core = (TransmutatorCoreBlockEntity) c.getWorld().getBlockEntity(pos);
        installInput(c, core); core.wake();
        c.setBlockState(CX + 1, y, CZ, ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(CX + 2, y, CZ, ModBlocks.ALCHEMY_PIPE);
        BlockPos pipe = c.getAbsolutePos(new BlockPos(CX + 1, y, CZ));
        var filter = (net.alkahest.block.entity.AlchemyPipeBlockEntity)c.getWorld().getBlockEntity(pipe.east());
        var metal = jar(c, CX + 1, y, CZ - 1, EssenceType.METALLUM, 10);
        var nearOrdo = jar(c, CX + 1, y, CZ + 1, EssenceType.ORDO, 1);
        var blockedOrdo = jar(c, CX + 3, y, CZ, EssenceType.ORDO, 10);
        filter.setFilter(EssenceType.IGNIS);
        run(c.getWorld(), pos, 110);
        c.assertTrue(net.alkahest.block.entity.AlchemyPipeBlockEntity.pulse(c.getWorld(),pipe)==0,
            "The physically connected but filtered jar cannot satisfy the missing Ordo");
        c.assertTrue(metal.contents().total()==10 && nearOrdo.contents().total()==1 && blockedOrdo.contents().total()==10,
            "Neither accessible nor blocked jars are partly drained");
        filter.setFilter(null);
        net.alkahest.block.entity.AlchemyPipeBlockEntity.pulse(c.getWorld(),pipe);
        c.assertTrue(metal.contents().total()==8 && nearOrdo.contents().total()+blockedOrdo.contents().total()==9,
            "Opening the route pays exactly the missing price");
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isOf(Items.GOLD_INGOT),"The operation resumes");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void closedValveBlocksWholePaymentUntilPowered(TestContext c) {
        int y = CY + 48;
        BlockPos pos = tower(c, y);
        var core = (TransmutatorCoreBlockEntity)c.getWorld().getBlockEntity(pos);
        installInput(c, core); core.wake();
        c.setBlockState(CX + 1, y, CZ, ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(CX + 2, y, CZ, ModBlocks.ALCHEMY_PIPE);
        BlockPos first = c.getAbsolutePos(new BlockPos(CX + 1, y, CZ));
        var valve = (net.alkahest.block.entity.AlchemyPipeBlockEntity)c.getWorld().getBlockEntity(first.east());
        valve.setValveMode(net.alkahest.block.entity.PipeValveMode.OPEN_WHEN_POWERED);
        var metal = jar(c, CX + 1, y, CZ - 1, EssenceType.METALLUM, 10);
        var ordo = jar(c, CX + 3, y, CZ, EssenceType.ORDO, 10);
        run(c.getWorld(), pos, 110);
        for (int i=0; i<100; i++) net.alkahest.block.entity.AlchemyPipeBlockEntity.pulse(c.getWorld(),first);
        c.assertTrue(metal.contents().total()==10 && ordo.contents().total()==10
            && core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isEmpty()
            && core.getStack(TransmutatorCoreBlockEntity.AMULET).get(ModDataComponents.ESSENCE_CHARGE)==4,
            "Closed valve never consumes a partial price, an item or charge");
        c.setBlockState(CX + 2, y - 1, CZ, net.minecraft.block.Blocks.REDSTONE_BLOCK);
        net.alkahest.block.entity.AlchemyPipeBlockEntity.pulse(c.getWorld(),first);
        c.assertTrue(metal.contents().total()==8 && ordo.contents().total()==8
            && core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isOf(Items.GOLD_INGOT)
            && core.getStack(TransmutatorCoreBlockEntity.AMULET).get(ModDataComponents.ESSENCE_CHARGE)==3,
            "Opening the valve commits the exact complete price once");
        c.setBlockState(CX + 2, y - 1, CZ, net.minecraft.block.Blocks.AIR);
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void directionalPipeBlocksWholePriceUntilTurnedTowardMachine(TestContext c) {
        int y=CY+48;
        BlockPos pos=tower(c,y);
        var core=(TransmutatorCoreBlockEntity)c.getWorld().getBlockEntity(pos);
        installInput(c,core); core.wake();
        c.setBlockState(CX+1,y,CZ,ModBlocks.ALCHEMY_PIPE);
        c.setBlockState(CX+2,y,CZ,ModBlocks.ALCHEMY_PIPE);
        BlockPos first=c.getAbsolutePos(new BlockPos(CX+1,y,CZ));
        var branch=(net.alkahest.block.entity.AlchemyPipeBlockEntity)c.getWorld().getBlockEntity(first.east());
        branch.setDirection(net.alkahest.block.entity.PipeDirection.EAST);
        var metal=jar(c,CX+1,y,CZ-1,EssenceType.METALLUM,10);
        var ordo=jar(c,CX+3,y,CZ,EssenceType.ORDO,10);
        run(c.getWorld(),pos,110);
        for(int i=0;i<100;i++) net.alkahest.block.entity.AlchemyPipeBlockEntity.pulse(c.getWorld(),first);
        c.assertTrue(metal.contents().total()==10 && ordo.contents().total()==10
            && core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isEmpty()
            && core.getStack(TransmutatorCoreBlockEntity.AMULET).get(ModDataComponents.ESSENCE_CHARGE)==4,
            "A reversed branch cannot pay part of a recipe or spend an item or charge");
        branch.setDirection(net.alkahest.block.entity.PipeDirection.WEST);
        net.alkahest.block.entity.AlchemyPipeBlockEntity.pulse(c.getWorld(),first);
        c.assertTrue(metal.contents().total()==8 && ordo.contents().total()==8
            && core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isOf(Items.GOLD_INGOT)
            && core.getStack(TransmutatorCoreBlockEntity.AMULET).get(ModDataComponents.ESSENCE_CHARGE)==3,
            "Turning the branch toward the core pays the entire price exactly once");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void completedTransmutationPaysBeforeOptInJarFeed(TestContext c) {
        int y=CY+48;
        BlockPos pos=tower(c,y);
        var core=(TransmutatorCoreBlockEntity)c.getWorld().getBlockEntity(pos);
        installInput(c,core); core.wake();
        c.setBlockState(CX+1,y,CZ,ModBlocks.ALCHEMY_PIPE);
        var pipe=c.getAbsolutePos(new BlockPos(CX+1,y,CZ));
        var metal=jar(c,CX+1,y,CZ-1,EssenceType.METALLUM,10);
        var ordo=jar(c,CX+2,y,CZ,EssenceType.ORDO,10);
        metal.setPipeSource(true); ordo.setPipeSource(true);
        c.setBlockState(CX+1,y,CZ+1,ModBlocks.STABILIZER_RECEIVER);
        var receiver=(net.alkahest.block.entity.StabilizerReceiverBlockEntity)c.getWorld().getBlockEntity(pipe.south());
        run(c.getWorld(),pos,110);
        net.alkahest.block.entity.AlchemyPipeBlockEntity.pulse(c.getWorld(),pipe);
        c.assertTrue(core.getStack(TransmutatorCoreBlockEntity.OUTPUT).isOf(Items.GOLD_INGOT)
            && core.getStack(TransmutatorCoreBlockEntity.AMULET).get(ModDataComponents.ESSENCE_CHARGE)==3,
            "A fully payable finished operation gets priority over optional feeding");
        c.assertTrue(metal.contents().total()==4 && ordo.contents().total()==4 && receiver.contents().total()==8,
            "After the exact 2+2 payment each source sends four units, conserving the remainder");
        c.complete();
    }

}
