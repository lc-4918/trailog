package fr.lc4918.trailog.ui.location

import android.content.Context
import android.location.LocationManager
import android.os.SystemClock
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.test.core.app.ApplicationProvider
import fr.lc4918.trailog.location.LocationHub
import fr.lc4918.trailog.ui.components.MapController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * La derniere position mesuree, gardee sur la carte quand le suivi s'arrete.
 *
 * **Ce que ce fichier testait avant, et pourquoi ce n'est plus vrai.** Le repere passait au gris quand le
 * capteur n'avait plus rien donne depuis trente secondes : l'idee etait qu'un repere fige est visuellement
 * identique a un repere juste. Mais un cycliste s'arrete - un col, un pique-nique, une reparation - et le
 * repere virait au gris alors qu'il disait parfaitement vrai. La couleur annoncait un doute qui n'existait
 * pas, sur ce qui est le plus regarde de la carte.
 *
 * Le gris sert desormais a dire quelque chose de SUR : ce point n'est plus suivi. Le suivi arrete, la
 * derniere position reste la ou le capteur l'a laissee - si le reglage le demande, et il est eteint par
 * defaut.
 *
 * ---
 *
 * **Deux precautions, chacune payee d'une suite de tests bloquee pendant treize minutes.** Elles valent
 * pour cette classe telle qu'elle est, et non pour ce qu'elle testait :
 *
 * 1. **Le temps se simule sur la MESURE, jamais sur l'horloge.** Une premiere version avancait l'horloge
 *    du bac a sable avec `ShadowSystemClock.advanceBy`. Elle est partagee avec toutes les classes qui
 *    suivent dans la meme JVM.
 *
 * 2. **Toute ecriture d'etat passe par [modifie].** Ce test pilote des `mutableStateOf` - les siens, et
 *    ceux que porte [LocationControls] - hors de toute composition. Ecrites nues, ces modifications
 *    restent en attente dans le snapshot global, lui aussi partage : tout test d'interface qui suit y lit
 *    "il reste du travail a faire", et son attente d'inactivite tourne jusqu'a expirer au bout de soixante
 *    secondes. Treize tests y sont passes. Un snapshot applique les publie et ne laisse rien derriere.
 *
 * Les deux fautes ont le meme visage : cette classe ne compose rien, mais elle touche a des etats que
 * Compose partage a l'echelle du processus. Ce qu'on y ecrit doit etre referme derriere soi.
 */
@RunWith(RobolectricTestRunner::class)
class LastFixShownTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val trackingState = mutableStateOf(true)
    private val fixState = mutableStateOf<LocationHub.Fix?>(null)
    private val controls = LocationControls(
        ctx = ctx,
        controller = MapController(),
        locationManager = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager,
        trackingState = trackingState,
        fixState = fixState,
    )

    /** Toute mutation d'etat Compose passe par ici : le snapshot publie ce qu'on y ecrit (cf. l'en-tete). */
    private fun <R> modifie(bloc: () -> R): R = Snapshot.withMutableSnapshot(bloc)

    /** Le capteur repond : une mesure de plus, recue a l'instant. */
    private fun mesureRecue(lat: Double = 45.18, lon: Double = 5.72) = modifie {
        val fix = LocationHub.Fix(
            lat = lat, lon = lon, accuracyM = 5f, bearingDeg = null, speedMps = null, altitudeM = null,
            timeMs = 1_700_000_000_000L, receivedAtMs = SystemClock.elapsedRealtime(),
        )
        fixState.value = fix
        controls.onFix(fix)
        fix
    }

    /** L'utilisateur coupe le suivi. */
    private fun suiviArrete() = modifie {
        trackingState.value = false
        controls.onTrackingStopped()
    }

    /** Le defaut : la carte se rend nue a qui vient de couper le suivi, comme elle l'a toujours fait. */
    @Test fun `le reglage eteint, rien ne reste`() {
        controls.showLastFix = false
        mesureRecue()
        suiviArrete()
        assertNull(controls.lastFixShown)
    }

    /**
     * Le reglage allume : la derniere position reste, et c'est bien la DERNIERE - celle du moment ou l'on
     * a coupe, non la premiere de la sortie.
     */
    @Test fun `le reglage allume, la derniere position reste`() {
        controls.showLastFix = true
        mesureRecue(lat = 45.10, lon = 5.70)
        val derniere = mesureRecue(lat = 45.18, lon = 5.72)
        suiviArrete()
        assertEquals(derniere, controls.lastFixShown)
    }

    /** Aucune mesure recue de toute la session : il n'y a rien a figer, et le reglage n'y change rien. */
    @Test fun `sans mesure, le reglage n'a rien a garder`() {
        controls.showLastFix = true
        suiviArrete()
        assertNull(controls.lastFixShown)
    }

    /**
     * Le suivi repart : le point fige s'efface a la premiere mesure.
     *
     * C'est ce qui rend sa couleur honnete - le gris dit "ce point n'est plus suivi", et il le dirait a
     * tort sur un repere qui vient de recommencer a bouger.
     */
    @Test fun `le suivi repris efface le point fige`() {
        controls.showLastFix = true
        mesureRecue()
        suiviArrete()
        assertEquals(fixState.value, controls.lastFixShown)
        modifie { trackingState.value = true }
        mesureRecue(lat = 45.20, lon = 5.75)
        assertNull(controls.lastFixShown)
    }

    /** Couper deux fois de suite ne perd pas la position : le second arret la repose telle quelle. */
    @Test fun `un second arret garde la meme position`() {
        controls.showLastFix = true
        val derniere = mesureRecue()
        suiviArrete()
        suiviArrete()
        assertEquals(derniere, controls.lastFixShown)
    }
}
