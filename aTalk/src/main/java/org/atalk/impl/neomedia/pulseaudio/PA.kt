/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.pulseaudio

/**
 * Declares the functions, structures and constants of the native `PulseAudio` API for use
 * within Java in general and neomedia in particular.
 *
 * @author Lyubomir Marinov
 */
object PA {
    const val CONTEXT_AUTHORIZING = 2
    const val CONTEXT_CONNECTING = 1
    const val CONTEXT_FAILED = 5
    const val CONTEXT_NOFAIL = 2
    const val CONTEXT_NOFLAGS = 0
    const val CONTEXT_READY = 4
    const val CONTEXT_SETTING_NAME = 3
    const val CONTEXT_TERMINATED = 6
    const val CONTEXT_UNCONNECTED = 0
    const val ENCODING_ANY = 0
    const val ENCODING_INVALID = -1
    const val ENCODING_PCM = 1
    const val INVALID_INDEX = -1
    const val OPERATION_CANCELLED = 2
    const val OPERATION_DONE = 1
    const val OPERATION_RUNNING = 0
    const val PROP_APPLICATION_NAME = "application.name"
    const val PROP_APPLICATION_VERSION = "application.version"
    const val PROP_FORMAT_CHANNELS = "format.channels"
    const val PROP_FORMAT_RATE = "format.rate"
    const val PROP_MEDIA_NAME = "media.name"
    const val PROP_MEDIA_ROLE = "media.role"

    /**
     * The `pa_sample_format_t` value which specifies an invalid value.
     */
    const val SAMPLE_INVALID = -1

    /**
     * The `pa_sample_format_t` value which specifies signed 16-bit PCM, little endian.
     */
    const val SAMPLE_S16LE = 3
    const val SEEK_RELATIVE = 0
    const val STREAM_ADJUST_LATENCY = 0x2000
    const val STREAM_FAILED = 3
    const val STREAM_NOFLAGS = 0x0000
    const val STREAM_READY = 2
    const val STREAM_START_CORKED = 0x0001
    const val STREAM_TERMINATED = 4

    init {
        System.loadLibrary("jnpulseaudio")
    }

    external fun buffer_attr_free(attr: Long)
    external fun buffer_attr_new(maxlength: Int, tlength: Int, prebuf: Int, minreq: Int,
            fragsize: Int): Long

    external fun context_connect(c: Long, server: String?, flags: Int, api: Long): Int
    external fun context_disconnect(c: Long)
    external fun context_get_sink_info_list(c: Long, cb: sink_info_cb_t?): Long
    external fun context_get_source_info_list(c: Long, cb: source_info_cb_t?): Long
    external fun context_get_state(c: Long): Int
    external fun context_new_with_proplist(mainloop: Long, name: String?, proplist: Long): Long
    external fun context_set_sink_input_volume(c: Long, idx: Int, volume: Long,
            cb: context_success_cb_t?): Long

    external fun context_set_source_output_volume(c: Long, idx: Int, volume: Long,
            cb: context_success_cb_t?): Long

    external fun context_set_state_callback(c: Long, cb: Runnable?)
    external fun context_unref(c: Long)
    external fun cvolume_free(cv: Long)
    external fun cvolume_new(): Long
    external fun cvolume_set(cv: Long, channels: Int, v: Int): Long
    external fun format_info_get_encoding(f: Long): Int
    external fun format_info_get_plist(f: Long): Long
    external fun format_info_get_prop_int(f: Long, key: String?): Int
    external fun get_library_version(): String?
    external fun operation_get_state(o: Long): Int
    external fun operation_unref(o: Long)
    external fun proplist_free(p: Long)
    external fun proplist_new(): Long
    external fun proplist_sets(p: Long, key: String?, value: String?): Int
    external fun sample_spec_free(ss: Long)
    external fun sample_spec_new(format: Int, rate: Int, channels: Int): Long
    external fun sink_info_get_description(i: Long): String?
    external fun sink_info_get_flags(i: Long): Int
    external fun sink_info_get_formats(i: Long): LongArray?
    external fun sink_info_get_index(i: Long): Int
    external fun sink_info_get_monitor_source(i: Long): Int
    external fun sink_info_get_monitor_source_name(i: Long): String?
    external fun sink_info_get_name(i: Long): String?
    external fun sink_info_get_sample_spec_channels(i: Long): Int
    external fun sink_info_get_sample_spec_format(i: Long): Int
    external fun sink_info_get_sample_spec_rate(i: Long): Int
    external fun source_info_get_description(i: Long): String?
    external fun source_info_get_flags(i: Long): Int
    external fun source_info_get_formats(i: Long): LongArray?
    external fun source_info_get_index(i: Long): Int
    external fun source_info_get_monitor_of_sink(i: Long): Int
    external fun source_info_get_name(i: Long): String?
    external fun source_info_get_sample_spec_channels(i: Long): Int
    external fun source_info_get_sample_spec_format(i: Long): Int
    external fun source_info_get_sample_spec_rate(i: Long): Int
    external fun stream_connect_playback(s: Long, dev: String?, attr: Long, flags: Int,
            volume: Long, syncStream: Long): Int

    external fun stream_connect_record(s: Long, dev: String?, attr: Long, flags: Int): Int
    external fun stream_cork(s: Long, b: Boolean, cb: stream_success_cb_t?): Long
    external fun stream_disconnect(s: Long): Int
    external fun stream_drop(s: Long): Int

    /**
     * Gets the name of the sink or source a specified `pa_stream` is connected to in the
     * server.
     *
     * @param s
     * the `pa_stream` of which to get the name of the sink or source it is connected
     * to in the server
     * @return the name of the sink or source the specified `pa_stream` is connected to in
     * the server
     */
    external fun stream_get_device_name(s: Long): String?
    external fun stream_get_index(s: Long): Int
    external fun stream_get_state(s: Long): Int
    external fun stream_new_with_proplist(c: Long, name: String?, ss: Long, map: Long,
            p: Long): Long

    external fun stream_peek(s: Long, data: ByteArray?, dataOffset: Int): Int
    external fun stream_readable_size(s: Long): Int
    external fun stream_set_read_callback(s: Long, cb: stream_request_cb_t?)
    external fun stream_set_state_callback(s: Long, cb: Runnable?)
    external fun stream_set_write_callback(s: Long, cb: stream_request_cb_t?)
    external fun stream_unref(s: Long)
    external fun stream_writable_size(s: Long): Int
    external fun stream_write(s: Long, data: ByteArray?, dataOffset: Int, dataLength: Int,
            freeCb: Runnable?, offset: Long, seek: Int): Int

    external fun sw_volume_from_linear(v: Double): Int
    external fun threaded_mainloop_free(m: Long)
    external fun threaded_mainloop_get_api(m: Long): Long
    external fun threaded_mainloop_lock(m: Long)
    external fun threaded_mainloop_new(): Long
    external fun threaded_mainloop_signal(m: Long, waitForAccept: Boolean)
    external fun threaded_mainloop_start(m: Long): Int
    external fun threaded_mainloop_stop(m: Long)
    external fun threaded_mainloop_unlock(m: Long)
    external fun threaded_mainloop_wait(m: Long)
    interface context_success_cb_t {
        fun callback(c: Long, success: Boolean)
    }

    interface sink_info_cb_t {
        fun callback(c: Long, i: Long, eol: Int)
    }

    interface source_info_cb_t {
        fun callback(c: Long, i: Long, eol: Int)
    }

    interface stream_request_cb_t {
        fun callback(s: Long, nbytes: Int)
    }

    interface stream_success_cb_t {
        fun callback(s: Long, success: Boolean)
    }
}