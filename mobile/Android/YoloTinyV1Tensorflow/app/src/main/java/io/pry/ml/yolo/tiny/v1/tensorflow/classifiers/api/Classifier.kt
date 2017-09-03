package io.pry.ml.yolo.tiny.v1.tensorflow.classifiers.api

import android.graphics.Bitmap

interface Classifier<out T> {
    val outputSize: Int
        get

    fun result(): T

    fun classify(bitmap: Bitmap)

    fun pause()
    fun unpause()

    fun close()
}