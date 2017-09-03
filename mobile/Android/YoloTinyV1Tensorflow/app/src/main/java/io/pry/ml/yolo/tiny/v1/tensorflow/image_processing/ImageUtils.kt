/*
 * Copyright 2017 The Android Things Samples Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.pry.ml.yolo.tiny.v1.tensorflow.image_processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.media.Image
import junit.framework.Assert

/**
 * Utility class for manipulating images.
 */
object ImageUtils {
    // This value is 2 ^ 18 - 1, and is used to clamp the RGB values before their ranges
    // are normalized to eight bits.
    internal val kMaxChannelValue = 262143


    /**
     * Utility method to compute the allocated size in bytes of a YUV420SP image
     * of the given dimensions.
     */
    fun getYUVByteSize(width: Int, height: Int): Int {
        // The luminance plane requires 1 byte per pixel.
        val ySize = width * height

        // The UV plane works on 2x2 blocks, so dimensions with odd size must be rounded up.
        // Each 2x2 block takes 2 bytes to encode, one each for U and V.
        val uvSize = (width + 1) / 2 * ((height + 1) / 2) * 2

        return ySize + uvSize
    }


    fun convertImageToBitmap(image: Image, output: IntArray, cachedYuvBytes: Array<ByteArray?>): IntArray {
        var cachedYuvBytes = cachedYuvBytes
        if (cachedYuvBytes == null || cachedYuvBytes.size != 3) {
            cachedYuvBytes = arrayOfNulls<ByteArray>(3)
        }
        val planes = image.planes
        fillBytes(planes, cachedYuvBytes)

        val yRowStride = planes[0].rowStride
        val uvRowStride = planes[1].rowStride
        val uvPixelStride = planes[1].pixelStride

        convertYUV420ToARGB8888(cachedYuvBytes[0]!!, cachedYuvBytes[1]!!, cachedYuvBytes[2]!!,
                image.width, image.height, yRowStride, uvRowStride, uvPixelStride, output)
        return output
    }

    private fun convertYUV420ToARGB8888(yData: ByteArray, uData: ByteArray, vData: ByteArray, width: Int, height: Int,
                                        yRowStride: Int, uvRowStride: Int, uvPixelStride: Int, out: IntArray) {
        var i = 0
        for (y in 0..height - 1) {
            val pY = yRowStride * y
            val uv_row_start = uvRowStride * (y shr 1)
            val pU = uv_row_start
            val pV = uv_row_start

            for (x in 0..width - 1) {
                val uv_offset = (x shr 1) * uvPixelStride
                out[i++] = YUV2RGB(
                        convertByteToInt(yData, pY + x),
                        convertByteToInt(uData, pU + uv_offset),
                        convertByteToInt(vData, pV + uv_offset))
            }
        }
    }

    private fun convertByteToInt(arr: ByteArray, pos: Int): Int {
        return arr[pos].toInt() and 0xFF
    }

    private fun YUV2RGB(nY: Int, nU: Int, nV: Int): Int {
        var nY = nY
        var nU = nU
        var nV = nV
        nY -= 16
        nU -= 128
        nV -= 128
        if (nY < 0) nY = 0

        // This is the floating point equivalent. We do the conversion in integer
        // because some Android devices do not have floating point in hardware.
        // nR = (int)(1.164 * nY + 2.018 * nU);
        // nG = (int)(1.164 * nY - 0.813 * nV - 0.391 * nU);
        // nB = (int)(1.164 * nY + 1.596 * nV);

        var nR = (1192 * nY + 1634 * nV).toInt()
        var nG = (1192 * nY - 833 * nV - 400 * nU).toInt()
        var nB = (1192 * nY + 2066 * nU).toInt()

        nR = Math.min(kMaxChannelValue, Math.max(0, nR))
        nG = Math.min(kMaxChannelValue, Math.max(0, nG))
        nB = Math.min(kMaxChannelValue, Math.max(0, nB))

        nR = nR shr 10 and 0xff
        nG = nG shr 10 and 0xff
        nB = nB shr 10 and 0xff

        return 0xff000000.toInt() or (nR shl 16) or (nG shl 8) or nB
    }

    private fun fillBytes(planes: Array<Image.Plane>, yuvBytes: Array<ByteArray?>) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (i in planes.indices) {
            val buffer = planes[i].buffer
            if (yuvBytes[i] == null || yuvBytes[i]!!.size != buffer.capacity()) {
                yuvBytes[i] = ByteArray(buffer.capacity())
            }
            buffer.get(yuvBytes[i])
        }
    }


    fun rescaleBitmap(src: Bitmap, dst: Bitmap, sensorOrientation: Int) {
        Assert.assertEquals(dst.width, dst.height)

        val matrix = Matrix()

        matrix.postScale(dst.width / src.width.toFloat(), dst.height / src.height.toFloat())

        // Rotate around the center if necessary.
        if (sensorOrientation != 0) {
            matrix.postTranslate(-dst.width / 2.0f, -dst.height / 2.0f)
            matrix.postRotate(sensorOrientation.toFloat())
            matrix.postTranslate(dst.width / 2.0f, dst.height / 2.0f)
        }

        val canvas = Canvas(dst)
        canvas.drawBitmap(src, matrix, null)
    }

    fun cropAndRescaleBitmap(src: Bitmap, dst: Bitmap, sensorOrientation: Int) {
        Assert.assertEquals(dst.width, dst.height)
        val minDim = Math.min(src.width, src.height).toFloat()

        val matrix = Matrix()

        // We only want the center square out of the original rectangle.
        val translateX = -Math.max(0f, (src.width - minDim) / 2)
        val translateY = -Math.max(0f, (src.height - minDim) / 2)
        matrix.preTranslate(translateX, translateY)

        val scaleFactor = dst.height / minDim
        matrix.postScale(scaleFactor, scaleFactor)

        // Rotate around the center if necessary.
        if (sensorOrientation != 0) {
            matrix.postTranslate(-dst.width / 2.0f, -dst.height / 2.0f)
            matrix.postRotate(sensorOrientation.toFloat())
            matrix.postTranslate(dst.width / 2.0f, dst.height / 2.0f)
        }

        val canvas = Canvas(dst)
        canvas.drawBitmap(src, matrix, null)
    }
}
