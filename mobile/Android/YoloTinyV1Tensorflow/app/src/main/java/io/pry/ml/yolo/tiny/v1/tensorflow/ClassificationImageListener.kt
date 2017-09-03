package io.pry.ml.yolo.tiny.v1.tensorflow

import android.graphics.Bitmap
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import io.pry.ml.yolo.tiny.v1.tensorflow.classifiers.api.Classifier
import io.pry.ml.yolo.tiny.v1.tensorflow.image_processing.ImagePreprocessor
import java.util.concurrent.atomic.AtomicBoolean


class ClassificationImageListener<T>(val classifier: Classifier<T>) : ImageReader.OnImageAvailableListener {
    //region defs
    private val logTag = "CIL"
    //endregion

    //region deps
    private var imagePreprocessor: ImagePreprocessor? = null
    private var classifierBackgroundHandler: Handler? = null
    private var classifierBackgroundThread: HandlerThread? = null
    //endregion

    //region internal state
    private val isProcessing = AtomicBoolean(false)
    //endregion

    //region properties
    var displayRotation: Int = 0
    var sensorOrientation: Int = 0
    //endregion

    //region callbacks
    var onClassificationResultCallback: ((T) -> Unit)? = null
        set(value) {
            field = value
        }

    var onImagePreprocessedCallback: ((Bitmap) -> Unit)? = null
        set(value) {
            field = value
        }

    var onFpsChangedCallback: ((Float) -> Unit)? = null
        set(value) {
            field = value
        }
    //endregion

    //region interface implementation
    override fun onImageAvailable(imageReader: ImageReader?) {
        val image = imageReader!!.acquireLatestImage()

        if (image == null) {
            return
        }

        if (isProcessing.get()) {
            image.close()
            return
        }

        isProcessing.set(true)

        var rotation = 0
        if (displayRotation == Surface.ROTATION_0) {
            rotation = sensorOrientation
        } else if (displayRotation == Surface.ROTATION_180) {
            rotation = sensorOrientation + 180
        } else if (displayRotation == Surface.ROTATION_90) {
            rotation = sensorOrientation - 90
        } else if (displayRotation == Surface.ROTATION_270) {
            rotation = sensorOrientation + 90
        }

        classifierBackgroundHandler!!.post {
            val overall = SystemClock.elapsedRealtime()

            val started1 = SystemClock.elapsedRealtime()
            val bitmap = imagePreprocessor?.preprocess(image, rotation)!!
            Log.i(logTag, String.format("Processing took %s", (SystemClock.elapsedRealtime() - started1) / 1000f))
            onImagePreprocessedCallback?.invoke(bitmap)

            val started2 = SystemClock.elapsedRealtime()
            classifier.classify(bitmap)
            Log.i(logTag, String.format("Classification took %s", (SystemClock.elapsedRealtime() - started2) / 1000f))

            val started3 = SystemClock.elapsedRealtime()
            onClassificationResultCallback?.invoke(classifier.result())
            Log.i(logTag, String.format("Callback took %s", (SystemClock.elapsedRealtime() - started3) / 1000f))

            isProcessing.set(false)

            val duration = (SystemClock.elapsedRealtime() - overall) / 1000f
            onFpsChangedCallback?.invoke(1f / duration)
            Log.i(logTag, String.format("Overall took %s", (SystemClock.elapsedRealtime() - overall) / 1000f))
        }
    }
    //endregion

    //region public interface
    fun setInputDimensions(inputImageWidth: Int, inputImageHeight: Int) {
        imagePreprocessor = ImagePreprocessor(inputImageWidth, inputImageHeight, classifier.outputSize)
    }

    fun prepare() {
        classifierBackgroundThread = HandlerThread("Classifier Background Thread")
        classifierBackgroundThread?.start()
        classifierBackgroundHandler = Handler(classifierBackgroundThread?.looper)
    }

    fun shutdown() {
        classifierBackgroundThread?.quitSafely()
        classifierBackgroundThread?.join()
        classifierBackgroundThread = null
        classifierBackgroundHandler = null
    }
    //endregion
}