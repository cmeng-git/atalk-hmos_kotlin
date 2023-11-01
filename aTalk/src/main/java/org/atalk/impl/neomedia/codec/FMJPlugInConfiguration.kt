/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec

import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.MediaServiceImpl
import org.atalk.util.OSUtils
import timber.log.Timber
import java.io.IOException
import java.util.*
import javax.media.Codec
import javax.media.Multiplexer
import javax.media.PackageManager
import javax.media.PlugInManager

/**
 * Utility class that handles registration of FMJ packages and plugins.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
object FMJPlugInConfiguration {
    /**
     * Whether the custom codecs have been registered with FMJ.
     */
    private var codecsRegistered = false

    /**
     * Whether the custom multiplexers have been registered with FMJ.
     */
    private var multiplexersRegistered = false

    /**
     * The additional custom JMF codecs.
     */
    private val CUSTOM_CODECS = arrayOf( // "org.atalk.impl.neomedia.codec.AndroidMediaCodec",
            if (OSUtils.IS_ANDROID) "org.atalk.impl.neomedia.codec.video.AndroidEncoder" else null,
            if (OSUtils.IS_ANDROID) "org.atalk.impl.neomedia.codec.video.AndroidDecoder" else null,
            "org.atalk.impl.neomedia.codec.audio.alaw.DePacketizer",
            "org.atalk.impl.neomedia.codec.audio.alaw.JavaEncoder",
            "org.atalk.impl.neomedia.codec.audio.alaw.Packetizer",
            "org.atalk.impl.neomedia.codec.audio.ulaw.JavaDecoder",
            "org.atalk.impl.neomedia.codec.audio.ulaw.JavaEncoder",
            "org.atalk.impl.neomedia.codec.audio.ulaw.Packetizer",
            "org.atalk.impl.neomedia.codec.audio.opus.JNIDecoder",
            "org.atalk.impl.neomedia.codec.audio.opus.JNIEncoder",
            "org.atalk.impl.neomedia.codec.audio.speex.JNIDecoder",
            "org.atalk.impl.neomedia.codec.audio.speex.JNIEncoder",
            "org.atalk.impl.neomedia.codec.audio.speex.SpeexResampler",
            "org.atalk.impl.neomedia.codec.audio.ilbc.JavaDecoder",
            "org.atalk.impl.neomedia.codec.audio.ilbc.JavaEncoder",  // g722
            "org.atalk.impl.neomedia.codec.audio.g722.JNIDecoder",
            "org.atalk.impl.neomedia.codec.audio.g722.JNIEncoder",  // g729
            "org.atalk.impl.neomedia.codec.audio.g729.JNIDecoder",
            "org.atalk.impl.neomedia.codec.audio.g729.JNIEncoder",  // gsm
            "org.atalk.impl.neomedia.codec.audio.gsm.Decoder",
            "org.atalk.impl.neomedia.codec.audio.gsm.Encoder",
            "org.atalk.impl.neomedia.codec.audio.gsm.DePacketizer",
            "org.atalk.impl.neomedia.codec.audio.gsm.Packetizer",  // silk
            "org.atalk.impl.neomedia.codec.audio.silk.JavaDecoder",
            "org.atalk.impl.neomedia.codec.audio.silk.JavaEncoder",  // VP8
            "org.atalk.impl.neomedia.codec.video.vp8.DePacketizer",
            "org.atalk.impl.neomedia.codec.video.vp8.Packetizer",
            "org.atalk.impl.neomedia.codec.video.vp8.VP8Decoder",
            "org.atalk.impl.neomedia.codec.video.vp8.VP8Encoder",  // VP9 (FMJPlugInConfiguration.java:228)#registerCustomCodecs:
            "org.atalk.impl.neomedia.codec.video.vp9.DePacketizer",
            "org.atalk.impl.neomedia.codec.video.vp9.Packetizer",
            "org.atalk.impl.neomedia.codec.video.vp9.VP9Decoder",
            "org.atalk.impl.neomedia.codec.video.vp9.VP9Encoder")

    /**
     * The additional custom JMF codecs, which depend on ffmpeg and should
     * therefore only be used when ffmpeg is enabled.
     */
    private val CUSTOM_CODECS_FFMPEG = arrayOf( // MP3 - cmeng (not working)
            // "org.atalk.impl.neomedia.codec.audio.mp3.JNIEncoder",
            // h264
            "org.atalk.impl.neomedia.codec.video.h264.DePacketizer",
            "org.atalk.impl.neomedia.codec.video.h264.JNIDecoder",
            "org.atalk.impl.neomedia.codec.video.h264.JNIEncoder",
            "org.atalk.impl.neomedia.codec.video.h264.Packetizer",
            "org.atalk.impl.neomedia.codec.video.SwScale",  // Adaptive Multi-Rate Wideband (AMR-WB)
            "org.atalk.impl.neomedia.codec.audio.amrwb.DePacketizer",
            "org.atalk.impl.neomedia.codec.audio.amrwb.JNIDecoder",
            "org.atalk.impl.neomedia.codec.audio.amrwb.JNIEncoder",
            "org.atalk.impl.neomedia.codec.audio.amrwb.Packetizer")

    /**
     * The package prefixes of the additional JMF `DataSource`s (e.g. low latency PortAudio
     * and ALSA `CaptureDevice`s).
     */
    private val CUSTOM_PACKAGES = arrayOf(
            "org.atalk.impl.neomedia.jmfext",
            "net.java.sip.communicator.impl.neomedia.jmfext",
            "net.sf.fmj"
    )

    /**
     * The list of class names to register as FMJ plugins with type `PlugInManager.MULTIPLEXER`.
     */
    private val CUSTOM_MULTIPLEXERS = arrayOf(
            "org.atalk.impl.neomedia.recording.BasicWavMux"
    )

    /**
     * Whether custom packages have been registered with JFM
     */
    private var packagesRegistered = false

    /**
     * Register in JMF the custom codecs we provide
     *
     * @param enableFfmpeg whether codecs which depend of ffmpeg should be registered.
     */
    @JvmStatic
    fun registerCustomCodecs(enableFfmpeg: Boolean) {
        if (codecsRegistered) return

        // Register the custom codec which haven't already been registered.
        val registeredPlugins = HashSet<String>(PlugInManager.getPlugInList(null, null, PlugInManager.CODEC) as Vector<String>)
        var commit = false

        // Remove JavaRGBToYUV.
        PlugInManager.removePlugIn("com.sun.media.codec.video.colorspace.JavaRGBToYUV", PlugInManager.CODEC)
        PlugInManager.removePlugIn("com.sun.media.codec.video.colorspace.JavaRGBConverter", PlugInManager.CODEC)
        PlugInManager.removePlugIn("com.sun.media.codec.video.colorspace.RGBScaler", PlugInManager.CODEC)

        // Remove JMF's GSM codec. As working only on some OS.
        val gsmCodecPackage = "com.ibm.media.codec.audio.gsm."
        val gsmCodecClasses = arrayOf(
                "JavaDecoder",
                "JavaDecoder_ms",
                "JavaEncoder",
                "JavaEncoder_ms",
                "NativeDecoder",
                "NativeDecoder_ms",
                "NativeEncoder",
                "NativeEncoder_ms",
                "Packetizer"
        )
        for (gsmCodecClass in gsmCodecClasses) {
            PlugInManager.removePlugIn(gsmCodecPackage + gsmCodecClass, PlugInManager.CODEC)
        }

        /*
         * Remove FMJ's JavaSoundCodec because it seems to slow down the
         * building of the filter graph, and we do not currently seem to need it.
         */
        PlugInManager.removePlugIn("net.sf.fmj.media.codec.JavaSoundCodec", PlugInManager.CODEC)
        val customCodecs = LinkedList(listOf(*CUSTOM_CODECS))
        if (enableFfmpeg) {
            customCodecs.addAll(listOf(*CUSTOM_CODECS_FFMPEG))
        }
        for (className in customCodecs) {
            /*
             * A codec with a className null configured at compile time to not be registered.
             */
            if (className == null) continue
            if (registeredPlugins.contains(className)) {
                Timber.log(TimberLog.FINER, "Codec %s is already registered", className)
            } else {
                commit = true
                try {
                    val codec = Class.forName(className).newInstance() as Codec
                    PlugInManager.addPlugIn(
                            className,
                            codec.supportedInputFormats,
                            codec.getSupportedOutputFormats(null),
                            PlugInManager.CODEC)
                    Timber.log(TimberLog.FINER, "Codec %s is successfully registered", className)
                    // Timber.d("Codec %s is successfully registered", className);
                } catch (ex: Throwable) {
                    Timber.w("Codec %s is NOT successfully registered: %s", className, ex.message)
                }
            }
        }

        /*
         * If aTalk provides a codec which is also provided by FMJ and/or JMF, use aTalk's version.
         */
        val codecs = PlugInManager.getPlugInList(null, null, PlugInManager.CODEC)
        if (codecs != null) {
            var setPlugInList = false
            for (className in customCodecs) {
                if (className != null) {
                    val classNameIndex = codecs.indexOf(className)
                    if (classNameIndex != -1) {
                        codecs.removeAt(classNameIndex)
                        codecs.add(0, className)
                        setPlugInList = true
                    }
                }
            }
            if (setPlugInList) PlugInManager.setPlugInList(codecs, PlugInManager.CODEC)
        }
        if (commit && !MediaServiceImpl.isJmfRegistryDisableLoad) {
            try {
                PlugInManager.commit()
            } catch (ex: IOException) {
                Timber.e(ex, "Cannot commit to PlugInManager")
            }
        }
        codecsRegistered = true
    }

    /**
     * Register in JMF the custom packages we provide
     */
    @JvmStatic
    fun registerCustomPackages() {
        if (packagesRegistered) return
        val packages = PackageManager.getProtocolPrefixList()

        // We prefer our custom packages/protocol prefixes over FMJ's.
        for (i in CUSTOM_PACKAGES.indices.reversed()) {
            val customPackage = CUSTOM_PACKAGES[i]

            /*
             * Linear search in a loop but it doesn't have to scale since the list is always short.
             */
            if (!packages.contains(customPackage)) {
                packages.add(0, customPackage)
                Timber.d("Adding package: %s", customPackage)
            }
        }
        PackageManager.setProtocolPrefixList(packages)
        PackageManager.commitProtocolPrefixList()
        Timber.d("Registering new protocol prefix list: %s", packages)
        packagesRegistered = true
    }

    /**
     * Registers custom libjitsi `Multiplexer` implementations.
     */
    @JvmStatic
    fun registerCustomMultiplexers() {
        if (multiplexersRegistered) return

        // Remove the FMJ WAV multiplexers, as they don't work.
        PlugInManager.removePlugIn("com.sun.media.multiplexer.audio.WAVMux", PlugInManager.MULTIPLEXER)
        val removePlugIn = PlugInManager.removePlugIn("net.sf.fmj.media.multiplexer.audio.WAVMux", PlugInManager.MULTIPLEXER)
        val registeredMuxers = HashSet<String>(PlugInManager.getPlugInList(null, null, PlugInManager.MULTIPLEXER) as Vector<String>)
        var commit = false
        for (className in CUSTOM_MULTIPLEXERS) {
            if (registeredMuxers.contains(className)) {
                Timber.d("Multiplexer %s is already registered", className)
                continue
            }
            var registered: Boolean
            try {
                val multiplexer = Class.forName(className).newInstance() as Multiplexer
                registered = PlugInManager.addPlugIn(
                        className,
                        multiplexer.supportedInputFormats,
                        multiplexer.getSupportedOutputContentDescriptors(null),
                        PlugInManager.MULTIPLEXER)
                Timber.log(TimberLog.FINER, "Codec %s is successfully registered", className)
            } catch (ex: Throwable) {
                Timber.w("Codec %s is NOT successfully registered: %s", className, ex.message)
                registered = false
            }
            commit = commit or registered
        }
        if (commit && !MediaServiceImpl.isJmfRegistryDisableLoad) {
            try {
                PlugInManager.commit()
            } catch (ex: IOException) {
                Timber.e(ex, "Cannot commit to PlugInManager")
            }
        }
        multiplexersRegistered = true
    }
}