/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.cells;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.provider.Browser;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.ImageReceiver;
import org.telegram.android.MediaController;
import org.telegram.android.MessageObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.TLRPC;
import org.telegram.ui.components.ResourceLoader;
import org.telegram.ui.components.StaticLayoutEx;
import org.telegram.ui.components.URLSpanNoUnderline;

import java.io.File;
import java.util.Locale;

public class ChatMessageCell extends ChatBaseCell {

  private int textX, textY;
  private int totalHeight = 0;
  private int linkBlockNum;

  private int lastVisibleBlockNum = 0;
  private int firstVisibleBlockNum = 0;
  private int totalVisibleBlocksCount = 0;

  private ImageReceiver linkImageView;
  private boolean isSmallImage;
  private boolean drawLinkImageView;
  private boolean hasLinkPreview;
  private int linkPreviewHeight;
  private boolean isInstagram;
  private int descriptionY;
  private int durationWidth;
  private int descriptionX;
  private int titleX;
  private int authorX;
  private StaticLayout siteNameLayout;
  private StaticLayout titleLayout;
  private StaticLayout descriptionLayout;
  private StaticLayout durationLayout;
  private StaticLayout authorLayout;
  private static TextPaint durationPaint;

  private static Drawable igvideoDrawable;

  public ChatMessageCell(Context context) {
    super(context);
    drawForwardedName = true;
    linkImageView = new ImageReceiver(this);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    boolean result = false;
    if (currentMessageObject != null
        && currentMessageObject.textLayoutBlocks != null
        && !currentMessageObject.textLayoutBlocks.isEmpty()
        && currentMessageObject.messageText instanceof Spannable
        && delegate.canPerformActions()) {
      if (event.getAction() == MotionEvent.ACTION_DOWN
          || (linkPreviewPressed || pressedLink != null)
              && event.getAction() == MotionEvent.ACTION_UP) {
        int x = (int) event.getX();
        int y = (int) event.getY();
        if (x >= textX
            && y >= textY
            && x <= textX + currentMessageObject.textWidth
            && y <= textY + currentMessageObject.textHeight) {
          y -= textY;
          int blockNum = Math.max(0, y / currentMessageObject.blockHeight);
          if (blockNum < currentMessageObject.textLayoutBlocks.size()) {
            try {
              MessageObject.TextLayoutBlock block =
                  currentMessageObject.textLayoutBlocks.get(blockNum);
              x -= textX - (int) Math.ceil(block.textXOffset);
              y -= block.textYOffset;
              final int line = block.textLayout.getLineForVertical(y);
              final int off =
                  block.textLayout.getOffsetForHorizontal(line, x) + block.charactersOffset;

              final float left = block.textLayout.getLineLeft(line);
              if (left <= x && left + block.textLayout.getLineWidth(line) >= x) {
                Spannable buffer = (Spannable) currentMessageObject.messageText;
                ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);
                if (link.length != 0) {
                  if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    resetPressedLink();
                    pressedLink = link[0];
                    linkBlockNum = blockNum;
                    try {
                      int start = buffer.getSpanStart(pressedLink) - block.charactersOffset;
                      urlPath.setCurrentLayout(block.textLayout, start);
                      block.textLayout.getSelectionPath(
                          start, buffer.getSpanEnd(pressedLink) - block.charactersOffset, urlPath);
                    } catch (Exception e) {
                      FileLog.e("tmessages", e);
                    }
                    result = true;
                  } else {
                    if (link[0] == pressedLink) {
                      try {
                        if (pressedLink instanceof URLSpanNoUnderline) {
                          String url = ((URLSpanNoUnderline) pressedLink).getURL();
                          if (url.startsWith("@") || url.startsWith("#") || url.startsWith("/")) {
                            if (delegate != null) {
                              delegate.didPressUrl(currentMessageObject, url);
                            }
                          }
                        } else {
                          pressedLink.onClick(this);
                        }
                      } catch (Exception e) {
                        FileLog.e("tmessages", e);
                      }
                      resetPressedLink();
                      result = true;
                    }
                  }
                } else {
                  resetPressedLink();
                }
              } else {
                resetPressedLink();
              }
            } catch (Exception e) {
              resetPressedLink();
              FileLog.e("tmessages", e);
            }
          } else {
            resetPressedLink();
          }
        } else if (hasLinkPreview
            && x >= textX
            && x <= textX + backgroundWidth
            && y >= textY + currentMessageObject.textHeight
            && y
                <= textY
                    + currentMessageObject.textHeight
                    + linkPreviewHeight
                    + AndroidUtilities.dp(8)) {
          if (event.getAction() == MotionEvent.ACTION_DOWN) {
            resetPressedLink();
            if (drawLinkImageView && linkImageView.isInsideImage(x, y)) {
              linkPreviewPressed = true;
              result = true;
            } else {
              if (descriptionLayout != null && y >= descriptionY) {
                try {
                  x -= textX + AndroidUtilities.dp(10) + descriptionX;
                  y -= descriptionY;
                  final int line = descriptionLayout.getLineForVertical(y);
                  final int off = descriptionLayout.getOffsetForHorizontal(line, x);

                  final float left = descriptionLayout.getLineLeft(line);
                  if (left <= x && left + descriptionLayout.getLineWidth(line) >= x) {
                    Spannable buffer = (Spannable) currentMessageObject.linkDescription;
                    ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);
                    if (link.length != 0) {
                      resetPressedLink();
                      pressedLink = link[0];
                      linkPreviewPressed = true;
                      linkBlockNum = -10;
                      result = true;
                      try {
                        int start = buffer.getSpanStart(pressedLink);
                        urlPath.setCurrentLayout(descriptionLayout, start);
                        descriptionLayout.getSelectionPath(
                            start, buffer.getSpanEnd(pressedLink), urlPath);
                      } catch (Exception e) {
                        FileLog.e("tmessages", e);
                      }
                    } else {
                      resetPressedLink();
                    }
                  } else {
                    resetPressedLink();
                  }
                } catch (Exception e) {
                  resetPressedLink();
                  FileLog.e("tmessages", e);
                }
              }
            }
          } else if (linkPreviewPressed) {
            try {
              if (pressedLink != null) {
                pressedLink.onClick(this);
              } else {
                TLRPC.WebPage webPage = currentMessageObject.messageOwner.media.webpage;
                if (Build.VERSION.SDK_INT >= 19
                    && webPage.embed_url != null
                    && webPage.embed_url.length() != 0) {
                  delegate.needOpenWebView(
                      webPage.embed_url,
                      webPage.site_name,
                      webPage.url,
                      webPage.embed_width,
                      webPage.embed_height);
                } else {
                  Uri uri = Uri.parse(webPage.url);
                  Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                  intent.putExtra(Browser.EXTRA_APPLICATION_ID, getContext().getPackageName());
                  getContext().startActivity(intent);
                }
              }
            } catch (Exception e) {
              FileLog.e("tmessages", e);
            }
            resetPressedLink();
            result = true;
          }
        } else {
          resetPressedLink();
        }
      } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
        resetPressedLink();
      }
    } else {
      resetPressedLink();
    }
    if (result && event.getAction() == MotionEvent.ACTION_DOWN) {
      startCheckLongPress();
    }
    if (event.getAction() != MotionEvent.ACTION_DOWN
        && event.getAction() != MotionEvent.ACTION_MOVE) {
      cancelCheckLongPress();
    }
    return result || super.onTouchEvent(event);
  }

  public void setVisiblePart(int position, int height) {
    if (currentMessageObject == null || currentMessageObject.textLayoutBlocks == null) {
      return;
    }
    int newFirst = -1, newLast = -1, newCount = 0;

    for (int a = Math.max(0, (position - textY) / currentMessageObject.blockHeight);
        a < currentMessageObject.textLayoutBlocks.size();
        a++) {
      MessageObject.TextLayoutBlock block = currentMessageObject.textLayoutBlocks.get(a);
      float y = textY + block.textYOffset;
      if (intersect(y, y + currentMessageObject.blockHeight, position, position + height)) {
        if (newFirst == -1) {
          newFirst = a;
        }
        newLast = a;
        newCount++;
      } else if (y > position) {
        break;
      }
    }

    if (lastVisibleBlockNum != newLast
        || firstVisibleBlockNum != newFirst
        || totalVisibleBlocksCount != newCount) {
      lastVisibleBlockNum = newLast;
      firstVisibleBlockNum = newFirst;
      totalVisibleBlocksCount = newCount;
      invalidate();
    }
  }

  private boolean intersect(float left1, float right1, float left2, float right2) {
    if (left1 <= left2) {
      return right1 >= left2;
    }
    return left1 <= right2;
  }

  private StaticLayout generateStaticLayout(
      CharSequence text,
      TextPaint paint,
      int maxWidth,
      int smallWidth,
      int linesCount,
      int maxLines) {
    SpannableStringBuilder stringBuilder = new SpannableStringBuilder(text);
    int addedChars = 0;
    StaticLayout layout =
        new StaticLayout(text, paint, smallWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
    for (int a = 0; a < linesCount; a++) {
      if (layout.getLineLeft(a) != 0) {
        maxWidth = smallWidth;
      }
      int pos = layout.getLineEnd(a);
      if (pos == text.length()) {
        break;
      }
      pos--;
      if (stringBuilder.charAt(pos + addedChars) == ' ') {
        stringBuilder.replace(pos + addedChars, pos + addedChars + 1, "\n");
      } else if (stringBuilder.charAt(pos + addedChars) != '\n') {
        stringBuilder.insert(pos + addedChars, "\n");
        addedChars++;
      }
      if (a == layout.getLineCount() - 1 || a == maxLines - 1) {
        break;
      }
    }
    return StaticLayoutEx.createStaticLayout(
        stringBuilder,
        paint,
        maxWidth,
        Layout.Alignment.ALIGN_NORMAL,
        1.0f,
        AndroidUtilities.dp(1),
        false,
        TextUtils.TruncateAt.END,
        maxWidth,
        maxLines);
  }

  @Override
  protected boolean isUserDataChanged() {
    if (!hasLinkPreview
        && currentMessageObject.messageOwner.media != null
        && currentMessageObject.messageOwner.media.webpage instanceof TLRPC.TL_webPage) {
      return true;
    }
    //suppress warning
    return super.isUserDataChanged();
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    linkImageView.onDetachedFromWindow();
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    linkImageView.onAttachedToWindow();
  }

  @Override
  public void setMessageObject(MessageObject messageObject) {
    if (currentMessageObject != messageObject || isUserDataChanged()) {
      if (currentMessageObject != messageObject) {
        firstVisibleBlockNum = 0;
        lastVisibleBlockNum = 0;
      }
      drawLinkImageView = false;
      hasLinkPreview = false;
      resetPressedLink();
      linkPreviewPressed = false;
      linkPreviewHeight = 0;
      isInstagram = false;
      durationLayout = null;
      descriptionLayout = null;
      titleLayout = null;
      siteNameLayout = null;
      authorLayout = null;
      int maxWidth;

      if (AndroidUtilities.isTablet()) {
        if (isChat && !messageObject.isOut()) {
          maxWidth = AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(122);
          drawName = true;
        } else {
          maxWidth = AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(80);
          drawName = false;
        }
      } else {
        if (isChat && !messageObject.isOut()) {
          maxWidth =
              Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y)
                  - AndroidUtilities.dp(122);
          drawName = true;
        } else {
          maxWidth =
              Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y)
                  - AndroidUtilities.dp(80);
          drawName = false;
        }
      }

      backgroundWidth = maxWidth;

      super.setMessageObject(messageObject);

      backgroundWidth = messageObject.textWidth;
      totalHeight = messageObject.textHeight + AndroidUtilities.dp(19.5f) + namesOffset;

      int maxChildWidth = Math.max(backgroundWidth, nameWidth);
      maxChildWidth = Math.max(maxChildWidth, forwardedNameWidth);
      maxChildWidth = Math.max(maxChildWidth, replyNameWidth);
      maxChildWidth = Math.max(maxChildWidth, replyTextWidth);
      int maxWebWidth = 0;

      int timeMore = timeWidth + AndroidUtilities.dp(6);
      if (messageObject.isOut()) {
        timeMore += AndroidUtilities.dp(20.5f);
      }

      if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage
          && messageObject.messageOwner.media.webpage instanceof TLRPC.TL_webPage) {
        int linkPreviewMaxWidth;
        if (AndroidUtilities.isTablet()) {
          if (currentMessageObject.messageOwner.to_id.chat_id != 0
              && !currentMessageObject.isOut()) {
            linkPreviewMaxWidth = AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(122);
          } else {
            linkPreviewMaxWidth = AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(80);
          }
        } else {
          if (currentMessageObject.messageOwner.to_id.chat_id != 0
              && !currentMessageObject.isOut()) {
            linkPreviewMaxWidth =
                Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y)
                    - AndroidUtilities.dp(122);
          } else {
            linkPreviewMaxWidth =
                Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y)
                    - AndroidUtilities.dp(80);
          }
        }

        TLRPC.TL_webPage webPage = (TLRPC.TL_webPage) messageObject.messageOwner.media.webpage;

        if (webPage.site_name != null
            && webPage.photo != null
            && webPage.site_name.toLowerCase().equals("instagram")) {
          linkPreviewMaxWidth =
              Math.max(AndroidUtilities.displaySize.y / 3, currentMessageObject.textWidth);
        }

        int additinalWidth = AndroidUtilities.dp(10);
        int restLinesCount = 3;
        int additionalHeight = 0;
        linkPreviewMaxWidth -= additinalWidth;

        hasLinkPreview = true;

        if (currentMessageObject.photoThumbs == null && webPage.photo != null) {
          currentMessageObject.generateThumbs(true);
        }

        isSmallImage =
            webPage.description != null
                && webPage.type != null
                && (webPage.type.equals("app")
                    || webPage.type.equals("profile")
                    || webPage.type.equals("article"))
                && currentMessageObject.photoThumbs != null;

        if (webPage.site_name != null) {
          try {
            int width = (int) Math.ceil(replyNamePaint.measureText(webPage.site_name));
            siteNameLayout =
                new StaticLayout(
                    webPage.site_name,
                    replyNamePaint,
                    Math.min(width, linkPreviewMaxWidth),
                    Layout.Alignment.ALIGN_NORMAL,
                    1.0f,
                    0.0f,
                    false);
            int height = siteNameLayout.getLineBottom(siteNameLayout.getLineCount() - 1);
            linkPreviewHeight += height;
            totalHeight += height;
            additionalHeight += height;
            width = siteNameLayout.getWidth();
            maxChildWidth = Math.max(maxChildWidth, width + additinalWidth);
            maxWebWidth = Math.max(maxWebWidth, width + additinalWidth);
          } catch (Exception e) {
            FileLog.e("tmessages", e);
          }
        }

        boolean titleIsRTL = false;
        if (webPage.title != null) {
          try {
            titleX = 0;
            if (linkPreviewHeight != 0) {
              linkPreviewHeight += AndroidUtilities.dp(2);
              totalHeight += AndroidUtilities.dp(2);
            }
            int restLines = 0;
            if (!isSmallImage || webPage.description == null) {
              titleLayout =
                  StaticLayoutEx.createStaticLayout(
                      webPage.title,
                      replyNamePaint,
                      linkPreviewMaxWidth,
                      Layout.Alignment.ALIGN_NORMAL,
                      1.0f,
                      AndroidUtilities.dp(1),
                      false,
                      TextUtils.TruncateAt.END,
                      linkPreviewMaxWidth,
                      4);
            } else {
              restLines = restLinesCount;
              titleLayout =
                  generateStaticLayout(
                      webPage.title,
                      replyNamePaint,
                      linkPreviewMaxWidth,
                      linkPreviewMaxWidth - AndroidUtilities.dp(48 + 2),
                      restLinesCount,
                      4);
              restLinesCount -= titleLayout.getLineCount();
            }
            int height = titleLayout.getLineBottom(titleLayout.getLineCount() - 1);
            linkPreviewHeight += height;
            totalHeight += height;
            for (int a = 0; a < titleLayout.getLineCount(); a++) {
              int lineLeft = (int) titleLayout.getLineLeft(a);
              if (lineLeft != 0) {
                titleIsRTL = true;
                if (titleX == 0) {
                  titleX = -lineLeft;
                } else {
                  titleX = Math.max(titleX, -lineLeft);
                }
              }
              int width;
              if (lineLeft != 0) {
                width = titleLayout.getWidth() - lineLeft;
              } else {
                width = (int) Math.ceil(titleLayout.getLineWidth(a));
              }
              if (a < restLines || lineLeft != 0 && isSmallImage) {
                width += AndroidUtilities.dp(48 + 2);
              }
              maxChildWidth = Math.max(maxChildWidth, width + additinalWidth);
              maxWebWidth = Math.max(maxWebWidth, width + additinalWidth);
            }
          } catch (Exception e) {
            FileLog.e("tmessages", e);
          }
        }

        if (webPage.author != null) {
          try {
            if (linkPreviewHeight != 0) {
              linkPreviewHeight += AndroidUtilities.dp(2);
              totalHeight += AndroidUtilities.dp(2);
            }
            int width =
                Math.min(
                    (int) Math.ceil(replyNamePaint.measureText(webPage.author)),
                    linkPreviewMaxWidth);
            if (restLinesCount == 3 && (!isSmallImage || webPage.description == null)) {
              authorLayout =
                  new StaticLayout(
                      webPage.author,
                      replyNamePaint,
                      width,
                      Layout.Alignment.ALIGN_NORMAL,
                      1.0f,
                      0.0f,
                      false);
            } else {
              authorLayout =
                  generateStaticLayout(
                      webPage.author,
                      replyNamePaint,
                      width,
                      linkPreviewMaxWidth - AndroidUtilities.dp(48 + 2),
                      restLinesCount,
                      1);
              restLinesCount -= authorLayout.getLineCount();
            }
            int height = authorLayout.getLineBottom(authorLayout.getLineCount() - 1);
            linkPreviewHeight += height;
            totalHeight += height;
            int lineLeft = (int) authorLayout.getLineLeft(0);
            authorX = -lineLeft;
            maxChildWidth = Math.max(maxChildWidth, width + additinalWidth);
            maxWebWidth = Math.max(maxWebWidth, width + additinalWidth);
          } catch (Exception e) {
            FileLog.e("tmessages", e);
          }
        }

        if (webPage.description != null) {
          try {
            descriptionX = 0;
            currentMessageObject.generateLinkDescription();
            if (linkPreviewHeight != 0) {
              linkPreviewHeight += AndroidUtilities.dp(2);
              totalHeight += AndroidUtilities.dp(2);
            }
            int restLines = 0;
            if (restLinesCount == 3 && !isSmallImage) {
              descriptionLayout =
                  StaticLayoutEx.createStaticLayout(
                      messageObject.linkDescription,
                      replyTextPaint,
                      linkPreviewMaxWidth,
                      Layout.Alignment.ALIGN_NORMAL,
                      1.0f,
                      AndroidUtilities.dp(1),
                      false,
                      TextUtils.TruncateAt.END,
                      linkPreviewMaxWidth,
                      6);
            } else {
              restLines = restLinesCount;
              descriptionLayout =
                  generateStaticLayout(
                      messageObject.linkDescription,
                      replyTextPaint,
                      linkPreviewMaxWidth,
                      linkPreviewMaxWidth - AndroidUtilities.dp(48 + 2),
                      restLinesCount,
                      6);
            }
            int height = descriptionLayout.getLineBottom(descriptionLayout.getLineCount() - 1);
            linkPreviewHeight += height;
            totalHeight += height;
            for (int a = 0; a < descriptionLayout.getLineCount(); a++) {
              int lineLeft = (int) Math.ceil(descriptionLayout.getLineLeft(a));
              if (descriptionX == 0) {
                descriptionX = -lineLeft;
              } else {
                descriptionX = Math.max(descriptionX, -lineLeft);
              }

              int width;
              if (lineLeft != 0) {
                width = descriptionLayout.getWidth() - lineLeft;
              } else {
                width = (int) Math.ceil(descriptionLayout.getLineWidth(a));
              }
              if (a < restLines || lineLeft != 0 && isSmallImage) {
                width += AndroidUtilities.dp(48 + 2);
              }
              if (maxWebWidth < width + additinalWidth) {
                if (titleIsRTL) {
                  titleX += (width + additinalWidth - maxWebWidth);
                }
                maxWebWidth = width + additinalWidth;
              }
              maxChildWidth = Math.max(maxChildWidth, width + additinalWidth);
            }
          } catch (Exception e) {
            FileLog.e("tmessages", e);
          }
        }

        if (webPage.photo != null) {
          boolean smallImage =
              webPage.type != null
                  && (webPage.type.equals("app")
                      || webPage.type.equals("profile")
                      || webPage.type.equals("article"));
          if (smallImage
              && (descriptionLayout == null
                  || descriptionLayout != null && descriptionLayout.getLineCount() == 1)) {
            smallImage = false;
            isSmallImage = false;
          }
          int maxPhotoWidth = smallImage ? AndroidUtilities.dp(48) : linkPreviewMaxWidth;
          TLRPC.PhotoSize currentPhotoObject =
              FileLoader.getClosestPhotoSizeWithSize(
                  messageObject.photoThumbs, maxPhotoWidth, true);
          TLRPC.PhotoSize currentPhotoObjectThumb =
              FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 80);
          if (currentPhotoObjectThumb == currentPhotoObject) {
            currentPhotoObjectThumb = null;
          }
          if (currentPhotoObject != null) {
            if (linkPreviewHeight != 0) {
              linkPreviewHeight += AndroidUtilities.dp(2);
              totalHeight += AndroidUtilities.dp(2);
            }

            maxChildWidth = Math.max(maxChildWidth, maxPhotoWidth + additinalWidth);
            currentPhotoObject.size = -1;
            if (currentPhotoObjectThumb != null) {
              currentPhotoObjectThumb.size = -1;
            }

            int width;
            int height;
            if (smallImage) {
              width = height = maxPhotoWidth;
            } else {
              width = currentPhotoObject.w;
              height = currentPhotoObject.h;
              float scale = width / (float) maxPhotoWidth;
              width /= scale;
              height /= scale;
              if (webPage.site_name != null
                  && !webPage.site_name.toLowerCase().equals("instagram")) {
                if (height > AndroidUtilities.displaySize.y / 3) {
                  height = AndroidUtilities.displaySize.y / 3;
                }
              }
            }
            if (isSmallImage) {
              if (AndroidUtilities.dp(50) + additionalHeight > linkPreviewHeight) {
                totalHeight +=
                    AndroidUtilities.dp(50)
                        + additionalHeight
                        - linkPreviewHeight
                        + AndroidUtilities.dp(8);
                linkPreviewHeight = AndroidUtilities.dp(50) + additionalHeight;
              }
              linkPreviewHeight -= AndroidUtilities.dp(8);
            } else {
              totalHeight += height + AndroidUtilities.dp(12);
              linkPreviewHeight += height;
            }

            linkImageView.setImageCoords(0, 0, width, height);

            String fileName = FileLoader.getAttachFileName(currentPhotoObject);

            boolean photoExist = true;
            File cacheFile = FileLoader.getPathToAttach(currentPhotoObject, true);
            if (!cacheFile.exists()) {
              photoExist = false;
            }

            String filter = String.format(Locale.US, "%d_%d", width, height);

            if (photoExist
                || MediaController.getInstance()
                    .canDownloadMedia(MediaController.AUTODOWNLOAD_MASK_PHOTO)
                || FileLoader.getInstance().isLoadingFile(fileName)) {
              linkImageView.setImage(
                  currentPhotoObject.location,
                  filter,
                  currentPhotoObjectThumb != null ? currentPhotoObjectThumb.location : null,
                  String.format(Locale.US, "%d_%d_b", width, height),
                  0,
                  null,
                  false);
            } else {
              if (currentPhotoObjectThumb != null) {
                linkImageView.setImage(
                    null,
                    null,
                    currentPhotoObjectThumb.location,
                    String.format(Locale.US, "%d_%d_b", width, height),
                    0,
                    null,
                    false);
              } else {
                linkImageView.setImageBitmap((Drawable) null);
              }
            }
            drawLinkImageView = true;

            if (webPage.site_name != null) {
              if (webPage.site_name.toLowerCase().equals("instagram")
                  && webPage.type != null
                  && webPage.type.equals("video")) {
                isInstagram = true;
                if (igvideoDrawable == null) {
                  igvideoDrawable = getResources().getDrawable(R.drawable.igvideo);
                }
              }
            }
          }

          if (webPage.type != null && webPage.type.equals("video") && webPage.duration != 0) {
            if (durationPaint == null) {
              durationPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
              durationPaint.setTextSize(AndroidUtilities.dp(12));
              durationPaint.setColor(0xffffffff);
            }
            int minutes = webPage.duration / 60;
            int seconds = webPage.duration - minutes * 60;
            String str = String.format("%d:%02d", minutes, seconds);
            durationWidth = (int) Math.ceil(durationPaint.measureText(str));
            durationLayout =
                new StaticLayout(
                    str,
                    durationPaint,
                    durationWidth,
                    Layout.Alignment.ALIGN_NORMAL,
                    1.0f,
                    0.0f,
                    false);
          }
        } else {
          linkPreviewHeight -= AndroidUtilities.dp(6);
          totalHeight += AndroidUtilities.dp(4);
        }
      }

      if (hasLinkPreview || maxWidth - messageObject.lastLineWidth < timeMore) {
        totalHeight += AndroidUtilities.dp(14);
        backgroundWidth =
            Math.max(maxChildWidth, messageObject.lastLineWidth) + AndroidUtilities.dp(29);
      } else {
        int diff = maxChildWidth - messageObject.lastLineWidth;
        if (diff >= 0 && diff <= timeMore) {
          backgroundWidth = maxChildWidth + timeMore - diff + AndroidUtilities.dp(29);
        } else {
          backgroundWidth =
              Math.max(maxChildWidth, messageObject.lastLineWidth + timeMore)
                  + AndroidUtilities.dp(29);
        }
      }
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), totalHeight);
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);

    if (currentMessageObject.isOut()) {
      textX = layoutWidth - backgroundWidth + AndroidUtilities.dp(10);
      textY = AndroidUtilities.dp(10) + namesOffset;
    } else {
      textX = AndroidUtilities.dp(19) + (isChat ? AndroidUtilities.dp(52) : 0);
      textY = AndroidUtilities.dp(10) + namesOffset;
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (currentMessageObject == null
        || currentMessageObject.textLayoutBlocks == null
        || currentMessageObject.textLayoutBlocks.isEmpty()) {
      return;
    }

    if (currentMessageObject.isOut()) {
      textX = layoutWidth - backgroundWidth + AndroidUtilities.dp(10);
      textY = AndroidUtilities.dp(10) + namesOffset;
    } else {
      textX = AndroidUtilities.dp(19) + (isChat ? AndroidUtilities.dp(52) : 0);
      textY = AndroidUtilities.dp(10) + namesOffset;
    }

    if (firstVisibleBlockNum >= 0) {
      for (int a = firstVisibleBlockNum; a <= lastVisibleBlockNum; a++) {
        if (a >= currentMessageObject.textLayoutBlocks.size()) {
          break;
        }
        MessageObject.TextLayoutBlock block = currentMessageObject.textLayoutBlocks.get(a);
        canvas.save();
        canvas.translate(textX - (int) Math.ceil(block.textXOffset), textY + block.textYOffset);
        if (pressedLink != null && a == linkBlockNum) {
          canvas.drawPath(urlPath, urlPaint);
        }
        try {
          block.textLayout.draw(canvas);
        } catch (Exception e) {
          FileLog.e("tmessages", e);
        }
        canvas.restore();
      }
    }

    if (hasLinkPreview) {
      int startY = textY + currentMessageObject.textHeight + AndroidUtilities.dp(8);
      int linkPreviewY = startY;
      int smallImageStartY = 0;
      replyLinePaint.setColor(currentMessageObject.isOut() ? 0xff8dc97a : 0xff6c9fd2);

      canvas.drawRect(
          textX,
          linkPreviewY - AndroidUtilities.dp(3),
          textX + AndroidUtilities.dp(2),
          linkPreviewY + linkPreviewHeight + AndroidUtilities.dp(3),
          replyLinePaint);

      if (siteNameLayout != null) {
        replyNamePaint.setColor(currentMessageObject.isOut() ? 0xff70b15c : 0xff4b91cf);
        canvas.save();
        canvas.translate(textX + AndroidUtilities.dp(10), linkPreviewY - AndroidUtilities.dp(3));
        siteNameLayout.draw(canvas);
        canvas.restore();
        linkPreviewY += siteNameLayout.getLineBottom(siteNameLayout.getLineCount() - 1);
      }

      if (titleLayout != null) {
        if (linkPreviewY != startY) {
          linkPreviewY += AndroidUtilities.dp(2);
        }
        replyNamePaint.setColor(0xff000000);
        smallImageStartY = linkPreviewY - AndroidUtilities.dp(1);
        canvas.save();
        canvas.translate(
            textX + AndroidUtilities.dp(10) + titleX, linkPreviewY - AndroidUtilities.dp(3));
        titleLayout.draw(canvas);
        canvas.restore();
        linkPreviewY += titleLayout.getLineBottom(titleLayout.getLineCount() - 1);
      }

      if (authorLayout != null) {
        if (linkPreviewY != startY) {
          linkPreviewY += AndroidUtilities.dp(2);
        }
        if (smallImageStartY == 0) {
          smallImageStartY = linkPreviewY - AndroidUtilities.dp(1);
        }
        replyNamePaint.setColor(0xff000000);
        canvas.save();
        canvas.translate(
            textX + AndroidUtilities.dp(10) + authorX, linkPreviewY - AndroidUtilities.dp(3));
        authorLayout.draw(canvas);
        canvas.restore();
        linkPreviewY += authorLayout.getLineBottom(authorLayout.getLineCount() - 1);
      }

      if (descriptionLayout != null) {
        if (linkPreviewY != startY) {
          linkPreviewY += AndroidUtilities.dp(2);
        }
        if (smallImageStartY == 0) {
          smallImageStartY = linkPreviewY - AndroidUtilities.dp(1);
        }
        replyTextPaint.setColor(0xff000000);
        descriptionY = linkPreviewY - AndroidUtilities.dp(3);
        canvas.save();
        canvas.translate(textX + AndroidUtilities.dp(10) + descriptionX, descriptionY);
        if (pressedLink != null && linkBlockNum == -10) {
          canvas.drawPath(urlPath, urlPaint);
        }
        descriptionLayout.draw(canvas);
        canvas.restore();
        linkPreviewY += descriptionLayout.getLineBottom(descriptionLayout.getLineCount() - 1);
      }

      if (drawLinkImageView) {
        if (linkPreviewY != startY) {
          linkPreviewY += AndroidUtilities.dp(2);
        }

        if (isSmallImage) {
          linkImageView.setImageCoords(
              textX + backgroundWidth - AndroidUtilities.dp(77),
              smallImageStartY,
              linkImageView.getImageWidth(),
              linkImageView.getImageHeight());
        } else {
          linkImageView.setImageCoords(
              textX + AndroidUtilities.dp(10),
              linkPreviewY,
              linkImageView.getImageWidth(),
              linkImageView.getImageHeight());
        }
        linkImageView.draw(canvas);

        if (isInstagram && igvideoDrawable != null) {
          int x =
              linkImageView.getImageX()
                  + linkImageView.getImageWidth()
                  - igvideoDrawable.getIntrinsicWidth()
                  - AndroidUtilities.dp(4);
          int y = linkImageView.getImageY() + AndroidUtilities.dp(4);
          igvideoDrawable.setBounds(
              x,
              y,
              x + igvideoDrawable.getIntrinsicWidth(),
              y + igvideoDrawable.getIntrinsicHeight());
          igvideoDrawable.draw(canvas);
        }

        if (durationLayout != null) {
          int x =
              linkImageView.getImageX()
                  + linkImageView.getImageWidth()
                  - AndroidUtilities.dp(8)
                  - durationWidth;
          int y =
              linkImageView.getImageY() + linkImageView.getImageHeight() - AndroidUtilities.dp(19);
          ResourceLoader.mediaBackgroundDrawable.setBounds(
              x - AndroidUtilities.dp(4),
              y - AndroidUtilities.dp(1.5f),
              x + durationWidth + AndroidUtilities.dp(4),
              y + AndroidUtilities.dp(14.5f));
          ResourceLoader.mediaBackgroundDrawable.draw(canvas);

          canvas.save();
          canvas.translate(x, y);
          durationLayout.draw(canvas);
          canvas.restore();
        }
      }
    }
  }
}
