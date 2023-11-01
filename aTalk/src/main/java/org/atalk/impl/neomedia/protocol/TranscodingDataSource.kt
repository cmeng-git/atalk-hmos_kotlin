/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.protocol

import org.atalk.impl.neomedia.ProcessorUtility
import org.atalk.impl.neomedia.control.ControlsAdapter
import java.io.IOException
import java.lang.reflect.UndeclaredThrowableException
import javax.media.*
import javax.media.format.AudioFormat
import javax.media.protocol.*

/**
 * Represents a `DataSource` which transcodes the tracks of a specific input
 * `DataSource` into a specific output `Format`. The transcoding is attempted only for
 * tracks which actually support it for the specified output `Format`.
 *
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
class TranscodingDataSource
/**
 * Initializes a new `TranscodingDataSource` instance to transcode the tracks of a
 * specific `DataSource` into a specific output `Format`.
 *
 * @param inputDataSource
 * the `DataSource` which is to have its tracks transcoded in a specific output
 * `Format`
 * @param outputFormat
 * the `Format` in which the new instance is to transcode the tracks of
 * `inputDataSource`
 */
(
        /**
         * The `DataSource` which has its tracks transcoded by this instance.
         */
        private val inputDataSource: DataSource,
        /**
         * The `Format` in which the tracks of `inputDataSource` are transcoded.
         */
        private val outputFormat: Format) : DataSource(inputDataSource.locator) {
    /**
     * The `DataSource` which contains the transcoded tracks of `inputDataSource` and
     * which is wrapped by this instance. It is the output of `transcodingProcessor`.
     */
    private var outputDataSource: DataSource? = null
    /**
     * Returns this instance's `Processor` object
     *
     * @return this instance's `Processor` object
     */
    /**
     * The `Processor` which carries out the actual transcoding of the tracks of
     * `inputDataSource`.
     */
    var transcodingProcessor: Processor? = null
        private set

    /**
     * Implements [DataSource.connect]. Sets up the very transcoding process and just does
     * not start it i.e. creates a `Processor` on the `inputDataSource`, sets
     * `outputFormat` on its tracks (which support a `Format` compatible with
     * `outputFormat`) and connects to its `output DataSource`.
     *
     * @throws IOException
     * if creating the transcoding `Processor`, setting its `Format` or
     * connecting to it fails
     */
    @Synchronized
    @Throws(IOException::class)
    override fun connect() {
        if (outputDataSource != null) return

        /*
		 * Manager#createProcessor(DataSource) requires the specified DataSource to be connected.
		 */inputDataSource.connect()
        val processor: Processor
        processor = try {
            Manager.createProcessor(inputDataSource)
        } catch (npex: NoProcessorException) {
            val ioex = IOException()
            ioex.initCause(npex)
            throw ioex
        }
        val processorUtility = ProcessorUtility()
        if (!processorUtility.waitForState(processor, Processor.Configured)) throw IOException("Couldn't configure transcoding processor.")
        val trackControls = processor.trackControls
        if (trackControls != null) for (trackControl in trackControls) {
            val trackFormat = trackControl.format

            /*
				 * XXX We only care about AudioFormat here and we assume outputFormat is of such
				 * type because it is in our current and only use case of TranscodingDataSource
				 */
            if (trackFormat is AudioFormat && !trackFormat.matches(outputFormat)) {
                val supportedTrackFormats = trackControl.supportedFormats
                if (supportedTrackFormats != null) {
                    for (supportedTrackFormat in supportedTrackFormats) {
                        if (supportedTrackFormat.matches(outputFormat)) {
                            val intersectionFormat = supportedTrackFormat
                                    .intersects(outputFormat)
                            if (intersectionFormat != null) {
                                trackControl.format = intersectionFormat
                                break
                            }
                        }
                    }
                }
            }
        }
        if (!processorUtility.waitForState(processor, Processor.Realized)) throw IOException("Couldn't realize transcoding processor.")
        val outputDataSource = processor.dataOutput
        outputDataSource.connect()
        transcodingProcessor = processor
        this.outputDataSource = outputDataSource
    }

    /**
     * Implements [DataSource.disconnect]. Stops and undoes the whole setup of the very
     * transcoding process i.e. disconnects from the output `DataSource` of the
     * transcodingProcessor and disposes of the `transcodingProcessor`.
     */
    @Synchronized
    override fun disconnect() {
        if (outputDataSource == null) return
        try {
            stop()
        } catch (ioex: IOException) {
            throw UndeclaredThrowableException(ioex)
        }
        outputDataSource!!.disconnect()
        transcodingProcessor!!.deallocate()
        transcodingProcessor!!.close()
        transcodingProcessor = null
        outputDataSource = null
    }

    /**
     * Implements [DataSource.getContentType]. Delegates to the actual output of the
     * transcoding.
     *
     * @return a `String` value which describes the type of the content made available by
     * this `DataSource`
     */
    @Synchronized
    override fun getContentType(): String? {
        return if (outputDataSource == null) null else outputDataSource!!.contentType
    }

    /**
     * Implements [DataSource.getControl]. Delegates to the actual output of the
     * transcoding.
     *
     * @param controlType
     * a `String` value which names the type of the control to be retrieved
     * @return an `Object` which represents the control of this instance with the specified
     * type if such a control is available; otherwise, `null`
     */
    @Synchronized
    override fun getControl(controlType: String): Any {
        /*
		 * The Javadoc of DataSource#getControl(String) says it's an error to call the method
		 * without being connected and by that time we should have the outputDataSource.
		 */
        return outputDataSource!!.getControl(controlType)
    }

    /**
     * Implements [DataSource.getControls]. Delegates to the actual output of the
     * transcoding.
     *
     * @return an array of `Object`s which represent the controls available for this
     * instance
     */
    @Synchronized
    override fun getControls(): Array<Any> {
        return if (outputDataSource == null) ControlsAdapter.EMPTY_CONTROLS else outputDataSource!!.controls
    }

    /**
     * Implements [DataSource.getDuration]. Delegates to the actual output of the
     * transcoding.
     *
     * @return a `Time` value which describes the duration of the content made available by
     * this instance
     */
    @Synchronized
    override fun getDuration(): Time {
        return if (outputDataSource == null) DURATION_UNKNOWN else outputDataSource!!.duration
    }

    /**
     * Gets the output streams that this instance provides. Some of them may be the result of
     * transcoding the tracks of the input `DataSource` of this instance in the output
     * `Format` of this instance.
     *
     * @return an array of `SourceStream`s which represents the collection of output streams
     * that this instance provides
     */
    @get:Synchronized
    val streams: Array<out SourceStream?>
        get() {
            if (outputDataSource is PushBufferDataSource) return (outputDataSource as PushBufferDataSource).streams
            if (outputDataSource is PullBufferDataSource) return (outputDataSource as PullBufferDataSource).streams
            if (outputDataSource is PushDataSource) return (outputDataSource as PushDataSource).streams
            return if (outputDataSource is PullDataSource) (outputDataSource as PullDataSource).streams else arrayOfNulls(0)
        }

    /**
     * Implements [DataSource.start]. Starts the actual transcoding process already set up
     * with [.connect].
     *
     * @throws IOException
     * if starting the transcoding fails
     */
    @Synchronized
    @Throws(IOException::class)
    override fun start() {
        /*
		 * The Javadoc of DataSource#start() says it's an error to call the method without being
		 * connected and by that time we should have the outputDataSource.
		 */
        outputDataSource!!.start()
        transcodingProcessor!!.start()
    }

    /**
     * Implements [DataSource.stop]. Stops the actual transcoding process if it has already
     * been set up with [.connect].
     *
     * @throws IOException
     * if stopping the transcoding fails
     */
    @Synchronized
    @Throws(IOException::class)
    override fun stop() {
        if (outputDataSource != null) {
            transcodingProcessor!!.stop()
            outputDataSource!!.stop()
        }
    }
}