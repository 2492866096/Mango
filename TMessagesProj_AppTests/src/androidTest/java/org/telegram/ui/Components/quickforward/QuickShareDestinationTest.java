package org.telegram.ui.Components.quickforward;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class QuickShareDestinationTest {
    @Test
    public void accountIsPartOfDestinationIdentity() {
        QuickShareDestination accountA = new QuickShareDestination(1, 42L);
        QuickShareDestination sameDestination = new QuickShareDestination(1, 42L);
        QuickShareDestination accountB = new QuickShareDestination(2, 42L);

        assertEquals(accountA, sameDestination);
        assertEquals(accountA.hashCode(), sameDestination.hashCode());
        assertNotEquals(accountA, accountB);
    }
}
