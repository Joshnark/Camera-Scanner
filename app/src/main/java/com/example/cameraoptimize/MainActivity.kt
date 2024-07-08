package com.example.cameraoptimize

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Point
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Display
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.LinearLayout.LayoutParams
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.max
import kotlin.math.min


class MainActivity : ComponentActivity() {

    private val cameraManager: CameraManager by lazy {
        getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private val cameraId: String by lazy {
        cameraManager.cameraIdList.find { currentId ->
            cameraManager
                .getCameraCharacteristics(currentId)
                .get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_BACK
        } ?: throw Exception() // todo: format correctly exception to reflect missing camera
    }

    private val characteristics: CameraCharacteristics by lazy {
        cameraManager.getCameraCharacteristics(cameraId)
    }

    private lateinit var preview: AutoFitSurfaceView
    private lateinit var imageReader: ImageReader

    private lateinit var camera: CameraDevice
    private lateinit var session: CameraCaptureSession

    private val cameraThread = HandlerThread("CameraThread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        preview = findViewById(R.id.preview)

        preview.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int) = Unit

            override fun surfaceCreated(holder: SurfaceHolder) {
                val previewSize = getPreviewOutputSize(
                    preview.display,
                    characteristics,
                    SurfaceHolder::class.java,
                    ImageFormat.JPEG
                )

                preview.setAspectRatio(
                    previewSize.width,
                    previewSize.height
                )

                runOnUiThread { initializeCamera() }
            }
        })

    }

    private fun initializeCamera() = lifecycleScope.launch (Dispatchers.Main) {
        camera = openCamera(cameraManager, cameraId, cameraHandler)

        val size = characteristics.get(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            .getOutputSizes(ImageFormat.JPEG).maxByOrNull { it.height * it.width }!!

        imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, IMAGE_BUFFER_SIZE)

        val targets = listOf(preview.holder.surface, imageReader.surface)

        session = createCaptureSession(camera, targets, cameraHandler)

        val captureRequest = camera.createCaptureRequest(
            CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(preview.holder.surface) }

        session.setRepeatingRequest(captureRequest.build(), null, cameraHandler)
    }

    /**
     * @suppress DEPRECATION for createCaptureSession in lower api
     */
    @Suppress("DEPRECATION")
    private suspend fun createCaptureSession(
        device: CameraDevice,
        targets: List<Surface>,
        handler: Handler? = null
    ): CameraCaptureSession = suspendCoroutine { cont ->
        //todo: fix pool executor issue in new method implementation
        device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) = cont.resume(session)

            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exc = RuntimeException("Camera ${device.id} session configuration failed")
                cont.resumeWithException(exc)
            }
        }, handler)
    }


    /**
     * @suppress MissingPermission to avoid unnecessary code cluttering
     */
    @SuppressLint("MissingPermission")
    private suspend fun openCamera(
        manager: CameraManager,
        cameraId: String,
        handler: Handler? = null
    ): CameraDevice = suspendCancellableCoroutine { cont ->
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) = cont.resume(device)

            override fun onDisconnected(device: CameraDevice) = finish()

            override fun onError(device: CameraDevice, error: Int) {
                val msg = when (error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }
                val exc = RuntimeException("Camera $cameraId error: ($error) $msg")
                Log.e(this::class.java.simpleName, exc.message, exc)
                if (cont.isActive) cont.resumeWithException(exc)
            }
        }, handler)
    }

    fun getDisplaySmartSize(display: Display): SmartSize {
        val outPoint = Point()
        display.getRealSize(outPoint)
        return SmartSize(outPoint.x, outPoint.y)
    }

    fun <T> getPreviewOutputSize(
        display: Display,
        characteristics: CameraCharacteristics,
        targetClass: Class<T>,
        format: Int? = null
    ): Size {
        val screenSize = getDisplaySmartSize(display)
        val hdScreen = screenSize.long >= SIZE_1080P.long || screenSize.short >= SIZE_1080P.short
        val maxSize = if (hdScreen) SIZE_1080P else screenSize

        val config = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: throw Exception() //todo: format exception

        if (format == null) {
            assert(StreamConfigurationMap.isOutputSupportedFor(targetClass))
        } else {
            assert(config.isOutputSupportedFor(format))
        }

        val allSizes = if (format == null) {
            config.getOutputSizes(targetClass) }
        else {
            config.getOutputSizes(format)
        }

        val validSizes = allSizes
            .sortedWith(compareBy { it.height * it.width })
            .map { SmartSize(it.width, it.height) }.reversed()

        return screenSize.size
    }
}

val SIZE_1080P: SmartSize = SmartSize(1920, 1080)
private const val IMAGE_BUFFER_SIZE: Int = 3

class SmartSize(width: Int, height: Int) {
    var size = Size(width, height)
    var long = max(size.width, size.height)
    var short = min(size.width, size.height)
    override fun toString() = "SmartSize(${long}x${short})"
}

/**
 *
 *
 *         cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
 *
 *         textureView.surfaceTextureListener = object:  TextureView.SurfaceTextureListener {
 *             override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
 *                 startCamera()
 *             }
 *
 *             override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {}
 *
 *             override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
 *
 *             override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
 *         }
 *     private fun startBackgroundThread() {
 *         backgroundHandlerThread = HandlerThread("CameraVideoThread")
 *         backgroundHandlerThread.start()
 *         backgroundHandler = Handler(
 *             backgroundHandlerThread.looper)
 *     }
 *
 *     private fun stopBackgroundThread() {
 *         backgroundHandlerThread.quitSafely()
 *         backgroundHandlerThread.join()
 *     }
 *
 *     private fun createCamera(): String {
 *         val cameraIdList = cameraManager.cameraIdList
 *
 *         val cameraId = cameraIdList.find { currentId ->
 *             cameraManager
 *                 .getCameraCharacteristics(currentId)
 *                 .get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_BACK
 *         } ?: throw Exception()
 *
 *         val cameraConfiguration: StreamConfigurationMap? =
 *             cameraManager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
 *
 *         val sizesForJPEG: Array<Size> = cameraConfiguration?.getOutputSizes(ImageFormat.JPEG) ?: throw Exception()
 *
 *         var bestFitJPEG = 0
 *
 *         for (i in sizesForJPEG.indices) {
 *             if (sizesForJPEG[i].width <= 500 && sizesForJPEG[i].height <= 500) {
 *                 bestFitJPEG = i
 *                 break
 *             }
 *         }
 *
 *         imageReader = ImageReader.newInstance(
 *             sizesForJPEG[bestFitJPEG].width,
 *             sizesForJPEG[bestFitJPEG].height,
 *             ImageFormat.JPEG,
 *             3
 *         )
 *
 *         return cameraId
 *     }
 *
 *     @Suppress("DEPRECATION")
 *     private fun startCamera() {
 *         if (ActivityCompat.checkSelfPermission(
 *                 this,
 *                 Manifest.permission.CAMERA
 *             ) == PackageManager.PERMISSION_GRANTED
 *         ) {
 *             val cameraId = createCamera()
 *
 *             val cameraStateCallback = object : CameraDevice.StateCallback() {
 *                 override fun onOpened(camera: CameraDevice) {
 *                     val surfaceTexture : SurfaceTexture? = textureView.surfaceTexture
 *
 *                     val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
 *                     val previewSize = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
 *                         ?.getOutputSizes(ImageFormat.JPEG)
 *                         ?.maxByOrNull { it.height * it.width } ?: throw Exception()
 *
 *                     surfaceTexture?.setDefaultBufferSize(previewSize.width, previewSize.height)
 *
 *                     val previewSurface = Surface(surfaceTexture)
 *
 *                     val captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
 *                     captureRequestBuilder.addTarget(previewSurface) //5
 *
 *                     val captureStateCallback = object : CameraCaptureSession.StateCallback() {
 *                         override fun onConfigureFailed(session: CameraCaptureSession) {
 *
 *                         }
 *                         override fun onConfigured(session: CameraCaptureSession) {
 *                             session.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler)
 *                         }
 *                     }
 *
 *                     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
 *                         camera.createCaptureSession(
 *                             SessionConfiguration(
 *                                 SessionConfiguration.SESSION_REGULAR,
 *                                 listOf(
 *                                     OutputConfiguration(previewSurface),
 *                                     OutputConfiguration(imageReader.surface)
 *                                 ),
 *                                 Executors.newSingleThreadScheduledExecutor(),
 *                                 captureStateCallback
 *                             )
 *                         )
 *                     } else {
 *                         camera.createCaptureSession(listOf(previewSurface, imageReader.surface), captureStateCallback, null)
 *                     }
 *
 *                 }
 *
 *                 override fun onDisconnected(cameraDevice: CameraDevice) {
 *
 *                 }
 *
 *                 override fun onError(cameraDevice: CameraDevice, error: Int) {
 *                     val errorMsg = when(error) {
 *                         ERROR_CAMERA_DEVICE -> "Fatal (device)"
 *                         ERROR_CAMERA_DISABLED -> "Device policy"
 *                         ERROR_CAMERA_IN_USE -> "Camera in use"
 *                         ERROR_CAMERA_SERVICE -> "Fatal (service)"
 *                         ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
 *                         else -> "Unknown"
 *                     }
 *                 }
 *             }
 *
 *             cameraManager.openCamera(cameraId, cameraStateCallback, backgroundHandler)
 *         }
 *     }
*/

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
                layoutParams = LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT
                )
            }

            val surfaces = listOf(previewView.holder.surface, imageReader.surface)

            previewView.holder.addCallback(object: SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    camera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
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