package com.lumen.player.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.lumen.player.core.storage.LumenPaths
import com.lumen.player.core.util.hsl
import com.lumen.player.core.util.seededTone

val LocalPaths = staticCompositionLocalOf<LumenPaths> { error("LumenPaths not provided") }

@Composable
fun Placeholder(seed: String, shape: Shape, modifier: Modifier = Modifier, letterSize: TextUnit = 18.sp) {
    val colors = remember(seed) {
        val tone = seededTone(seed)
        listOf(Color(hsl(tone.hue, 0.45f, 0.30f)), Color(hsl((tone.hue + 40) % 360, 0.50f, 0.14f)))
    }
    Box(
        modifier.clip(shape).background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            seed.trim().firstOrNull()?.uppercase() ?: "♪",
            style = LumenType.display.copy(fontSize = letterSize),
            color = LumenColors.paper.copy(alpha = 0.85f),
        )
    }
}

// a cover from a local file or a URL, with the seeded-tone placeholder while loading or when missing
@Composable
fun CoverImage(model: Any?, seed: String, modifier: Modifier = Modifier, radius: Dp = Radii.listCover, letterSize: TextUnit = 18.sp) {
    val shape = RoundedCornerShape(radius)
    if (model == null) {
        Placeholder(seed, shape, modifier, letterSize)
        return
    }
    SubcomposeAsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(shape),
        loading = { Placeholder(seed, shape, Modifier.fillMaxSize(), letterSize) },
        error = { Placeholder(seed, shape, Modifier.fillMaxSize(), letterSize) },
    )
}

@Composable
fun ArtistAvatar(model: Any?, name: String, modifier: Modifier = Modifier, letterSize: TextUnit = 22.sp) {
    if (model == null) {
        Placeholder(name, CircleShape, modifier, letterSize)
        return
    }
    SubcomposeAsyncImage(
        model = model,
        contentDescription = name,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(CircleShape),
        loading = { Placeholder(name, CircleShape, Modifier.fillMaxSize(), letterSize) },
        error = { Placeholder(name, CircleShape, Modifier.fillMaxSize(), letterSize) },
    )
}

@Composable
fun trackThumb(trackId: String, hasCover: Boolean): Any? {
    val paths = LocalPaths.current
    return if (hasCover) paths.coverThumb(trackId) else null
}

@Composable
fun trackCover(trackId: String, hasCover: Boolean): Any? {
    val paths = LocalPaths.current
    return if (hasCover) paths.cover(trackId) else null
}

@Composable
fun artistThumb(artistId: String, hasImage: Boolean): Any? {
    val paths = LocalPaths.current
    return if (hasImage) paths.artistThumb(artistId) else null
}

val CoverSmall = 48.dp
