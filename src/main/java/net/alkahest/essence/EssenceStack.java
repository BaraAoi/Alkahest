package net.alkahest.essence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;

/** An immutable quantity of one essence and its average stability. */
public record EssenceStack(EssenceType type, int amount, float stability) {
    public static final Codec<EssenceStack> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        EssenceType.CODEC.fieldOf("type").forGetter(EssenceStack::type),
        Codec.intRange(1, 500).fieldOf("amount").forGetter(EssenceStack::amount),
        Codec.floatRange(0.0f, 1.0f).fieldOf("stability").forGetter(EssenceStack::stability)
    ).apply(instance, EssenceStack::new));

    public EssenceStack {
        Objects.requireNonNull(type, "type");
        if (amount < 1 || amount > 500) throw new IllegalArgumentException("amount must be 1..500");
        if (!Float.isFinite(stability) || stability < 0 || stability > 1) throw new IllegalArgumentException("stability must be 0..1");
    }
}
