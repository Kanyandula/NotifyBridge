package com.nyasa.notifybridge.applock

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BiometricAuthenticatorTest {
    @Test fun canAuthenticate_returns_a_status() {
        val a = BiometricAuthenticator(ApplicationProvider.getApplicationContext())
        assertNotNull(a.availability())
    }
}
