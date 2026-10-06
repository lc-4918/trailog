package fr.lc4918.trailog.data.db

import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsDefaultsTest {
    /** Un itineraire calcule, son trace comme son profil, n'est pas colorie par la pente tant qu'on ne le demande pas. */
    @Test fun `l'itineraire n'est pas colorie par la pente par defaut`() {
        assertFalse(SettingsEntity().routeSlopeLine)
    }
}
