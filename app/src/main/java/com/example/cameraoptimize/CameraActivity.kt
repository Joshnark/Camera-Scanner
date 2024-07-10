package com.example.cameraoptimize

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import com.example.cameraoptimize.camera.CameraPreview

class CameraActivity : ComponentActivity() {

    private lateinit var cameraPreview: CameraPreview

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        cameraPreview = findViewById(R.id.preview)
        findViewById<View>(R.id.picture).setOnClickListener {
            cameraPreview.takePicture()
        }
    }

    override fun onResume() {
        super.onResume()
        cameraPreview.resume()
    }

    override fun onPause() {
        cameraPreview.pause()
        super.onPause()
    }
}