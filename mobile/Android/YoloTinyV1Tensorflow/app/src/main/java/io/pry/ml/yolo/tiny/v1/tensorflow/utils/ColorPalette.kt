package io.pry.ml.yolo.tiny.v1.tensorflow.utils

import android.graphics.Color
import android.graphics.Paint


//https://sashat.me/2017/01/11/list-of-20-simple-distinct-colors/
object ColorPalette {

    //region defs
    private val colors = arrayOf(
        Color.argb(255, 230, 25, 75),
        Color.argb(255, 60, 180, 75),
        Color.argb(255, 255, 225, 25),
        Color.argb(255, 0, 130, 200),
        Color.argb(255, 245, 130, 48),
        Color.argb(255, 145, 30, 180),
        Color.argb(255, 70, 240, 240),
        Color.argb(255, 240, 50, 230),
        Color.argb(255, 210, 245, 60),
        Color.argb(255, 250, 190, 190),
        Color.argb(255, 0, 128, 128),
        Color.argb(255, 230, 190, 255),
        Color.argb(255, 170, 110, 40),
        Color.argb(255, 255, 250, 200),
        Color.argb(255, 128, 0, 0),
        Color.argb(255, 170, 255, 195),
        Color.argb(255, 128, 128, 0),
        Color.argb(255, 255, 215, 180),
        Color.argb(255, 0, 0, 128),
        Color.argb(255, 128, 128, 128),
        Color.argb(255, 255, 255, 255),
        Color.argb(255, 0, 0, 0)
    )
    //endregion

    //region state
    private val paints: Array<Paint> = Array(colors.size, { i-> createPaint(8f, colors[i]) })
    //endregion


    //region public
    fun paintForIndex(i: Int): Paint {
        return paints[i % paints.size]
    }
    //endregion`

    //region helpers
    private fun createPaint(strokeWidth: Float, color: Int): Paint {
        val paint = Paint()
        paint.color = color
        paint.strokeWidth = strokeWidth
        paint.style = Paint.Style.STROKE
        paint.textSize = 48f
        return paint
    }
    //endregion
}