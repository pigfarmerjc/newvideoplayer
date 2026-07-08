package com.pigfarmerjc.galleryplayer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testActivityLaunchesAndControlsAreDisplayed() {
        // Verify Title is displayed
        composeTestRule.onNodeWithText("GalleryPlayer Phase 0.5 Test Host").assertIsDisplayed()

        // Verify select document button is displayed
        composeTestRule.onNodeWithText("Select Document").assertIsDisplayed()

        // Verify play button is displayed
        composeTestRule.onNodeWithText("Play").assertIsDisplayed()

        // Verify diagnostics header is displayed
        composeTestRule.onNodeWithText("Diagnostics Details").assertIsDisplayed()
    }
}
