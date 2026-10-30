package com.lumen.player.feature.player

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.lumen.player.core.db.TrackRow
import com.lumen.player.core.design.Accent
import com.lumen.player.core.design.LocalAccent
import com.lumen.player.core.design.LocalPaths
import com.lumen.player.core.design.LumenColors
import com.lumen.player.core.design.LumenFonts
import com.lumen.player.core.design.LumenType
import com.lumen.player.core.design.PillButton
import com.lumen.player.core.design.PillStyle
import com.lumen.player.core.design.Radii
import com.lumen.player.core.design.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.graphics.Typeface as AndroidTypeface

private const val WIDTH = 1080
private const val HEIGHT = 1920
private const val MAX_LINES = 5
private const val INSTAGRAM = "com.instagram.android"

// share sheet: share the song itself, or pick up to 5 lyric lines for a lyrics card
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryShareSheet(track: TrackRow, lyricLines: List<String>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    val paths = LocalPaths.current
    val scope = rememberCoroutineScope()
    val selected = remember { mutableStateListOf<Int>() }
    var working by remember { mutableStateOf(false) }

    fun share(lines: List<String>) {
        if (working) return
        working = true
        scope.launch {
            val cover = paths.cover(track.id).takeIf { track.hasCover && it.exists() }
            val file = withContext(Dispatchers.Default) {
                val bitmap = renderStory(track, cover, lines, accent)
                File(context.cacheDir, "share").apply { mkdirs() }.let { dir ->
                    File(dir, "story.png").also { out -> out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                }
            }
            working = false
            openInInstagram(context, file)
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = LumenColors.ink2, shape = RoundedCornerShape(topStart = Radii.sheet, topEnd = Radii.sheet)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Share to Instagram Story", style = LumenType.title, color = LumenColors.paper, modifier = Modifier.padding(horizontal = Space.side))
            Spacer(Modifier.height(12.dp))
            PillButton("Share song", { share(emptyList()) }, style = PillStyle.EMBER, modifier = Modifier.padding(horizontal = Space.side).testTag("story_song"))
            if (lyricLines.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "Or pick lyrics (up to $MAX_LINES lines)",
                    style = LumenType.label,
                    color = LumenColors.paper2,
                    modifier = Modifier.padding(horizontal = Space.side),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    itemsIndexed(lyricLines) { index, line ->
                        val on = index in selected
                        Text(
                            line,
                            style = LumenType.body,
                            color = if (on) LumenColors.ink else LumenColors.paper,
                            modifier = Modifier
                                .padding(horizontal = Space.side, vertical = 3.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (on) accent.accent else LumenColors.ink3)
                                .clickable {
                                    if (on) selected.remove(index) else if (selected.size < MAX_LINES) selected.add(index)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("story_line_$index"),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                PillButton(
                    "Share lyrics",
                    { if (selected.isNotEmpty()) share(selected.sorted().map { lyricLines[it] }) },
                    style = if (selected.isEmpty()) PillStyle.OUTLINED else PillStyle.EMBER,
                    modifier = Modifier.padding(horizontal = Space.side).testTag("story_lyrics"),
                )
            }
        }
    }
}

// opens Instagram's story editor; if that screen is not found, Instagram's own share picker
private fun openInInstagram(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    val story = Intent(send).setComponent(ComponentName(INSTAGRAM, "com.instagram.share.handleractivity.StoryShareHandlerActivity"))
    try {
        context.startActivity(story)
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(send).setPackage(INSTAGRAM))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Instagram is not installed", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun typeface(file: String, weight: Int, axes: String = "") =
    AndroidTypeface.Builder(LumenFonts.assets, "fonts/$file").setWeight(weight).setFontVariationSettings("'wght' $weight$axes").build()

private fun textLayout(text: String, paint: TextPaint, width: Int, maxLines: Int, align: Layout.Alignment) =
    StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setAlignment(align)
        .setMaxLines(maxLines)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()

private fun Canvas.drawLayout(layout: StaticLayout, x: Float, y: Float) {
    save()
    translate(x, y)
    layout.draw(this)
    restore()
}

private fun Canvas.drawCover(cover: Bitmap?, rect: RectF, radius: Float, fallback: Int) {
    val clip = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
    save()
    clipPath(clip)
    if (cover != null) drawBitmap(cover, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
    else drawRect(rect, Paint().apply { color = fallback })
    restore()
}

// a 1080x1920 story picture on a dark gradient from the song's colour.
// no lyric lines: big cover with title and artist. with lines: a lyrics card in the accent colour
fun renderStory(track: TrackRow, coverFile: File?, lines: List<String>, accent: Accent): Bitmap {
    val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val background = Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, HEIGHT.toFloat(), accent.darkGlow.toArgb(), accent.darkDeep.toArgb(), Shader.TileMode.CLAMP)
    }
    canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), background)
    val cover = coverFile?.let { BitmapFactory.decodeFile(it.absolutePath) }
    val serif = typeface("fraunces.ttf", 500, ", 'opsz' 48")
    val sans = typeface("manrope.ttf", 600)
    val title = track.title
    val artist = track.artistLine.orEmpty()

    if (lines.isEmpty()) {
        val size = 760f
        val top = 380f
        val rect = RectF((WIDTH - size) / 2, top, (WIDTH + size) / 2, top + size)
        canvas.drawCover(cover, rect, 36f, accent.darkAccent.toArgb())
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = serif; textSize = 72f; color = 0xFFFFFFFF.toInt() }
        val titleLayout = textLayout(title, titlePaint, 900, 2, Layout.Alignment.ALIGN_CENTER)
        canvas.drawLayout(titleLayout, 90f, rect.bottom + 70f)
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = sans; textSize = 40f; color = 0xB3FFFFFF.toInt() }
        val artistLayout = textLayout(artist, artistPaint, 900, 1, Layout.Alignment.ALIGN_CENTER)
        canvas.drawLayout(artistLayout, 90f, rect.bottom + 70f + titleLayout.height + 20f)
    } else {
        val cardWidth = 900
        val padding = 64f
        val textWidth = (cardWidth - padding * 2).toInt()
        val ink = 0xFF0B0B0C.toInt()
        val lyricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = serif; textSize = 62f; color = ink }
        val lyricLayout = textLayout(lines.joinToString("\n"), lyricPaint, textWidth, 12, Layout.Alignment.ALIGN_NORMAL)
        val thumb = 120f
        val headerGap = 48f
        val cardHeight = padding * 2 + thumb + headerGap + lyricLayout.height
        val left = (WIDTH - cardWidth) / 2f
        val top = (HEIGHT - cardHeight) / 2f
        val card = RectF(left, top, left + cardWidth, top + cardHeight)
        canvas.drawRoundRect(card, 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent.darkAccent.toArgb() })
        val thumbRect = RectF(left + padding, top + padding, left + padding + thumb, top + padding + thumb)
        canvas.drawCover(cover, thumbRect, 16f, accent.darkGlow.toArgb())
        val headerX = thumbRect.right + 28f
        val headerWidth = (card.right - padding - headerX).toInt()
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = typeface("manrope.ttf", 700); textSize = 40f; color = ink }
        val titleLayout = textLayout(title, titlePaint, headerWidth, 1, Layout.Alignment.ALIGN_NORMAL)
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = sans; textSize = 34f; color = 0xB30B0B0C.toInt() }
        val artistLayout = textLayout(artist, artistPaint, headerWidth, 1, Layout.Alignment.ALIGN_NORMAL)
        val headerTop = thumbRect.centerY() - (titleLayout.height + 6f + artistLayout.height) / 2
        canvas.drawLayout(titleLayout, headerX, headerTop)
        canvas.drawLayout(artistLayout, headerX, headerTop + titleLayout.height + 6f)
        canvas.drawLayout(lyricLayout, left + padding, thumbRect.bottom + headerGap)
    }

    val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = typeface("manrope.ttf", 700); textSize = 30f; color = 0x80FFFFFF.toInt(); letterSpacing = 0.3f; textAlign = Paint.Align.CENTER
    }
    canvas.drawText("LUMEN", WIDTH / 2f, HEIGHT - 140f, brandPaint)
    cover?.recycle()
    return bitmap
}
