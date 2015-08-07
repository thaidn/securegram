/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.ImageLoader;
import org.telegram.android.MediaController;
import org.telegram.android.MessageObject;
import org.telegram.android.MessagesController;
import org.telegram.android.SendMessagesHelper;
import org.telegram.messenger.FileLoader;
import org.telegram.ui.components.RadialProgress;
import org.telegram.ui.components.ResourceLoader;
import org.telegram.ui.components.SeekBar;

import java.io.File;

public class ChatAudioCell extends ChatBaseCell
    implements SeekBar.SeekBarDelegate, MediaController.FileDownloadProgressListener {

  private static TextPaint timePaint;
  private static Paint circlePaint;

  private SeekBar seekBar;
  private int seekBarX;
  private int seekBarY;

  private RadialProgress radialProgress;
  private int buttonState = 0;
  private int buttonX;
  private int buttonY;
  private boolean buttonPressed = false;

  private StaticLayout timeLayout;
  private int timeX;
  private int timeWidth;
  private String lastTimeString = null;

  private int TAG;

  public ChatAudioCell(Context context) {
    super(context);
    TAG = MediaController.getInstance().generateObserverTag();

    seekBar = new SeekBar(context);
    seekBar.delegate = this;
    radialProgress = new RadialProgress(this);
    drawForwardedName = true;

    if (timePaint == null) {
      timePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
      timePaint.setTextSize(AndroidUtilities.dp(12));

      circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    MediaController.getInstance().removeLoadingFileObserver(this);
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    updateButtonState(false);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    float x = event.getX();
    float y = event.getY();
    boolean result =
        seekBar.onTouch(event.getAction(), event.getX() - seekBarX, event.getY() - seekBarY);
    if (result) {
      if (event.getAction() == MotionEvent.ACTION_DOWN) {
        getParent().requestDisallowInterceptTouchEvent(true);
      }
      invalidate();
    } else {
      int side = AndroidUtilities.dp(36);
      if (event.getAction() == MotionEvent.ACTION_DOWN) {
        if (x >= buttonX && x <= buttonX + side && y >= buttonY && y <= buttonY + side) {
          buttonPressed = true;
          invalidate();
          result = true;
        }
      } else if (buttonPressed) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
          buttonPressed = false;
          playSoundEffect(SoundEffectConstants.CLICK);
          didPressedButton();
          invalidate();
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
          buttonPressed = false;
          invalidate();
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
          if (!(x >= buttonX && x <= buttonX + side && y >= buttonY && y <= buttonY + side)) {
            buttonPressed = false;
            invalidate();
          }
        }
      }
      if (!result) {
        result = super.onTouchEvent(event);
      }
    }

    return result;
  }

  private void didPressedButton() {
    if (buttonState == 0) {
      boolean result = MediaController.getInstance().playAudio(currentMessageObject);
      if (!currentMessageObject.isOut() && currentMessageObject.isContentUnread()) {
        MessagesController.getInstance().markMessageContentAsRead(currentMessageObject.getId());
      }
      if (result) {
        buttonState = 1;
        radialProgress.setBackground(getDrawableForCurrentState(), false, false);
        invalidate();
      }
    } else if (buttonState == 1) {
      boolean result = MediaController.getInstance().pauseAudio(currentMessageObject);
      if (result) {
        buttonState = 0;
        radialProgress.setBackground(getDrawableForCurrentState(), false, false);
        invalidate();
      }
    } else if (buttonState == 2) {
      FileLoader.getInstance().loadFile(currentMessageObject.messageOwner.media.audio, true);
      buttonState = 3;
      radialProgress.setBackground(getDrawableForCurrentState(), true, false);
      invalidate();
    } else if (buttonState == 3) {
      FileLoader.getInstance().cancelLoadFile(currentMessageObject.messageOwner.media.audio);
      buttonState = 2;
      radialProgress.setBackground(getDrawableForCurrentState(), false, false);
      invalidate();
    } else if (buttonState == 4) {
      if (currentMessageObject.isOut() && currentMessageObject.isSending()) {
        if (delegate != null) {
          delegate.didPressedCancelSendButton(this);
        }
      }
    }
  }

  public void updateProgress() {
    if (currentMessageObject == null) {
      return;
    }

    if (!seekBar.isDragging()) {
      seekBar.setProgress(currentMessageObject.audioProgress);
    }

    int duration;
    if (!MediaController.getInstance().isPlayingAudio(currentMessageObject)) {
      duration = currentMessageObject.messageOwner.media.audio.duration;
    } else {
      duration = currentMessageObject.audioProgressSec;
    }
    String timeString = String.format("%02d:%02d", duration / 60, duration % 60);
    if (lastTimeString == null || lastTimeString != null && !lastTimeString.equals(timeString)) {
      lastTimeString = timeString;
      timeWidth = (int) Math.ceil(timePaint.measureText(timeString));
      timeLayout =
          new StaticLayout(
              timeString, timePaint, timeWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
    }
    invalidate();
  }

  public void downloadAudioIfNeed() {
    if (buttonState == 2) {
      FileLoader.getInstance().loadFile(currentMessageObject.messageOwner.media.audio, true);
      buttonState = 3;
      radialProgress.setBackground(getDrawableForCurrentState(), false, false);
    }
  }

  public void updateButtonState(boolean animated) {
    if (currentMessageObject == null) {
      return;
    }
    if (currentMessageObject.isOut() && currentMessageObject.isSending()) {
      MediaController.getInstance()
          .addLoadingFileObserver(currentMessageObject.messageOwner.attachPath, this);
      buttonState = 4;
      radialProgress.setBackground(getDrawableForCurrentState(), true, animated);
      Float progress =
          ImageLoader.getInstance().getFileProgress(currentMessageObject.messageOwner.attachPath);
      if (progress == null
          && SendMessagesHelper.getInstance().isSendingMessage(currentMessageObject.getId())) {
        progress = 1.0f;
      }
      radialProgress.setProgress(progress != null ? progress : 0, false);
    } else {
      File cacheFile = null;
      if (currentMessageObject.messageOwner.attachPath != null
          && currentMessageObject.messageOwner.attachPath.length() > 0) {
        cacheFile = new File(currentMessageObject.messageOwner.attachPath);
        if (!cacheFile.exists()) {
          cacheFile = null;
        }
      }
      if (cacheFile == null) {
        cacheFile = FileLoader.getPathToMessage(currentMessageObject.messageOwner);
      }
      if (cacheFile.exists()) {
        MediaController.getInstance().removeLoadingFileObserver(this);
        boolean playing = MediaController.getInstance().isPlayingAudio(currentMessageObject);
        if (!playing || playing && MediaController.getInstance().isAudioPaused()) {
          buttonState = 0;
        } else {
          buttonState = 1;
        }
        radialProgress.setProgress(0, animated);
        radialProgress.setBackground(getDrawableForCurrentState(), false, animated);
      } else {
        String fileName = currentMessageObject.getFileName();
        MediaController.getInstance().addLoadingFileObserver(fileName, this);
        if (!FileLoader.getInstance().isLoadingFile(fileName)) {
          buttonState = 2;
          radialProgress.setProgress(0, animated);
          radialProgress.setBackground(getDrawableForCurrentState(), false, animated);
        } else {
          buttonState = 3;
          Float progress = ImageLoader.getInstance().getFileProgress(fileName);
          if (progress != null) {
            radialProgress.setProgress(progress, animated);
          } else {
            radialProgress.setProgress(0, animated);
          }
          radialProgress.setBackground(getDrawableForCurrentState(), true, animated);
        }
      }
    }
    updateProgress();
  }

  @Override
  public void onFailedDownload(String fileName) {
    updateButtonState(true);
  }

  @Override
  public void onSuccessDownload(String fileName) {
    updateButtonState(true);
  }

  @Override
  public void onProgressDownload(String fileName, float progress) {
    radialProgress.setProgress(progress, true);
    if (buttonState != 3) {
      updateButtonState(false);
    }
  }

  @Override
  public void onProgressUpload(String fileName, float progress, boolean isEncrypted) {
    radialProgress.setProgress(progress, true);
  }

  @Override
  public int getObserverTag() {
    return TAG;
  }

  @Override
  public void onSeekBarDrag(float progress) {
    if (currentMessageObject == null) {
      return;
    }
    currentMessageObject.audioProgress = progress;
    MediaController.getInstance().seekToProgress(currentMessageObject, progress);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int width = MeasureSpec.getSize(widthMeasureSpec);
    setMeasuredDimension(width, AndroidUtilities.dp(66) + namesOffset);
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);

    if (currentMessageObject.isOut()) {
      seekBarX = layoutWidth - backgroundWidth + AndroidUtilities.dp(55);
      buttonX = layoutWidth - backgroundWidth + AndroidUtilities.dp(13);
      timeX = layoutWidth - backgroundWidth + AndroidUtilities.dp(66);
    } else {
      if (isChat) {
        seekBarX = AndroidUtilities.dp(116);
        buttonX = AndroidUtilities.dp(74);
        timeX = AndroidUtilities.dp(127);
      } else {
        seekBarX = AndroidUtilities.dp(64);
        buttonX = AndroidUtilities.dp(22);
        timeX = AndroidUtilities.dp(75);
      }
    }

    seekBar.width = backgroundWidth - AndroidUtilities.dp(70);
    seekBar.height = AndroidUtilities.dp(30);
    seekBarY = AndroidUtilities.dp(11) + namesOffset;
    buttonY = AndroidUtilities.dp(13) + namesOffset;
    radialProgress.setProgressRect(
        buttonX, buttonY, buttonX + AndroidUtilities.dp(40), buttonY + AndroidUtilities.dp(40));

    updateProgress();
  }

  @Override
  public void setMessageObject(MessageObject messageObject) {
    boolean dataChanged = currentMessageObject == messageObject && isUserDataChanged();
    if (currentMessageObject != messageObject || dataChanged) {
      if (AndroidUtilities.isTablet()) {
        backgroundWidth =
            Math.min(
                AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(isChat ? 102 : 50),
                AndroidUtilities.dp(300));
      } else {
        backgroundWidth =
            Math.min(
                AndroidUtilities.displaySize.x - AndroidUtilities.dp(isChat ? 102 : 50),
                AndroidUtilities.dp(300));
      }

      if (messageObject.isOut()) {
        seekBar.type = 0;
        radialProgress.setProgressColor(0xff87bf78);
      } else {
        seekBar.type = 1;
        radialProgress.setProgressColor(0xffa2b5c7);
      }

      super.setMessageObject(messageObject);
    }
    updateButtonState(dataChanged);
  }

  private Drawable getDrawableForCurrentState() {
    return ResourceLoader.audioStatesDrawable[
        currentMessageObject.isOut() ? buttonState : buttonState + 5][0];
    //buttonPressed ? 1 :
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    if (currentMessageObject == null) {
      return;
    }

    canvas.save();
    canvas.translate(seekBarX, seekBarY);
    seekBar.draw(canvas);
    canvas.restore();

    if (currentMessageObject.isOut()) {
      timePaint.setColor(0xff70b15c);
      circlePaint.setColor(0xff87bf78);
    } else {
      timePaint.setColor(0xffa1aab3);
      circlePaint.setColor(0xff4195e5);
    }
    radialProgress.onDraw(canvas);

    canvas.save();
    canvas.translate(timeX, AndroidUtilities.dp(42) + namesOffset);
    timeLayout.draw(canvas);
    canvas.restore();

    if (currentMessageObject.isContentUnread()) {
      canvas.drawCircle(
          timeX + timeWidth + AndroidUtilities.dp(8),
          AndroidUtilities.dp(49.5f) + namesOffset,
          AndroidUtilities.dp(3),
          circlePaint);
    }
  }
}
