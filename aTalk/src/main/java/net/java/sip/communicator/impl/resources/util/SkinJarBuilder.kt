/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.impl.resources.util

import net.java.sip.communicator.service.resources.ResourcePack
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Class for building of skin bundles from zip files.
 * @author Adam Netocny
 * @author Eng Chong Meng
 */
object SkinJarBuilder {
    /**
     * Creates bundle from zip file.
     * @param srv `ResourcePack` containing class files and manifest
     * for the SkinResourcePack.
     * @param zip Zip file with skin contents.
     * @return Jar `File`.
     * @throws Exception When something goes wrong.
     */
    @Throws(Exception::class)
    fun createBundleFromZip(zip: File?, srv: ResourcePack): File {
        val tmpDir = unzipIntoTmp(zip)
        var tmpDir2 = findBase(tmpDir)
        if (tmpDir2 == null) {
            tmpDir2 = tmpDir
        }
        if (!test(tmpDir2)) {
            deleteDir(tmpDir)
            throw Exception("Zip file doesn't contain all necessary files and folders.")
        }
        cpTmp(tmpDir2, srv)
        val jar = insertIntoZip(tmpDir2)
        deleteDir(tmpDir)
        return jar
    }

    /**
     * Creates a copy of skinresources.jar in temp folder.
     *
     * @param unzippedBase Base dir where files should appear.
     * @param srv `ResourcePack` containing class files and manifest
     * for the SkinResourcePack.
     * @throws IOException Is thrown if the jar cannot be located or if a file
     * operation goes wrong.
     */
    @Throws(IOException::class)
    private fun cpTmp(unzippedBase: File?, srv: ResourcePack) {
        var `in` = srv.javaClass.classLoader!!
                .getResourceAsStream(
                        "resources/skinresourcepack/SkinResourcePack.class")
        var dest = File(unzippedBase, "net" + File.separatorChar + "java"
                + File.separatorChar + "sip" + File.separatorChar
                + "communicator" + File.separatorChar + "plugin"
                + File.separatorChar + "skinresourcepack")
        if (!dest.mkdirs()) {
            throw IOException("Unable to build resource pack.")
        }
        var out = FileOutputStream(File(dest, "SkinResourcePack.class"))
        copy(`in`, out)
        `in` = srv.javaClass.classLoader!!
                .getResourceAsStream("resources/skinresourcepack/skinresourcepack.manifest.mf")
        dest = File(unzippedBase, "META-INF")
        if (!dest.mkdirs()) {
            throw IOException("Unable to build resource pack.")
        }
        out = FileOutputStream(File(dest, "MANIFEST.MF"))
        copy(`in`, out)
    }

    /**
     * Simple file copy operation.
     * @param in `InputStream` for the source.
     * @param out `OutputStream` for the destination file.
     * @throws IOException Is thrown if the jar cannot be located or if a file
     * operation goes wrong.
     */
    @Throws(IOException::class)
    private fun copy(`in`: InputStream, out: OutputStream) {
        val buf = ByteArray(1024)
        var len: Int
        while (`in`.read(buf).also { len = it } > 0) {
            out.write(buf, 0, len)
        }
        `in`.close()
        out.close()
    }

    /**
     * Unzips a specified `File` to temp folder.
     *
     * @param zip ZIP `File` to be unzipped.
     * @return temporary directory with the content of the ZIP file.
     * @throws IOException Is thrown if a file operation goes wrong.
     */
    @Throws(IOException::class)
    private fun unzipIntoTmp(zip: File?): File {
        val dest = File.createTempFile("zip", null)
        if (!dest.delete()) throw IOException("Cannot unzip given zip file")
        if (!dest.mkdirs()) throw IOException("Cannot unzip given zip file")
        val archive = ZipFile(zip)
        try {
            val e = archive.entries()
            if (e.hasMoreElements()) {
                val buffer = ByteArray(8192)
                while (e.hasMoreElements()) {
                    val entry = e.nextElement()
                    val file = File(dest, entry.name)
                    if (entry.isDirectory && !file.exists()) {
                        file.mkdirs()
                    } else {
                        val parentFile = file.parentFile
                        if (!parentFile!!.exists()) parentFile.mkdirs()
                        val `in` = archive.getInputStream(entry)
                        try {
                            val out = BufferedOutputStream(
                                    FileOutputStream(file))
                            try {
                                var read: Int
                                while (-1 != `in`.read(buffer).also { read = it }) out.write(buffer, 0, read)
                            } finally {
                                out.close()
                            }
                        } finally {
                            `in`.close()
                        }
                    }
                }
            }
        } finally {
            archive.close()
        }
        return dest
    }

    /**
     * Inserts files into ZIP file.
     *
     * @param tmpDir Folder which contains the data.
     * @return `File` containing reference of the jar file.
     * @throws IOException Is thrown if a file operation goes wrong.
     */
    @Throws(IOException::class)
    private fun insertIntoZip(tmpDir: File?): File {
        val jar = File.createTempFile("skinresourcepack", ".jar")
        val out = ZipOutputStream(FileOutputStream(jar))
        zipDir(tmpDir!!.absolutePath, out)
        out.close()
        return jar
    }

    /**
     * Zips the content of a folder.
     * @param dir2zip Path to the directory with the data to be stored.
     * @param zos Opened `ZipOutputStream` in which will be information
     * stored.
     * @throws IOException Is thrown if a file operation goes wrong.
     */
    @Throws(IOException::class)
    private fun zipDir(dir2zip: String, zos: ZipOutputStream) {
        val directory = File(dir2zip)
        zip(directory, directory, zos)
    }

    /**
     * Zips a file.
     * @param directory Path to the dir with the data to be stored.
     * @param base Base path for cutting paths into zip entries.
     * @param zos Opened `ZipOutputStream` in which will be information
     * stored.
     * @throws IOException Is thrown if a file operation goes wrong.
     */
    @Throws(IOException::class)
    private fun zip(directory: File, base: File, zos: ZipOutputStream) {
        val files = directory.listFiles()
        val buffer = ByteArray(8192)
        var read = 0
        var i = 0
        val n = files!!.size
        while (i < n) {
            if (files[i].isDirectory) {
                zip(files[i], base, zos)
            } else {
                val `in` = FileInputStream(files[i])
                val entry = ZipEntry(files[i].path.substring(
                        base.path.length + 1))
                zos.putNextEntry(entry)
                while (-1 != `in`.read(buffer).also { read = it }) {
                    zos.write(buffer, 0, read)
                }
                `in`.close()
            }
            i++
        }
    }

    /**
     * Deletes a directory with all its sub-directories.
     *
     * @param tmp the directory to be deleted
     */
    private fun deleteDir(tmp: File) {
        if (tmp.exists()) {
            val files = tmp.listFiles()
            for (i in files!!.indices) {
                if (files[i].isDirectory) {
                    deleteDir(files[i])
                } else {
                    files[i].delete()
                }
            }
            tmp.delete()
        }
    }

    /**
     * Tests if the content of a folder has the same structure as the skin
     * content.
     *
     * @param tmpDir Directory to be tested.
     * @return `true` - if the directory contains valid skin, else
     * `false`.
     */
    private fun test(tmpDir: File?): Boolean {
        var colors = false
        var images = false
        var styles = false
        val list = tmpDir!!.listFiles() ?: return false
        for (f in list) {
            if (f.name == "info.properties") {
                if (!f.isFile) {
                    return false
                }
            } else if (f.name == "colors") {
                if (f.isFile) {
                    return false
                }
                val ff = f.listFiles() ?: return false
                for (x in ff) {
                    if (x.name == "colors.properties") {
                        colors = true
                    }
                }
            } else if (f.name == "images") {
                if (f.isFile) {
                    return false
                }
                val ff = f.listFiles() ?: return false
                for (x in ff) {
                    if (x.name == "images.properties") {
                        images = true
                    }
                }
            } else if (f.name == "styles") {
                if (f.isFile) {
                    return false
                }
                val ff = f.listFiles() ?: return false
                for (x in ff) {
                    if (x.name == "styles.properties") {
                        styles = true
                    }
                }
            }
        }
        return styles || colors || images
    }

    /**
     * Moves to top level directory for unzipped files. (e.g.
     * /dir/info.properties will be changed to /info.properties.)
     * @param tmpDir Directory in which is the skin unzipped.
     * @return the top level directory
     */
    private fun findBase(tmpDir: File): File? {
        val list = tmpDir.listFiles() ?: return null
        var test = false
        for (f in list) {
            if (f.name == "info.properties") {
                if (f.isFile) {
                    test = true
                }
            }
        }
        return if (!test) {
            if (list.size != 0) {
                var tmp: File? = null
                for (f in list) {
                    if (f.isDirectory) {
                        val tmp2 = findBase(f)
                        if (tmp2 != null && tmp == null) {
                            tmp = tmp2
                        }
                    }
                }
                tmp
            } else {
                null
            }
        } else tmpDir
    }
}