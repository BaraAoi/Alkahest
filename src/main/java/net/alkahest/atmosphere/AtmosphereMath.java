package net.alkahest.atmosphere;

import net.alkahest.essence.EssenceType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Pure, bounded arithmetic shared by the world simulation and its tests. */
public final class AtmosphereMath {
    public static final List<EssenceType> PRIMALS = List.of(EssenceType.IGNIS, EssenceType.AQUA,
        EssenceType.TERRA, EssenceType.AER, EssenceType.ORDO, EssenceType.PERDITIO);
    public static final double LEGACY_MAX_EXCESS = 80;
    private static final double STABILITY_LEVEL_LIMIT = 100;
    public static final long LEGACY_HALF_LIFE = 12000; // only for importing pre-diffusion saves
    public static final double SPAWN_THRESHOLD = 25;

    /** Split composite essence equally at every branch, conserving the input amount. */
    public static void split(EssenceType type, double amount, Map<EssenceType, Double> out) {
        if (type == null || !Double.isFinite(amount) || amount <= 0) return;
        if (type.tier() == EssenceType.Tier.PRIMA) out.merge(type, amount, Double::sum);
        else if (!type.parents().isEmpty()) {
            double each = amount / type.parents().size();
            for (EssenceType parent : type.parents()) split(parent, each, out);
        }
    }
    public static EnumMap<EssenceType, Double> split(Map<EssenceType, ? extends Number> input) {
        var result = new EnumMap<EssenceType, Double>(EssenceType.class);
        input.forEach((type, amount) -> split(type, amount.doubleValue(), result));
        return result;
    }
    /** One-time conversion of the old lazy-decay format to its visible value at upgrade. */
    public static double legacyDecay(double value, long elapsed) {
        if (!Double.isFinite(value) || value <= 0) return 0;
        return Math.min(LEGACY_MAX_EXCESS, value) * Math.pow(0.5, Math.max(0, elapsed) / (double) LEGACY_HALF_LIFE);
    }
    /** Weighted recipe-specific overload slows synthesis but never blocks it or destroys inputs. */
    public static int stability(Map<EssenceType, ? extends Number> cost, Map<EssenceType, Double> levels) {
        var weights = split(cost);
        double weight = 0, overload = 0;
        for (var entry : weights.entrySet()) {
            weight += entry.getValue();
            overload += entry.getValue() * Math.max(0, Math.min(STABILITY_LEVEL_LIMIT, levels.getOrDefault(entry.getKey(), 0.0)) - 30);
        }
        if (weight == 0) return 100;
        return (int) Math.round(Math.max(50, Math.min(100, 100 - overload / weight)));
    }
    /** One roll per active cell per minute, summed chance capped at 12%. */
    public static double spawnChance(Map<EssenceType, Double> levels) {
        double excess = 0;
        for (EssenceType type : PRIMALS) {
            excess += Math.min(120, Math.max(0, levels.getOrDefault(type, 0.0) - SPAWN_THRESHOLD));
            if (excess >= 120) return 0.12;
        }
        return excess * 0.001;
    }
    /** Slow positive-gradient flow per face; larger differences accelerate it, without overtaking the old rate. */
    public static double diffusionTransfer(double source, double target) {
        double difference = source - target;
        if (!(difference > 0) || !Double.isFinite(difference)) return 0;
        // Saturating response: 1/2048 for tiny gradients, approaching 1/512 for extreme ones.
        double acceleration = 1 + 3 * (1 - 400 / (difference + 400));
        return difference / 2048 * acceleration;
    }
    /** Finite saturation protects the saved and network formats from IEEE overflow, not a gameplay cap. */
    public static double finiteSum(double a, double b) {
        double sum = a + b;
        return Double.isFinite(sum) ? sum : Double.MAX_VALUE;
    }
    public static String formatLevel(double value) {
        return String.format(java.util.Locale.ROOT, value >= 100000 ? "%.2e" : "%.1f", value);
    }
    private AtmosphereMath() { }
}
