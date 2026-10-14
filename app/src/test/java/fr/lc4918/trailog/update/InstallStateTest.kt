package fr.lc4918.trailog.update

import android.content.pm.PackageInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Ce que l'appli retient du rapport du systeme sur la session d'installation. */
class InstallStateTest {

    @Test fun `une demande de confirmation met l'appli en attente de l'utilisateur`() {
        assertEquals(InstallState.WaitingUser, InstallState.fromStatus(PackageInstaller.STATUS_PENDING_USER_ACTION, null))
    }

    @Test fun `un succes ne change rien a afficher`() {
        // Le systeme remplace l'appli et la ferme : rien a montrer.
        assertNull(InstallState.fromStatus(PackageInstaller.STATUS_SUCCESS, null))
    }

    @Test fun `un refus de l'utilisateur n'est pas un echec`() {
        assertEquals(InstallState.Idle, InstallState.fromStatus(PackageInstaller.STATUS_FAILURE_ABORTED, "INSTALL_FAILED_ABORTED"))
    }

    @Test fun `les autres statuts sont des echecs qui gardent le message du systeme`() {
        listOf(
            PackageInstaller.STATUS_FAILURE, PackageInstaller.STATUS_FAILURE_BLOCKED, PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_STORAGE, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
        ).forEach { status ->
            assertEquals(InstallState.Failed("raison"), InstallState.fromStatus(status, "raison"))
        }
    }

    @Test fun `un echec sans message reste un echec`() {
        assertEquals(InstallState.Failed(null), InstallState.fromStatus(PackageInstaller.STATUS_FAILURE_STORAGE, null))
    }
}

/** Les deux regles de la progression : ce que veut dire le retour dans l'appli, et que faire d'une installation figee. */
class InstallProgressRulesTest {

    @Test fun `de retour dans l'appli apres la confirmation, le systeme installe`() {
        assertEquals(InstallState.Installing, InstallState.afterReturn(InstallState.WaitingUser))
    }

    @Test fun `le retour dans l'appli ne change aucun autre etat`() {
        listOf(InstallState.Idle, InstallState.Downloading, InstallState.Staging, InstallState.Installing, InstallState.Failed("x"))
            .forEach { assertEquals(it, InstallState.afterReturn(it)) }
    }

    @Test fun `une installation figee se relance une fois puis on y renonce`() {
        assertEquals(StallAction.RETRY, UpdateInstaller.onStall(attempt = 0))
        assertEquals(StallAction.GIVE_UP, UpdateInstaller.onStall(attempt = 1))
        assertEquals(StallAction.GIVE_UP, UpdateInstaller.onStall(attempt = 5))
    }

    @Test fun `le nombre de relances se regle`() {
        assertEquals(StallAction.RETRY, UpdateInstaller.onStall(attempt = 1, maxRetries = 2))
        assertEquals(StallAction.GIVE_UP, UpdateInstaller.onStall(attempt = 2, maxRetries = 2))
    }
}

/** La part d'un telechargement, pour la barre de progression. */
class DownloadProgressTest {

    @Test fun `la part est le rapport des octets recus a la taille`() {
        assertEquals(0.25f, UpdateInstaller.progressFraction(25L, 100L)!!, 1e-6f)
        assertEquals(1f, UpdateInstaller.progressFraction(100L, 100L)!!, 1e-6f)
    }

    @Test fun `une taille inconnue ne donne aucune part`() {
        // DownloadManager rend -1 tant que le serveur n'a pas repondu.
        assertNull(UpdateInstaller.progressFraction(0L, -1L))
        assertNull(UpdateInstaller.progressFraction(10L, 0L))
    }

    @Test fun `la part reste entre 0 et 1`() {
        assertEquals(1f, UpdateInstaller.progressFraction(150L, 100L)!!, 1e-6f)
        assertEquals(0f, UpdateInstaller.progressFraction(-5L, 100L)!!, 1e-6f)
    }
}
