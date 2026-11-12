package net.alkahest.test;

import net.alkahest.assembly.AssemblyBlueprint;
import net.alkahest.registry.ModBlocks;
import net.alkahest.registry.ModItems;
import net.alkahest.essence.EssenceType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

public class AssemblyBlueprintGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void guidesTowersAndDistilleryWithoutChangingBlocks(TestContext c) {
        var world = c.getWorld();
        c.setBlockState(3, 3, 3, ModBlocks.STABILIZER_CORE);
        BlockPos pos = c.getAbsolutePos(new BlockPos(3, 3, 3));
        var inspection = AssemblyBlueprint.inspect(world, pos);
        c.assertTrue(inspection.kind() == AssemblyBlueprint.Kind.STABILIZER && inspection.missing().size() == 2, "Receiver and crest are both missing");
        c.setBlockState(3, 2, 3, ModBlocks.STABILIZER_RECEIVER);
        c.setBlockState(3, 4, 3, Blocks.STONE);
        inspection = AssemblyBlueprint.inspect(world, pos);
        c.assertTrue(inspection.missing().size() == 1 && inspection.missing().getFirst().expected() == ModBlocks.STABILIZER_CREST
            && inspection.missing().getFirst().obstructed(), "Wrong blocks are reported as obstacles, not removed");
        c.assertTrue(world.getBlockState(pos.up()).isOf(Blocks.STONE), "Inspection never changes the world");
        c.setBlockState(3, 4, 3, ModBlocks.STABILIZER_CREST);
        c.assertTrue(AssemblyBlueprint.inspect(world, pos).missing().isEmpty(), "Correct tower needs nothing");
        c.setBlockState(3, 3, 3, ModBlocks.DISTILLERY_TIER2);
        c.setBlockState(3, 4, 3, Blocks.AIR);
        inspection = AssemblyBlueprint.inspect(world, pos);
        c.assertTrue(inspection.kind() == AssemblyBlueprint.Kind.TIER2 && inspection.missing().size() == 1
            && inspection.missing().getFirst().expected() == ModBlocks.DISTILLERY_BELLOWS, "Cube needs a bellows directly above");
        c.setBlockState(3, 4, 3, ModBlocks.DISTILLERY_BELLOWS);
        c.assertTrue(AssemblyBlueprint.inspect(world, pos).missing().isEmpty(), "One bellows is enough");
        c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void matrixShellMatchesRealAssemblyValidation(TestContext c) {
        c.setBlockState(3, 3, 3, ModBlocks.MATRIX_CORE);
        var world = c.getWorld(); var pos = c.getAbsolutePos(new BlockPos(3, 3, 3));
        var core = (net.alkahest.block.entity.MatrixCoreBlockEntity) world.getBlockEntity(pos);
        var initial = AssemblyBlueprint.inspect(world, pos);
        c.assertTrue(initial.missing().size() == 26 && !core.formed(), "Empty shell has eight pillars and eighteen bricks");
        c.assertTrue(initial.missing().stream().filter(p -> p.expected() == ModBlocks.MATRIX_PILLAR).count() == 8, "Eight corners are pillars");
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dy == 0 && dz == 0) continue;
            c.setBlockState(3 + dx, 3 + dy, 3 + dz, dx != 0 && dy != 0 && dz != 0 ? ModBlocks.MATRIX_PILLAR : ModBlocks.ALCHEMICAL_BRICK);
        }
        c.assertTrue(core.formed() && AssemblyBlueprint.inspect(world, pos).missing().isEmpty(), "Template and actual shell agree");
        c.assertTrue(pos.equals(AssemblyBlueprint.targetCore(world, pos.add(1, 0, 0))), "A shell click reaches the enclosed core");
        c.setBlockState(4, 4, 4, Blocks.DIRT);
        var part = AssemblyBlueprint.inspect(world, pos).missing().getFirst();
        c.assertTrue(!core.formed() && part.obstructed() && part.expected() == ModBlocks.MATRIX_PILLAR, "Replacing a corner is detected");
        c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void transmutatorSupportsBothModernAndExistingLegacyLayouts(TestContext c) {
        c.setBlockState(3, 3, 3, ModBlocks.TRANSMUTATOR_CORE);
        var world = c.getWorld(); var pos = c.getAbsolutePos(new BlockPos(3, 3, 3));
        var core = (net.alkahest.block.entity.TransmutatorCoreBlockEntity)world.getBlockEntity(pos);
        c.assertTrue(AssemblyBlueprint.inspect(world, pos).missing().size() == 2 && !core.formed(), "Modern tower needs chamber and crown");
        c.setBlockState(3, 4, 3, ModBlocks.TRANSMUTATOR_CHAMBER); c.setBlockState(3, 5, 3, ModBlocks.TRANSMUTATOR_CROWN);
        c.assertTrue(core.verticalFormed() && AssemblyBlueprint.inspect(world, pos).missing().isEmpty(), "Modern tower is valid");
        c.setBlockState(3, 4, 3, Blocks.AIR); c.setBlockState(3, 5, 3, Blocks.AIR);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (dx != 0 || dz != 0)
            c.setBlockState(3 + dx, 3, 3 + dz, ModBlocks.TRANSMUTATOR_PILLAR);
        c.assertTrue(core.legacyFormed() && AssemblyBlueprint.inspect(world, pos).missing().isEmpty(), "Old complete platform stays recognised");
        c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void previewInteractionAndPacketDoNotConsumeWand(TestContext c) {
        c.setBlockState(3, 3, 3, ModBlocks.DISTILLERY_TIER2);
        var pos = c.getAbsolutePos(new BlockPos(3, 3, 3));
        var player = c.createMockCreativeServerPlayerInWorld(); player.setSneaking(true);
        var wand = new ItemStack(ModItems.WANDS.get(EssenceType.AER));
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, wand);
        int before = wand.getOrDefault(net.alkahest.registry.ModDataComponents.ESSENCE_CHARGE, 0);
        var hit = new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(pos), net.minecraft.util.math.Direction.UP, pos, false);
        var result = net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker().interact(player, c.getWorld(), net.minecraft.util.Hand.MAIN_HAND, hit);
        c.assertTrue(result.isAccepted() && player.getStackInHand(net.minecraft.util.Hand.MAIN_HAND).getOrDefault(net.alkahest.registry.ModDataComponents.ESSENCE_CHARGE, 0) == before, "Sneaking preview takes precedence over the GUI and costs no charge");
        var buf = new net.minecraft.network.RegistryByteBuf(io.netty.buffer.Unpooled.buffer(), c.getWorld().getRegistryManager());
        try {
            net.alkahest.network.AssemblyPreviewPayload.CODEC.encode(buf, new net.alkahest.network.AssemblyPreviewPayload(pos.asLong()));
            c.assertTrue(net.alkahest.network.AssemblyPreviewPayload.CODEC.decode(buf).pos().equals(pos), "Preview payload preserves the core position");
        } finally { buf.release(); }
        c.complete();
    }
}
