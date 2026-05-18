package com.nyasa.notifybridge.ui.permissions
import org.junit.Assert.assertEquals
import org.junit.Test
class PermissionsViewModelTest {
    @Test fun status_pills_map_correctly() {
        assertEquals(PermPill.GRANTED, permPill(granted = true))
        assertEquals(PermPill.ACTION_NEEDED, permPill(granted = false))
    }
}
