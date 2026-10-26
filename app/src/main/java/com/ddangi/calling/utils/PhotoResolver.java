package com.ddangi.calling.utils;

import android.content.Context;
import android.widget.ImageView;

import com.ddangi.calling.R;

/**
 * Partner photos ship as bundled drawables (photoResName on the Firestore user doc names one),
 * not via Firebase Storage — see the plan's UI/UX direction. Falls back to a plain placeholder
 * if the named drawable hasn't been added to res/drawable yet.
 */
public final class PhotoResolver {

    private PhotoResolver() {
    }

    public static void loadInto(ImageView imageView, String photoResName) {
        Context context = imageView.getContext();
        int resId = 0;
        if (photoResName != null && !photoResName.isEmpty()) {
            resId = context.getResources().getIdentifier(photoResName, "drawable", context.getPackageName());
        }
        imageView.setImageResource(resId != 0 ? resId : R.drawable.bg_partner_photo_placeholder);
    }
}
