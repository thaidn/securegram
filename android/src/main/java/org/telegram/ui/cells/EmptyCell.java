/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.cells;

import android.content.Context;
import android.widget.FrameLayout;

public class EmptyCell extends FrameLayout {

  int cellHeight;

  public EmptyCell(Context context) {
    this(context, 8);
  }

  public EmptyCell(Context context, int height) {
    super(context);
    cellHeight = height;
  }

  public void setHeight(int height) {
    cellHeight = height;
    requestLayout();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(cellHeight, MeasureSpec.EXACTLY));
  }
}
