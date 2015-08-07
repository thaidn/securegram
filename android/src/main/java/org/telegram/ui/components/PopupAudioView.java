/*
 * This is the source code of Telegram for Android v. 1.4.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.components;

import android.content.Context;
import android.graphics.Canvas;
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
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.R;
import org.telegram.ui.cells.BaseCell;

import java.io.File;

public class PopupAudioView extends BaseCell
    implements SeekBar.SeekBarDelegate, MediaController.FileDownloadProgressListener {

  private boolean wasLayout = false;
  protected MessageObject currentMessageObject;

  private static Drawable backgroundMediaDrawableIn;

  private static Drawable[][] statesDrawable = new Drawable[8][2];
  private static TextPaint timePaint;

  private SeekBar seekBar;
  private ProgressView progressView;
  private int seekBarX;
  private int seekBarY;

  private int buttonState = 0;
  private int buttonX;
  private int buttonY;
  private int buttonPressed = 0;

  private StaticLayout timeLayout;
  private int timeX;
  int timeWidth = 0;
  private String lastTimeString = null;

  private int TAG;

  public PopupAudioView(Context context) {
    super(context);
    if (backgroundMediaDrawableIn == null) {
      backgroundMediaDrawableIn = getResources().getDrawable(R.drawable.msg_in_photo);
      statesDrawable[0][0] = getResources().getDrawable(R.drawable.play_w2);
      statesDrawable[0][1] = getResources().getDrawable(R.drawable.play_w2_pressed);
      statesDrawable[1][0] = getResources().getDrawable(R.drawable.pause_w2);
      statesDrawable[1][1] = getResources().getDrawable(R.drawable.pause_w2_pressed);
      statesDrawable[2][0] = getResources().getDrawable(R.drawable.download_g);
      statesDrawable[2][1] = getResources().getDrawable(R.drawable.download_g_pressed);
      statesDrawable[3][0] = getResources().getDrawable(R.drawable.pause_g);
      statesDrawable[3][1] = getResources().getDrawable(R.drawable.pause_g_pressed);

      statesDrawable[4][0] = getResources().getDrawable(R.drawable.play_w);
      statesDrawable[4][1] = getResources().getDrawable(R.drawable.play_w_pressed);
      statesDrawable[5][0] = getResources().getDrawable(R.drawable.pause_w);
      statesDrawable[5][1] = getResources().getDrawable(R.drawable.pause_w_pressed);
      statesDrawable[6][0] = getResources().getDrawable(R.drawable.download_b);
      statesDrawable[6][1] = getResources().getDrawable(R.drawable.download_b_pressed);
      statesDrawable[7][0] = getResources().getDrawable(R.drawable.pause_b);
      statesDrawable[7][1] = getResources().getDrawable(R.drawable.pause_b_pressed);

      timePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
      timePaint.setTextSize(AndroidUtilities.dp(16));
    }

    TAG = MediaController.getInstance().generateObserverTag();

    seekBar = new SeekBar(getContext());
    seekBar.delegate = this;
    progressView = new ProgressView();
  }

  public void setMessageObject(MessageObject messageObject) {
    if (currentMessageObject != messageObject) {
      seekBar.type = 1;
      progressView.setProgressColors(0xffd9e2eb, 0xff86c5f8);

      currentMessageObject = messageObject;
      wasLayout = false;

      requestLayout();
    }
    updateButtonState();
  }

  public final MessageObject getMessageObject() {
    return currentMessageObject;
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int width = MeasureSpec.getSize(widthMeasureSpec);
    setMeasuredDimension(width, AndroidUtilities.dp(56));
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    if (currentMessageObject == null) {
      super.onLayout(changed, left, top, right, bottom);
      return;
    }

    seekBarX = AndroidUtilities.dp(54);
    buttonX = AndroidUtilities.dp(10);
    timeX = getMeasuredWidth() - timeWidth - AndroidUtilities.dp(16);

    seekBar.width = getMeasuredWidth() - AndroidUtilities.dp(70) - timeWidth;
    seekBar.height = AndroidUtilities.dp(30);
    progressView.width = getMeasuredWidth() - AndroidUtilities.dp(94) - timeWidth;
    progressView.height = AndroidUtilities.dp(30);
    seekBarY = AndroidUtilities.dp(13);
    buttonY = AndroidUtilities.dp(10);

    updateProgress();

    if (changed || !wasLayout) {
      wasLayout = true;
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (currentMessageObject == null) {
      return;
    }

    if (!wasLayout) {
      requestLayout();
      return;
    }

    setDrawableBounds(backgroundMediaDrawableIn, 0, 0, getMeasuredWidth(), getMeasuredHeight());
    backgroundMediaDrawableIn.draw(canvas);

    if (currentMessageObject == null) {
      return;
    }

    canvas.save();
    if (buttonState == 0 || buttonState == 1) {
      canvas.translate(seekBarX, seekBarY);
      seekBar.draw(canvas);
    } else {
      canvas.translate(seekBarX + AndroidUtilities.dp(12), seekBarY);
      progressView.draw(canvas);
    }
    canvas.restore();

    int state = buttonState + 4;
    timePaint.setColor(0xffa1aab3);
    Drawable buttonDrawable = statesDrawable[state][buttonPressed];
    int side = AndroidUtilities.dp(36);
    int x = (side - buttonDrawable.getIntrinsicWidth()) / 2;
    int y = (side - buttonDrawable.getIntrinsicHeight()) / 2;
    setDrawableBounds(buttonDrawable, x + buttonX, y + buttonY);
    buttonDrawable.draw(canvas);

    canvas.save();
    canvas.translate(timeX, AndroidUtilities.dp(18));
    timeLayout.draw(canvas);
    canvas.restore();
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    MediaController.getInstance().removeLoadingFileObserver(this);
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
          buttonPressed = 1;
          invalidate();
          result = true;
        }
      } else if (buttonPressed == 1) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
          buttonPressed = 0;
          playSoundEffect(SoundEffectConstants.CLICK);
          didPressedButton();
          invalidate();
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
          buttonPressed = 0;
          invalidate();
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
          if (!(x >= buttonX && x <= buttonX + side && y >= buttonY && y <= buttonY + side)) {
            buttonPressed = 0;
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
        currentMessageObject.setContentIsRead();
      }
      if (result) {
        buttonState = 1;
        invalidate();
      }
    } else if (buttonState == 1) {
      boolean result = MediaController.getInstance().pauseAudio(currentMessageObject);
      if (result) {
        buttonState = 0;
        invalidate();
      }
    } else if (buttonState == 2) {
      FileLoader.getInstance().loadFile(currentMessageObject.messageOwner.media.audio, true);
      buttonState = 3;
      invalidate();
    } else if (buttonState == 3) {
      FileLoader.getInstance().cancelLoadFile(currentMessageObject.messageOwner.media.audio);
      buttonState = 2;
      invalidate();
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
      invalidate();
    }
  }

  public void updateButtonState() {
    String fileName = currentMessageObject.getFileName();
    File cacheFile = FileLoader.getPathToMessage(currentMessageObject.messageOwner);
    if (cacheFile.exists()) {
      MediaController.getInstance().removeLoadingFileObserver(this);
      boolean playing = MediaController.getInstance().isPlayingAudio(currentMessageObject);
      if (!playing || playing && MediaController.getInstance().isAudioPaused()) {
        buttonState = 0;
      } else {
        buttonState = 1;
      }
      progressView.setProgress(0);
    } else {
      MediaController.getInstance().addLoadingFileObserver(fileName, this);
      if (!FileLoader.getInstance().isLoadingFile(fileName)) {
        buttonState = 2;
        progressView.setProgress(0);
      } else {
        buttonState = 3;
        Float progress = ImageLoader.getInstance().getFileProgress(fileName);
        if (progress != null) {
          progressView.setProgress(progress);
        } else {
          progressView.setProgress(0);
        }
      }
    }
    updateProgress();
  }

  @Override
  public void onFailedDownload(String fileName) {
    updateButtonState();
  }

  @Override
  public void onSuccessDownload(String fileName) {
    updateButtonState();
  }

  @Override
  public void onProgressDownload(String fileName, float progress) {
    progressView.setProgress(progress);
    if (buttonState != 3) {
      updateButtonState();
    }
    invalidate();
  }

  @Override
  public void onProgressUpload(String fileName, float progress, boolean isEncrypted) {}

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
}
