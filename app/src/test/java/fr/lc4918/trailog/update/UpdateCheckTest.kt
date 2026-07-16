package fr.lc4918.trailog.update

import fr.lc4918.trailog.BuildConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Verification des mises a jour. Le telechargement et l'installateur relevent de l'instrumentation
 *  (DownloadManager, PackageInstaller) : ici on couvre la decision, qui est la logique. */
@RunWith(RobolectricTestRunner::class)
class UpdateCheckTest {
    /** En debug, la verification est inerte : ce build a son propre applicationId et une autre
     *  signature, l'APK de release ne peut pas le remplacer. */
    @Test fun `la verification est inerte en debug`() = runTest {
        assertEquals(BuildConfig.DEBUG, !UpdateManager.isSupported)
        if (BuildConfig.DEBUG) assertEquals(UpdateCheck.UpToDate, UpdateManager.check())
    }

    @Test fun `les trois issues d'une verification sont distinctes`() {
        val r = ReleaseInfo("1.0.0", 10000, "2026-01-01", "https://x/a.apk")
        assertTrue(UpdateCheck.Available(r) != UpdateCheck.UpToDate)
        assertTrue(UpdateCheck.UpToDate != UpdateCheck.Failed)
        assertEquals(UpdateCheck.Available(r), UpdateCheck.Available(r))
    }
}
