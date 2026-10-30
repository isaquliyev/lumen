package com.lumen.player.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

// when mono is on, both channels of 16-bit stereo audio get the average of left and right.
// the on/off flag lives in the playback service and is read for every buffer
@OptIn(UnstableApi::class)
class MonoAudioProcessor(private val isMono: () -> Boolean) : BaseAudioProcessor() {
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        if (inputAudioFormat.channelCount == 2 && inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) inputAudioFormat
        else AudioProcessor.AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        // empty input and a fresh output are the same shared EMPTY_BUFFER; copying it into itself throws
        if (!inputBuffer.hasRemaining()) return
        val output = replaceOutputBuffer(inputBuffer.remaining())
        if (isMono()) {
            while (inputBuffer.remaining() >= 4) {
                val mixed = ((inputBuffer.getShort() + inputBuffer.getShort()) / 2).toShort()
                output.putShort(mixed)
                output.putShort(mixed)
            }
        }
        output.put(inputBuffer)
        output.flip()
    }
}
