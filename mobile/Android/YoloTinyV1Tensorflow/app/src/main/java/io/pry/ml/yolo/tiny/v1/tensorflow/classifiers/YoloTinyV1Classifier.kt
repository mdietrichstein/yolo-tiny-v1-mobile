package io.pry.ml.yolo.tiny.v1.tensorflow.classifiers

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import io.pry.ml.yolo.tiny.v1.tensorflow.classifiers.api.Classifier
import org.tensorflow.contrib.android.TensorFlowInferenceInterface
import java.util.concurrent.atomic.AtomicBoolean

data class Box(val left: Float, val top: Float,
               val right: Float, val bottom: Float,
               val confidence: Float, val classIndex: Int,
               val label: String)

class YoloTinyV1Classifier(assetManager: AssetManager) : Classifier<Array<Box?>> {

    //region defs
    private val logTag = "Classifier"
    //endregion

    //region constants
    private val imageDimension = 448L
    private val gridSize = 7
    private val numClasses = 20
    private val numBoxesPerCell = 2
    private val cellDim = 1f / gridSize

    private val inputTensorName = "image_input:0"
    private val outputTensorName = "prediction/BiasAdd:0"
    private val outputTensorArray = arrayOf(outputTensorName)
    private val labels = arrayOf("aeroplane", "bicycle", "bird", "boat", "bottle", "bus", "car", "cat", "chair", "cow", "diningtable", "dog", "horse", "motorbike", "person", "pottedplant", "sheep", "sofa", "train", "tvmonitor")

    private val modelFile = "file:///android_asset/optimized_yolo.pb"
    //endregion

    //region state
    private val intValues = IntArray(imageDimension.toInt() * imageDimension.toInt())
    private val floatValues = FloatArray(imageDimension.toInt() * imageDimension.toInt() * 3)
    private val outValues = FloatArray(1470)
    private val monitor = Object()

    private val isClosed = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    val boxes = Array<Box?>(gridSize * gridSize * numBoxesPerCell, { null })
    //endregion

    //region deps
    private var inference: TensorFlowInferenceInterface? = null
    //endregion

    //region initialization
    init {
        inference = TensorFlowInferenceInterface(assetManager, modelFile)
    }
    //endregion

    //region implementation
    override val outputSize: Int
        get() = imageDimension.toInt()

    override fun result(): Array<Box?> {
        return boxes
    }

    override fun classify(bitmap: Bitmap) {
        if (isClosed.get() || isPaused.get()) {
            return
        }

        boxes.fill(null, 0, boxes.size)
        bitmap.getPixels(intValues, 0, imageDimension.toInt(), 0, 0, imageDimension.toInt(), imageDimension.toInt())

        for ((i, v) in intValues.withIndex()) {
            floatValues[i * 3] = 2 * (((v shr 16) and 0xff) / 255f) - 1
            floatValues[i * 3 + 1] = 2 * (((v shr 8) and 0xff) / 255f) - 1
            floatValues[i * 3 + 2] = 2 * ((v and 0xff) / 255f) - 1
        }

        synchronized(monitor) {
            if (isClosed.get() || isPaused.get()) {
                return
            }

            val started = SystemClock.elapsedRealtime()
            inference?.feed(inputTensorName, floatValues, 1L, imageDimension, imageDimension, 3L)
            inference?.run(outputTensorArray)
            inference?.fetch(outputTensorName, outValues)
            Log.i(logTag, String.format("Inference took %s", (SystemClock.elapsedRealtime() - started) / 1000f))
        }

        val predictions = outValues.slice(0..979)
        val confidences = outValues.slice(980..(980 + 97))
        val coordinates = outValues.slice(1078..1469)

        for (cellIndex in 0..gridSize * gridSize - 1) {
            for (boxIndex in 0..numBoxesPerCell - 1) {
                val boxConfidence = confidences[cellIndex * numBoxesPerCell + boxIndex]

                val basePredictionsIndex = cellIndex * numClasses
                val classPredictions = predictions.slice(basePredictionsIndex..basePredictionsIndex + numClasses - 1)

                val highestClassProbability = classPredictions.max()!!
                val highestClassProbabilityIndex = classPredictions.indexOfFirst { it == highestClassProbability }

                val classConfidence = boxConfidence * highestClassProbability

                if (classConfidence >= 0.1) {
                    val gridRow = cellIndex / gridSize
                    val gridColumn = cellIndex % gridSize

                    val baseCoordinatesIndex = (cellIndex * numBoxesPerCell * 4) + boxIndex * 4
                    val x = (gridColumn * cellDim) + (coordinates[baseCoordinatesIndex] * cellDim)
                    val y = (gridRow * cellDim) + (coordinates[baseCoordinatesIndex + 1] * cellDim)
                    val width = Math.pow(coordinates[baseCoordinatesIndex + 2].toDouble(), 1.8).toFloat()
                    val height = Math.pow(coordinates[baseCoordinatesIndex + 3].toDouble(), 1.8).toFloat()

                    boxes[gridRow * gridColumn * numBoxesPerCell + boxIndex] =
                            Box(
                                    x - (width / 2), y - (height / 2),
                                    x + (width / 2), y + (height / 2),
                                    classConfidence, highestClassProbabilityIndex,
                                    labels[highestClassProbabilityIndex]
                            )
                }
            }
        }
    }

    override fun unpause() {
        isPaused.set(false)
    }

    override fun pause() {
        isPaused.set(true)
    }

    override fun close() {
        if (!isClosed.get()) {
            isClosed.set(true)

            synchronized(monitor) {
                inference?.close()
            }
        }
    }
    //endregion
}
