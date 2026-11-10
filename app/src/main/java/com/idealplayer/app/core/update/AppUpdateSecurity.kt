package com.idealplayer.app.core.update

import okhttp3.HttpUrl

/**
 * Update endpoints are deliberately stricter than the playlist client: self-hosted update
 * downloads must remain HTTPS and must never carry credentials in a URL, including after a
 * redirect. The app still permits cleartext only for user-supplied IPTV sources.
 */
internal fun HttpUrl.isTrustedUpdateUrl(): Boolean =
    isHttps && host.isNotBlank() && username.isBlank() && password.isBlank()

/** Resolves a redirect relative to [current] and accepts only another trusted HTTPS URL. */
internal fun resolveTrustedUpdateRedirect(current: HttpUrl, location: String?): HttpUrl? =
    location
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.let(current::resolve)
        ?.takeIf(HttpUrl::isTrustedUpdateUrl)

/**
 * Verifies the archive's current signer set against the installed application.
 *
 * A one-signer certificate rotation is allowed only when Android's verified archive lineage
 * contains the installed signer. Multi-signer APKs intentionally require an exact set match;
 * Android does not support rotating one member of a multi-signer identity independently.
 */
internal fun isTrustedUpdateSigner(
    installedCurrentSigners: Set<String>,
    archiveCurrentSigners: Set<String>,
    archiveSignerLineage: Set<String>
): Boolean {
    if (installedCurrentSigners.isEmpty() || archiveCurrentSigners.isEmpty()) return false
    if (installedCurrentSigners == archiveCurrentSigners) return true

    return installedCurrentSigners.size == 1 &&
        archiveCurrentSigners.size == 1 &&
        installedCurrentSigners.single() in archiveSignerLineage
}
