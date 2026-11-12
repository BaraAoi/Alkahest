package net.alkahest.essence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Contents are immutable and contain at most one entry per type. Capacity is enforced by the container. */
public record JarContentsComponent(List<EssenceStack> contents) {
    public static final JarContentsComponent EMPTY = new JarContentsComponent(List.of());
    public static final Codec<JarContentsComponent> CODEC = codec(500);
    // One product batch may contain up to 500 of each type; old item data has the same structure.
    public static final Codec<JarContentsComponent> DISTILLERY_CODEC = codec(500 * EssenceType.values().length);
    private static Codec<JarContentsComponent> codec(int maximum) {
        return RecordCodecBuilder.<JarContentsComponent>create(instance -> instance.group(
        EssenceStack.CODEC.listOf().fieldOf("contents").forGetter(JarContentsComponent::contents)
    ).apply(instance, JarContentsComponent::new)).validate(value -> {
        long total = value.contents.stream().mapToLong(EssenceStack::amount).sum();
        long types = value.contents.stream().map(EssenceStack::type).distinct().count();
        return total <= maximum && types == value.contents.size()
            ? DataResult.success(value) : DataResult.error(() -> "Invalid essence contents");
        });
    }

    public JarContentsComponent {
        contents = List.copyOf(contents);
    }

    public int total() { return contents.stream().mapToInt(EssenceStack::amount).sum(); }

    private static boolean valid(JarContentsComponent jar) {
        return jar.contents.stream().mapToLong(EssenceStack::amount).sum() <= 500
            && jar.contents.stream().map(EssenceStack::type).distinct().count() == jar.contents.size();
    }

    /** Adds in list order; overflow is evaporated. Vacuum must be handled by the caller. */
    public JarContentsComponent merge(JarContentsComponent incoming, int capacity) {
        if (capacity < 0 || capacity > 500 || total() > capacity) throw new IllegalArgumentException("invalid capacity");
        if (!valid(this) || !valid(incoming)) throw new IllegalArgumentException("invalid contents");
        Map<EssenceType, EssenceStack> result = new EnumMap<>(EssenceType.class);
        contents.forEach(stack -> result.put(stack.type(), stack));
        int remaining = capacity - total();
        for (EssenceStack addition : incoming.contents) {
            int accepted = Math.min(remaining, addition.amount());
            if (accepted == 0) break;
            EssenceStack existing = result.get(addition.type());
            int oldAmount = existing == null ? 0 : existing.amount();
            float oldStability = existing == null ? 0 : existing.stability();
            float stability = (oldStability * oldAmount + addition.stability() * accepted) / (oldAmount + accepted);
            result.put(addition.type(), new EssenceStack(addition.type(), oldAmount + accepted, stability));
            remaining -= accepted;
        }
        return new JarContentsComponent(List.copyOf(result.values()));
    }
}
