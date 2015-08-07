/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.components;

import android.text.TextPaint;
import android.text.style.URLSpan;

public class URLSpanNoUnderline extends URLSpan {
  public URLSpanNoUnderline(String url) {
    super(url);
  }

  @Override
  public void updateDrawState(TextPaint ds) {
    super.updateDrawState(ds);
    ds.setUnderlineText(false);
  }
}
