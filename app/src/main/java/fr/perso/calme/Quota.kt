package fr.perso.calme

/**
 * Plafond quotidien d'éléments vus. Logique pure (stockage et horloge injectés) donc testable.
 * Limite connue : effacer les données de l'appli ou changer la date du téléphone le remet à zéro.
 */
class Quota(
    private val store: Store,
    private val today: () -> String,
    val dailyLimit: Int,
) {
    interface Store {
        fun read(): String?
        fun write(value: String)
    }

    private fun state(): Pair<String, Int> {
        val parts = store.read()?.split("|")
        val day = parts?.getOrNull(0)
        val count = parts?.getOrNull(1)?.toIntOrNull() ?: 0
        return if (day == today()) day to count else today() to 0
    }

    val used: Int get() = state().second
    val remaining: Int get() = (dailyLimit - used).coerceAtLeast(0)

    /** @return false si la limite du jour est déjà atteinte. */
    fun tryConsume(): Boolean {
        val (day, count) = state()
        if (count >= dailyLimit) return false
        store.write("$day|${count + 1}")
        return true
    }
}
