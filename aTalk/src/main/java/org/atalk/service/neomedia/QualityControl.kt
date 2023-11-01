/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia

/**
 * The quality controls we use to control other party video presets.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
interface QualityControl {
    /**
     * The currently used quality preset announced as receive by remote party.
     *
     * @return the current quality preset.
     */
    fun getRemoteReceivePreset(): QualityPreset?

    /**
     * The minimum preset that the remote party is sending and we are receiving.
     *
     * @return the minimum remote preset.
     */
    fun getRemoteSendMinPreset(): QualityPreset?

    /**
     * The maximum preset that the remote party is sending and we are receiving.
     *
     * @return the maximum preset announced from remote party as send.
     */
    fun getRemoteSendMaxPreset(): QualityPreset?

    /**
     * Changes remote send preset. This doesn't have impact of current stream. But will have on next
     * media changes. With this we can try to change the resolution that the remote part is sending.
     *
     * @param preset
     * the new preset value.
     */
    fun setRemoteSendMaxPreset(preset: QualityPreset)

    /**
     * Changes remote send preset and protocols who can handle the changes will implement this for
     * re-inviting the other party or just sending that media has changed.
     *
     * @param preset
     * the new preset.
     * @throws MediaException
     */
    @Throws(MediaException::class)
    fun setPreferredRemoteSendMaxPreset(preset: QualityPreset)
}