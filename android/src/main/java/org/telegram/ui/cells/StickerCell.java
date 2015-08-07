/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.cells;

import android.content.Context;
import android.view.Gravity;

import org.telegram.android.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.TLRPC;
import org.telegram.ui.components.BackupImageView;
import org.telegram.ui.components.FrameLayoutFixed;
import org.telegram.ui.components.LayoutHelper;

public class StickerCell extends FrameLayoutFixed {

  private BackupImageView imageView;

  public StickerCell(Context context) {
    super(context);

    imageView = new BackupImageView(context);
    imageView.setAspectFit(true);
    addView(imageView, LayoutHelper.createFrame(66, 66, Gravity.CENTER_HORIZONTAL, 0, 5, 0, 0));
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(
        MeasureSpec.makeMeasureSpec(
            AndroidUtilities.dp(76) + getPaddingLeft() + getPaddingRight(), MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(78), MeasureSpec.EXACTLY));
  }

  @Override
  public void setPressed(boolean pressed) {
    if (imageView.getImageReceiver().getPressed() != pressed) {
      imageView.getImageReceiver().setPressed(pressed);
      imageView.invalidate();
    }
    super.setPressed(pressed);
  }

  public void setSticker(TLRPC.Document document, int side) {
    if (document != null) {
      imageView.setImage(document.thumb.location, null, "webp", null);
    }
    if (side == -1) {
      setBackgroundResource(R.drawable.stickers_back_left);
      setPadding(AndroidUtilities.dp(7), 0, 0, 0);
    } else if (side == 0) {
      setBackgroundResource(R.drawable.stickers_back_center);
      setPadding(0, 0, 0, 0);
    } else if (side == 1) {
      setBackgroundResource(R.drawable.stickers_back_right);
      setPadding(0, 0, AndroidUtilities.dp(7), 0);
    } else if (side == 2) {
      setBackgroundResource(R.drawable.stickers_back_all);
      setPadding(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(3), 0);
    }
    if (getBackground() != null) {
      getBackground().setAlpha(230);
    }
  }
}
