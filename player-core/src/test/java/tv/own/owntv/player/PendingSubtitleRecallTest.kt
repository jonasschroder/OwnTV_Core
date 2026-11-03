package tv.own.owntv.player

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.player.PlaybackPrefsStore

class PendingSubtitleRecallTest {
    private fun track(id: Int, lang: String?, selected: Boolean = false, image: Boolean = false) =
        TrackOption(label = "", mpvId = id, selected = selected, lang = lang, image = image)

    @Test fun lateRememberedManualLanguageWinsOverSwedishPreference() {
        val recall = PendingSubtitleRecall().apply { load("eng") }
        assertEquals(TrackMemory.SubtitleAction.None, recall.action(listOf(track(1, "sv", true)), false))
        assertFalse(recall.complete)
        assertEquals(TrackMemory.SubtitleAction.Select(track(9, "en-US")),
            recall.action(listOf(track(1, "sv", true), track(9, "en-US")), false))
        assertTrue(recall.complete)
    }

    @Test fun rememberedSwedishSurvivesEpisodeIdsAndOrdering() {
        for ((id, tracks) in listOf(7 to listOf(track(7, "swe")), 91 to listOf(track(2, "en"), track(91, "sv-SE")))) {
            val recall = PendingSubtitleRecall().apply { load("sv") }
            val action = recall.action(tracks, false) as TrackMemory.SubtitleAction.Select
            assertEquals(id, action.track.mpvId)
        }
    }

    @Test fun offReachesEngineBeforeTracksAndRemainsFinal() {
        val recall = PendingSubtitleRecall().apply { load(PlaybackPrefsStore.SUBTITLES_OFF) }
        assertEquals(TrackMemory.SubtitleAction.Off, recall.action(emptyList(), false))
        assertTrue(recall.explicitlyOff)
        assertEquals(TrackMemory.SubtitleAction.None, recall.action(listOf(track(1, "sv", true)), true))
    }

    @Test fun manualOffOrLanguageCancelsALateStoreAnswer() {
        for (manual in listOf(null, "fra", PlaybackPrefsStore.SUBTITLES_OFF)) {
            val recall = PendingSubtitleRecall().apply { manual(manual); load("swe") }
            assertEquals(manual, recall.choice)
            assertEquals(TrackMemory.SubtitleAction.None, recall.action(listOf(track(1, "sv")), true))
        }
    }

    @Test fun alreadySelectedAliasCompletesWithoutReselecting() {
        val recall = PendingSubtitleRecall().apply { load("swe") }
        assertEquals(TrackMemory.SubtitleAction.None, recall.action(listOf(track(1, "SV_se", true)), true))
        assertTrue(recall.complete)
    }

    @Test fun mpvDoesNotTriggerBitmapHandoverButExoCanRecallBitmap() {
        val recall = PendingSubtitleRecall().apply { load("swe") }
        val bitmap = track(3, "sv", image = true)
        assertEquals(TrackMemory.SubtitleAction.None, recall.action(listOf(bitmap), false))
        assertFalse(recall.complete)
        assertEquals(TrackMemory.SubtitleAction.Select(bitmap), recall.action(listOf(bitmap), true))
    }

    @Test fun absentChoiceAndSeparateOwnersDoNotLeakRecall() {
        val first = PendingSubtitleRecall().apply { load("swe") }
        val second = PendingSubtitleRecall().apply { load(null) }
        assertEquals(TrackMemory.SubtitleAction.None, second.action(listOf(track(1, "sv")), false))
        assertTrue(second.complete)
        assertFalse(first.complete)
        assertTrue(first.action(listOf(track(2, "sv")), false) is TrackMemory.SubtitleAction.Select)
    }
}
