/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.cells;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.ui.components.LayoutHelper;

public class GreySectionCell extends FrameLayout {
  private TextView textView;

  public GreySectionCell(Context context) {
    super(context);

    setBackgroundColor(0xfff2f2f2);

    textView = new TextView(getContext());
    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
    textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
    textView.setTextColor(0xff8a8a8a);
    textView.setGravity(
        (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
    addView(
        textView,
        LayoutHelper.createFrame(
            LayoutHelper.MATCH_PARENT,
            LayoutHelper.MATCH_PARENT,
            (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP,
            16,
            0,
            16,
            0));
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(
        MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(36), MeasureSpec.EXACTLY));
  }

  public void setText(String text) {
    textView.setText(text);
  }
}
