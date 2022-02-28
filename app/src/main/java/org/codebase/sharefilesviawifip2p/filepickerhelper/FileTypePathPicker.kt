package org.codebase.sharefilesviawifip2p.filepickerhelper

import android.os.Build
import android.os.Environment
import androidx.annotation.RequiresApi
import java.io.File

@RequiresApi(Build.VERSION_CODES.KITKAT)

class FileTypePathPicker {

    companion object {
        /**
         * MEDIA_TYPE_UNKNOWN: a constant representing a file type that is not corresponding to known PDF, AUDIO, VIDEO,
         * IMAGE mime types.
         */
        const val MEDIA_TYPE_UNKNOWN = 0

        /**
         * MEDIA_TYPE_IMAGE: a constant representing IMAGE file types
         */
        const val MEDIA_TYPE_IMAGE = 1

        /**
         * MEDIA_TYPE_VIDEO: a constant representing VIDEO file types
         */
        const val MEDIA_TYPE_VIDEO = 2

        /**
         * MEDIA_TYPE_PDF: a constant representing PDF file types
         */
        const val MEDIA_TYPE_PDF = 3

        /**
         * MEDIA_TYPE_AUDIO: a constant representing AUDIO file types
         */
        const val MEDIA_TYPE_AUDIO = 4

        /**
         * This checks if there exists a external storage on the remote device, and if that external storage has
         * available space that can be written to.
         *
         * @return a true/false value if the external storage could be written to.
         */
        fun isExternalStorageWritable(): Boolean {
            val state = Environment.getExternalStorageState()
            return Environment.MEDIA_MOUNTED == state
        }

        /**
         * Gets the name of the directory on the remote device storing the various IMAGE, AUDIO, VIDEO, PDF, DOWNLOADS
         * file types.
         *
         * @param fileName name of the file to be sent
         * @return File object with a path that contains that directory name.
         */

        fun getPublicStorageDir(fileName: String): File {
            val mediaType = getFileType(fileName)
            val directoryType: String
            when(mediaType) {
                MEDIA_TYPE_IMAGE -> directoryType = Environment.DIRECTORY_PICTURES
                MEDIA_TYPE_VIDEO -> directoryType = Environment.DIRECTORY_MOVIES
                MEDIA_TYPE_AUDIO -> directoryType = Environment.DIRECTORY_MUSIC
                MEDIA_TYPE_PDF -> directoryType = Environment.DIRECTORY_DOCUMENTS
                else -> directoryType = Environment.DIRECTORY_DOWNLOADS
            }
            return File(Environment.getExternalStoragePublicDirectory(directoryType), fileName)
        }

        /**
         * Gets the type of file, based on the extension in the file name.
         *
         * @param fileName
         * @return integer value corresponding to the file type constants declared above.
         */

        private fun getFileType(fileName: String): Int {
            var extension: String? = null
            val i = fileName.lastIndexOf('.')
            if (i > 0) {
                extension = fileName.substring(i + 1)
            }
            if (extension == null) {
                return MEDIA_TYPE_UNKNOWN
            }
            return when (extension) {
                "png", "jpg" -> MEDIA_TYPE_IMAGE
                "pdf" -> MEDIA_TYPE_PDF
                "mp3" -> MEDIA_TYPE_AUDIO
                "mp4" -> MEDIA_TYPE_VIDEO
                else -> MEDIA_TYPE_UNKNOWN
            }
        }
    }
}