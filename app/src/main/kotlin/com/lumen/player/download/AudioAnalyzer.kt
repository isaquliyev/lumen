package com.lumen.player.download

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

data class AudioAnalysis(val waveform: ByteArray, val gainDb: Float, val durationMs: Long)

// one decode pass: 96-bar waveform + gain towards -14 dB (from -12 to +12 dB)
fun analyseAudio(file: File, bars: Int = 96, targetDb: Float = -14f): AudioAnalysis {
    val extractor = MediaExtractor().apply { setDataSource(file.absolutePath) }
    val index = (0 until extractor.trackCount).first {
        extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
    }
    val format = extractor.getTrackFormat(index)
    extractor.selectTrack(index)
    val durationUs = format.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1)
    val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
    codec.configure(format, null, null, 0)
    codec.start()
    var floatOutput = false
    val squares = DoubleArray(bars)
    val counts = LongArray(bars)
    var totalSquares = 0.0
    var totalCount = 0L
    val info = MediaCodec.BufferInfo()
    var inputDone = false
    var outputDone = false
    try {
        while (!outputDone) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val size = extractor.readSampleData(codec.getInputBuffer(inIndex)!!, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIndex = codec.dequeueOutputBuffer(info, 10_000)
            if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val out = codec.outputFormat
                floatOutput = out.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                    out.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
            } else if (outIndex >= 0) {
                if (info.size > 0) {
                    val buffer = codec.getOutputBuffer(outIndex)!!.order(ByteOrder.nativeOrder())
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    val bar = (info.presentationTimeUs * bars / durationUs).toInt().coerceIn(0, bars - 1)
                    if (floatOutput) {
                        val samples = buffer.asFloatBuffer()
                        while (samples.hasRemaining()) {
                            val v = samples.get().toDouble()
                            squares[bar] += v * v; counts[bar]++; totalSquares += v * v; totalCount++
                        }
                    } else {
                        val samples = buffer.asShortBuffer()
                        while (samples.hasRemaining()) {
                            val v = samples.get() / 32768.0
                            squares[bar] += v * v; counts[bar]++; totalSquares += v * v; totalCount++
                        }
                    }
                }
                codec.releaseOutputBuffer(outIndex, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        }
    } finally {
        codec.stop(); codec.release(); extractor.release()
    }
    val rms = DoubleArray(bars) { if (counts[it] == 0L) 0.0 else sqrt(squares[it] / counts[it]) }
    val peak = rms.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val waveform = ByteArray(bars) { ((rms[it] / peak).pow(0.7) * 255).toInt().toByte() }
    val overallDb = 20 * log10(sqrt(totalSquares / totalCount.coerceAtLeast(1)).coerceAtLeast(1e-9))
    val gain = (targetDb - overallDb).toFloat().coerceIn(-12f, 12f)
    return AudioAnalysis(waveform, gain, durationUs / 1000)
}
