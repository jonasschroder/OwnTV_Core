package tv.own.owntv.player

import android.content.ContextWrapper
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import java.lang.reflect.Proxy
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import tv.own.owntv.core.network.StreamingHttpClient
import tv.own.owntv.core.player.PlayerBudget

/** Exercises the engine's real track-change/selection path without a decoder, network or Android device. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ExoSubtitleEngineSelectionTest {
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }
    private class Fixture {
        var tracks = Tracks.EMPTY
        var parameters = TrackSelectionParameters.Builder().build()
        private val player = Proxy.newProxyInstance(ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java)) { _, method, args ->
            when (method.name) {
                "getTrackSelectionParameters" -> parameters
                "setTrackSelectionParameters" -> { parameters = args!![0] as TrackSelectionParameters; null }
                "getCurrentTracks" -> tracks
                else -> when (method.returnType) {
                    Boolean::class.javaPrimitiveType -> false
                    Int::class.javaPrimitiveType -> 0
                    Long::class.javaPrimitiveType -> 0L
                    Float::class.javaPrimitiveType -> 0f
                    else -> null
                }
            }
        } as ExoPlayer
        private val callbacks = Proxy.newProxyInstance(ExoSubtitleEngine.Callbacks::class.java.classLoader,
            arrayOf(ExoSubtitleEngine.Callbacks::class.java)) { _, _, _ -> null } as ExoSubtitleEngine.Callbacks
        val engine = ExoSubtitleEngine(ContextWrapper(null), StreamingHttpClient(OkHttpClient()),
            PlayerBudget("1MiB", "1MiB", "1", "1", true), callbacks, AudioDelayClock())
        private val listener: Player.Listener
        init {
            field("player").set(engine, player)
            listener = field("listener").get(engine) as Player.Listener
            engine.prefSubLang = "swe"
        }
        private fun field(name: String) = ExoSubtitleEngine::class.java.getDeclaredField(name).apply { isAccessible = true }
        fun discover(vararg languages: String?) = discoverLabels(*languages.map { it to null }.toTypedArray())
        fun discoverLabels(vararg languages: Pair<String?, String?>) {
            tracks = Tracks(languages.mapIndexed { index, (language, label) ->
                Tracks.Group(TrackGroup("group-$index", Format.Builder().setId("sub-$index")
                    .setSampleMimeType("application/x-subrip").setLanguage(language).setLabel(label).build()),
                    false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false))
            })
            listener.onTracksChanged(tracks)
        }
        fun pendingExternal(label: String) { field("pendingExternalLabel").set(engine, label) }
        fun selectedLanguage(): String? = parameters.overrides.values.singleOrNull()
            ?.let { it.mediaTrackGroup.getFormat(it.trackIndices.single()).language }
        fun assertOff() = assertTrue(parameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
    }

    @Test fun realTrackCallbackRetriesNonSwedishThenSwedish() {
        val f = Fixture()
        f.discover("en")
        f.assertOff()
        f.discover("en", "sv-SE")
        assertEquals("sv-se", f.selectedLanguage())
        assertFalse(f.parameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
    }

    @Test fun manualOffBeforeDiscoveryCannotBeUndoneByTracksOrPreferenceEmission() {
        val f = Fixture()
        f.engine.disableTextTracks()
        f.discover("sv")
        f.engine.prefSubLang = "sv-SE"
        f.assertOff()
    }

    @Test fun manualEnglishWinsOverLateSwedish() {
        val f = Fixture()
        f.discover("en")
        f.engine.selectTextTrack(0, "eng")
        f.discover("en", "sv")
        f.engine.prefSubLang = "swe"
        assertEquals("en", f.selectedLanguage())
    }

    @Test fun pendingExternalIsNotReplacedByAutomaticSwedishAndOffCancelsIt() {
        val f = Fixture()
        f.pendingExternal("explicit-file")
        f.discover("sv")
        assertNull(f.selectedLanguage())
        f.engine.disableTextTracks()
        f.discover("sv")
        f.assertOff()
    }
    @Test fun explicitExternalLabelWaitsThenWinsOverSwedishPreference() {
        val f = Fixture()
        f.pendingExternal("explicit-file")
        f.discover("sv")
        assertNull(f.selectedLanguage())
        f.discoverLabels("sv" to null, "en" to "explicit-file")
        assertEquals("en", f.selectedLanguage())
        f.engine.prefSubLang = "swe"
        assertEquals("en", f.selectedLanguage())
    }
    @Test fun pinnedStartApiAndItsKotlinDefaultBridgeRemainAvailable() {
        val original = ExoSubtitleEngine::class.java.getDeclaredMethod("start",
            String::class.java, Long::class.javaPrimitiveType, android.view.Surface::class.java,
            String::class.java, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
            List::class.java, String::class.java, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
        assertEquals(Void.TYPE, original.returnType)
        assertTrue(ExoSubtitleEngine::class.java.declaredMethods.any {
            it.name == "start\$default" && it.parameterCount == 13
        })
    }
}
