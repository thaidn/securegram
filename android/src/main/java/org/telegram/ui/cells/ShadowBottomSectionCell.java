/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.cells;

import android.content.Context;
import android.view.View;

import org.telegram.android.AndroidUtilities;
import org.telegram.messenger.R;

public class ShadowBottomSectionCell extends View {

  public ShadowBottomSectionCell(Context context) {
    super(context);
    setBackgroundResource(R.drawable.greydivider_bottom);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(
        widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(6), MeasureSpec.EXACTLY));
  }
}
