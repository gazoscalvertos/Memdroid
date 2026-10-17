package com.memdroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/** Medium width and up: unfolded foldables, tablets, phones in landscape. Uses a navigation rail. */
@Composable
fun isMediumWidth(): Boolean = LocalConfiguration.current.screenWidthDp >= 600

/** Expanded width: tablets and wide foldables. Screens split into two panes. */
@Composable
fun isExpandedWidth(): Boolean = LocalConfiguration.current.screenWidthDp >= 840
