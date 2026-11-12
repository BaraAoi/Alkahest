package net.alkahest.essence;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EssenceComparatorTest {
    @Test void boundedMonotonicAndFullOnlyAtCapacity() {
        for (int capacity : new int[]{64, 100}) {
            int previous = 0;
            for (int amount = 0; amount <= capacity; amount++) {
                int signal = EssenceComparator.signal(amount, capacity);
                assertTrue(signal >= previous && signal <= 15);
                if (amount == 0) assertEquals(0, signal);
                else if (amount < capacity) assertTrue(signal >= 1 && signal <= 14);
                else assertEquals(15, signal);
                previous = signal;
            }
            assertEquals(15, EssenceComparator.signal(Integer.MAX_VALUE, capacity));
        }
        assertEquals(0, EssenceComparator.signal(-1, 100));
        assertEquals(0, EssenceComparator.signal(10, 0));
        assertEquals(2, EssenceComparator.signal(8, 100));
        assertEquals(2, EssenceComparator.signal(5, 64));
    }
}
