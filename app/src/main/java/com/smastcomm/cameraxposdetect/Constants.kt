package com.smastcomm.cameraxposdetect

import android.Manifest

object Constants {
    const val TAG = "LogApp"
    const val FILE_NAME_FORMAT = "yy-MM-dd-HH-mm-ss-sss"
    const val REQUEST_CODE_PERMISSION = 101
    val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        //Manifest.permission.READ_EXTERNAL_STORAGE
    )
}