package net.alkahest.essence;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringIdentifiable;

import java.util.List;
import java.util.Locale;

public enum EssenceType implements StringIdentifiable {
    IGNIS(0xFF4500, Tier.PRIMA), AQUA(0x1E90FF, Tier.PRIMA),
    TERRA(0x8B5A2B, Tier.PRIMA), AER(0xF0F8FF, Tier.PRIMA),
    ORDO(0xFFD700, Tier.PRIMA), PERDITIO(0x4B0082, Tier.PRIMA),
    VAPOR(0xB0E0E6, Tier.COMPOSITA, AQUA, IGNIS),
    HERBA(0x2E8B57, Tier.COMPOSITA, TERRA, AQUA),
    METALLUM(0xA9A9A9, Tier.COMPOSITA, TERRA, ORDO),
    LUX(0xFFFFE0, Tier.COMPOSITA, IGNIS, AER),
    MOTUS(0x87CEFA, Tier.COMPOSITA, AER, ORDO),
    MORTUUS(0x556B2F, Tier.COMPOSITA, PERDITIO, AQUA),
    POTENTIA(0xFF1493, Tier.COMPOSITA, IGNIS, ORDO),
    VITIUM(0x800080, Tier.COMPOSITA, PERDITIO, TERRA),
    GELUM(0x00FFFF, Tier.COMPOSITA, AQUA, ORDO),
    SPIRITUS(0xE6E6FA, Tier.COMPOSITA, MORTUUS, ORDO),
    VACUUM(0x0D001A, Tier.VACUUM);

    public enum Tier { PRIMA, COMPOSITA, VACUUM }
    public static final Codec<EssenceType> CODEC = StringIdentifiable.createCodec(EssenceType::values);
    private final int color;
    private final Tier tier;
    private final List<EssenceType> parents;

    EssenceType(int color, Tier tier, EssenceType... parents) {
        this.color = color;
        this.tier = tier;
        this.parents = List.of(parents);
    }
    @Override public String asString() { return name().toLowerCase(Locale.ROOT); }
    public String displayName() { String id = asString(); return Character.toUpperCase(id.charAt(0)) + id.substring(1); }
    public int color() { return color; }
    public Tier tier() { return tier; }
    public List<EssenceType> parents() { return parents; }
    public EssenceType opposite() {
        return switch (this) {
            case IGNIS -> AQUA; case AQUA -> IGNIS;
            case TERRA -> AER; case AER -> TERRA;
            case ORDO -> PERDITIO; case PERDITIO -> ORDO;
            default -> null;
        };
    }
}
