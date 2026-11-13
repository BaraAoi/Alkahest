package net.alkahest.shield;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShieldBarLayoutTest {
    @Test void sitsAboveHeartsAndArmorWithoutOverlapping() {
        var bare=ShieldBarLayout.calculate(320,240,20,0,false,1,2);
        var armor=ShieldBarLayout.calculate(320,240,20,0,true,1,2);
        assertEquals(69,bare.x()); assertEquals(193,bare.y());
        assertEquals(183,armor.y()); assertEquals(36,bare.fillWidth());
        assertTrue(armor.y()+6 < 240-39-10);
    }
    @Test void tracksAbsorptionAndExtraHeartRowsAndClampsFill() {
        var one=ShieldBarLayout.calculate(320,240,20,0,true,6,6);
        var two=ShieldBarLayout.calculate(320,240,20,4,true,6,6);
        var three=ShieldBarLayout.calculate(320,240,40,4,true,-1,6);
        assertTrue(two.y()<one.y()); assertTrue(three.y()<two.y());
        assertEquals(0,three.fillWidth());
        assertEquals(one.width()-10,ShieldBarLayout.calculate(320,240,20,0,false,99,6).fillWidth());
        assertEquals(0,ShieldBarLayout.calculate(320,240,20,0,false,1,0).fillWidth());
    }
}
