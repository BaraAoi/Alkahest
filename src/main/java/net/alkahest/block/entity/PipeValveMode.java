package net.alkahest.block.entity;

import com.mojang.serialization.Codec;
import net.minecraft.text.Text;
import net.minecraft.util.StringIdentifiable;

/** Durable segment configuration; ordinary pipes remain open in all old worlds. */
public enum PipeValveMode implements StringIdentifiable {
    OPEN("open"),
    CLOSE_WHEN_POWERED("close_when_powered"),
    OPEN_WHEN_POWERED("open_when_powered"),
    CLOSED("closed");

    public static final Codec<PipeValveMode> CODEC = StringIdentifiable.createCodec(PipeValveMode::values);
    private final String id;
    PipeValveMode(String id) { this.id = id; }
    @Override public String asString() { return id; }
    public PipeValveMode next() { return values()[(ordinal() + 1) % values().length]; }
    public boolean allows(boolean powered) {
        return switch (this) {
            case OPEN -> true;
            case CLOSE_WHEN_POWERED -> !powered;
            case OPEN_WHEN_POWERED -> powered;
            case CLOSED -> false;
        };
    }
    public Text displayText() { return Text.translatable("message.alkahest.pipe_valve." + id); }
    public static PipeValveMode fromId(String id) {
        for (PipeValveMode mode : values()) if (mode.id.equals(id)) return mode;
        return OPEN;
    }
}
