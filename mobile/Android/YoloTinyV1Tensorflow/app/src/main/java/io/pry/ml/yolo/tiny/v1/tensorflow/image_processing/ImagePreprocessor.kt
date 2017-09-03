package io.pry.ml.yolo.tiny.v1.tensorflow.image_processing

import android.graphics.Bitmap
import android.media.Image

import junit.framework.Assert

/**
 * Adapted version of:
 *
 * https://github.com/androidthings/sample-tensorflow-imageclassifier/blob/901bd741ab7f93ffca93719548448a9251919dfa/app/src/main/java/com/example/androidthings/imageclassifier/ImagePreprocessor.java
 */
class ImagePreprocessor(inputImageWidth: Int, inputImageHeight: Int, outputSize: Int) {

    //region pre-allocated buffers
    private val rgbFrameBitmap: Bitmap
    private val croppedBitmap: Bitmap

    private val cachedYuvBytes: Array<ByteArray?>
    private var cachedRgbBytes: IntArray
    //endregion

    //region initialization
    init {
        this.cachedRgbBytes = IntArray(inputImageWidth * inputImageHeight)
        this.cachedYuvBytes = arrayOfNulls<ByteArray>(3)
        this.croppedBitmap = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        this.rgbFrameBitmap = Bitmap.createBitmap(inputImageWidth, inputImageHeight, Bitmap.Config.ARGB_8888)
    }
    //endregion

    //region public interface
    fun preprocess(image: Image?, sensorOrientation: Int = 0): Bitmap? {
        if (image == null) {
            return null
        }

        Assert.assertEquals("Invalid size right", rgbFrameBitmap.width, image.width)
        Assert.assertEquals("Invalid size bottom", rgbFrameBitmap.height, image.height)

        cachedRgbBytes = ImageUtils.convertImageToBitmap(image, cachedRgbBytes, cachedYuvBytes)

        rgbFrameBitmap.setPixels(cachedRgbBytes, 0, image.width, 0, 0,
                image.width, image.height)
        ImageUtils.cropAndRescaleBitmap(rgbFrameBitmap, croppedBitmap, sensorOrientation)
//            ImageUtils.rescaleBitmap(rgbFrameBitmap, croppedBitmap, sensorOrientation)


        image.close()

        return croppedBitmap
    }
    //endregion
}
