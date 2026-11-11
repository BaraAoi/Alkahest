package net.alkahest.atmosphere;

import net.alkahest.essence.EssenceType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Pure, bounded arithmetic shared by the world simulation and its tests. */
public final class AtmosphereMath {
    public static final List<EssenceType> PRIMALS = List.of(EssenceType.IGNIS, EssenceType.AQUA,
        EssenceType.TERRA, EssenceType.AER, EssenceType.ORDO, EssenceType.PERDITIO);
    public static final double MAX_EXCESS = 80, MAX_LEVEL = 100;
    public static final long HALF_LIFE = 12000; // 10 minutes at 20 TPS
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
    public static double decay(double value, long elapsed) {
        if (!Double.isFinite(value) || value <= 0) return 0;
        return Math.min(MAX_EXCESS, value) * Math.pow(0.5, Math.max(0, elapsed) / (double) HALF_LIFE);
    }
    /** Weighted recipe-specific overload slows synthesis but never blocks it or destroys inputs. */
    public static int stability(Map<EssenceType, ? extends Number> cost, Map<EssenceType, Double> levels) {
        var weights = split(cost);
        double weight = 0, overload = 0;
        for (var entry : weights.entrySet()) {
            weight += entry.getValue();
            overload += entry.getValue() * Math.max(0, Math.min(MAX_LEVEL, levels.getOrDefault(entry.getKey(), 0.0)) - 30);
        }
        if (weight == 0) return 100;
        return (int) Math.round(Math.max(50, Math.min(100, 100 - overload / weight)));
    }
    /** One roll per active cell per minute, summed chance capped at 8%. */
    public static double spawnChance(Map<EssenceType, Double> levels) {
        double excess = 0;
        for (EssenceType type : PRIMALS) excess += Math.max(0, Math.min(MAX_LEVEL, levels.getOrDefault(type, 0.0)) - SPAWN_THRESHOLD);
        return Math.min(0.08, excess * 0.001);
    }
    private AtmosphereMath() { }
}
