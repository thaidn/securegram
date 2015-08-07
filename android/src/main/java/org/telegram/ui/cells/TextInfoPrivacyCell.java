/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.cells;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.ui.components.LayoutHelper;

public class TextInfoPrivacyCell extends FrameLayout {

  private TextView textView;

  public TextInfoPrivacyCell(Context context) {
    super(context);

    textView = new TextView(context);
    textView.setTextColor(0xff808080);
    textView.setLinkTextColor(0xff316f9f);
    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
    textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
    textView.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(17));
    textView.setMovementMethod(LinkMovementMethod.getInstance());
    addView(
        textView,
        LayoutHelper.createFrame(
            LayoutHelper.WRAP_CONTENT,
            LayoutHelper.WRAP_CONTENT,
            (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP,
            17,
            0,
            17,
            0));
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
  }

  public void setText(CharSequence text) {
    textView.setText(text);
  }

  public void setTextColor(int color) {
    textView.setTextColor(color);
  }
}
