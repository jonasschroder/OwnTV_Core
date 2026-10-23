package tv.own.owntv.player

/** One prepare's pending embedded-subtitle choice; external labels stay engine-owned. */
internal class PendingSubtitleSelection {
    var applied = false
        private set

    fun reset() { applied = false }
    fun finish() { applied = true }

    fun resolve(languages: List<String?>, preferred: String, ordinal: Int, language: String?): Int? {
        if (applied || languages.isEmpty()) return null
        val target = if (ordinal < 0 && language == null) {
            languages.indexOfFirst { subtitleLanguageMatches(preferred, it) }.takeIf { it >= 0 }
        } else {
            ordinal.takeIf { it in languages.indices && (language == null || subtitleLanguageMatches(language, languages[it])) }
                ?: language?.let { wanted -> languages.indexOfFirst { subtitleLanguageMatches(wanted, it) }.takeIf { it >= 0 } }
                ?: languages.indices.singleOrNull().takeIf { language == null }
        }
        // A non-matching list is not final: manifests may discover the wanted language later.
        applied = target != null
        return target
    }
}
