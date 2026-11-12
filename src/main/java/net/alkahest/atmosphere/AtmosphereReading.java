package net.alkahest.atmosphere;

import net.alkahest.essence.EssenceType;
import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Bounded immutable server reading; locations are fixed cell origins, not arbitrary targets. */
public record AtmosphereReading(String dimension, int x, int y, int z,
                                List<Double> levels, List<Double> baseline, double chance) {
    public AtmosphereReading {
        levels = List.copyOf(levels);
        baseline = List.copyOf(baseline);
        if (dimension == null || dimension.length() > 128 || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                || x % 16 != 0 || y % 16 != 0 || z % 16 != 0
                || Math.abs((long) x) > 30000016 || Math.abs((long) z) > 30000016
                || y < -2048 || y > 2047 || levels.size() != 6 || baseline.size() != 6
                || levels.stream().anyMatch(v -> !Double.isFinite(v) || v < 0 || v > 100)
                || baseline.stream().anyMatch(v -> !Double.isFinite(v) || v < 0 || v > 100)
                || !Double.isFinite(chance) || chance < 0 || chance > .08)
            throw new IllegalArgumentException("Invalid atmospheric reading");
    }
    public static AtmosphereReading create(String dimension, BlockPos pos, Map<EssenceType, Double> levels,
                                           Map<EssenceType, Double> baseline) {
        BlockPos origin = AtmosphereState.cellOrigin(pos);
        List<Double> current = new ArrayList<>(), base = new ArrayList<>();
        for (EssenceType type : AtmosphereMath.PRIMALS) {
            current.add(levels.getOrDefault(type, 10.0));
            base.add(baseline.getOrDefault(type, 10.0));
        }
        return new AtmosphereReading(dimension, origin.getX(), origin.getY(), origin.getZ(), current, base,
            AtmosphereMath.spawnChance(levels));
    }
    /** Pipe-delimited bounded format, used only for server-to-client updates. */
    public String encode() {
        StringBuilder result = new StringBuilder(dimension).append('|').append(x).append('|').append(y).append('|').append(z);
        for (double value : levels) result.append('|').append(value);
        for (double value : baseline) result.append('|').append(value);
        return result.append('|').append(chance).toString();
    }
    public static Optional<AtmosphereReading> decode(String text) {
        if (text == null || text.length() > 512) return Optional.empty();
        String[] fields = text.split("\\|", -1);
        if (fields.length != 17) return Optional.empty();
        try {
            var levels = new ArrayList<Double>(); var baseline = new ArrayList<Double>();
            for (int i = 4; i < 10; i++) levels.add(Double.parseDouble(fields[i]));
            for (int i = 10; i < 16; i++) baseline.add(Double.parseDouble(fields[i]));
            return Optional.of(new AtmosphereReading(fields[0], Integer.parseInt(fields[1]),
                Integer.parseInt(fields[2]), Integer.parseInt(fields[3]), levels, baseline, Double.parseDouble(fields[16])));
        } catch (IllegalArgumentException ex) { return Optional.empty(); }
    }
    public boolean matches(String dimension, BlockPos pos) {
        BlockPos origin = AtmosphereState.cellOrigin(pos);
        return this.dimension.equals(dimension) && x == origin.getX() && y == origin.getY() && z == origin.getZ();
    }
}
