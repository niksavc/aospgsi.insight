package com.bloxinsight.core.verification

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerificationPolicyTest {
    private fun report(readable: Boolean, verified: Boolean) = VerificationReport(
        archiveReadable = readable,
        signatureVerified = verified,
        sha256 = null,
        warnings = emptyList()
    )

    @Test
    fun installAllowedOnlyWhenReadableAndSignatureVerified() {
        assertTrue(VerificationPolicy.shouldAllowInstall(report(readable = true, verified = true)))
        assertFalse(VerificationPolicy.shouldAllowInstall(report(readable = true, verified = false)))
        assertFalse(VerificationPolicy.shouldAllowInstall(report(readable = false, verified = true)))
        assertFalse(VerificationPolicy.shouldAllowInstall(report(readable = false, verified = false)))
    }
}
