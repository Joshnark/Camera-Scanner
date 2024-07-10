package com.example.cameraoptimize.camera

import android.util.SparseIntArray
import android.view.Surface

/**
 * Conversion from screen rotation to JPEG orientation.
 */
val ORIENTATIONS = SparseIntArray().apply {
    append(Surface.ROTATION_0, 90)
    append(Surface.ROTATION_90, 0)
    append(Surface.ROTATION_180, 270)
    append(Surface.ROTATION_270, 180)
}

const val IMAGE_BUFFER_SIZE: Int = 2

/**
 * Max preview width that is guaranteed by Camera2 API
 */
const val MAX_PREVIEW_WIDTH = 1920

/**
 * Max preview height that is guaranteed by Camera2 API
 */
const val MAX_PREVIEW_HEIGHT = 1080
