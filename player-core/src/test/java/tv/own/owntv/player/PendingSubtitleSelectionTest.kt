package tv.own.owntv.player

import org.junit.Assert.*
import org.junit.Test

class PendingSubtitleSelectionTest {
    @Test fun swedishAfterAnInitiallyNonSwedishListIsSelected() {
        val selection = PendingSubtitleSelection()
        assertNull(selection.resolve(listOf("eng"), "swe", -1, null))
        assertFalse(selection.applied)
        assertEquals(1, selection.resolve(listOf("eng", "sv-SE"), "swe", -1, null))
    }

    @Test fun aliasesWorkImmediatelyAndAfterEmptyDiscovery() {
        for (tag in listOf("sv", "swe", "sv-SE", "SV_se", "swe-FI")) {
            val selection = PendingSubtitleSelection()
            assertNull(selection.resolve(emptyList(), "swe", -1, null))
            assertEquals(0, selection.resolve(listOf(tag, "en"), "swe", -1, null))
            assertTrue(selection.applied)
        }
    }

    @Test fun newEpisodeDoesNotReuseAnOldOrdinalOrCompletion() {
        val selection = PendingSubtitleSelection()
        assertEquals(0, selection.resolve(listOf("sv", "en"), "swe", -1, null))
        selection.reset()
        assertEquals(1, selection.resolve(listOf("en", "sv-SE"), "swe", -1, null))
    }

    @Test fun manualHandoverUsesLanguageAliasesWhenTrackOrderChanges() {
        assertEquals(1, PendingSubtitleSelection().resolve(listOf("en", "sv-SE"), "eng", 0, "swe"))
    }

    @Test fun missingSwedishNeverChoosesAnUnrelatedOrUnknownTrack() {
        val selection = PendingSubtitleSelection()
        for (tracks in listOf(listOf("en"), listOf(null, "und", "fr"))) {
            assertNull(selection.resolve(tracks, "swe", -1, null))
            assertFalse(selection.applied)
        }
        assertNull(selection.resolve(listOf("en"), "swe", 0, "sv"))
        assertFalse(selection.applied)
    }

    @Test fun manualChoiceRemainsFinalAcrossRepeatedTrackAndPreferenceUpdates() {
        val selection = PendingSubtitleSelection()
        assertEquals(0, selection.resolve(listOf("en", "sv"), "swe", 0, "eng"))
        assertNull(selection.resolve(listOf("sv", "en"), "swe", -1, null))
        assertTrue(selection.applied)
    }

    @Test fun explicitOffOrSelectedExternalCompletesPendingAutomaticSelection() {
        val selection = PendingSubtitleSelection()
        selection.finish()
        assertNull(selection.resolve(listOf("sv"), "swe", -1, null))
        assertTrue(selection.applied)
    }

    @Test fun latePreferenceCanSatisfyPendingChoice() {
        val selection = PendingSubtitleSelection()
        assertNull(selection.resolve(listOf("sv"), "", -1, null))
        assertEquals(0, selection.resolve(listOf("sv"), "swe", -1, null))
    }
}
