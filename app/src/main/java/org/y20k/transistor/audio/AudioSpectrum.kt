/*
 * AudioSpectrum.kt
 * Implements the AudioSpectrum object
 * AudioSpectrum turns the decoded audio of the player into frequency bands for the real time visualizer.
 * It reads the PCM data inside the app's own audio pipeline (no microphone, no RECORD_AUDIO permission)
 * and hands out each set of bands at the moment its audio is actually heard.
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor.audio

import android.media.AudioFormat
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt


/*
 * AudioSpectrum object
 * The analysis runs on the player's playback thread, the bands are read on the main thread.
 */
object AudioSpectrum {

    /* Number of frequency bands (= bars in the visualizer) */
    const val BAND_COUNT: Int = 32

    /* Analysis settings */
    private const val FFT_SIZE: Int = 2048
    private const val ANALYSES_PER_SECOND: Int = 60
    private const val LOWEST_FREQUENCY: Double = 50.0
    private const val HIGHEST_FREQUENCY: Double = 16000.0
    private const val DB_RANGE: Float = 42f          // dynamic range shown by a bar, from empty to full
    private const val MIN_PEAK_DB: Float = -36f      // keeps silence and very quiet passages low
    private const val PEAK_RELEASE_DB_PER_ANALYSIS: Float = 0.05f // ~3 dB per second, slow automatic gain
    private const val MAX_PENDING: Int = 180         // ~3 seconds of bands waiting to be heard

    /* Viewers: the analysis only runs while at least one visualizer is on screen */
    private val viewers: AtomicInteger = AtomicInteger(0)

    /* Latest bands that are being heard right now - written on the playback thread, read on the main thread */
    @Volatile private var heardBands: FloatArray? = null
    @Volatile private var heardAtMillis: Long = 0L

    /* Playback thread state */
    private var sampleRate: Int = 0
    private var channelCount: Int = 0
    private var encoding: Int = AudioFormat.ENCODING_INVALID
    private var bufferPresentationTimeUs: Long = Long.MIN_VALUE
    private var framesIntoBuffer: Long = 0L
    private val ring: FloatArray = FloatArray(FFT_SIZE)
    private var ringPosition: Int = 0
    private var framesSinceAnalysis: Int = 0
    private var hopSize: Int = FFT_SIZE / 2
    private var peakDb: Float = MIN_PEAK_DB
    private val pending: ArrayDeque<Snapshot> = ArrayDeque()
    private var bandBins: Array<IntArray> = emptyArray()
    private var bandTilt: FloatArray = FloatArray(BAND_COUNT)

    /* FFT work arrays and tables */
    private val window: FloatArray = FloatArray(FFT_SIZE) { i -> (0.5 - 0.5 * cos(2.0 * PI * i / (FFT_SIZE - 1))).toFloat() }
    private val real: FloatArray = FloatArray(FFT_SIZE)
    private val imaginary: FloatArray = FloatArray(FFT_SIZE)
    private val cosTable: FloatArray = FloatArray(FFT_SIZE / 2) { i -> cos(2.0 * PI * i / FFT_SIZE).toFloat() }
    private val sinTable: FloatArray = FloatArray(FFT_SIZE / 2) { i -> sin(2.0 * PI * i / FFT_SIZE).toFloat() }
    private val bitReversed: IntArray = IntArray(FFT_SIZE) { i -> Integer.reverse(i) ushr (32 - Integer.numberOfTrailingZeros(FFT_SIZE)) }


    /* A set of bands and the moment (in player time) its audio is played */
    private class Snapshot(val presentationTimeUs: Long, val bands: FloatArray)


    /* A visualizer became visible */
    fun addViewer() {
        viewers.incrementAndGet()
    }


    /* A visualizer is no longer visible */
    fun removeViewer() {
        if (viewers.decrementAndGet() < 0) viewers.set(0)
    }


    /* Returns the bands heard right now (values 0..1), or null if nothing was heard recently */
    fun currentBands(maxAgeMillis: Long): FloatArray? {
        val bands: FloatArray = heardBands ?: return null
        return if (SystemClock.elapsedRealtime() - heardAtMillis <= maxAgeMillis) bands else null
    }


    /* PLAYBACK THREAD: the audio format changed or the stream was flushed */
    fun onFormat(sampleRateHz: Int, channels: Int, pcmEncoding: Int) {
        if (sampleRateHz != sampleRate) {
            sampleRate = sampleRateHz
            computeBandBins()
        }
        channelCount = channels
        encoding = pcmEncoding
        hopSize = if (sampleRateHz > 0) max(1, sampleRateHz / ANALYSES_PER_SECOND) else FFT_SIZE / 2
        clear()
    }


    /* PLAYBACK THREAD: the audio sink receives a buffer that starts at the given player time */
    fun onSinkBuffer(presentationTimeUs: Long) {
        if (presentationTimeUs != bufferPresentationTimeUs) {
            bufferPresentationTimeUs = presentationTimeUs
            framesIntoBuffer = 0L
        }
    }


    /* PLAYBACK THREAD: decoded PCM data passes through the audio pipeline */
    fun onPcm(buffer: ByteBuffer) {
        if (viewers.get() <= 0) {
            if (pending.isNotEmpty()) pending.clear()
            return
        }
        val bytesPerSample: Int = when (encoding) {
            AudioFormat.ENCODING_PCM_16BIT -> 2
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            else -> return // other formats are not analysed
        }
        if (channelCount <= 0 || sampleRate <= 0 || bandBins.isEmpty()) return
        val data: ByteBuffer = buffer.duplicate().order(ByteOrder.nativeOrder())
        val frameSize: Int = bytesPerSample * channelCount
        val start: Int = data.position()
        val frames: Int = data.remaining() / frameSize
        for (frame in 0 until frames) {
            // mix all channels down to mono
            var sum = 0f
            val frameStart: Int = start + frame * frameSize
            for (channel in 0 until channelCount) {
                val index: Int = frameStart + channel * bytesPerSample
                sum += if (bytesPerSample == 2) data.getShort(index) / 32768f else data.getFloat(index)
            }
            ring[ringPosition] = sum / channelCount
            ringPosition = (ringPosition + 1) % FFT_SIZE
            framesSinceAnalysis++
            if (framesSinceAnalysis >= hopSize) {
                framesSinceAnalysis = 0
                // time at which the last sample of this analysis is played
                val presentationTimeUs: Long = if (bufferPresentationTimeUs == Long.MIN_VALUE) Long.MIN_VALUE
                        else bufferPresentationTimeUs + (framesIntoBuffer + frame) * 1_000_000L / sampleRate
                if (presentationTimeUs != Long.MIN_VALUE) {
                    pending.addLast(Snapshot(presentationTimeUs, analyse()))
                    while (pending.size > MAX_PENDING) pending.removeFirst()
                }
            }
        }
        framesIntoBuffer += frames
    }


    /* PLAYBACK THREAD: the audio output has reached the given player time */
    fun onPlaybackPosition(positionUs: Long) {
        var heard: Snapshot? = null
        while (pending.isNotEmpty() && pending.peekFirst()!!.presentationTimeUs <= positionUs) {
            heard = pending.pollFirst()
        }
        if (heard != null) {
            heardBands = heard.bands
            heardAtMillis = SystemClock.elapsedRealtime()
        }
    }


    /* PLAYBACK THREAD: the audio sink was flushed or reset (stop, new station, seek) */
    fun onSinkFlush() {
        clear()
        bufferPresentationTimeUs = Long.MIN_VALUE
        framesIntoBuffer = 0L
    }


    /* Forgets the audio analysed so far */
    private fun clear() {
        pending.clear()
        ring.fill(0f)
        ringPosition = 0
        framesSinceAnalysis = 0
    }


    /* Runs the FFT over the last FFT_SIZE samples and returns the band levels (0..1) */
    private fun analyse(): FloatArray {
        // copy the ring (oldest sample first), apply the window
        for (i in 0 until FFT_SIZE) {
            val sample: Float = ring[(ringPosition + i) % FFT_SIZE]
            real[bitReversed[i]] = sample * window[i]
            imaginary[bitReversed[i]] = 0f
        }
        fft()
        // amplitude per bin: a full scale sine reads 0 dB (Hann window: coherent gain 0.5)
        val bands = FloatArray(BAND_COUNT)
        var loudestDb: Float = -120f
        val bandDb = FloatArray(BAND_COUNT)
        for (band in 0 until BAND_COUNT) {
            var maxPower = 0f
            for (bin in bandBins[band]) {
                val power: Float = real[bin] * real[bin] + imaginary[bin] * imaginary[bin]
                if (power > maxPower) maxPower = power
            }
            val amplitude: Float = 4f * sqrt(maxPower) / FFT_SIZE
            val db: Float = (if (amplitude > 1e-7f) 20f * log10(amplitude) else -140f) + bandTilt[band]
            bandDb[band] = db
            if (db > loudestDb) loudestDb = db
        }
        // slow automatic gain: the loudest band sets the top, quiet stations still move the bars
        peakDb = if (loudestDb > peakDb) loudestDb else max(MIN_PEAK_DB, peakDb - PEAK_RELEASE_DB_PER_ANALYSIS)
        val floorDb: Float = peakDb - DB_RANGE
        for (band in 0 until BAND_COUNT) {
            // a gentle curve keeps the bars lively instead of all near the top
            val level: Float = ((bandDb[band] - floorDb) / DB_RANGE).coerceIn(0f, 1f)
            bands[band] = level * sqrt(level) // level^1.5
        }
        return bands
    }


    /* In place radix-2 FFT on real/imaginary (input already in bit reversed order) */
    private fun fft() {
        var size = 2
        while (size <= FFT_SIZE) {
            val half: Int = size / 2
            val step: Int = FFT_SIZE / size
            var start = 0
            while (start < FFT_SIZE) {
                for (k in 0 until half) {
                    val wr: Float = cosTable[k * step]
                    val wi: Float = -sinTable[k * step]
                    val even: Int = start + k
                    val odd: Int = even + half
                    val tr: Float = wr * real[odd] - wi * imaginary[odd]
                    val ti: Float = wr * imaginary[odd] + wi * real[odd]
                    real[odd] = real[even] - tr
                    imaginary[odd] = imaginary[even] - ti
                    real[even] += tr
                    imaginary[even] += ti
                }
                start += size
            }
            size *= 2
        }
    }


    /* Maps every band to its FFT bins: logarithmic bands, like the ear hears them */
    private fun computeBandBins() {
        if (sampleRate <= 0) {
            bandBins = emptyArray()
            return
        }
        val binWidth: Double = sampleRate.toDouble() / FFT_SIZE
        val highest: Double = min(HIGHEST_FREQUENCY, sampleRate / 2.0 - binWidth)
        val ratio: Double = ln(highest / LOWEST_FREQUENCY)
        bandBins = Array(BAND_COUNT) { band ->
            val low: Double = LOWEST_FREQUENCY * kotlin.math.exp(ratio * band / BAND_COUNT)
            val high: Double = LOWEST_FREQUENCY * kotlin.math.exp(ratio * (band + 1) / BAND_COUNT)
            val firstBin: Int = max(1, floor(low / binWidth).toInt())
            val lastBin: Int = max(firstBin, floor(high / binWidth).toInt())
            IntArray(lastBin - firstBin + 1) { firstBin + it }
        }
        // music gets quieter towards the treble: lift the high bands by 3 dB per octave (around 1 kHz)
        bandTilt = FloatArray(BAND_COUNT) { band ->
            val center: Double = LOWEST_FREQUENCY * kotlin.math.exp(ratio * (band + 0.5) / BAND_COUNT)
            (3.0 * ln(center / 1000.0) / ln(2.0)).toFloat()
        }
    }


}
