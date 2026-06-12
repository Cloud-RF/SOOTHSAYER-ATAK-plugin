package com.cloudrf.android.soothsayer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.cloudrf.android.soothsayer.models.response.Key

class ColourKeyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private var key: List<Key> = emptyList()

    fun setKey(key: List<Key>) {
        this.key = key
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (key.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        val bandH = h / key.size

        // Scale text to fit the narrow strip; cap at a reasonable max
        val sp = resources.displayMetrics.scaledDensity
        textPaint.textSize = (w * 0.55f).coerceAtMost(9f * sp).coerceAtLeast(6f * sp)

        key.forEachIndexed { i, k ->
            val top = i * bandH
            val bottom = top + bandH

            bandPaint.color = Color.rgb(k.r, k.g, k.b)
            canvas.drawRect(0f, top, w, bottom, bandPaint)

            // Rotate label -90° so text reads downward along the strip
            canvas.save()
            canvas.translate(w / 2f, top + bandH / 2f)
            val label = k.l.replace(Regex("\\s*[a-zA-Z]+$"), "").trim()
            canvas.drawText(label, 0f, textPaint.textSize / 3f, textPaint)
            canvas.restore()
        }
    }
}
