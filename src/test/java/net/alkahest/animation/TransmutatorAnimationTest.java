package net.alkahest.animation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransmutatorAnimationTest {
    @Test void targetSpeedRisesAndFallsWithRealProgress() {
        float start = TransmutatorAnimation.targetSpeed(0, 100, true);
        float early = TransmutatorAnimation.targetSpeed(25, 100, true);
        float peak = TransmutatorAnimation.targetSpeed(50, 100, true);
        float late = TransmutatorAnimation.targetSpeed(75, 100, true);
        float end = TransmutatorAnimation.targetSpeed(100, 100, true);
        assertTrue(start < early && early < peak && peak > late && late > end);
        assertEquals(early, late, .0001f);
        assertEquals(TransmutatorAnimation.IDLE_SPEED, end, .0001f);
        assertEquals(start, TransmutatorAnimation.targetSpeed(50, 100, false), .0001f);
        assertEquals(start, TransmutatorAnimation.targetSpeed(-10, 100, true), .0001f);
        assertEquals(end, TransmutatorAnimation.targetSpeed(150, 100, true), .0001f);
    }

    @Test void checkpointsCoverMidasSkippedBoundariesWithoutTickSpam() {
        assertTrue(TransmutatorAnimation.shouldSync(0, 1));
        assertFalse(TransmutatorAnimation.shouldSync(1, 2));
        assertTrue(TransmutatorAnimation.shouldSync(9, 11));
        assertTrue(TransmutatorAnimation.shouldSync(99, 100));
        assertFalse(TransmutatorAnimation.shouldSync(100, 100));
        int sent = 0;
        for (int i = 0; i < 100; i++) if (TransmutatorAnimation.shouldSync(i, i + 1)) sent++;
        assertEquals(11, sent);
    }

    @Test void extrapolationIsBoundedAndStopsWhenMachineIsNotWorking() {
        assertEquals(20, TransmutatorAnimation.estimateProgress(20, 8, false, 100), .0001);
        assertTrue(TransmutatorAnimation.estimateProgress(20, 8, true, 100) > 20);
        assertEquals(33.2f, TransmutatorAnimation.estimateProgress(20, 500, true, 100), .0001);
        assertEquals(100, TransmutatorAnimation.estimateProgress(99, 8, true, 100), .0001);
        assertEquals(20, TransmutatorAnimation.estimateProgress(20, -2, true, 100), .0001);
    }

    @Test void motionIntegratesContinuouslyAcrossStopsAndAngleWrap() {
        var motion = new TransmutatorAnimation.Motion();
        motion.update(0, 0, 100, true);
        for (int tick = 1; tick <= 50; tick++) motion.update(tick, tick, 100, true);
        float fast = motion.speed();
        assertTrue(fast > TransmutatorAnimation.IDLE_SPEED);
        float beforeStop = motion.ringAngle();
        motion.update(51, 50, 100, false);
        assertTrue(motion.speed() < fast && motion.speed() > TransmutatorAnimation.IDLE_SPEED);
        assertTrue(motion.ringAngle() > beforeStop, "Stopping smoothly changes speed, never jumps phase");
        for (int tick = 52; tick < 2000; tick++) {
            float a = motion.outerRingAngle();
            motion.update(tick, 0, 100, false);
            float delta = Math.abs(((motion.outerRingAngle() - a + 540) % 360) - 180);
            assertTrue(delta < 2, "Outer ring wraps without a 72 degree jump");
        }
        assertNotEquals(0, motion.amuletAngle(), .001f);
    }
}
