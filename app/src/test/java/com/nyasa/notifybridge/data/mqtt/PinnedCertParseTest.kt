package com.nyasa.notifybridge.data.mqtt

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.cert.X509Certificate

class PinnedCertParseTest {

    // Self-signed cert generated with:
    //   openssl req -x509 -newkey rsa:2048 -keyout /dev/null -nodes \
    //     -subj "/CN=notifybridge-test" -days 3650 -outform PEM 2>/dev/null
    private val validPem = """
        -----BEGIN CERTIFICATE-----
        MIIDGTCCAgGgAwIBAgIUQ09DGJ64Pu6kbkOjPdEElA4LyvwwDQYJKoZIhvcNAQEL
        BQAwHDEaMBgGA1UEAwwRbm90aWZ5YnJpZGdlLXRlc3QwHhcNMjYwNTE4MDgwNDA1
        WhcNMzYwNTE1MDgwNDA1WjAcMRowGAYDVQQDDBFub3RpZnlicmlkZ2UtdGVzdDCC
        ASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALXA37LLB1Ga5/ag17xOZ0OG
        XM9RTX7Y/P+NNf+hi2Ami0MOXiyZ/bfq0/yWtxAaq9dqCxk1JBDYDG2U3EjvgSnc
        U47I15OgHkN6Lpt6N3cbNsfyqxjzof0Wv59n8cdWnal6IgKMqeDCG148MVMGrfGu
        uQc5sqrBFqF1i2YXkXyGuuMpgPFCyih7OFyCOWvP4OlkHELndk6HC2+GRRawAzK0
        HKYRXWZvBt53tACiFNeE7gcTOsHgCW09iIZX5Ms7KSwTspUTEkXOAVQrG38Qub5L
        L5c2bXjZ3q6xYPa/7vB7KqnvzDt1uFXAfJ7IRtxIywi7tl8t+7Na45kLSHpD5J8C
        AwEAAaNTMFEwHQYDVR0OBBYEFFzpgsSe+eWtppzhykhQEyl1JXSfMB8GA1UdIwQY
        MBaAFFzpgsSe+eWtppzhykhQEyl1JXSfMA8GA1UdEwEB/wQFMAMBAf8wDQYJKoZI
        hvcNAQELBQADggEBACjFLRGaP4h1zbYxKmSGxUWploF3F1E9nuNSYa0UVb7s987p
        cXnYvl9lrtumqsPugnn9CjSxD/Xyni7A5sWNimtkoQANR9Y49XqzPsomGSXmv6jm
        Hz3305TI7B+qxGq7+TQuNfgw9s1LhIYYKjgHufbzgGjECemYLraazTssLDk2ju5R
        W62OwSTvw5lff5zY0NhmvM0qI4l525lgdBkQ7nc0MkDDf4+iC7kNjsuQEAoqWpjD
        FlbcQv5EuvZKGQHvTzOyBAQNXj8KPV2BKaP+1LLaG/kP0DnudZM5DAyT+MCCeIuS
        SmdJ0hll+di984/NJE0LgPid+egTShTaKGAw2qE=
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
