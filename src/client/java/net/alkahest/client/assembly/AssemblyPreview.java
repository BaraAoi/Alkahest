package net.alkahest.client.assembly;

import net.alkahest.assembly.AssemblyBlueprint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3f;
import java.util.LinkedHashMap;

/** Local dotted ghost outlines; no blocks, entities, or persistent server data are created. */
public final class AssemblyPreview {
    private static final int DURATION = 160;
    private static final DustParticleEffect EMPTY = new DustParticleEffect(new Vector3f(.45f, .9f, 1f), .9f);
    private static final DustParticleEffect BLOCKED = new DustParticleEffect(new Vector3f(1f, .60f, .25f), .9f);
    private static ClientWorld world;
    private static BlockPos core;
    private static int ticks;
    public static void clear() { world = null; core = null; ticks = 0; }
    public static void open(BlockPos pos) {
        var client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        var result = AssemblyBlueprint.inspect(client.world, pos);
        if (result == null) return;
        world = client.world; core = pos.toImmutable(); ticks = DURATION;
        var player = client.player;
        player.sendMessage(Text.translatable("message.alkahest.assembly_preview", result.missing().size(), result.unknown()), true);
        var counts = new LinkedHashMap<net.minecraft.block.Block, Integer>();
        for (var part : result.missing()) counts.merge(part.expected(), 1, Integer::sum);
        for (var entry : counts.entrySet())
            player.sendMessage(Text.translatable("message.alkahest.assembly_part", entry.getKey().getName(), entry.getValue()), false);
        if (!counts.isEmpty()) player.sendMessage(Text.translatable("message.alkahest.assembly_colors"), false);
        if (result.kind() == AssemblyBlueprint.Kind.MATRIX)
            player.sendMessage(Text.translatable("message.alkahest.assembly_pedestals"), false);
        if (result.unknown() > 0) player.sendMessage(Text.translatable("message.alkahest.assembly_unknown"), false);
    }
    public static void tick(MinecraftClient client) {
        if (core == null) return;
        if (client.world != world || client.player == null || --ticks <= 0
                || client.player.getBlockPos().getSquaredDistance(core) > 32 * 32) { clear(); return; }
        if (ticks % 5 != 0) return;
        var inspection = AssemblyBlueprint.inspect(world, core);
        if (inspection == null) { clear(); return; }
        var missing = inspection.missing();
        if (missing.isEmpty()) return;
        // A rotating batch bounds particle load even for an entirely empty Matrix shell.
        int start = ((DURATION - ticks) / 5 * 7) % missing.size();
        for (int i = 0; i < Math.min(7, missing.size()); i++) outline(world, missing.get((start + i) % missing.size()));
    }
    private static void outline(ClientWorld world, AssemblyBlueprint.Missing part) {
        var dust = part.obstructed() ? BLOCKED : EMPTY;
        var p = part.pos();
        // Just outside the block faces, so outlines remain visible when a solid block is in the way.
        for (int a = 0; a <= 2; a++) for (int b = 0; b <= 2; b++) for (int c = 0; c <= 2; c++) {
            int middle = (a == 1 ? 1 : 0) + (b == 1 ? 1 : 0) + (c == 1 ? 1 : 0);
            if (middle > 1) continue;
            world.addParticle(dust, p.getX() - .04 + .54 * a, p.getY() - .04 + .54 * b, p.getZ() - .04 + .54 * c, 0, 0, 0);
        }
    }
    private AssemblyPreview() { }
}
