/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util

import java.io.PrintStream

/**
 * This class provides a PrintWriter implementation that we use to replace
 * System.out so that we could capture output from all libs or SC code that
 * uses calls to System.out.println();
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class ScStdOut(printStream: PrintStream?) : PrintStream(printStream) {
    /**
     * Returns the default System.out `PrintStream` that was in use
     * before this class was instantiated.
     *
     * @return the original System.out PrintStream
     */
    /**
     * This PrintStream contains System.out when the class were initiated.
     * Normally that would be the system default System.out
     */
    val systemOut: PrintStream

    init {
        systemOut = System.out
    }

    /**
     * Prints `string` if `stdOutPrintingEnabled` is enabled.
     *
     * @param string the `String` to print.
     */
    override fun print(string: String) {
        if (stdOutPrintingEnabled) super.print(string)
    }

    /**
     * Prints `x` if `stdOutPrintingEnabled` is enabled.
     *
     * @param x the `boolean` to print.
     */
    override fun println(x: Boolean) {
        if (stdOutPrintingEnabled) super.println(x)
    }

    /**
     * Prints `x` if `stdOutPrintingEnabled` is enabled.
     *
     * @param x the `char` to print.
     */
    override fun println(x: Char) {
        if (stdOutPrintingEnabled) super.println(x)
    }

    /**
     * Prints `x` if `stdOutPrintingEnabled` is enabled.
     *
     * @param x the `char[]` to print.
     */
    override fun println(x: CharArray) {
        if (stdOutPrintingEnabled) super.println(x)
    }

    /**
     * Prints `x` if `stdOutPrintingEnabled` is enabled.
     *
     * @param x the `double` to print.
     */
    override fun println(x: Double) {
        if (stdOutPrintingEnabled) super.println(x)
    }

    /**
     * Prints `x` if `stdOutPrintingEnabled` is enabled.
     *
     * @param x the `float` to print.
     */
    override fun println(x: Float) {
        if (stdOutPrintingEnabled) super.println(x)
    }

    /**
     * Prints `x` if `stdOutPrintingEnabled` is enabled.
     *
     * @param x the `int` to print.
     */
    override fun println(x: Int) {
        if (stdOutPrintingEnabled) super.println(x)
    }

    /**
     * Prints `x` if `stdOutPrintingEnabled` is enabled.
     *
     * @param x the `long` to print.
     */
    override fun println(x: Long) {
        if (stdOutPrintingEnabled) super.println(x)
    }

    /**
     * Prints `x` if `stdOutPrintingEnabled` is enabled.
     *
     * @param x the `Object` to print.
     */
    override fun println(x: Any) {
        if (stdOutPrintingEnabled) super.println(x)
    }

    /**
     * Prints `x` if `stdOutPrintingEnabled` is enabled.
     *
     * @param x the `String` to print.
     */
    override fun println(x: String) {
        if (stdOutPrintingEnabled) super.println(x)
    }

    /**
     * Prints `b` if `stdOutPrintingEnabled` is enabled.
     *
     * @param b the `boolean` to print.
     */
    override fun print(b: Boolean) {
        if (stdOutPrintingEnabled) super.print(b)
    }

    /**
     * Prints `c` if `stdOutPrintingEnabled` is enabled.
     *
     * @param c the `char` to print.
     */
    override fun print(c: Char) {
        if (stdOutPrintingEnabled) super.print(c)
    }

    /**
     * Prints `s` if `stdOutPrintingEnabled` is enabled.
     *
     * @param s the `char[]` to print.
     */
    override fun print(s: CharArray) {
        if (stdOutPrintingEnabled) super.print(s)
    }

    /**
     * Prints `d` if `stdOutPrintingEnabled` is enabled.
     *
     * @param d the `double` to print.
     */
    override fun print(d: Double) {
        if (stdOutPrintingEnabled) super.print(d)
    }

    /**
     * Prints `f` if `stdOutPrintingEnabled` is enabled.
     *
     * @param f the `float` to print.
     */
    override fun print(f: Float) {
        if (stdOutPrintingEnabled) super.print(f)
    }

    /**
     * Prints `i` if `stdOutPrintingEnabled` is enabled.
     *
     * @param i the `int` to print.
     */
    override fun print(i: Int) {
        if (stdOutPrintingEnabled) super.print(i)
    }

    /**
     * Prints `l` if `stdOutPrintingEnabled` is enabled.
     *
     * @param l the `long` to print.
     */
    override fun print(l: Long) {
        if (stdOutPrintingEnabled) super.print(l)
    }

    /**
     * Prints `obj` if `stdOutPrintingEnabled` is enabled.
     *
     * @param obj the `Object` to print.
     */
    override fun print(obj: Any) {
        if (stdOutPrintingEnabled) super.print(obj)
    }

    /**
     * Prints an empty line `stdOutPrintingEnabled` is enabled.
     */
    override fun println() {
        if (stdOutPrintingEnabled) super.println()
    }

    companion object {
        private var stdOutPrintingEnabled = false
        fun setStdOutPrintingEnabled(enabled: Boolean) {
            stdOutPrintingEnabled = enabled
        }
    }
}