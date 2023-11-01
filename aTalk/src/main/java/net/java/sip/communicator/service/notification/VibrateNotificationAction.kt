/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.notification

/**
 * `VibrateNotificationAction` is meant to define haptic feedback notification using device's vibrator.<br></br>
 * <br></br>
 *
 * Given array of `long` are the duration for which to turn on or off the vibrator in milliseconds.
 * The first value indicates the number of milliseconds to wait before turning the vibrator on. The next value
 * indicates the number of milliseconds for which to keep the vibrator on before turning it off and so on.<br></br>
 * <br></br>
 *
 * The `repeat` parameter is an index into the pattern at which it will be looped until the
 * [VibrateNotificationHandler.cancel] method is called.
 *
 * @author Pawel Domas
 */
class VibrateNotificationAction : NotificationAction {
    /**
     * Returns vibrate pattern array.
     *
     * @return vibrate pattern array.
     */
    /**
     * The pattern of off/on intervals in millis that will be played.
     */
    val pattern: LongArray
    /**
     * The index at which the pattern shall be looped during playback or `-1` to play it once.
     *
     * @return the index at which the pattern will be looped or `-1` to play it once.
     */
    /**
     * Repeat index into the pattern(-1 to disable repeat).
     */
    val repeat: Int
    /**
     * The string identifier of this action.
     *
     * @return string identifier of this action which can be used to distinguish different actions.
     */
    /**
     * Descriptor that can be used to identify action.
     */
    val descriptor: String?

    /**
     * Vibrate constantly for the specified period of time.
     *
     * @param descriptor string identifier of this action.
     * @param millis the number of milliseconds to vibrate.
     */
    constructor(descriptor: String?, millis: Long) : super(ACTION_VIBRATE) {
        pattern = LongArray(2)
        pattern[0] = 0
        pattern[1] = millis
        repeat = -1
        this.descriptor = descriptor
    }

    /**
     * Vibrate using given `patter` and optionally loop if the `repeat` index is not `-1`.
     *
     * @param descriptor the string identifier of this action.
     * @param patter the array containing vibrate pattern intervals.
     * @param repeat the index into the patter at which it will be looped (-1 to disable repeat).
     * @see VibrateNotificationAction
     */
    constructor(descriptor: String?, patter: LongArray, repeat: Int) : super(ACTION_VIBRATE) {
        pattern = patter
        this.repeat = repeat
        this.descriptor = descriptor
    }
}