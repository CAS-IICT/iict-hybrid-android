package cn.iict.virustrack

import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation


class Animation {

    fun fadeOut(view: View, duration: Long = 500) {
        if (view.visibility != View.VISIBLE) return
        view.isEnabled = false
        val animation = AlphaAnimation(1f, 0f)
        animation.duration = duration
        animation.interpolator = AccelerateInterpolator()
        animation.fillAfter = true
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationRepeat(animation: Animation?) {
            }

            override fun onAnimationStart(animation: Animation?) {
            }

            override fun onAnimationEnd(animation: Animation?) {
                view.visibility = View.GONE
            }
        })
        view.startAnimation(animation)
    }
}