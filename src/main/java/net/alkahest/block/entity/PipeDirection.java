package net.alkahest.block.entity;

import com.mojang.serialization.Codec;
import net.minecraft.text.Text;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Optional output face. AUTO preserves the historical bidirectional routing. */
public enum PipeDirection implements StringIdentifiable {
    AUTO("auto", null), DOWN("down", Direction.DOWN), UP("up", Direction.UP),
    NORTH("north", Direction.NORTH), SOUTH("south", Direction.SOUTH),
    WEST("west", Direction.WEST), EAST("east", Direction.EAST);

    public static final Codec<PipeDirection> CODEC = StringIdentifiable.createCodec(PipeDirection::values);
    private final String id;
    private final Direction output;
    PipeDirection(String id, Direction output) { this.id = id; this.output = output; }
    @Override public String asString() { return id; }
    public PipeDirection next() { return values()[(ordinal() + 1) % values().length]; }
    public boolean permits(BlockPos from, BlockPos to) { return output == null || from.offset(output).equals(to); }
    public Text displayText() { return Text.translatable("message.alkahest.pipe_direction." + id); }
    public static PipeDirection fromId(String id) {
        for (PipeDirection direction : values()) if (direction.id.equals(id)) return direction;
        return AUTO;
    }
}
