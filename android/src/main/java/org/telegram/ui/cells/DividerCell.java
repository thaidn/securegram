/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;

import org.telegram.android.AndroidUtilities;

public class DividerCell extends BaseCell {

  private static Paint paint;

  public DividerCell(Context context) {
    super(context);
    if (paint == null) {
      paint = new Paint();
      paint.setColor(0xffd9d9d9);
      paint.setStrokeWidth(1);
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(16) + 1);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    canvas.drawLine(
        getPaddingLeft(),
        AndroidUtilities.dp(8),
        getWidth() - getPaddingRight(),
        AndroidUtilities.dp(8),
        paint);
  }
}
