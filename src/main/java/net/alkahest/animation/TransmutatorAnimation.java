package net.alkahest.animation;

/** Client-side visual maths and bounded progress interpolation; never affects recipes. */
public final class TransmutatorAnimation {
    public static final float IDLE_SPEED = .32f;
    private static final float SPEED_BOOST = 1.05f;
    private static final float MAX_EXTRAPOLATION_TICKS = 12f;
    private static final float ESTIMATED_STEP = 1.1f; // bounded approximation, corrected by server checkpoints

    private TransmutatorAnimation() { }

    /** Send at start and on each ten-progress boundary, including boosted steps that cross it. */
    public static boolean shouldSync(int before, int after) {
        return after > before && (before == 0 || before / 10 != after / 10);
    }

    public static float estimateProgress(int progress, float elapsedTicks, boolean working, int duration) {
        float estimate = progress;
        if (working) estimate += Math.max(0, Math.min(MAX_EXTRAPOLATION_TICKS, elapsedTicks)) * ESTIMATED_STEP;
        return Math.max(0, Math.min(duration, estimate));
    }

    /** Idle at both ends, a smooth faster peak halfway through the actual cycle. */
    public static float targetSpeed(float progress, int duration, boolean working) {
        if (!working || duration <= 0) return IDLE_SPEED;
        float fraction = Math.max(0, Math.min(1, progress / duration));
        return IDLE_SPEED + SPEED_BOOST * (float)Math.sin(Math.PI * fraction);
    }

    /** Per-block, per-client phase: integrate speed, never multiply wall time by changing speed. */
    public static final class Motion {
        private double lastTime = Double.NaN;
        private float speed = IDLE_SPEED;
        private float ringAngle;
        private float outerRingAngle;
        private float amuletAngle;

        public void update(double time, float progress, int duration, boolean working) {
            if (Double.isNaN(lastTime)) { lastTime = time; return; }
            float delta = (float)Math.max(0, Math.min(3, time - lastTime));
            lastTime = time;
            float target = targetSpeed(progress, duration, working);
            float blend = 1f - (float)Math.exp(-.3f * delta);
            speed += (target - speed) * blend;
            ringAngle = (ringAngle + speed * delta) % 360f;
            outerRingAngle = (outerRingAngle - speed * .8f * delta) % 360f;
            amuletAngle = (amuletAngle + .85f * delta) % 360f;
        }

        public float ringAngle() { return ringAngle; }
        public float outerRingAngle() { return outerRingAngle; }
        public float amuletAngle() { return amuletAngle; }
        public float speed() { return speed; }
    }
}
