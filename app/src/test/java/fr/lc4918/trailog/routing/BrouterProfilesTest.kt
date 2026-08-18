package fr.lc4918.trailog.routing

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import fr.lc4918.trailog.domain.model.HillPref
import fr.lc4918.trailog.domain.model.RoutingPrefs
import fr.lc4918.trailog.domain.model.RoutingProfile
import fr.lc4918.trailog.domain.model.SurfacePref
import fr.lc4918.trailog.domain.model.WayPref
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Les profils BRouter reellement livres dans les assets, confrontes a la table qui les regle.
 *
 * Comme pour le jeu de demonstration, ces cas verifient des DONNEES, et pour la meme raison : la faute
 * qu'ils attrapent ne casse rien. Un profil amont qui renomme un curseur - ou une faute de frappe dans la
 * table - laisse l'itineraire se calculer, avec le profil dans son etat par defaut. L'utilisateur bouge
 * alors ses trois reglages sans que rien ne se passe, et rien a l'ecran ne le dit.
 */
@RunWith(RobolectricTestRunner::class)
class BrouterProfilesTest {
    private val ctx = ApplicationProvider.getApplicationContext<Application>()

    private fun raw(p: RoutingProfile) =
        ctx.assets.open(BrouterProfile.assetOf(p)).use { it.readBytes().decodeToString() }

    /** Toutes les combinaisons des trois preferences, soit 27 par discipline. */
    private val toutes = WayPref.entries.flatMap { w ->
        HillPref.entries.flatMap { h -> SurfacePref.entries.map { s -> RoutingPrefs(w, h, s) } }
    }

    @Test fun `les cinq profils sont livres`() {
        RoutingProfile.entries.forEach { p ->
            assertTrue("profil absent des assets : ${BrouterProfile.assetOf(p)}", raw(p).isNotEmpty())
        }
    }

    /**
     * LE garde-fou : chaque variable que la table pretend regler existe dans le profil qui la recoit.
     *
     * C'est ici que se rattrape la derive d'un profil amont, et c'est la contrepartie assumee du choix de
     * reprendre ces profils tels quels plutot que de les ecrire.
     */
    @Test fun `chaque variable reglee existe dans le profil qui la recoit`() {
        RoutingProfile.entries.forEach { p ->
            val texte = raw(p)
            toutes.flatMap { BrouterProfile.tuningOf(p, it).keys }.distinct().forEach { nom ->
                val declaree = Regex("""^\s*assign\s+${Regex.escape(nom)}\s*(?:=|\s)""", RegexOption.MULTILINE)
                assertTrue("$p : $nom absent de ${BrouterProfile.assetOf(p)}", declaree.containsMatchIn(texte))
            }
        }
    }

    /**
     * Et apres reglage, la ligne porte REELLEMENT la valeur demandee.
     *
     * Le test precedent verifie que la variable existe, celui-ci qu'on l'a bien ecrite : une declaration
     * commentee, ou une seconde declaration plus bas qui reprend la main, passerait le premier et pas
     * celui-la. On ne compare pas les deux textes - ecrire la valeur que le profil portait deja est un
     * reglage legitime, qui ne change rien par construction (le velo de route evite les cotes d'office).
     */
    @Test fun `apres reglage, la ligne porte la valeur demandee`() {
        RoutingProfile.entries.forEach { p ->
            toutes.forEach { prefs ->
                val texte = BrouterProfile.tune(raw(p), p, prefs)
                BrouterProfile.tuningOf(p, prefs).forEach { (nom, valeur) ->
                    val pose = Regex("""^\s*assign\s+${Regex.escape(nom)}\s*(?:=\s*)?${Regex.escape(valeur)}(\s|$)""",
                        RegexOption.MULTILINE)
                    assertTrue("$p / $prefs : $nom n'a pas recu $valeur", pose.containsMatchIn(texte))
                }
            }
        }
    }

    /** La position centrale laisse le profil mot pour mot tel que ses auteurs l'ont ecrit. */
    @Test fun `sans preference, le profil part intact`() {
        RoutingProfile.entries.forEach { p ->
            val texte = raw(p)
            assertTrue("$p", BrouterProfile.tune(texte, p, RoutingPrefs.Balanced) == texte)
        }
    }

    /** Les profils sont repris tels quels, licence MIT : l'entete qui le dit doit rester en tete. */
    @Test fun `chaque profil porte son attribution`() {
        RoutingProfile.entries.forEach { p ->
            val debut = raw(p).lineSequence().take(4).joinToString(" ")
            assertTrue("$p : attribution absente", "MIT" in debut && "brouter" in debut)
        }
    }
}
