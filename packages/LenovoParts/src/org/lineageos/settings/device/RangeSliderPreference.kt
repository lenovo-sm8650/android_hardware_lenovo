/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.slider.RangeSlider
import kotlin.math.roundToInt

/**
 * A preference with a two-thumb percent slider; the summary shows the range.
 * [onRangeChanged] gets the range when the user lets go of a thumb.
 */
class RangeSliderPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    private var from = 0
    private var to = 100
    private var step = 1
    private var low = 0
    private var high = 100

    var onRangeChanged: ((Int, Int) -> Unit)? = null

    init {
        layoutResource = R.layout.preference_range_slider
        isSelectable = false
    }

    /** Bounds and step; the thumbs stay at least one step apart. */
    fun setBounds(from: Int, to: Int, step: Int) {
        this.from = from
        this.to = to
        this.step = step
        notifyChanged()
    }

    fun setRange(low: Int, high: Int) {
        if (low == this.low && high == this.high) return
        this.low = low
        this.high = high
        notifyChanged()
    }

    override fun getSummary(): CharSequence = rangeText(low, high)

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val summary = holder.findViewById(android.R.id.summary) as TextView
        (holder.findViewById(R.id.range_start) as TextView).text = percent(from)
        (holder.findViewById(R.id.range_end) as TextView).text = percent(to)

        val slider = holder.findViewById(R.id.range_slider) as RangeSlider
        slider.clearOnChangeListeners()
        slider.clearOnSliderTouchListeners()
        slider.valueFrom = from.toFloat()
        slider.valueTo = to.toFloat()
        slider.stepSize = step.toFloat()
        slider.setMinSeparationValue(step.toFloat())
        slider.values = listOf(low.toFloat(), high.toFloat())
        slider.isEnabled = isEnabled
        slider.setLabelFormatter { percent(it.roundToInt()) }
        slider.addOnChangeListener { s, _, _ ->
            summary.text = rangeText(s.values[0].roundToInt(), s.values[1].roundToInt())
        }
        slider.addOnSliderTouchListener(object : RangeSlider.OnSliderTouchListener {
            override fun onStartTrackingTouch(s: RangeSlider) {}

            override fun onStopTrackingTouch(s: RangeSlider) {
                val newLow = s.values[0].roundToInt()
                val newHigh = s.values[1].roundToInt()
                if (newLow == low && newHigh == high) return
                low = newLow
                high = newHigh
                onRangeChanged?.invoke(newLow, newHigh)
            }
        })
    }

    private fun percent(value: Int) = context.getString(R.string.percent_value, value)

    private fun rangeText(low: Int, high: Int) =
        context.getString(R.string.range_value, low, high)
}
