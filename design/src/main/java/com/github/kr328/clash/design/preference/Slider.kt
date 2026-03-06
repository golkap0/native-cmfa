package com.github.kr328.clash.design.preference

import android.graphics.drawable.Drawable
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.github.kr328.clash.common.compat.getDrawableCompat
import com.github.kr328.clash.design.databinding.PreferenceSliderBinding
import com.github.kr328.clash.design.util.layoutInflater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.reflect.KMutableProperty0

interface SliderPreference : Preference {
    var icon: Drawable?
    var title: CharSequence?
    var summary: CharSequence?
    var valueFrom: Float
    var valueTo: Float
    var stepSize: Float
    var listener: OnChangedListener?
}

fun PreferenceScreen.slider(
    value: KMutableProperty0<Int>,
    @DrawableRes icon: Int? = null,
    @StringRes title: Int? = null,
    from: Float,
    to: Float,
    configure: SliderPreference.() -> Unit = {},
): SliderPreference {
    val binding = PreferenceSliderBinding
        .inflate(context.layoutInflater, root, false)

    val impl = object : SliderPreference {
        override val view: View
            get() = binding.root
        override var icon: Drawable?
            get() = binding.iconView.background
            set(value) {
                binding.iconView.background = value
            }
        override var title: CharSequence?
            get() = binding.titleView.text
            set(value) {
                binding.titleView.text = value
            }
        override var summary: CharSequence?
            get() = binding.summaryView.text
            set(value) {
                binding.summaryView.text = value
                binding.summaryView.visibility = if (value == null) View.GONE else View.VISIBLE
            }
        override var valueFrom: Float
            get() = binding.sliderView.valueFrom
            set(value) {
                binding.sliderView.valueFrom = value
            }
        override var valueTo: Float
            get() = binding.sliderView.valueTo
            set(value) {
                binding.sliderView.valueTo = value
            }
        override var stepSize: Float
            get() = binding.sliderView.stepSize
            set(value) {
                binding.sliderView.stepSize = value
            }
        override var listener: OnChangedListener? = null
    }

    if (icon != null) {
        impl.icon = context.getDrawableCompat(icon)
    }

    if (title != null) {
        impl.title = context.getString(title)
    }

    impl.valueFrom = from
    impl.valueTo = to
    impl.stepSize = 1.0f

    binding.sliderView.apply {
        this.valueFrom = from
        this.valueTo = to
        this.stepSize = 1.0f
        this.value = from
    }

    impl.configure()

    addElement(impl)

    launch(Dispatchers.Main) {
        val initialValue = withContext(Dispatchers.IO) {
            value.get()
        }

        binding.sliderView.apply {
            this.value = initialValue.toFloat().coerceIn(valueFrom, valueTo)

            addOnChangeListener { _, v, fromUser ->
                if (fromUser) {
                    this@slider.launch(Dispatchers.Main) {
                        withContext(Dispatchers.IO) {
                            value.set(v.toInt())
                        }
                        impl.listener?.onChanged()
                    }
                }
            }
        }
    }

    return impl
}
