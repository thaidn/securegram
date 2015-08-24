/*
 * This is the source code of Telegram for Android v. 2.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.ui.cells;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import xyz.securegram.R;
import org.telegram.ui.components.LayoutHelper;

public class LocationLoadingCell extends FrameLayout {

  private ProgressBar progressBar;
  private TextView textView;

  public LocationLoadingCell(Context context) {
    super(context);

    progressBar = new ProgressBar(context);
    addView(
        progressBar,
        LayoutHelper.createFrame(
            LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

    textView = new TextView(context);
    textView.setTextColor(0xff999999);
    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
    textView.setText(LocaleController.getString("NoResult", R.string.NoResult));
    addView(
        textView,
        LayoutHelper.createFrame(
            LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(
        widthMeasureSpec,
        MeasureSpec.makeMeasureSpec((int) (AndroidUtilities.dp(56) * 2.5f), MeasureSpec.EXACTLY));
  }

  public void setLoading(boolean value) {
    progressBar.setVisibility(value ? VISIBLE : INVISIBLE);
    textView.setVisibility(value ? INVISIBLE : VISIBLE);
  }
}
