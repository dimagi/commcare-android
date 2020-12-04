package org.commcare

import android.os.StatFs

object DiskUtils {

    /**
     * Calculates and returns the amount of used disk space in bytes at the specified path.
     *
     * @param path Filesystem path at which to make the space calculations
     * @return Amount of disk space used, in bytes
     */
     @JvmStatic
     fun calculateFreeDiskSpaceInBytes(path: String?): Long {
        val statFs = StatFs(path)
        val blockSizeBytes = statFs.blockSize.toLong()
        return blockSizeBytes * statFs.availableBlocks
    }
}