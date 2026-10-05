package com.lumen.player.feature.playlist

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.lumen.player.core.design.CoverImage
import com.lumen.player.core.design.LocalPaths
import com.lumen.player.core.design.LumenColors
import com.lumen.player.core.design.Placeholder
import com.lumen.player.core.util.hsl
import com.lumen.player.data.SmartKind
import java.io.File
import kotlin.math.min

// custom image, or a 2x2 mosaic of the first 4 covers, or one cover, or a placeholder
@Composable
fun PlaylistCover(
    playlistId: String,
    name: String,
    hasCustomCover: Boolean,
    updatedAt: Long,
    coverTrackIds: List<String>,
    modifier: Modifier = Modifier,
    radius: Dp = 6.dp,
) {
    val paths = LocalPaths.current
    val context = LocalContext.current
    val shape = RoundedCornerShape(radius)
    when {
        hasCustomCover -> {
            val file = paths.playlistCover(playlistId)
            val request = remember(file, updatedAt) {
                ImageRequest.Builder(context).data(file).memoryCacheKey("${file.path}#$updatedAt").build()
            }
            Box(modifier.clip(shape)) {
                Placeholder(name, shape, Modifier.fillMaxSize())
                AsyncImage(request, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        coverTrackIds.size >= 4 -> Column(modifier.clip(shape)) {
            coverTrackIds.take(4).chunked(2).forEach { pair ->
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    pair.forEach { id ->
                        AsyncImage(paths.coverThumb(id), null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxSize())
                    }
                }
            }
        }
        coverTrackIds.isNotEmpty() -> CoverImage(paths.coverThumb(coverTrackIds.first()), name, modifier, radius)
        else -> Placeholder(name, shape, modifier)
    }
}

@Composable
fun SmartCover(kind: SmartKind, modifier: Modifier = Modifier, radius: Dp = 6.dp) {
    val (hue, icon) = when (kind) {
        SmartKind.LIKED -> 11f to Icons.Rounded.Favorite
        SmartKind.ON_REPEAT -> 280f to Icons.Rounded.Repeat
        SmartKind.RECENT -> 190f to Icons.Rounded.NewReleases
        SmartKind.FORGOTTEN -> 45f to Icons.Rounded.History
    }
    val colors = remember(hue) { listOf(Color(hsl(hue, 0.85f, 0.55f)), Color(hsl((hue + 30) % 360, 0.70f, 0.22f))) }
    Box(
        modifier.clip(RoundedCornerShape(radius)).background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = LumenColors.paper, modifier = Modifier.fillMaxSize(0.42f))
    }
}


// centre-crops a picked image to a 1000 px square JPEG
fun saveSquareCover(context: Context, uri: Uri, target: File) {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val side = min(info.size.width, info.size.height)
        if (side > 2000) decoder.setTargetSampleSize(side / 1000)
    }
    val side = min(bitmap.width, bitmap.height)
    val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
    val scaled = if (side > 1000) Bitmap.createScaledBitmap(square, 1000, 1000, true) else square
    target.parentFile?.mkdirs()
    val temp = File(target.parentFile, ".${target.name}.part")
    temp.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    temp.renameTo(target)
}

