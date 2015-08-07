/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.components;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.messenger.R;

public class PickerBottomLayout extends FrameLayout {

  public LinearLayout doneButton;
  public TextView cancelButton;
  public TextView doneButtonTextView;
  public TextView doneButtonBadgeTextView;

  private boolean isDarkTheme;

  public PickerBottomLayout(Context context) {
    this(context, true);
  }

  public PickerBottomLayout(Context context, boolean darkTheme) {
    super(context);
    isDarkTheme = darkTheme;

    setBackgroundColor(isDarkTheme ? 0xff1a1a1a : 0xffffffff);

    cancelButton = new TextView(context);
    cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
    cancelButton.setTextColor(isDarkTheme ? 0xffffffff : 0xff19a7e8);
    cancelButton.setGravity(Gravity.CENTER);
    cancelButton.setBackgroundResource(
        isDarkTheme ? R.drawable.bar_selector_picker : R.drawable.bar_selector_audio);
    cancelButton.setPadding(AndroidUtilities.dp(29), 0, AndroidUtilities.dp(29), 0);
    cancelButton.setText(LocaleController.getString("Cancel", R.string.Cancel).toUpperCase());
    cancelButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
    addView(cancelButton);
    LayoutParams layoutParams = (LayoutParams) cancelButton.getLayoutParams();
    layoutParams.width = LayoutHelper.WRAP_CONTENT;
    layoutParams.height = LayoutHelper.MATCH_PARENT;
    layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
    cancelButton.setLayoutParams(layoutParams);

    doneButton = new LinearLayout(context);
    doneButton.setOrientation(LinearLayout.HORIZONTAL);
    doneButton.setBackgroundResource(
        isDarkTheme ? R.drawable.bar_selector_picker : R.drawable.bar_selector_audio);
    doneButton.setPadding(AndroidUtilities.dp(29), 0, AndroidUtilities.dp(29), 0);
    addView(doneButton);
    layoutParams = (LayoutParams) doneButton.getLayoutParams();
    layoutParams.width = LayoutHelper.WRAP_CONTENT;
    layoutParams.height = LayoutHelper.MATCH_PARENT;
    layoutParams.gravity = Gravity.TOP | Gravity.RIGHT;
    doneButton.setLayoutParams(layoutParams);

    doneButtonBadgeTextView = new TextView(context);
    doneButtonBadgeTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
    doneButtonBadgeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
    doneButtonBadgeTextView.setTextColor(0xffffffff);
    doneButtonBadgeTextView.setGravity(Gravity.CENTER);
    doneButtonBadgeTextView.setBackgroundResource(
        isDarkTheme ? R.drawable.photobadge : R.drawable.bluecounter);
    doneButtonBadgeTextView.setMinWidth(AndroidUtilities.dp(23));
    doneButtonBadgeTextView.setPadding(
        AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), AndroidUtilities.dp(1));
    doneButton.addView(doneButtonBadgeTextView);
    LinearLayout.LayoutParams layoutParams1 =
        (LinearLayout.LayoutParams) doneButtonBadgeTextView.getLayoutParams();
    layoutParams1.width = LayoutHelper.WRAP_CONTENT;
    layoutParams1.height = AndroidUtilities.dp(23);
    layoutParams1.rightMargin = AndroidUtilities.dp(10);
    layoutParams1.gravity = Gravity.CENTER_VERTICAL;
    doneButtonBadgeTextView.setLayoutParams(layoutParams1);

    doneButtonTextView = new TextView(context);
    doneButtonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
    doneButtonTextView.setTextColor(isDarkTheme ? 0xffffffff : 0xff19a7e8);
    doneButtonTextView.setGravity(Gravity.CENTER);
    doneButtonTextView.setCompoundDrawablePadding(AndroidUtilities.dp(8));
    doneButtonTextView.setText(LocaleController.getString("Send", R.string.Send).toUpperCase());
    doneButtonTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
    doneButton.addView(doneButtonTextView);
    layoutParams1 = (LinearLayout.LayoutParams) doneButtonTextView.getLayoutParams();
    layoutParams1.width = LayoutHelper.WRAP_CONTENT;
    layoutParams1.gravity = Gravity.CENTER_VERTICAL;
    layoutParams1.height = LayoutHelper.WRAP_CONTENT;
    doneButtonTextView.setLayoutParams(layoutParams1);
  }

  public void updateSelectedCount(int count, boolean disable) {
    if (count == 0) {
      doneButtonBadgeTextView.setVisibility(View.GONE);

      if (disable) {
        doneButtonTextView.setTextColor(0xff999999);
        doneButton.setEnabled(false);
      } else {
        doneButtonTextView.setTextColor(isDarkTheme ? 0xffffffff : 0xff19a7e8);
      }
    } else {
      doneButtonTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
      doneButtonBadgeTextView.setVisibility(View.VISIBLE);
      doneButtonBadgeTextView.setText(String.format("%d", count));

      doneButtonTextView.setTextColor(isDarkTheme ? 0xffffffff : 0xff19a7e8);
      if (disable) {
        doneButton.setEnabled(true);
      }
    }
  }
}
