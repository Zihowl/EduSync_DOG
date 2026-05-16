package dev.zihowl.dog.utils

/**
 * Convierte una URL de servidor en un host limpio y legible para mostrarlo en
 * la UI. Ej.: `https://www.app.ceti.mx/graphql` -> `app.ceti.mx`.
 */
object ServerUrlFormatter {

    /** Devuelve solo el host (sin esquema, sin `www.`, sin puerto ni path). */
    fun displayHost(url: String?): String {
        if (url.isNullOrBlank()) return ""
        var host = url.trim()
        host = host.removePrefix("https://").removePrefix("http://")
        // Recorta cualquier path, query o puerto.
        host = host.substringBefore('/').substringBefore('?').substringBefore(':')
        host = host.removePrefix("www.")
        return host.trimEnd('.')
    }
}
