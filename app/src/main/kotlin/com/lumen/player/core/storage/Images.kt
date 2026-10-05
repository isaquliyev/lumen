package com.lumen.player.core.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.lumen.player.core.util.CoverTone
import com.lumen.player.core.util.coverTone
import java.io.File
import kotlin.math.min

// crops to a centred square, saves a large JPEG and a 256 px thumbnail, and returns the cover tone
fun processSquareImage(source: File, large: File, thumb: File, maxSize: Int = 1000): CoverTone? {
    val decoded = BitmapFactory.decodeFile(source.absolutePath) ?: return null
    val side = min(decoded.width, decoded.height)
    val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
    val big = if (side > maxSize) Bitmap.createScaledBitmap(square, maxSize, maxSize, true) else square
    large.parentFile?.mkdirs()
    large.outputStream().use { big.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    val small = Bitmap.createScaledBitmap(square, 256, 256, true)
    thumb.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    return coverTone(square)
}
