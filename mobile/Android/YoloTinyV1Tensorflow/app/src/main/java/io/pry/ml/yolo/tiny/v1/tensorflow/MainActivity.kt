package io.pry.ml.yolo.tiny.v1.tensorflow

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import io.pry.ml.yolo.tiny.v1.tensorflow.classifiers.Box
import io.pry.ml.yolo.tiny.v1.tensorflow.classifiers.YoloTinyV1Classifier
import io.pry.ml.yolo.tiny.v1.tensorflow.utils.ColorPalette
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit


// https://github.com/androidthings/sample-tensorflow-imageclassifier/blob/901bd741ab7f93ffca93719548448a9251919dfa/app/src/main/java/com/example/androidthings/imageclassifier/ImageClassifierActivity.java
// maybe use renderscript to convert to rgb: http://werner-dittmann.blogspot.co.at/
// https://codelabs.developers.google.com/codelabs/tensorflow-for-poets/#0

class MainActivity : AppCompatActivity() {

    private val logTag = "YoloTinyV1"
    private val REQUEST_CAMERA_PERMISSION = 200

    private var imageDimension: Size? = null

    private val cameraOpenCloseLock = Semaphore(1)
    private var cameraId: String? = null
    private var cameraDevice: CameraDevice? = null
    private var captureRequestBuilder: CaptureRequest.Builder? = null
    private var cameraCaptureSession: CameraCaptureSession? = null

    private var cameraBackgroundHandler: Handler? = null
    private var cameraBackgroundThread: HandlerThread? = null
    private var isFlashSupported = false

    private var imageReader: ImageReader? = null

    private var overlayDrawer: OverlayDrawer? = null

    private var previewSize: Size? = null
    private var sensorOrientation: Int? = null
    private var displayRotation: Int? = null

    //region deps
    private var classifier: YoloTinyV1Classifier? = null
    private var classificationImageListener: ClassificationImageListener<Array<Box?>>? = null
    //endregion

    //region life-cycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        classifier = (application as? ClassificationApplication)!!.classifier
        classificationImageListener = ClassificationImageListener(classifier!!)
    }

    override fun onResume() {
        super.onResume()

        displayRotation = windowManager.defaultDisplay.rotation
        classificationImageListener?.displayRotation = displayRotation!!

        startThreads()
        classificationImageListener?.prepare()

        overlayTextureView.isOpaque = false

        if (cameraTextureView.isAvailable) {
            openCamera(cameraTextureView.width, cameraTextureView.height)
            overlayDrawer?.targetSurfaceDimensions(cameraTextureView.width, cameraTextureView.height)
        } else {
            cameraTextureView.surfaceTextureListener = cameraSurfaceTextureListener
        }

        if (overlayTextureView.isAvailable) {
            overlayDrawer?.surfaceDimensions(overlayTextureView.width, overlayTextureView.height)
        } else {
            overlayTextureView.surfaceTextureListener = overlaySurfaceTextureListener
        }
    }

    override fun onPause() {
        closeCamera()
        stopThreads()
        classificationImageListener?.shutdown()
        super.onPause()
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.first() == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(this, "No camera permission granted", Toast.LENGTH_LONG).show()
                finish()
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }
    //endregion

    //region permission handling
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_CAMERA_PERMISSION)
    }

    private fun hasPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
    //endregion

    //region background thread
    private fun startThreads() {
        cameraBackgroundThread = HandlerThread("Camera Background Thread")
        cameraBackgroundThread?.start()
        cameraBackgroundHandler = Handler(cameraBackgroundThread?.looper)
    }

    private fun stopThreads() {
        cameraBackgroundThread?.quitSafely()
        cameraBackgroundThread?.join()
        cameraBackgroundThread = null
        cameraBackgroundHandler = null
    }
    //endregion

    //region camera
    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice?) {
            cameraOpenCloseLock.release()
            cameraDevice = camera
            createCameraPreview()
        }

        override fun onDisconnected(camera: CameraDevice?) {
            cameraOpenCloseLock.release()
            cameraDevice?.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice?, errorCode: Int) {
            cameraOpenCloseLock.release()
            cameraDevice?.close()
            cameraDevice = null
            finish()
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(width: Int, height: Int) {

        if (!hasPermissions()) {
            requestPermissions()
            return
        }

        setupCamera(width, height)
        configureTransform(width, height)

        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.SECONDS)) {
            throw RuntimeException("timeout while waiting for camera")
        }

        try {
            cameraManager.openCamera(cameraId, cameraStateCallback, null)
        } catch (e: CameraAccessException) {
            throw RuntimeException(e)
        } catch (e: InterruptedException) {
            throw RuntimeException("interrupted while waiting for camera")
        }
    }

    private fun closeCamera() {
        try {
            cameraOpenCloseLock.acquire()

            cameraCaptureSession?.close()
            cameraCaptureSession = null

            cameraDevice?.close()
            cameraDevice = null

            imageReader?.close()
            imageReader = null
        } catch (e: InterruptedException) {
            throw RuntimeException(e)
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    private fun setupCamera(width: Int, height: Int) {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            // get back facing camera
            cameraId = cameraManager.cameraIdList.first {
                cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }

            val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

            isFlashSupported = cameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
            sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
            classificationImageListener?.sensorOrientation = sensorOrientation!!

            val swappedDimensions = when (displayRotation) {
                Surface.ROTATION_0 or Surface.ROTATION_180 -> sensorOrientation == 90 || sensorOrientation == 270
                Surface.ROTATION_90 or Surface.ROTATION_270 -> sensorOrientation == 0 || sensorOrientation == 180
                else -> false
            }

            val displaySize = Point()
            windowManager.defaultDisplay.getSize(displaySize)

            var rotatedPreviewWidth = width
            var rotatedPreviewHeight = height
            var maxPreviewWidth = displaySize.x
            var maxPreviewHeight = displaySize.y

            if (swappedDimensions) {
                rotatedPreviewWidth = height
                rotatedPreviewHeight = width
                maxPreviewWidth = displaySize.y
                maxPreviewHeight = displaySize.x
            }

            imageDimension = map.getOutputSizes(ImageFormat.YUV_420_888).minBy { it.width * it.height }

            imageReader = ImageReader.newInstance(
                    imageDimension!!.width, imageDimension!!.height, ImageFormat.YUV_420_888, 2)

            classificationImageListener?.setInputDimensions(imageDimension!!.width, imageDimension!!.height)

//            classificationImageListener?.onImagePreprocessedCallback = { bitmap ->
//                runOnUiThread {
//                    cameraImageView.setImageBitmap(bitmap)
//                }
//            }

            classificationImageListener?.onClassificationResultCallback = { boxes ->
                overlayDrawer?.drawBoxes(boxes)
            }

            classificationImageListener?.onFpsChangedCallback = { fps ->
                runOnUiThread {
                    fpsTextView.text = String.format("%.2f", fps)
                }
            }
            imageReader!!.setOnImageAvailableListener(classificationImageListener, cameraBackgroundHandler)

            previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture::class.java), rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth, maxPreviewHeight)

            val orientation = resources.configuration.orientation
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                cameraTextureView.setAspectRatio(previewSize!!.width, previewSize!!.height)
            } else {
                cameraTextureView.setAspectRatio(previewSize!!.height, previewSize!!.width)
            }
        } catch(e: CameraAccessException) {
            throw RuntimeException(e)
        }
    }

    private fun chooseOptimalSize(choices: Array<out Size>, textureViewWidth: Int, textureViewHeight: Int, maxPreviewWidth: Int, maxPreviewHeight: Int): Size? {
        val bigEnough = mutableListOf<Size>()
        val notBigEnough = mutableListOf<Size>()
        val w = this.imageDimension!!.width
        val h = this.imageDimension!!.height

        choices
                .filter { it.width <= maxPreviewWidth && it.height <= maxPreviewHeight && it.height == it.width * h / w }
                .forEach {
                    if (it.width >= textureViewWidth && it.height >= textureViewHeight) {
                        bigEnough.add(it)
                    } else {
                        notBigEnough.add(it)
                    }
                }

        if (bigEnough.size > 0) {
            return bigEnough.minBy { it.width * it.height }
        } else if (notBigEnough.size > 0) {
            return notBigEnough.maxBy { it.width * it.height }
        } else {
            return choices[0]
        }
    }

    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, previewSize!!.height.toFloat(), previewSize!!.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        if (Surface.ROTATION_90 == displayRotation || Surface.ROTATION_270 == displayRotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(
                    viewHeight.toFloat() / previewSize!!.height.toFloat(),
                    viewWidth.toFloat() / previewSize!!.width.toFloat())
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90f * (displayRotation!! - 2)), centerX, centerY)
        } else if (Surface.ROTATION_180 == displayRotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
        cameraTextureView.setTransform(matrix)
    }

    private fun createCameraPreview() {
        if (cameraDevice == null || imageDimension == null) {
            return
        }

        val texture = cameraTextureView.surfaceTexture
        texture.setDefaultBufferSize(previewSize!!.width, previewSize!!.height)

        val surface = Surface(texture)

        captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        captureRequestBuilder?.addTarget(surface)
        captureRequestBuilder?.addTarget(imageReader!!.surface)

        cameraDevice?.createCaptureSession(arrayListOf(surface, imageReader!!.surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession?) {
                if (cameraDevice == null) {
                    return
                }

                cameraCaptureSession = session

                try {
                    captureRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)

                    if (isFlashSupported) {
                        captureRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                    }

                    cameraCaptureSession?.setRepeatingRequest(captureRequestBuilder?.build(), null, cameraBackgroundHandler)
                } catch (e: CameraAccessException) {
                    throw RuntimeException(e)
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession?) {
                Toast.makeText(this@MainActivity, "No camera permission granted", Toast.LENGTH_LONG).show()
            }
        }, cameraBackgroundHandler)

    }
    //endregion

    //region surface listeners
    private val cameraSurfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
            Log.i(logTag, "1 ${width}x${height}, ${previewSize?.width}x${previewSize?.height}, ${imageDimension?.width}x${imageDimension?.height}")

            overlayDrawer?.targetSurfaceDimensions(width, height)
            openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
            Log.i(logTag, "2 ${width}x${height}, ${previewSize?.width}x${previewSize?.height}, ${imageDimension?.width}x${imageDimension?.height}")

            overlayDrawer?.targetSurfaceDimensions(width, height)
            configureTransform(width, height)
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {}
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?) = false
    }

    private val overlaySurfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
            overlayDrawer = OverlayDrawer(surface!!)
            overlayDrawer?.surfaceDimensions(width, height)
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
            overlayDrawer?.surfaceDimensions(width, height)
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {}
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?) = false
    }
    //endregion

    //region overlay
    private class OverlayDrawer(val surfaceTexture: SurfaceTexture) : Thread() {

        //region defs
        private val logTag = "OverlayDrawer"
        //endregion

        //region state
        private var surfaceWidth = 0
        private var surfaceHeight = 0

        private var targetSurfaceWidth = 0
        private var targetSurfaceHeight = 0

        private var detectionBoxSize = 0f
        private var detectionBoxXOffset = 0f
        private var detectionBoxYOffset = 0f
        //endregion

        //region synchronization
        private val monitor = Object()
        //endregion

        //region utils
        private val dirty = Rect()
        //endregion

        //region initialization
        init {
            dirty.setEmpty() // TODO: use dirty regions for better performance
        }
        //endregion

        //region private
        private fun updateGeometry() {
            if (this.surfaceWidth == null || this.surfaceHeight == null ||
                    this.targetSurfaceWidth == null || this.targetSurfaceHeight == null) {
                return
            }

            detectionBoxSize = Math.min(targetSurfaceWidth, targetSurfaceHeight).toFloat()
            detectionBoxXOffset = (surfaceWidth - targetSurfaceWidth) / 2f + (targetSurfaceWidth - detectionBoxSize) / 2f
            detectionBoxYOffset = (surfaceHeight - targetSurfaceHeight) / 2f + (targetSurfaceHeight - detectionBoxSize) / 2f
        }
        //endregion

        //region interface
        fun surfaceDimensions(surfaceWidth: Int, surfaceHeight: Int) {
            synchronized(monitor) {
                this.surfaceWidth = surfaceWidth
                this.surfaceHeight = surfaceHeight
                updateGeometry()
            }
        }

        fun targetSurfaceDimensions(targetSurfaceWidth: Int, targetSurfaceHeight: Int) {
            synchronized(monitor) {
                this.targetSurfaceWidth = targetSurfaceWidth
                this.targetSurfaceHeight = targetSurfaceHeight
                updateGeometry()
            }
        }

        fun drawBoxes(boxes: Array<Box?>) {
            var surface: Surface? = null

            try {
                surface = Surface(surfaceTexture)

                val canvas = if (dirty.isEmpty) {
                    surface.lockCanvas(null)
                } else {
                    surface.lockCanvas(dirty)
                }

                if (canvas == null) {
                    Log.w(logTag, "canvas is null")
                    return
                }

                try {
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                    for (box in boxes) {
                        if (box == null) {
                            continue
                        }

//                        canvas.drawRect(
//                                Math.max(0f, box.left * 448f),
//                                Math.max(0f, box.top * 448f),
//                                Math.min(448f, box.right * 448f),
//                                Math.min(448f, box.bottom * 448f),
//                                paint)

                        synchronized(monitor) {
                            val paint = ColorPalette.paintForIndex(box.classIndex)
                            val left = Math.max(detectionBoxXOffset, box.left * detectionBoxSize + detectionBoxXOffset)
                            val top = Math.max(detectionBoxYOffset, box.top * detectionBoxSize + detectionBoxYOffset)

                            paint.strokeWidth = 1f
                            paint.style = Paint.Style.FILL
                            canvas.drawText("${box.label} %.2f".format(box.confidence), left, top - 10f, paint)

                            paint.strokeWidth = 8f
                            paint.style = Paint.Style.STROKE
                            canvas.drawRect(
                                    left, top,
                                    Math.min(detectionBoxSize + detectionBoxXOffset, box.right * detectionBoxSize + detectionBoxXOffset),
                                    Math.min(detectionBoxSize + detectionBoxYOffset, box.bottom * detectionBoxSize + detectionBoxYOffset),
                                    paint)
                        }
                    }
                } catch(e: Exception) {
                    Log.e(logTag, e.message, e)
                } finally {
                    surface.unlockCanvasAndPost(canvas)
                }
            } catch(e: Exception) {
                Log.e(logTag, e.message, e)
            } finally {
                surface?.release()
            }
        }
        //endregion
    }
    //endregion
}
