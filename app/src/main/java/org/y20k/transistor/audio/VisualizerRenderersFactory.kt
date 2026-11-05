/*
 * VisualizerRenderersFactory.kt
 * Implements the VisualizerRenderersFactory and VisualizerAudioSink classes
 * They plug AudioSpectrum into the player's own audio output, so the visualizer
 * sees exactly the audio that is played - without the microphone permission
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor.audio

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer


/*
 * VisualizerRenderersFactory class: the default renderers, with an audio sink that feeds AudioSpectrum
 */
@UnstableApi
class VisualizerRenderersFactory(context: Context): DefaultRenderersFactory(context) {

    /* Overrides buildAudioSink from DefaultRenderersFactory */
    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioOutputPlaybackParams: Boolean): AudioSink? {
        val audioProcessors: Array<AudioProcessor> = arrayOf(TeeAudioProcessor(SpectrumBufferSink()))
        val defaultAudioSink: DefaultAudioSink = DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
            .setAudioProcessors(audioProcessors)
            .build()
        return VisualizerAudioSink(defaultAudioSink)
    }

}


/*
 * SpectrumBufferSink class: receives the decoded PCM audio from the TeeAudioProcessor
 */
@UnstableApi
private class SpectrumBufferSink: TeeAudioProcessor.AudioBufferSink {

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        AudioSpectrum.onFormat(sampleRateHz, channelCount, encoding)
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        AudioSpectrum.onPcm(buffer)
    }

}


/*
 * VisualizerAudioSink class: tells AudioSpectrum the time of every buffer and how far the output has played
 */
@UnstableApi
class VisualizerAudioSink(sink: AudioSink): ForwardingAudioSink(sink) {

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        AudioSpectrum.onSinkBuffer(presentationTimeUs)
        return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val positionUs: Long = super.getCurrentPositionUs(sourceEnded)
        if (positionUs != AudioSink.CURRENT_POSITION_NOT_SET) AudioSpectrum.onPlaybackPosition(positionUs)
        return positionUs
    }

    override fun flush() {
        super.flush()
        AudioSpectrum.onSinkFlush()
    }

    override fun reset() {
        super.reset()
        AudioSpectrum.onSinkFlush()
    }

}
