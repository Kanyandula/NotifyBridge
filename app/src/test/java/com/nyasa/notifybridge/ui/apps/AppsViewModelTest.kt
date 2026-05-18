package com.nyasa.notifybridge.ui.apps
import org.junit.Assert.assertEquals
import org.junit.Test
class AppsViewModelTest {
    private val apps = listOf(
        AppRow("WhatsApp","com.whatsapp",true),
        AppRow("Gmail","com.google.android.gm",false))
    @Test fun search_filters_by_label_or_package() {
        assertEquals(1, filterApps(apps, "whats").size)
        assertEquals(1, filterApps(apps, "gm").size)
        assertEquals(2, filterApps(apps, "").size)
    }
    @Test fun toggle_updates_selection_set() {
        assertEquals(setOf("com.whatsapp","com.x"),
            toggle(setOf("com.whatsapp"), "com.x", on = true))
        assertEquals(emptySet<String>(),
            toggle(setOf("com.whatsapp"), "com.whatsapp", on = false))
    }
}
