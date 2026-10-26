package com.ddangi.calling.utils;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Scales + fades a ring view in a loop to suggest "ringing" — used by Outgoing/IncomingCallActivity. */
public final class PulseAnimator {

    private PulseAnimator() {
    }

    public static ValueAnimator start(View ringView) {
        ValueAnimator animator = ValueAnimator.ofFloat(1f, 1.35f);
        animator.setDuration(1200);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            float scale = (float) animation.getAnimatedValue();
            ringView.setScaleX(scale);
            ringView.setScaleY(scale);
            ringView.setAlpha(1f - fraction);
        });
        animator.start();
        return animator;
    }
}
