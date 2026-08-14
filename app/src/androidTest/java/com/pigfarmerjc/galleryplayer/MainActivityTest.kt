package com.pigfarmerjc.galleryplayer

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun activityShowsMediaLibraryOrFirstRunPermissionGate() {
        composeTestRule.waitForIdle()

        val permissionGate = composeTestRule
            .onAllNodesWithText("允许访问本地媒体")
            .fetchSemanticsNodes()
        val videoDestination = composeTestRule
            .onAllNodesWithText("视频")
            .fetchSemanticsNodes()

        assertTrue(
            "App should show either its first-run permission gate or the video library",
            permissionGate.isNotEmpty() || videoDestination.isNotEmpty()
        )
    }
}
