package com.nyasa.notifybridge.ui.onboarding
import org.junit.Assert.assertEquals
import org.junit.Test
class OnboardingViewModelTest {
    @Test fun steps_gate_sequentially() {
        val s = onboardingState(notifAccess = false, brokerSet = false, appsChosen = false)
        assertEquals(OnboardingStep.GRANT_ACCESS, s.activeStep)
        val s2 = onboardingState(notifAccess = true, brokerSet = false, appsChosen = false)
        assertEquals(OnboardingStep.CONNECT_BROKER, s2.activeStep)
        val s3 = onboardingState(notifAccess = true, brokerSet = true, appsChosen = true)
        assertEquals(OnboardingStep.DONE, s3.activeStep)
    }
}
