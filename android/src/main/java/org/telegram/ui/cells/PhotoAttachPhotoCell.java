/*
 * This is the source code of Telegram for Android v. 2.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.ui.cells;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.MediaController;
import xyz.securegram.R;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.components.BackupImageView;
import org.telegram.ui.components.CheckBox;
import org.telegram.ui.components.LayoutHelper;

public class PhotoAttachPhotoCell extends FrameLayout {

  private BackupImageView imageView;
  private FrameLayout checkFrame;
  private CheckBox checkBox;
  private boolean isLast;

  private MediaController.PhotoEntry photoEntry;

  public PhotoAttachPhotoCell(Context context) {
    super(context);

    imageView = new BackupImageView(context);
    addView(imageView, LayoutHelper.createFrame(80, 80));

    checkFrame = new FrameLayout(context);
    //addView(checkFrame, LayoutHelper.createFrame(42, 42, Gravity.LEFT | Gravity.TOP, 38, 0, 0, 0));
    addView(checkFrame, LayoutHelper.createFrame(80, 80, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));

    checkBox = new CheckBox(context, R.drawable.checkbig);
    checkBox.setSize(30);
    checkBox.setCheckOffset(AndroidUtilities.dp(1));
    checkBox.setDrawBackground(true);
    checkBox.setColor(0xff3ccaef);
    addView(checkBox, LayoutHelper.createFrame(30, 30, Gravity.LEFT | Gravity.TOP, 46, 4, 0, 0));
    checkBox.setVisibility(VISIBLE);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(
        MeasureSpec.makeMeasureSpec(
            AndroidUtilities.dp(80 + (isLast ? 0 : 6)), MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(80), MeasureSpec.EXACTLY));
  }

  public MediaController.PhotoEntry getPhotoEntry() {
    return photoEntry;
  }

  public void setPhotoEntry(MediaController.PhotoEntry entry, boolean last) {
    photoEntry = entry;
    isLast = last;
    if (photoEntry.thumbPath != null) {
      imageView.setImage(
          photoEntry.thumbPath, null, getResources().getDrawable(R.drawable.nophotos));
    } else if (photoEntry.path != null) {
      imageView.setOrientation(photoEntry.orientation, true);
      imageView.setImage(
          "thumb://" + photoEntry.imageId + ":" + photoEntry.path,
          null,
          getResources().getDrawable(R.drawable.nophotos));
    } else {
      imageView.setImageResource(R.drawable.nophotos);
    }
    boolean showing = PhotoViewer.getInstance().isShowingImage(photoEntry.path);
    imageView.getImageReceiver().setVisible(!showing, true);
    checkBox.setVisibility(showing ? View.INVISIBLE : View.VISIBLE);
    requestLayout();
  }

  public void setChecked(boolean value, boolean animated) {
    checkBox.setChecked(value, animated);
  }

  public void setOnCheckClickLisnener(OnClickListener onCheckClickLisnener) {
    checkFrame.setOnClickListener(onCheckClickLisnener);
    imageView.setOnClickListener(onCheckClickLisnener);
  }
}
