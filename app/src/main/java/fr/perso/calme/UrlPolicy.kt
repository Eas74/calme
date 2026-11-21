package fr.perso.calme

import java.net.URI

/**
 * Toute la politique réseau/navigation est ici : fonctions pures, testées en JVM.
 * Principe : tout ce qui n'est pas explicitement autorisé est refusé (fail closed).
 */
object UrlPolicy {
    const val HOME_FOLLOWING = "https://www.instagram.com/?variant=following"
    const val INBOX = "https://www.instagram.com/direct/inbox/"

    /** Domaines autorisés (domaine exact ou sous-domaine). */
    private val ALLOWED_DOMAINS = listOf("instagram.com", "cdninstagram.com", "fbcdn.net")

    /** Chemins de pages interdits (Explorer, Reels). Correspondance par segment entier. */
    private val BLOCKED_PATHS = listOf("/explore", "/reels", "/reel")

    /** Chemins où le scroll reste libre (conversations, connexion). */
    private val FREE_SCROLL_PATHS = listOf("/direct", "/accounts")

    /** Schémas sans réseau, nécessaires au fonctionnement de la page. */
    private val LOCAL_SCHEMES = setOf("data", "blob")

    private fun parse(url: String): URI? = try { URI(url) } catch (_: Exception) { null }

    private fun pathOf(u: URI): String = (u.normalize().path ?: "").lowercase().ifEmpty { "/" }

    private fun matches(path: String, prefix: String) = path == prefix || path.startsWith("$prefix/")

    fun isHostAllowed(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return ALLOWED_DOMAINS.any { h == it || h.endsWith(".$it") }
    }

    /** Sous-ressources (images, scripts, XHR…) : HTTPS + domaine autorisé, ou data:/blob:. */
    fun isRequestAllowed(url: String): Boolean {
        val u = parse(url) ?: return false
        val scheme = u.scheme?.lowercase() ?: return false
        if (scheme in LOCAL_SCHEMES) return true
        return scheme == "https" && isHostAllowed(u.host)
    }

    /** Navigation de page : comme ci-dessus, sans data/blob, et hors chemins interdits. */
    fun isPageAllowed(url: String): Boolean {
        val u = parse(url) ?: return false
        if (u.scheme?.lowercase() != "https" || !isHostAllowed(u.host)) return false
        val path = pathOf(u)
        return BLOCKED_PATHS.none { matches(path, it) }
    }

    fun isScrollLocked(url: String): Boolean {
        val u = parse(url) ?: return true
        val path = pathOf(u)
        return FREE_SCROLL_PATHS.none { matches(path, it) }
    }

    /** Accueil sans le filtre « Abonnements » (= flux algorithmique) : à rediriger. */
    fun isUnfilteredHome(url: String): Boolean {
        val u = parse(url) ?: return false
        return pathOf(u) == "/" && u.rawQuery?.contains("variant=following") != true
    }
}
