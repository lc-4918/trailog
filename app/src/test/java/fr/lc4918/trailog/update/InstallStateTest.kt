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
