package net.alkahest.research;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResearchMapViewTest {
    @Test void zoomAnchorsUnderCursorAndHitRadiusScales() {
        var camera = new ResearchMapView();
        camera.pan(-80, 35);
        double oldX = camera.x(110), oldY = camera.y(-25);
        camera.zoomAt(1.4, oldX, oldY);
        assertEquals(oldX, camera.x(110), 1e-8);
        assertEquals(oldY, camera.y(-25), 1e-8);
        assertEquals(16 * 1.4, camera.radius(), 1e-8);
        camera.zoomAt(.001, oldX, oldY);
        assertEquals(ResearchMapView.MIN_ZOOM, camera.zoom(), 1e-8);
        camera.zoomAt(1000, oldX, oldY);
        assertEquals(ResearchMapView.MAX_ZOOM, camera.zoom(), 1e-8);
    }
    @Test void clampStillAllowsBothEndsOfTreeToBeReached() {
        var camera = new ResearchMapView();
        camera.pan(-10000, -10000);
        camera.clamp(378, 147, 24, 936, -108, 110);
        assertEquals(348, camera.x(936), 1e-8);
        assertEquals(147.0 / 2 - 25, camera.y(110), 1e-8);
        camera.pan(20000, 20000);
        camera.clamp(378, 147, 24, 936, -108, 110);
        assertEquals(30, camera.x(24), 1e-8);
        assertEquals(-147.0 / 2 + 25, camera.y(-108), 1e-8);
    }
}
