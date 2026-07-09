package com.pigfarmerjc.galleryplayer

import android.net.Uri
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pigfarmerjc.galleryplayer.core.player.api.DecoderMode
import com.pigfarmerjc.galleryplayer.core.player.api.PlaybackState
import com.pigfarmerjc.galleryplayer.core.player.api.VideoOutputHost
import com.pigfarmerjc.galleryplayer.player.libvlc.LibVlcPlaybackEngine
import com.pigfarmerjc.galleryplayer.player.libvlc.LibVlcVideoOutputHostFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

// derived test configuration for headless environments
class HeadlessLibVlcPlaybackEngine(context: android.content.Context) : LibVlcPlaybackEngine(context, isTestNoVideoMode = true)

@RunWith(AndroidJUnit4::class)
class PlaybackIntegrationTest {

    private suspend fun waitForState(
        engine: LibVlcPlaybackEngine, 
        expectedState: PlaybackState, 
        timeoutMs: Long = 15000
    ) {
        val startTime = System.currentTimeMillis()
        while (engine.playbackState.value != expectedState) {
            val elapsed = System.currentTimeMillis() - startTime
            Log.i("PlaybackIntegrationTest", "Waiting for state $expectedState (elapsed: ${elapsed}ms). Current state: ${engine.playbackState.value}")
            if (elapsed > timeoutMs) {
                throw AssertionError("Timed out waiting for state $expectedState. Current state: ${engine.playbackState.value}")
            }
            delay(100)
        }
        Log.i("PlaybackIntegrationTest", "Successfully transitioned to state: $expectedState")
    }

    @Test
    fun testRealVideoPlayback() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val testContext = instrumentation.context // contains test assets
        val appTargetContext = instrumentation.targetContext // app under test
        
        val targetFile = File(appTargetContext.filesDir, "sample_bbb.mp4")
        
        // Copy asset to app's files directory to bypass permission locks
        testContext.assets.open("sample_bbb.mp4").use { inputStream ->
            targetFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        
        assertTrue("Test file must exist in app private files directory", targetFile.exists())
        assertTrue("Test file must be readable", targetFile.canRead())

        // Use the headless derived configuration
        val engine = HeadlessLibVlcPlaybackEngine(appTargetContext)
        val uri = Uri.fromFile(targetFile)

        // Set to software decoding for reliable headless execution
        engine.setDecoderMode(DecoderMode.SOFTWARE_ONLY)

        runBlocking {
            // 1. 成功打开 URI
            engine.open(uri)
            
            // 2. 状态进入 Playing
            waitForState(engine, PlaybackState.Playing)

            // 3. durationMs 大于 0
            val duration = engine.durationMs.value
            assertTrue("Duration should be > 0, but was $duration", duration > 0)

            // 4. positionMs 连续增加至少 5 秒
            val posStart = engine.positionMs.value
            delay(5500)
            val posEnd = engine.positionMs.value
            assertTrue("Position should have progressed by at least 4.5s (start: $posStart, end: $posEnd)", (posEnd - posStart) >= 4500)

            // 5. 暂停后 positionMs 基本停止
            engine.pause()
            waitForState(engine, PlaybackState.Paused)
            
            val posPaused = engine.positionMs.value
            delay(2000)
            val posAfterPauseDelay = engine.positionMs.value
            assertTrue("Position should not progress when paused (paused: $posPaused, after delay: $posAfterPauseDelay)", Math.abs(posAfterPauseDelay - posPaused) < 100)

            // 6. 继续播放后 positionMs 恢复增加
            engine.play()
            waitForState(engine, PlaybackState.Playing)
            
            val posResumedStart = engine.positionMs.value
            delay(2000)
            val posResumedEnd = engine.positionMs.value
            assertTrue("Position should increase after resuming (resumedStart: $posResumedStart, resumedEnd: $posResumedEnd)", posResumedEnd > posResumedStart)

            // 7. seek 到 50% 后位置正确
            val seekTarget = duration / 2
            engine.seekTo(seekTarget)
            delay(1500) // Allow seek to catch up
            val posAfterSeek = engine.positionMs.value
            assertTrue("Position after seek ($posAfterSeek) should be close to target ($seekTarget)", Math.abs(posAfterSeek - seekTarget) < 3000)

            // 8. 2.0x 播放时进度增长速度发生变化 (先 seek 回 1s 保证有充足播放时间 headroom)
            engine.seekTo(1000)
            delay(1500)
            
            engine.setSpeed(2.0f)
            delay(1000)
            val speedStartPos = engine.positionMs.value
            delay(2000)
            val speedEndPos = engine.positionMs.value
            val progressAt2x = speedEndPos - speedStartPos
            // 2x speed should progress > 3s of video in 2s of real time
            assertTrue("At 2.0x speed, 2s real time should progress > 3s of video, but progressed $progressAt2x ms", progressAt2x > 3000)

            // 9. 切换回 1.0x 正常 (再次 seek 回 1s 避免触及视频尾部 EOF)
            engine.seekTo(1000)
            delay(1500)
            
            engine.setSpeed(1.0f)
            delay(1000)
            val normalStartPos = engine.positionMs.value
            delay(2000)
            val normalEndPos = engine.positionMs.value
            val progressAt1x = normalEndPos - normalStartPos
            assertTrue("At 1.0x speed, 2s real time should progress ~2s of video, but progressed $progressAt1x ms", progressAt1x in 1500..2500)

            // 10. stop 后状态正确
            engine.stop()
            waitForState(engine, PlaybackState.Stopped)

            // 11. 再次打开同一 URI 正常
            engine.open(uri)
            waitForState(engine, PlaybackState.Playing)

            // Clean up
            engine.release()
            
            // Remove copied file to clean up storage
            targetFile.delete()
        }
    }

    @Test
    fun testRealVideoPlaybackRendering() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val testContext = instrumentation.context
        val appTargetContext = instrumentation.targetContext
        
        val targetFile = File(appTargetContext.filesDir, "sample_bbb_rendering.mp4")
        testContext.assets.open("sample_bbb.mp4").use { inputStream ->
            targetFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        
        assertTrue("Test file must exist in app private files directory", targetFile.exists())
        val uri = Uri.fromFile(targetFile)
        
        val decoderModes = listOf(DecoderMode.AUTO, DecoderMode.SOFTWARE_ONLY)
        
        for (mode in decoderModes) {
            Log.i("PlaybackIntegrationTest", "Verifying real video rendering for mode: $mode")
            
            var engine: LibVlcPlaybackEngine? = null
            var videoHost: VideoOutputHost? = null
            
            scenario.onActivity { activity ->
                val eng = LibVlcPlaybackEngine(appTargetContext)
                eng.setDecoderMode(mode)
                engine = eng
                
                val host = LibVlcVideoOutputHostFactory().create(appTargetContext)
                eng.attachVideoOutput(host)
                videoHost = host
                
                // Add the VLCVideoLayout view directly to the running Activity's view hierarchy
                activity.addContentView(
                    host.view,
                    android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        400
                    )
                )
            }
            
            val activeEngine = engine!!
            val activeHost = videoHost!!
            
            runBlocking {
                activeEngine.open(uri)
                waitForState(activeEngine, PlaybackState.Playing)
                
                val duration = activeEngine.durationMs.value
                assertTrue("Duration must be > 0", duration > 0)
                
                // Play for 3 seconds of real video output verification
                val startPos = activeEngine.positionMs.value
                delay(3000)
                val endPos = activeEngine.positionMs.value
                assertTrue("Playback progress should advance under mode $mode (start: $startPos, end: $endPos)", endPos > startPos)
                
                scenario.onActivity { activity ->
                    (activeHost.view.parent as? android.view.ViewGroup)?.removeView(activeHost.view)
                    activeEngine.detachVideoOutput()
                    activeHost.dispose()
                    activeEngine.release()
                }
            }
        }
        
        targetFile.delete()
        scenario.close()
    }
}
