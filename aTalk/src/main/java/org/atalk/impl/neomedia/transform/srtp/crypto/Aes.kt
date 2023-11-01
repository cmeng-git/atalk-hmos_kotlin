/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.srtp.crypto

import android.text.TextUtils
import org.bouncycastle.crypto.BlockCipher
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.params.KeyParameter
import timber.log.Timber
import java.security.Provider
import java.util.*

/**
 * Implements a factory for an AES `BlockCipher`.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
object Aes {
    /**
     * The block size in bytes of the AES algorithm (implemented by the `BlockCipher`s
     * initialized by the `Aes` class).
     */
    private const val BLOCK_SIZE = 16

    /**
     * The simple name of the `BlockCipherFactory` class/interface which is used as a class
     * name suffix by the well-known `BlockCipherFactory` implementations.
     */
    private const val BLOCK_CIPHER_FACTORY_SIMPLE_CLASS_NAME = "BlockCipherFactory"

    /**
     * The `BlockCipherFactory` implemented with BouncyCastle. It is the well-known fallback.
     */
    private val BOUNCYCASTLE_FACTORY = BouncyCastleBlockCipherFactory()

    /**
     * The `BlockCipherFactory` implementations known to the `Aes`
     * class among which the fastest is to be elected as [.factory].
     */
    private var factories: Array<BlockCipherFactory?>? = null

    /**
     * The `BlockCipherFactory` implementation which is (to be) used by the class
     * `Aes` to initialize `BlockCipher`s.
     */
    private var factory: BlockCipherFactory? = null

    /**
     * The name of the class to instantiate as a `BlockCipherFactory`
     * implementation to be used by the class `Aes` to initialize `BlockCipher`s.
     */
    private var FACTORY_CLASS_NAME: String? = null

    /**
     * The `Class`es of the well-known `BlockCipherFactory` implementations.
     */
    private val FACTORY_CLASSES = arrayOf<Class<*>>(
            BouncyCastleBlockCipherFactory::class.java,
            SunJCEBlockCipherFactory::class.java,
            SunPKCS11BlockCipherFactory::class.java
    )

    /**
     * The number of milliseconds after which the benchmark which elected [.factory] is to be considered expired.
     */
    private const val FACTORY_TIMEOUT = 60 * 1000L

    /**
     * The class to instantiate as a `BlockCipherFactory` implementation to be used to initialized `BlockCipher`s.
     *
     * @see .FACTORY_CLASS_NAME
     */
    private var factoryClass: Class<out BlockCipherFactory?>? = null

    /**
     * The time in milliseconds at which [.factories] were benchMarked and [.factory] was elected.
     */
    private var factoryTimestamp = 0L

    /**
     * The input buffer to be used for the benchmarking of [.factories]. It consists of blocks
     * and its length specifies the number of blocks to process for the purposes of the benchmark.
     */
    private val inBuf = ByteArray(BLOCK_SIZE * 1024)

    /**
     * The output buffer to be used for the benchmarking of [.factories].
     */
    private val outBuf = ByteArray(BLOCK_SIZE)

    /**
     * The random number generator which generates keys and inputs for the benchmarking of the
     * `BlockCipherFactory` implementations.
     */
    private val random = Random()

    /**
     * Set the class to use as the factory class for AES cryptography.
     *
     * @param name the name of the class
     */
    @Synchronized
    fun setFactoryClassName(name: String?) {
        FACTORY_CLASS_NAME = name
        factoryClass = null
    }

    /**
     * Benchmarks a specific array/list of `BlockCipherFactory` instances
     * and returns the fastest-performing element.
     *
     * @param factories the `BlockCipherFactory` instances to benchmark
     * @param keySize AES key size (16, 24, 32 bytes)
     * @return the fastest-performing `BlockCipherFactory` among the specified `factories`
     */
    private fun benchmark(factories: Array<BlockCipherFactory?>?, keySize: Int): BlockCipherFactory? {
        val random = random
        val key = ByteArray(keySize)
        val inBuf = inBuf

        random.nextBytes(key)
        random.nextBytes(inBuf)

        val params = KeyParameter(key)
        val blockSize = BLOCK_SIZE
        val inEnd = inBuf.size - blockSize + 1
        val out = outBuf
        var minTime = Long.MAX_VALUE
        var minFactory: BlockCipherFactory? = null

        // Log information for the purposes of debugging.
        val log = StringBuilder()
        for (f in factories!!.indices) {
            val factory = factories[f] ?: continue

            try {
                val cipher = factory.createBlockCipher(keySize)
                if (cipher == null) {
                    // The BlockCipherFactory failed to initialize a new
                    // BlockCipher instance. We will not use it again because
                    // the failure may persist.
                    factories[f] = null
                }
                else {
                    cipher.init(true, params)
                    val startTime = System.nanoTime()
                    var inOff = 0

                    while (inOff < inEnd) {
                        cipher.processBlock(inBuf, inOff, out, 0)
                        inOff += blockSize
                    }

                    // We do not invoke the method BlockCipher.reset() so we do
                    // not need to take it into account in the benchmark.
                    val endTime = System.nanoTime()
                    val time = endTime - startTime

                    if (time < minTime) {
                        minTime = time
                        minFactory = factory
                    }

                    if (log.isNotEmpty()) log.append(", ")
                    log.append(getSimpleClassName(factory))
                            .append(' ')
                            .append(time)
                }
            } catch (t: Throwable) {
                if (t is InterruptedException)
                    Thread.currentThread().interrupt()
                else if (t is ThreadDeath)
                    throw t
            }
        }
        if (log.isNotEmpty()) {
            Timber.i("AES benchmark (of execution times expressed in nanoseconds): %s", log)
        }
        return minFactory
    }

    /**
     * Initializes a new `BlockCipher` instance which implements Advanced Encryption Standard (AES).
     *
     * @param keySize length of the AES key (16, 24, 32 bytes)
     * @return a new `BlockCipher` instance which implements Advanced Encryption Standard (AES)
     */
    fun createBlockCipher(keySize: Int): BlockCipher? {
        var factory: BlockCipherFactory?
        synchronized(Aes::class.java) {
            val now = System.currentTimeMillis()
            factory = Aes.factory
            if (factory != null && now > factoryTimestamp + FACTORY_TIMEOUT)
                factory = null

            if (factory == null) {
                try {
                    factory = getBlockCipherFactory(keySize)
                } catch (t: Throwable) {
                    when (t) {
                        is InterruptedException -> {
                            Thread.currentThread().interrupt()
                        }
                        is ThreadDeath -> {
                            throw t
                        }
                        else -> {
                            Timber.w("Failed to initialize an optimized AES implementation: %s", t.localizedMessage)
                        }
                    }
                } finally {
                    if (factory == null) {
                        factory = Aes.factory
                        if (factory == null)
                            factory = BOUNCYCASTLE_FACTORY
                    }
                    factoryTimestamp = now
                    if (Aes.factory != factory) {
                        Aes.factory = factory
                        // Simplify the name of the BlockCipherFactory class to
                        // be employed for the purposes of brevity and ease.
                        Timber.i("Will employ AES implemented by %s", getSimpleClassName(Aes.factory))
                    }
                }
            }
        }

        return try {
            factory!!.createBlockCipher(keySize)
        } catch (ex: Exception) {
            if (ex is RuntimeException)
                throw ex
            else
                throw RuntimeException(ex)
        }
    }

    // Support specifying FACTORY_CLASS_NAME without a package and
    // without BlockCipherFactory at the end for the purposes of brevity and ease.
    private val effectiveFactoryClassName: String?
        get() {
            var factoryClassName = FACTORY_CLASS_NAME
            if (factoryClassName == null || factoryClassName.isEmpty()) {
                return null
            }

            // Support specifying FACTORY_CLASS_NAME without a package and
            // without BlockCipherFactory at the end for the purposes of brevity and ease.
            if (Character.isUpperCase(factoryClassName[0])
                    && !factoryClassName.contains(".")
                    && !factoryClassName.endsWith(BLOCK_CIPHER_FACTORY_SIMPLE_CLASS_NAME)) {
                factoryClassName = Aes::class.java.name + "$" + factoryClassName + BLOCK_CIPHER_FACTORY_SIMPLE_CLASS_NAME
            }
            return factoryClassName
        }

    /**
     * Initializes the `BlockCipherFactory` instances to be benchmarked by the class
     * `Aes` and among which the fastest-performing one is to be selected.
     *
     * @return the `BlockCipherFactory` instances to be benchmarked by the class `AES`
     * and among which the fastest-performing one is to be selected
     */
    private fun createBlockCipherFactories(): Array<BlockCipherFactory?> {
        // The user may have specified a specific BlockCipherFactory class
        // (name) through setFactoryClassName(String). Practically, the specified FACTORY_CLASS_NAME
        // will override all other FACTORY_CLASSES and, consequently, it does
        // not seem necessary to try FACTORY_CLASSES at all. Technically though,
        // the specified BlockCipherFactory may malfunction. That is why all
        // FACTORY_CLASSES are tried as well and FACTORY_CLASS_NAME is selected
        // later on after it has proven itself functional.
        var factoryClass = factoryClass
        var factoryClasses = FACTORY_CLASSES
        var add = true

        if (factoryClass == null) {
            val factoryClassName = effectiveFactoryClassName

            if (factoryClassName != null) {
                // Is the specified FACTORY_CLASS_NAME one of the well-known
                // FACTORY_CLASSES? If it is, then we do not have to invoke the
                // method Class.forName(String) and add a new Class to FACTORY_CLASSES.
                for (clazz in factoryClasses) {
                    if (clazz.name == factoryClassName
                            && BlockCipherFactory::class.java.isAssignableFrom(clazz)) {
                        factoryClass = clazz as Class<out BlockCipherFactory>
                        Aes.factoryClass = factoryClass
                        add = false
                        break
                    }
                }

                // If FACTORY_CLASS_NAME does not specify a well-known Class, find and load the Class.
                if (add) {
                    try {
                        val clazz = Class.forName(factoryClassName)
                        if (BlockCipherFactory::class.java.isAssignableFrom(clazz)) {
                            factoryClass = clazz as Class<out BlockCipherFactory>
                            Aes.factoryClass = factoryClass
                        }
                    } catch (t: Throwable) {
                        when (t) {
                            is InterruptedException -> {
                                Thread.currentThread().interrupt()
                            }
                            is ThreadDeath -> {
                                throw t
                            }
                            else -> {
                                Timber.w("Failed to employ class %s as an AES implementation: %s",
                                        factoryClassName, t.localizedMessage)
                            }
                        }
                    }
                }
            }
        }

        // If FACTORY_CLASS_NAME does not specify a well-known Class, add the new Class to FACTORY_CLASSES.
        if (add && factoryClass != null) {
            for (clazz in factoryClasses) {
                if (factoryClass == clazz) {
                    add = false
                    break
                }
            }
            if (add) {
                val newFactoryClasses = ArrayList<Class<*>>()
                System.arraycopy(factoryClasses, 0, newFactoryClasses, 1, factoryClasses.size)
                factoryClasses = newFactoryClasses.toTypedArray()
            }
        }
        return createBlockCipherFactories(factoryClasses)
    }

    /**
     * Initializes `BlockCipherFactory` instances of specific `Class`es.
     *
     * @param classes the runtime `Class`es to instantiate
     * @return the `BlockCipherFactory` instances initialized by the specified `classes`
     */
    private fun createBlockCipherFactories(classes: Array<Class<*>>): Array<BlockCipherFactory?> {
        val factories = arrayOfNulls<BlockCipherFactory>(classes.size)
        var i = 0
        for (clazz in classes) {
            try {
                if (BlockCipherFactory::class.java.isAssignableFrom(clazz)) {
                    val factory = when (BouncyCastleBlockCipherFactory::class.java) {
                        clazz -> BOUNCYCASTLE_FACTORY
                        else -> clazz.newInstance() as BlockCipherFactory
                    }
                    factories[i++] = factory
                }
            } catch (t: Throwable) {
                if (t is InterruptedException) Thread.currentThread().interrupt()
                else if (t is ThreadDeath) throw t
            }
        }
        return factories
    }

    /**
     * Gets a `BlockCipherFactory` instance to be used by the `AES` class to
     * initialize `BlockCipher`s.
     *
     * Benchmarks the well-known `BlockCipherFactory` implementations and returns the fastest one.
     *
     * @param keySize AES key size (16, 24, 32 bytes)
     * @return a `BlockCipherFactory` instance to be used by the
     * `Aes` class to initialize `BlockCipher`s
     */
    private fun getBlockCipherFactory(keySize: Int): BlockCipherFactory? {
        var factories = factories
        if (factories == null) {
            // A single instance of each well-known BlockCipherFactory
            // implementation will be initialized i.e. the attempt to initialize
            // BlockCipherFactory instances will be made once only.
            factories = createBlockCipherFactories()
            Aes.factories = factories
        }

        // Benchmark the BlockCiphers provided by the available
        // BlockCipherFactories in order to select the fastest-performing BlockCipherFactory.
        var minFactory = benchmark(factories, keySize)

        // The user may have specified a specific BlockCipherFactory class
        // (name) through setFactoryClassName(String), Practically, FACTORY_CLASS_NAME may override
        // minFactory and, consequently, it may appear that the benchmark is
        // unnecessary. Technically though, the specified BlockCipherFactory may
        // malfunction. That is why FACTORY_CLASS_NAME is selected after it has proven itself functional.
        run {
            val factoryClass = factoryClass
            if (factoryClass != null) {
                for (factory in factories) {
                    if (factory != null && factory.javaClass == factoryClass) {
                        minFactory = factory
                        break
                    }
                }
            }
        }
        return minFactory
    }

    /**
     * Gets the simple name of the runtime `Class` of a specific `BlockCipherFactory`
     * to be used for display purposes of brevity and readability.
     *
     * @param factory the `BlockCipherFactory` for which a simple class name is to be returned
     * @return the simple name of the runtime `Class` of the specified `factory` to be
     * used for display purposes of brevity and readability
     */
    private fun getSimpleClassName(factory: BlockCipherFactory?): String {
        val clazz: Class<*> = factory!!.javaClass
        var className = clazz.simpleName
        if (TextUtils.isEmpty(className)) className = clazz.name
        val suffix = BLOCK_CIPHER_FACTORY_SIMPLE_CLASS_NAME
        if (className.endsWith(suffix)) {
            val simpleClassName = className.substring(0, className.length - suffix.length)
            var prefix = Aes::class.java.name + "$"
            if (simpleClassName.startsWith(prefix)) {
                className = simpleClassName.substring(prefix.length)
            }
            else if (simpleClassName.contains(".")) {
                val pkg = Aes::class.java.getPackage()
                if (pkg != null) {
                    prefix = pkg.name + "."
                    if (simpleClassName.startsWith(prefix)) className = simpleClassName.substring(prefix.length)
                }
            }
            else {
                className = simpleClassName
            }
        }
        return className
    }

    /**
     * Implements `BlockCipherFactory` using BouncyCastle.
     */
    class BouncyCastleBlockCipherFactory : BlockCipherFactory {
        /**
         * {@inheritDoc}
         */
        @Throws(Exception::class)
        override fun createBlockCipher(keySize: Int): BlockCipher {
            // The value of keySize can be ignored for BouncyCastle, it
            // determines the AES algorithm to be used with the KeyParameter.
            return AESEngine()
        }
    }

    /**
     * Implements `BlockCipherFactory` using Sun JCE.
     */
    class SunJCEBlockCipherFactory
    /**
     * Initializes a new `SunJCEBlockCipherFactory` instance.
     */
        : SecurityProviderBlockCipherFactory("AES_<size>/ECB/NoPadding", "SunJCE")

    /**
     * Implements `BlockCipherFactory` using Sun PKCS#11.
     */
    object SunPKCS11BlockCipherFactory : SecurityProviderBlockCipherFactory("AES_<size>/ECB/NoPadding", SunPKCS11BlockCipherFactory.getProvider()) {
        /**
         * The `java.security.Provider` instance (to be) employed for an (optimized) AES implementation.
         */
        private var provider: Provider? = null

        /**
         * The indicator which determines whether [.provider] is to be used. If `true`,
         * an attempt will be made to initialize a `java.security.Provider` instance. If the
         * attempt fails, `false` will be assigned in order to not repeatedly attempt the
         * initialization which is known to have failed.
         */
        private var useProvider = true

        /**
         * The `java.security.Provider` instance (to be) employed for an (optimized) AES implementation.
         */
        @Synchronized
        @Throws(java.lang.Exception::class)
        private fun getProvider(): Provider? {
            // var provider = provider
            if (provider == null && useProvider) {
                try {
                    val clazz = Class.forName("sun.security.pkcs11.SunPKCS11")
                    if (Provider::class.java.isAssignableFrom(clazz)) {
                        val constructor = clazz.getConstructor(String::class.java)

                        // The SunPKCS11 Config name should be unique in order
                        // to avoid repeated initialization exceptions.
                        var name: String? = null
                        val pkg = Aes::class.java.getPackage()
                        if (pkg != null) name = pkg.name
                        if (name == null || name.isEmpty()) name = "org.atalk.impl.neomedia.transform.srtp"
                        provider = constructor.newInstance("--name=" + name + "\\n"
                                + "nssDbMode=noDb\\n" + "attributes=compatibility") as Provider
                    }
                } finally {
                    if (provider == null) useProvider = false // else this.provider = provider
                }
            }
            return provider
        }
    }
}
