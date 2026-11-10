package net.alkahest.client.gui;

import dev.emi.trinkets.api.TrinketsApi;
import net.alkahest.block.custom.AlchemyPipeBlock;
import net.alkahest.block.custom.MatrixCoreBlock;
import net.alkahest.block.custom.MatrixPillarBlock;
import net.alkahest.block.custom.AlchemicalBrickBlock;
import net.alkahest.block.entity.MatrixCoreBlockEntity;
import net.alkahest.matrix.MatrixRecipe;
import net.alkahest.block.entity.AlchemyPipeBlockEntity;
import net.alkahest.block.entity.JarBlockEntity;
import net.alkahest.block.entity.CauldronBlockEntity;
import net.alkahest.block.entity.DistilleryBellowsBlockEntity;
import net.alkahest.block.entity.Tier2DistilleryBlockEntity;
import net.alkahest.block.entity.StabilizerReceiverBlockEntity;
import net.alkahest.essence.EssenceStack;
import net.alkahest.essence.EssenceType;
import net.alkahest.item.custom.JarItem;
import net.alkahest.registry.ModItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.ArrayList;
import java.util.List;

/** Quiet context panel for block entities already synchronised from the server. */
public final class MonocleHud {
    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden || client.currentScreen != null ||
            !TrinketsApi.getTrinketComponent(client.player).map(c -> c.isEquipped(ModItems.ALCHEMISTS_MONOCLE)).orElse(false) ||
            !(client.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        var blockEntity = client.world.getBlockEntity(hit.getBlockPos());
        List<Text> lines = new ArrayList<>();
        var targetedBlock = client.world.getBlockState(hit.getBlockPos()).getBlock();
        if (targetedBlock instanceof MatrixCoreBlock || targetedBlock instanceof MatrixPillarBlock
            || targetedBlock instanceof AlchemicalBrickBlock) {
            MatrixCoreBlockEntity matrix = blockEntity instanceof MatrixCoreBlockEntity direct ? direct
                : MatrixCoreBlockEntity.coreNearby(client.world, hit.getBlockPos());
            if (matrix == null) return;
            lines.add(Text.translatable("screen.alkahest.monocle.matrix_core"));
            lines.add(matrix.statusText());
            MatrixRecipe expected = matrix.recipe();
            if (expected == null && !matrix.started())
                expected = MatrixRecipe.uniqueForBase(matrix.getStack(MatrixCoreBlockEntity.BASE));
            if (expected == null && MatrixRecipe.hasMultipleForBase(matrix.getStack(MatrixCoreBlockEntity.BASE)))
                lines.add(Text.translatable("screen.alkahest.monocle.matrix_choose_recipe"));
            if (expected != null) {
                for (var entry : expected.cost().entrySet()) {
                    int stored = Math.min(matrix.stored(entry.getKey()), entry.getValue());
                    int remaining = entry.getValue() - stored;
                    lines.add(Text.translatable("screen.alkahest.monocle.matrix_essence",
                        entry.getKey().displayName(), stored, entry.getValue(), remaining)
                        .styled(style -> style.withColor(entry.getKey().color())));
                }
            }
        } else if (blockEntity instanceof JarBlockEntity jar) {
            var contents = jar.contents();
            lines.add(Text.translatable("screen.alkahest.monocle.jar", contents.total(), JarItem.CAPACITY));
            if (contents.total() == 0) lines.add(Text.translatable("screen.alkahest.monocle.empty"));
            else {
                int limit = Math.min(contents.contents().size(), 4);
                for (int i=0; i<limit; i++) {
                    var essence = contents.contents().get(i);
                    lines.add(Text.literal("◆ " + essence.type().displayName() + ": " + essence.amount() + " · " + Math.round(essence.stability()*100) + "%")
                        .styled(style -> style.withColor(essence.type().color())));
                }
                if (contents.contents().size() > limit) lines.add(Text.translatable("screen.alkahest.monocle.more", contents.contents().size() - limit));
            }
        } else if (blockEntity instanceof CauldronBlockEntity cauldron) {
            var contents = cauldron.essences();
            lines.add(Text.translatable("screen.alkahest.monocle.cauldron"));
            if (!cauldron.hasWater()) lines.add(Text.translatable("screen.alkahest.monocle.cauldron_dry"));
            else if (!cauldron.isHeated()) lines.add(Text.translatable("screen.alkahest.monocle.cauldron_cold"));
            if (cauldron.isMossStabilized()) {
                lines.add(Text.translatable("screen.alkahest.monocle.cauldron_stabilized")
                    .styled(style -> style.withColor(0xC084FC)));
            }
            if (contents.isEmpty()) lines.add(Text.translatable("screen.alkahest.monocle.empty"));
            else {
                int shown = 0;
                for (EssenceType type : EssenceType.values()) {
                    int amount = contents.getOrDefault(type, 0);
                    if (amount > 0 && shown++ < 6) lines.add(Text.literal("◆ " + type.displayName() + ": " + amount)
                        .styled(style -> style.withColor(type.color())));
                }
                if (shown > 6) lines.add(Text.translatable("screen.alkahest.monocle.more", shown - 6));
            }
        } else if (blockEntity instanceof DistilleryBellowsBlockEntity bellows) {
            lines.add(Text.translatable("screen.alkahest.monocle.bellows"));
            if (bellows.empty()) lines.add(Text.translatable("screen.alkahest.monocle.empty"));
            else lines.add(Text.literal("◆ " + bellows.storedType().displayName() + ": " + bellows.amount()
                + " / " + DistilleryBellowsBlockEntity.CAPACITY + " · " + Math.round(bellows.stability() * 100) + "%")
                .styled(style -> style.withColor(bellows.storedType().color())));
        } else if (blockEntity instanceof Tier2DistilleryBlockEntity distillery) {
            lines.add(Text.translatable("screen.alkahest.monocle.distillery_tier2"));
            lines.add(Text.translatable(switch (distillery.status()) {
                case Tier2DistilleryBlockEntity.STATUS_WORKING -> "screen.alkahest.monocle.working";
                case Tier2DistilleryBlockEntity.STATUS_BLOCKED -> "screen.alkahest.monocle.blocked";
                default -> "screen.alkahest.monocle.idle";
            }));
            lines.add(Text.translatable("screen.alkahest.monocle.progress",
                distillery.progress() * 100 / Tier2DistilleryBlockEntity.DURATION));
            int shownPending = 0;
            for (EssenceStack stack : distillery.pending()) {
                if (shownPending++ >= 4) break;
                lines.add(Text.literal("◆ " + stack.type().displayName() + ": " + stack.amount())
                    .styled(style -> style.withColor(stack.type().color())));
            }
            if (distillery.pending().size() > 4)
                lines.add(Text.translatable("screen.alkahest.monocle.more", distillery.pending().size() - 4));
        } else if (blockEntity instanceof StabilizerReceiverBlockEntity receiver) {
            var contents = receiver.contents();
            lines.add(Text.translatable("screen.alkahest.monocle.stabilizer", contents.total(), JarItem.CAPACITY));
            if (contents.total() == 0) {
                lines.add(Text.translatable("screen.alkahest.monocle.empty"));
            } else {
                int limit = Math.min(contents.contents().size(), 4);
                for (int i = 0; i < limit; i++) {
                    var essence = contents.contents().get(i);
                    lines.add(Text.literal("◆ " + essence.type().displayName() + ": " + essence.amount()
                        + " · " + Math.round(essence.stability() * 100) + "%")
                        .styled(style -> style.withColor(essence.type().color())));
                }
                if (contents.contents().size() > limit)
                    lines.add(Text.translatable("screen.alkahest.monocle.more", contents.contents().size() - limit));
            }
            lines.add(Text.translatable("screen.alkahest.monocle.progress",
                receiver.progress() * 100 / StabilizerReceiverBlockEntity.TICKS_PER_TYPE));
        } else if (blockEntity instanceof AlchemyPipeBlockEntity pipe && client.world.getBlockState(hit.getBlockPos()).getBlock() instanceof AlchemyPipeBlock) {
            lines.add(Text.translatable("screen.alkahest.monocle.pipe"));
            lines.add(Text.translatable("screen.alkahest.monocle.pipe_auto", pipe.active()
                ? Text.translatable("screen.alkahest.monocle.working") : Text.translatable("screen.alkahest.monocle.idle")));
            if (pipe.flowing() != null) {
                lines.add(Text.translatable("screen.alkahest.monocle.pipe_flowing",
                    Text.literal(pipe.flowing().displayName()).styled(style -> style.withColor(pipe.flowing().color()))));
            }
            lines.add(Text.translatable("screen.alkahest.monocle.no_buffer"));
        } else return;
        int maxWidth = 0;
        for (Text line : lines) maxWidth = Math.max(maxWidth, client.textRenderer.getWidth(line));
        int boxWidth = maxWidth + 12, boxHeight = lines.size() * 11 + 9;
        int screenWidth = client.getWindow().getScaledWidth(), screenHeight = client.getWindow().getScaledHeight();
        int left = Math.max(2, Math.min(screenWidth / 2 + 14, screenWidth - boxWidth - 3));
        int top = Math.max(2, Math.min(screenHeight / 2 + 12, screenHeight - boxHeight - 3));
        context.fill(left, top, left + boxWidth, top + boxHeight, 0xE40C1A25);
        context.fill(left, top, left + boxWidth, top + 1, 0xFFD5B479);
        context.fill(left, top + boxHeight - 1, left + boxWidth, top + boxHeight, 0xFF70583E);
        context.fill(left, top, left + 1, top + boxHeight, 0xFF99794F);
        context.fill(left + boxWidth - 1, top, left + boxWidth, top + boxHeight, 0xFF99794F);
        for (int i=0; i<lines.size(); i++) context.drawTextWithShadow(client.textRenderer, lines.get(i), left + 6, top + 5 + i*11, i == 0 ? 0xFFF0D39D : 0xFFDDE4D4);
    }
    private MonocleHud() { }
}
