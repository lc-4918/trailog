package fr.lc4918.trailog.ui.settings

/**
 * Les sept appuis sur le titre des reglages qui allument ou eteignent le mode expert des reglages.
 *
 * Le geste des options de developpement d'Android : un reglage cache ne doit pas s'ouvrir par megarde, et
 * sept appuis RAPPROCHES ne se font pas sans le vouloir. Un appui plus d'une seconde et demie apres le
 * precedent repart de un.
 */
class ExpertTaps(private val needed: Int = TAPS, private val maxGapMs: Long = MAX_GAP_MS) {

    private var count = 0
    private var lastMs = Long.MIN_VALUE

    /** Un appui a l'instant [nowMs] : rend vrai au septieme, et repart alors de zero. */
    fun tap(nowMs: Long): Boolean {
        count = if (lastMs != Long.MIN_VALUE && nowMs - lastMs <= maxGapMs) count + 1 else 1
        lastMs = nowMs
        if (count < needed) return false
        count = 0
        lastMs = Long.MIN_VALUE
        return true
    }

    companion object {
        const val TAPS = 7
        const val MAX_GAP_MS = 1_500L
    }
}
