package com.example.cameraoptimize

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.os.Bundle
import android.util.Size
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import com.example.cameraoptimize.ui.theme.CameraOptimizeTheme


class MainActivity : ComponentActivity() {

    private lateinit var imageReader: ImageReader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        val cameraIdList = cameraManager.cameraIdList

        val cameraId = cameraIdList.find { currentId ->
            cameraManager
                .getCameraCharacteristics(currentId)
                .get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_BACK
        } ?: throw Exception()

        val config: StreamConfigurationMap? =
            cameraManager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        val sizesForJPEG: Array<Size> = config?.getOutputSizes(ImageFormat.JPEG) ?: throw Exception()

        var bestFitJPEG = 0

        for (i in sizesForJPEG.indices) {
            if (sizesForJPEG[i].width <= 500 && sizesForJPEG[i].height <= 500) {
                bestFitJPEG = i
                break
            }
        }

        imageReader = ImageReader.newInstance(
            sizesForJPEG[bestFitJPEG].width,
            sizesForJPEG[bestFitJPEG].height,
            ImageFormat.JPEG,
            3
        )

        setContent {
            CameraOptimizeTheme {
                val cameraDevice = remember { mutableStateOf<CameraDevice?>(null) }

                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                        override fun onOpened(camera: CameraDevice) {
                            cameraDevice.value = camera
                        }

                        override fun onDisconnected(camera: CameraDevice) {
                            TODO("Not yet implemented")
                        }

                        override fun onError(camera: CameraDevice, error: Int) {
                            TODO("Not yet implemented")
                        }
                    }, null)
                }

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (cameraDevice.value != null){
                        CameraPreview(camera = cameraDevice.value!!, imageReader =imageReader)
                    }
                }
            }
        }
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    camera: CameraDevice,
    imageReader: ImageReader
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val previewView = SurfaceView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            previewView.holder.addCallback(object: SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    camera.createCaptureSession(listOf(previewView.holder.surface, imageReader.surface), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            val captureRequestBuilder =
                                session.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                            captureRequestBuilder.set(
                                CaptureRequest.FLASH_MODE,
                                CaptureRequest.FLASH_MODE_TORCH
                            )
                            captureRequestBuilder.addTarget(imageReader.surface)
                            captureRequestBuilder.addTarget(previewView.holder.surface)
                            session.setRepeatingRequest(captureRequestBuilder.build(), null, null)

                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            TODO("Not yet implemented")
                        }
                    }, null)
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                }

                override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int
                ) {
                }
            })

            previewView
        }
    )
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
            text = "Hello $name!",
            modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    CameraOptimizeTheme {
        Greeting("Android")
    }
}