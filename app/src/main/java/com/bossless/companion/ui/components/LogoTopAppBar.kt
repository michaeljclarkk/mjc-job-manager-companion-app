package com.bossless.companion.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Reusable TopAppBar with title on left, absolutely centered business logo, and actions on right.
 * Layout: |Title|  |Centered Logo|  |Actions|
 * 
 * Uses Box overlay to ensure logo is truly centered regardless of title/action widths.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogoTopAppBar(
    fallbackTitle: String,
    logoUrl: String?,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    Box {
        // Base TopAppBar with title and actions
        TopAppBar(
            navigationIcon = navigationIcon,
            title = { Text(fallbackTitle) },
            actions = actions
        )
        
        // Absolutely centered logo overlay
        if (logoUrl != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp), // Standard TopAppBar height
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(logoUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Business Logo",
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

/**
 * Variant with CenterAlignedTopAppBar for better centering
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogoCenterAlignedTopAppBar(
    fallbackTitle: String,
    logoUrl: String?,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    CenterAlignedTopAppBar(
        title = {
            if (logoUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(logoUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Business Logo",
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(fallbackTitle)
            }
        },
        navigationIcon = navigationIcon,
        actions = actions
    )
}
