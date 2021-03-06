
/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 battleent
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.battleent.cameleonlayout

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.Message
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.skydoves.cameleonlayout.R
import java.util.*

class CameleonLayout : FrameLayout, FilledStatusListener, View.OnClickListener {

    private lateinit var layer: FrameLayout
    lateinit var secondLayout: View

    lateinit var status: FilledStatus
    lateinit var filledStatusListener: FilledStatusListener
    var onLayerClickListener: OnLayerClickListener? = null

    private lateinit var timer: Timer
    private lateinit var thread: Thread
    private lateinit var handler: FillResolveHandler

    var duration: Long = 3000
    var delay: Long = 2000
    var density = 1
    var autoUnFill = true
    var drawOnBack = false
    var maxProgress = 100f
    var progress = 0f
        private set

    constructor(context: Context) : super(context) {
        onCreate()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        onCreate()
        getAttrs(attrs)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        onCreate()
        getAttrs(attrs, defStyleAttr)
    }

    private fun onCreate() {
        this.layer = FrameLayout(context)
        this.status = FilledStatus.UnFilled()
        this.handler = FillResolveHandler(this)
        this.filledStatusListener = this
        this.setOnClickListener(this)

        measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
    }

    private fun getAttrs(attrs: AttributeSet) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.CameleonLayout)
        setTypeArray(typedArray)
    }

    private fun getAttrs(attrs: AttributeSet, defStyle: Int) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.CameleonLayout, defStyle, 0)
        setTypeArray(typedArray)
    }

    private fun setTypeArray(typedArray: TypedArray) {
        try {
            maxProgress = typedArray.getFloat(R.styleable.CameleonLayout_maxProgress, maxProgress)
            duration = typedArray.getInt(R.styleable.CameleonLayout_duration, duration.toInt()).toLong()
            delay = typedArray.getInt(R.styleable.CameleonLayout_delay, delay.toInt()).toLong()
            density = typedArray.getInt(R.styleable.CameleonLayout_density, density)
            autoUnFill = typedArray.getBoolean(R.styleable.CameleonLayout_autoUnFill, autoUnFill)
            drawOnBack = typedArray.getBoolean(R.styleable.CameleonLayout_drawOnBack, drawOnBack)

            if(typedArray.hasValue(R.styleable.CameleonLayout_secondLayout)) {
                setSecondLayout(typedArray.getResourceId(R.styleable.CameleonLayout_secondLayout, -1))
            }
        } finally {
            typedArray.recycle()
        }
    }

    fun setSecondLayout(layout: Int) {
        val inflater = LayoutInflater.from(context)
        this.secondLayout = inflater.inflate(layout, null)
    }

    private fun initSecondLayout() {
        this.secondLayout.layoutParams = LayoutParams(measuredWidth, measuredHeight)
        this.secondLayout.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        this.layer.addView(this.secondLayout)
    }

    private fun initLayer() {
        this.layer.let {
            it.layoutParams = ViewGroup.LayoutParams(measuredWidth, measuredHeight)
            it.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            it.isDrawingCacheEnabled = true
            it.buildDrawingCache()
        }
    }

    fun updateProgress(progress: Float) {
        if(progress in 0..this.maxProgress.toInt()) {
            this.progress = progress
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        if(drawOnBack) {
            canvas?.let { drawLayer(it) }
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if(!drawOnBack) {
            drawLayer(canvas)
        }
   }

    private fun drawLayer(canvas: Canvas) {
        if(this.secondLayout.measuredWidth == 0 || this.secondLayout.measuredHeight == 0) {
            initSecondLayout()
            initLayer()
        }

        val bitmap = getBitmapFromView(this.layer)
        bitmap?.let { canvas.drawBitmap(bitmap, null, Rect(0, 0, (measuredWidth / this.maxProgress * this.progress).toInt(), measuredHeight), null) }
    }

    private fun getBitmapFromView(viewGroup: ViewGroup): Bitmap? {
        if (this.progress == 0f) return null
        val bitmap = Bitmap.createBitmap((measuredWidth / this.maxProgress * this.progress).toInt(), measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        viewGroup.layout(0, 0, measuredWidth, measuredHeight)
        viewGroup.draw(canvas)
        return bitmap
    }

    fun drawSecondLayout() {
        if(status is FilledStatus.UnFilled) {
            fireFilledStatus(FilledStatus.Loading())
            updateProgress(density.toFloat())

            val runnable = Runnable {
                thread.let {
                    while (progress < maxProgress) {
                        Thread.sleep((duration/maxProgress).toLong())
                        handler.sendEmptyMessage((progress + density).toInt())
                    }
                }
            }

            thread = Thread(runnable)
            thread.start()
        }
    }

    fun eraseSecondLayout() {
        if(status is FilledStatus.Filled) {
            val runnable = Runnable {
                thread.let {
                    this.timer = Timer()
                    val timerTask = object : TimerTask() {
                        override fun run() {
                            fireFilledStatus(FilledStatus.Loading())
                            while (progress > 0) {
                                Thread.sleep((duration/maxProgress).toLong())
                                handler.sendEmptyMessage((progress - density).toInt())
                            }
                        }
                    }
                    timer.schedule(timerTask, delay)
                }
            }

            thread = Thread(runnable)
            thread.start()
        }
    }

    private class FillResolveHandler(var cameleonLayout: CameleonLayout): Handler() {
        override fun handleMessage(msg: Message?) {
            super.handleMessage(msg)

            cameleonLayout.let {
                it.updateProgress((msg?.what)?.toFloat()!!)
                if(it.status is FilledStatus.Loading) {
                    if (msg.what > it.maxProgress) it.fireFilledStatus(FilledStatus.Filled())
                    if (msg.what <= 0) { it.fireFilledStatus(FilledStatus.UnFilled()) }
                }
            }
        }
    }

    private fun fireFilledStatus(status: FilledStatus) {
        this.status = status
        this.filledStatusListener.onFilledStatusChanged(status)
    }

    override fun onFilledStatusChanged(filledStatus: FilledStatus) {
        when(filledStatus) {
            is FilledStatus.Filled -> {
                if(autoUnFill) eraseSecondLayout()
            }
        }
    }

    override fun onClick(v: View?) {
        onLayerClickListener?.onClick(this.status)
    }
}
