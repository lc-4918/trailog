package com.tunturo.app.ui.components

import com.tunturo.app.data.db.LayerEntity
import com.tunturo.app.data.repo.TunturoRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Le bleu primaire du theme est reserve au trace du planificateur : aucune trace de la bibliotheque ne le
 * recoit, ni a l'import, ni dans le choix de sa couleur, ni par defaut.
 */
class TrackPaletteTest {

    private val primaryBlue = "#1F6FB2"

    @Test fun `le choix de la couleur d'une trace ne propose pas le bleu primaire`() {
        assertFalse(MapPalette.contains(primaryBlue))
    }

    @Test fun `l'import ne donne jamais le bleu primaire`() {
        assertFalse(TunturoRepository.Palette.colors.contains(primaryBlue))
        assertNotEquals(primaryBlue, TunturoRepository.Palette.pick(emptyList()))
    }

    @Test fun `une trace n'est pas bleu primaire par defaut`() {
        val layer = LayerEntity(name = "t", folderId = null, geometryFile = "t.geojson")
        assertNotEquals(primaryBlue, layer.color)
    }
}
