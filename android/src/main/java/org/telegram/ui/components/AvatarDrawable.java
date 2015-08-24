/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import org.telegram.android.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import xyz.securegram.R;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.UserConfig;

import java.util.Locale;

public class AvatarDrawable extends Drawable {

  private static Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private static TextPaint namePaint;
  private static TextPaint namePaintSmall;
  private static int[] arrColors = {
    0xffe56555, 0xfff28c48, 0xffeec764, 0xff76c84d, 0xff5fbed5, 0xff549cdd, 0xff8e85ee, 0xfff2749a
  };
  private static int[] arrColorsProfiles = {
    0xffd86f65, 0xfff69d61, 0xfffabb3c, 0xff67b35d, 0xff56a2bb, 0xff5c98cd, 0xff8c79d2, 0xfff37fa6
  };
  private static int[] arrColorsProfilesBack = {
    0xffca6056, 0xfff18944, 0xff7d6ac4, 0xff56a14c, 0xff4492ac, 0xff4c84b6, 0xff7d6ac4, 0xff4c84b6
  };
  private static int[] arrColorsProfilesText = {
    0xfff9cbc5, 0xfffdddc8, 0xffcdc4ed, 0xffc0edba, 0xffb8e2f0, 0xffb3d7f7, 0xffcdc4ed, 0xffb3d7f7
  };
  private static int[] arrColorsNames = {
    0xffca5650, 0xffd87b29, 0xff4e92cc, 0xff50b232, 0xff42b1a8, 0xff4e92cc, 0xff4e92cc, 0xff4e92cc
  };
  private static int[] arrColorsButtons = {
    R.drawable.bar_selector_red,
    R.drawable.bar_selector_orange,
    R.drawable.bar_selector_violet,
    R.drawable.bar_selector_green,
    R.drawable.bar_selector_cyan,
    R.drawable.bar_selector_blue,
    R.drawable.bar_selector_violet,
    R.drawable.bar_selector_blue
  };

  private static Drawable broadcastDrawable;
  private static Drawable photoDrawable;

  private int color;
  private StaticLayout textLayout;
  private float textWidth;
  private float textHeight;
  private float textLeft;
  private boolean isProfile;
  private boolean drawBrodcast;
  private boolean drawPhoto;
  private boolean smallStyle;
  private StringBuilder stringBuilder = new StringBuilder(5);

  public AvatarDrawable() {
    super();

    if (namePaint == null) {
      namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
      namePaint.setColor(0xffffffff);
      namePaint.setTextSize(AndroidUtilities.dp(20));

      namePaintSmall = new TextPaint(Paint.ANTI_ALIAS_FLAG);
      namePaintSmall.setColor(0xffffffff);
      namePaintSmall.setTextSize(AndroidUtilities.dp(14));

      broadcastDrawable =
          ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.broadcast_w);
    }
  }

  public AvatarDrawable(TLRPC.User user) {
    this(user, false);
  }

  public AvatarDrawable(TLRPC.Chat chat) {
    this(chat, false);
  }

  public AvatarDrawable(TLRPC.User user, boolean profile) {
    this();
    isProfile = profile;
    if (user != null) {
      setInfo(user.id, user.first_name, user.last_name, false);
    }
  }

  public AvatarDrawable(TLRPC.Chat chat, boolean profile) {
    this();
    isProfile = profile;
    if (chat != null) {
      setInfo(chat.id, chat.title, null, chat.id < 0);
    }
  }

  public void setSmallStyle(boolean value) {
    smallStyle = value;
  }

  public static int getColorIndex(int id) {
    if (id >= 0 && id < 8) {
      return id;
    }
    try {
      String str;
      if (id >= 0) {
        str = String.format(Locale.US, "%d%d", id, UserConfig.getClientUserId());
      } else {
        str = String.format(Locale.US, "%d", id);
      }
      if (str.length() > 15) {
        str = str.substring(0, 15);
      }
      java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
      byte[] digest = md.digest(str.getBytes());
      int b = digest[Math.abs(id % 16)];
      if (b < 0) {
        b += 256;
      }
      return Math.abs(b) % arrColors.length;
    } catch (Exception e) {
      FileLog.e("tmessages", e);
    }
    return id % arrColors.length;
  }

  public static int getColorForId(int id) {
    return arrColors[getColorIndex(id)];
  }

  public static int getButtonColorForId(int id) {
    return arrColorsButtons[getColorIndex(id)];
  }

  public static int getProfileColorForId(int id) {
    return arrColorsProfiles[getColorIndex(id)];
  }

  public static int getProfileTextColorForId(int id) {
    return arrColorsProfilesText[getColorIndex(id)];
  }

  public static int getProfileBackColorForId(int id) {
    return arrColorsProfilesBack[getColorIndex(id)];
  }

  public static int getNameColorForId(int id) {
    return arrColorsNames[getColorIndex(id)];
  }

  public void setInfo(TLRPC.User user) {
    if (user != null) {
      setInfo(user.id, user.first_name, user.last_name, false);
    }
  }

  public void setInfo(TLRPC.Chat chat) {
    if (chat != null) {
      setInfo(chat.id, chat.title, null, chat.id < 0);
    }
  }

  public void setColor(int value) {
    color = value;
  }

  public void setInfo(int id, String firstName, String lastName, boolean isBroadcast) {
    if (isProfile) {
      color = arrColorsProfiles[getColorIndex(id)];
    } else {
      color = arrColors[getColorIndex(id)];
    }

    drawBrodcast = isBroadcast;

    if (firstName == null || firstName.length() == 0) {
      firstName = lastName;
      lastName = null;
    }

    stringBuilder.setLength(0);
    if (firstName != null && firstName.length() > 0) {
      stringBuilder.append(firstName.substring(0, 1));
    }
    if (lastName != null && lastName.length() > 0) {
      String lastch = null;
      for (int a = lastName.length() - 1; a >= 0; a--) {
        if (lastch != null && lastName.charAt(a) == ' ') {
          break;
        }
        lastch = lastName.substring(a, a + 1);
      }
      if (Build.VERSION.SDK_INT >= 16) {
        stringBuilder.append("\u200C");
      }
      stringBuilder.append(lastch);
    } else if (firstName != null && firstName.length() > 0) {
      for (int a = firstName.length() - 1; a >= 0; a--) {
        if (firstName.charAt(a) == ' ') {
          if (a != firstName.length() - 1 && firstName.charAt(a + 1) != ' ') {
            if (Build.VERSION.SDK_INT >= 16) {
              stringBuilder.append("\u200C");
            }
            stringBuilder.append(firstName.substring(a + 1, a + 2));
            break;
          }
        }
      }
    }

    if (stringBuilder.length() > 0) {
      String text = stringBuilder.toString().toUpperCase();
      try {
        textLayout =
            new StaticLayout(
                text,
                (smallStyle ? namePaintSmall : namePaint),
                AndroidUtilities.dp(100),
                Layout.Alignment.ALIGN_NORMAL,
                1.0f,
                0.0f,
                false);
        if (textLayout.getLineCount() > 0) {
          textLeft = textLayout.getLineLeft(0);
          textWidth = textLayout.getLineWidth(0);
          textHeight = textLayout.getLineBottom(0);
        }
      } catch (Exception e) {
        FileLog.e("tmessages", e);
      }
    } else {
      textLayout = null;
    }
  }

  public void setDrawPhoto(boolean value) {
    if (value && photoDrawable == null) {
      photoDrawable =
          ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.photo_w);
    }
    drawPhoto = value;
  }

  @Override
  public void draw(Canvas canvas) {
    Rect bounds = getBounds();
    if (bounds == null) {
      return;
    }
    int size = bounds.width();
    paint.setColor(color);
    canvas.save();
    canvas.translate(bounds.left, bounds.top);
    canvas.drawCircle(size / 2, size / 2, size / 2, paint);

    if (drawBrodcast && broadcastDrawable != null) {
      int x = (size - broadcastDrawable.getIntrinsicWidth()) / 2;
      int y = (size - broadcastDrawable.getIntrinsicHeight()) / 2;
      broadcastDrawable.setBounds(
          x,
          y,
          x + broadcastDrawable.getIntrinsicWidth(),
          y + broadcastDrawable.getIntrinsicHeight());
      broadcastDrawable.draw(canvas);
    } else {
      if (textLayout != null) {
        canvas.translate((size - textWidth) / 2 - textLeft, (size - textHeight) / 2);
        textLayout.draw(canvas);
      } else if (drawPhoto && photoDrawable != null) {
        int x = (size - photoDrawable.getIntrinsicWidth()) / 2;
        int y = (size - photoDrawable.getIntrinsicHeight()) / 2;
        photoDrawable.setBounds(
            x, y, x + photoDrawable.getIntrinsicWidth(), y + photoDrawable.getIntrinsicHeight());
        photoDrawable.draw(canvas);
      }
    }
    canvas.restore();
  }

  @Override
  public void setAlpha(int alpha) {}

  @Override
  public void setColorFilter(ColorFilter cf) {}

  @Override
  public int getOpacity() {
    return 0;
  }

  @Override
  public int getIntrinsicWidth() {
    return 0;
  }

  @Override
  public int getIntrinsicHeight() {
    return 0;
  }
}
