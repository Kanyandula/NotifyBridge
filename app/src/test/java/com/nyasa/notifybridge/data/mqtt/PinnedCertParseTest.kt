package com.nyasa.notifybridge.data.mqtt

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.cert.X509Certificate

class PinnedCertParseTest {

    // Self-signed cert generated with:
    //   openssl req -x509 -newkey rsa:2048 -keyout /dev/null -nodes \
    //     -subj "/CN=test" -days 1 -outform PEM 2>/dev/null
    private val validPem = """
        -----BEGIN CERTIFICATE-----
        MIIC/zCCAeegAwIBAgIUe3PbpalWAmcYCY9PrGc9z1JU1ekwDQYJKoZIhvcNAQEL
        BQAwDzENMAsGA1UEAwwEdGVzdDAeFw0yNjA1MTgwNzUyMjFaFw0yNjA1MTkwNzUy
        MjFaMA8xDTALBgNVBAMMBHRlc3QwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEK
        AoIBAQDgbucV1JF9aLDiSu27jU8o91GVPsh1gIiVu+9tDFdOFtZBK89eRnRnmQ9X
        flkuURKmwXyUhp4Suq7jjIlf6hCR/fn7xU3MfBeSTHlhp5+E+F3Y92bd2TEOvTJV
        5MA5iZogZBPjC0FjMRXIKXiSs1UZpAPL7RJ0KQq/b8yVsnUh2IS18mlwfEoMywdm
        y93IrUBCauWowBRgVfRsQWQAdoLq15vrhEtD8oSvLswpmuAPnzK178ZyAbW/gDGH
        44IBiYKq7o0QxWWvFtmTN4wJqV/fN7GERKEn96HWIev5puasIrkCwmwA5Su7iz/B
        Ai9G/v7IDF5cmCYqLh2QfSOCbsI9AgMBAAGjUzBRMB0GA1UdDgQWBBQtb3p7DGJK
        z757n7HkSH8+h+loijAfBgNVHSMEGDAWgBQtb3p7DGJKz757n7HkSH8+h+loijAP
        BgNVHRMBAf8EBTADAQH/MA0GCSqGSIb3DQEBCwUAA4IBAQAZP+YFARckJSL97BUn
        nB5NjcQCnFM7R0js64of7CHaSEWsNyPBJr1QNKOUZK6dtty56wNtZKa6QL7eqGxP
        I/Bsk1U9BUc0Uk5sP0jdI4i8e9QT7/rQL0SlEJVVMD77kczvbLTPgeKT2/cfMoJ0
        FCwbR/AsS3aMwi+uq2dTOoU+5piffw4ODJ9iF+9+NHBZdplE6HR9CtIDvqJbLHkz
        MtkdVJkyPyr/Wi/h3c0OyYDgz35QPHqlKq7bn1tIv7Fwm+kl7r2V3NgA1UgEuX+e
        BcFw9C24IAARQGvjNSk4+AJvWKBYTy3UM3QmjMTGwl4eUiaD6HSxgK1uO1U5kcB2
        o51O
        -----END CERTIFICATE-----
    """.trimIndent()

    @Test
    fun `valid PEM parses to non-null X509Certificate`() {
        val cert = parsePinnedCert(validPem)
        assertNotNull(cert)
        assertTrue(cert is X509Certificate)
    }

    @Test(expected = Exception::class)
    fun `blank PEM throws`() {
        parsePinnedCert("")
    }

    @Test(expected = Exception::class)
    fun `malformed PEM throws`() {
        parsePinnedCert("-----BEGIN CERTIFICATE-----\nNOTBASE64!!!\n-----END CERTIFICATE-----")
    }
}
