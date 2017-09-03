package io.pry.ml.yolo.tiny.v1.tensorflow

import android.app.Application
import io.pry.ml.yolo.tiny.v1.tensorflow.classifiers.YoloTinyV1Classifier


class ClassificationApplication: Application() {
    //region deps

    // Keep classifier here so that it survives configuration changes (creation is expensive)
    val classifier by lazy { YoloTinyV1Classifier(assets) }
    //endregion
}