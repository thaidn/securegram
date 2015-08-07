/*
 * This is the source code of Telegram for Android v. 2.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.android.animationcompat.ViewProxy;
import org.telegram.messenger.R;
import org.telegram.messenger.TLRPC;
import org.telegram.ui.components.BackupImageView;
import org.telegram.ui.components.LayoutHelper;

import java.util.ArrayList;

public class StickerSetCell extends FrameLayout {

  private TextView textView;
  private TextView valueTextView;
  private BackupImageView imageView;
  private boolean needDivider;
  private ImageView optionsButton;
  private TLRPC.TL_messages_stickerSet stickersSet;

  private static Paint paint;

  public StickerSetCell(Context context) {
    super(context);

    if (paint == null) {
      paint = new Paint();
      paint.setColor(0xffd9d9d9);
    }

    textView = new TextView(context);
    textView.setTextColor(0xff212121);
    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
    textView.setLines(1);
    textView.setMaxLines(1);
    textView.setSingleLine(true);
    textView.setEllipsize(TextUtils.TruncateAt.END);
    textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
    addView(
        textView,
        LayoutHelper.createFrame(
            LayoutHelper.WRAP_CONTENT,
            LayoutHelper.WRAP_CONTENT,
            LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT,
            LocaleController.isRTL ? 40 : 71,
            10,
            LocaleController.isRTL ? 71 : 40,
            0));

    valueTextView = new TextView(context);
    valueTextView.setTextColor(0xff8a8a8a);
    valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
    valueTextView.setLines(1);
    valueTextView.setMaxLines(1);
    valueTextView.setSingleLine(true);
    valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
    addView(
        valueTextView,
        LayoutHelper.createFrame(
            LayoutHelper.WRAP_CONTENT,
            LayoutHelper.WRAP_CONTENT,
            LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT,
            LocaleController.isRTL ? 40 : 71,
            35,
            LocaleController.isRTL ? 71 : 40,
            0));

    imageView = new BackupImageView(context);
    imageView.setAspectFit(true);
    addView(
        imageView,
        LayoutHelper.createFrame(
            48,
            48,
            (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP,
            LocaleController.isRTL ? 0 : 12,
            8,
            LocaleController.isRTL ? 12 : 0,
            0));

    optionsButton = new ImageView(context);
    optionsButton.setBackgroundResource(R.drawable.bar_selector_grey);
    optionsButton.setImageResource(R.drawable.doc_actions_b);
    optionsButton.setScaleType(ImageView.ScaleType.CENTER);
    addView(
        optionsButton,
        LayoutHelper.createFrame(
            40, 40, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP));

    /*ActionBarMenuItem menuItem = new ActionBarMenuItem(context, null, R.drawable.bar_selector_grey);
     menuItem.setIcon(R.drawable.doc_actions_b);
     addView(menuItem, LayoutHelper.createFrame(40, 40, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, LocaleController.isRTL ? 40 : 0, 0, LocaleController.isRTL ? 0 : 40, 0));
     menuItem.addSubItem(1, "test", 0);
     menuItem.addSubItem(2, "test", 0);
     menuItem.addSubItem(3, "test", 0);
     menuItem.addSubItem(4, "test", 0);
     menuItem.addSubItem(5, "test", 0);
     menuItem.addSubItem(6, "test", 0);
     menuItem.addSubItem(7, "test", 0);*/
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(
        widthMeasureSpec,
        MeasureSpec.makeMeasureSpec(
            AndroidUtilities.dp(64) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
  }

  public void setStickersSet(TLRPC.TL_messages_stickerSet set, boolean divider) {
    needDivider = divider;
    stickersSet = set;

    textView.setText(stickersSet.set.title);
    if ((stickersSet.set.flags & 2) != 0) {
      ViewProxy.setAlpha(textView, 0.5f);
      ViewProxy.setAlpha(valueTextView, 0.5f);
      ViewProxy.setAlpha(imageView, 0.5f);
    } else {
      ViewProxy.setAlpha(textView, 1.0f);
      ViewProxy.setAlpha(valueTextView, 1.0f);
      ViewProxy.setAlpha(imageView, 1.0f);
    }
    ArrayList<TLRPC.Document> documents = set.documents;
    if (documents != null && !documents.isEmpty()) {
      valueTextView.setText(LocaleController.formatPluralString("Stickers", documents.size()));
      TLRPC.Document document = documents.get(0);
      if (document.thumb != null && document.thumb.location != null) {
        imageView.setImage(document.thumb.location, null, "webp", null);
      }
    } else {
      valueTextView.setText(LocaleController.formatPluralString("Stickers", 0));
    }
  }

  public void setOnOptionsClick(OnClickListener listener) {
    optionsButton.setOnClickListener(listener);
  }

  public TLRPC.TL_messages_stickerSet getStickersSet() {
    return stickersSet;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (needDivider) {
      canvas.drawLine(0, getHeight() - 1, getWidth() - getPaddingRight(), getHeight() - 1, paint);
    }
  }
}
