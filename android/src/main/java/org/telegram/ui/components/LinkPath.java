/*
 * This is the source code of Telegram for Android v. 2.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.ui.components;

import android.graphics.Path;
import android.text.StaticLayout;

public class LinkPath extends Path {

  private StaticLayout currentLayout;
  private int currentLine;
  private float lastTop = -1;

  public void setCurrentLayout(StaticLayout layout, int start) {
    currentLayout = layout;
    currentLine = layout.getLineForOffset(start);
    lastTop = -1;
  }

  @Override
  public void addRect(float left, float top, float right, float bottom, Direction dir) {
    if (lastTop == -1) {
      lastTop = top;
    } else if (lastTop != top) {
      lastTop = top;
      currentLine++;
    }
    float lineRight = currentLayout.getLineRight(currentLine);
    float lineLeft = currentLayout.getLineLeft(currentLine);
    if (left >= lineRight) {
      return;
    }
    if (right > lineRight) {
      right = lineRight;
    }
    if (left < lineLeft) {
      left = lineLeft;
    }
    super.addRect(left, top, right, bottom, dir);
  }
}
