/*
 * This is the source code of Telegram for Android v. 1.4.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.android;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.util.StateSet;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.EdgeEffect;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.telegram.android.animationcompat.AnimatorListenerAdapterProxy;
import org.telegram.android.animationcompat.AnimatorSetProxy;
import org.telegram.android.animationcompat.ObjectAnimatorProxy;
import org.telegram.android.animationcompat.ViewProxy;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.phoneformat.PhoneFormat;
import org.telegram.ui.components.ForegroundDetector;
import org.telegram.ui.components.NumberPicker;
import org.telegram.ui.components.TypefaceSpan;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.Locale;

public class AndroidUtilities {

    private static final Hashtable<String, Typeface> typefaceCache = new Hashtable<>();
    private static int prevOrientation = -10;
    private static boolean waitingForSms = false;
    private static final Object smsLock = new Object();

    public static int statusBarHeight = 0;
    public static float density = 1;
    public static Point displaySize = new Point();
    public static Integer photoSize = null;
    public static DisplayMetrics displayMetrics = new DisplayMetrics();
    public static int leftBaseline;
    public static boolean usingHardwareInput;
    private static Boolean isTablet = null;

    static {
        density = ApplicationLoader.applicationContext.getResources().getDisplayMetrics().density;
        leftBaseline = isTablet() ? 80 : 72;
        checkDisplaySize();
    }

    public static void lockOrientation(Activity activity) {
        if (activity == null || prevOrientation != -10 || Build.VERSION.SDK_INT < 9) {
            return;
        }
        try {
            prevOrientation = activity.getRequestedOrientation();
            WindowManager manager = (WindowManager) activity.getSystemService(Activity.WINDOW_SERVICE);
            if (manager != null && manager.getDefaultDisplay() != null) {
                int rotation = manager.getDefaultDisplay().getRotation();
                int orientation = activity.getResources().getConfiguration().orientation;
                int SCREEN_ORIENTATION_REVERSE_LANDSCAPE = 8;
                int SCREEN_ORIENTATION_REVERSE_PORTRAIT = 9;
                if (Build.VERSION.SDK_INT < 9) {
                    SCREEN_ORIENTATION_REVERSE_LANDSCAPE = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    SCREEN_ORIENTATION_REVERSE_PORTRAIT = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                }

                if (rotation == Surface.ROTATION_270) {
                    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    } else {
                        activity.setRequestedOrientation(SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
                    }
                } else if (rotation == Surface.ROTATION_90) {
                    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                        activity.setRequestedOrientation(SCREEN_ORIENTATION_REVERSE_PORTRAIT);
                    } else {
                        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    }
                } else if (rotation == Surface.ROTATION_0) {
                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    } else {
                        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    }
                } else {
                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        activity.setRequestedOrientation(SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
                    } else {
                        activity.setRequestedOrientation(SCREEN_ORIENTATION_REVERSE_PORTRAIT);
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    public static void unlockOrientation(Activity activity) {
        if (activity == null || Build.VERSION.SDK_INT < 9) {
            return;
        }
        try {
            if (prevOrientation != -10) {
                activity.setRequestedOrientation(prevOrientation);
                prevOrientation = -10;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    public static Typeface getTypeface(String assetPath) {
        synchronized (typefaceCache) {
            if (!typefaceCache.containsKey(assetPath)) {
                try {
                    Typeface t =
                        Typeface.createFromAsset(ApplicationLoader.applicationContext.getAssets(), assetPath);
                    typefaceCache.put(assetPath, t);
                } catch (Exception e) {
                    FileLog.e(
                        "Typefaces", "Could not get typeface '" + assetPath + "' because " + e.getMessage());
                    return null;
                }
            }
            return typefaceCache.get(assetPath);
        }
    }

    public static boolean isWaitingForSms() {
        boolean value;
        synchronized (smsLock) {
            value = waitingForSms;
        }
        return value;
    }

    public static void setWaitingForSms(boolean value) {
        synchronized (smsLock) {
            waitingForSms = value;
        }
    }

    public static void showKeyboard(View view) {
        if (view == null) {
            return;
        }
        InputMethodManager inputManager =
            (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
    }

    public static boolean isKeyboardShowed(View view) {
        if (view == null) {
            return false;
        }
        InputMethodManager inputManager =
            (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        return inputManager.isActive(view);
    }

    public static void hideKeyboard(View view) {
        if (view == null) {
            return;
        }
        InputMethodManager imm =
            (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (!imm.isActive()) {
            return;
        }
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public static File getCacheDir() {
        String state = null;
        try {
            state = Environment.getExternalStorageState();
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        if (state == null || state.startsWith(Environment.MEDIA_MOUNTED)) {
            try {
                File file = ApplicationLoader.applicationContext.getExternalCacheDir();
                if (file != null) {
                    return file;
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }
        try {
            File file = ApplicationLoader.applicationContext.getCacheDir();
            if (file != null) {
                return file;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return new File("");
    }

    public static int dp(float value) {
        if (value == 0) {
            return 0;
        }
        return (int) Math.ceil(density * value);
    }

    public static int compare(int lhs, int rhs) {
        if (lhs == rhs) {
            return 0;
        } else if (lhs > rhs) {
            return 1;
        }
        return -1;
    }

    public static float dpf2(float value) {
        if (value == 0) {
            return 0;
        }
        return density * value;
    }

    public static void checkDisplaySize() {
        try {
            Configuration configuration =
                ApplicationLoader.applicationContext.getResources().getConfiguration();
            usingHardwareInput =
                configuration.keyboard != Configuration.KEYBOARD_NOKEYS
                    && configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO;
            WindowManager manager =
                (WindowManager)
                    ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE);
            if (manager != null) {
                Display display = manager.getDefaultDisplay();
                if (display != null) {
                    display.getMetrics(displayMetrics);
                    if (android.os.Build.VERSION.SDK_INT < 13) {
                        displaySize.set(display.getWidth(), display.getHeight());
                    } else {
                        display.getSize(displaySize);
                    }
                    FileLog.e(
                        "tmessages",
                        "display size = "
                            + displaySize.x
                            + " "
                            + displaySize.y
                            + " "
                            + displayMetrics.xdpi
                            + "x"
                            + displayMetrics.ydpi);
                }
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }

    /*
     keyboardHidden
     public static final int KEYBOARDHIDDEN_NO = 1
     Constant for keyboardHidden, value corresponding to the keysexposed resource qualifier.

     public static final int KEYBOARDHIDDEN_UNDEFINED = 0
     Constant for keyboardHidden: a value indicating that no value has been set.

     public static final int KEYBOARDHIDDEN_YES = 2
     Constant for keyboardHidden, value corresponding to the keyshidden resource qualifier.

     hardKeyboardHidden
     public static final int HARDKEYBOARDHIDDEN_NO = 1
     Constant for hardKeyboardHidden, value corresponding to the physical keyboard being exposed.

     public static final int HARDKEYBOARDHIDDEN_UNDEFINED = 0
     Constant for hardKeyboardHidden: a value indicating that no value has been set.

     public static final int HARDKEYBOARDHIDDEN_YES = 2
     Constant for hardKeyboardHidden, value corresponding to the physical keyboard being hidden.

     keyboard
     public static final int KEYBOARD_12KEY = 3
     Constant for keyboard, value corresponding to the 12key resource qualifier.

     public static final int KEYBOARD_NOKEYS = 1
     Constant for keyboard, value corresponding to the nokeys resource qualifier.

     public static final int KEYBOARD_QWERTY = 2
     Constant for keyboard, value corresponding to the qwerty resource qualifier.
     */
    }

    public static float getPixelsInCM(float cm, boolean isX) {
        return (cm / 2.54f) * (isX ? displayMetrics.xdpi : displayMetrics.ydpi);
    }

    public static long makeBroadcastId(int id) {
        return 0x0000000100000000L | ((long) id & 0x00000000FFFFFFFFL);
    }

    public static int getMyLayerVersion(int layer) {
        return layer & 0xffff;
    }

    public static int getPeerLayerVersion(int layer) {
        return (layer >> 16) & 0xffff;
    }

    public static int setMyLayerVersion(int layer, int version) {
        return layer & 0xffff0000 | version;
    }

    public static int setPeerLayerVersion(int layer, int version) {
        return layer & 0x0000ffff | (version << 16);
    }

    public static void runOnUIThread(Runnable runnable) {
        runOnUIThread(runnable, 0);
    }

    public static void runOnUIThread(Runnable runnable, long delay) {
        if (delay == 0) {
            ApplicationLoader.applicationHandler.post(runnable);
        } else {
            ApplicationLoader.applicationHandler.postDelayed(runnable, delay);
        }
    }

    public static void cancelRunOnUIThread(Runnable runnable) {
        ApplicationLoader.applicationHandler.removeCallbacks(runnable);
    }

    public static boolean isTablet() {
        if (isTablet == null) {
            isTablet = ApplicationLoader.applicationContext.getResources().getBoolean(R.bool.isTablet);
        }
        return isTablet;
    }

    public static boolean isSmallTablet() {
        float minSide = Math.min(displaySize.x, displaySize.y) / density;
        return minSide <= 700;
    }

    public static int getMinTabletSide() {
        if (!isSmallTablet()) {
            int smallSide = Math.min(displaySize.x, displaySize.y);
            int leftSide = smallSide * 35 / 100;
            if (leftSide < dp(320)) {
                leftSide = dp(320);
            }
            return smallSide - leftSide;
        } else {
            int smallSide = Math.min(displaySize.x, displaySize.y);
            int maxSide = Math.max(displaySize.x, displaySize.y);
            int leftSide = maxSide * 35 / 100;
            if (leftSide < dp(320)) {
                leftSide = dp(320);
            }
            return Math.min(smallSide, maxSide - leftSide);
        }
    }

    public static int getPhotoSize() {
        if (photoSize == null) {
            if (Build.VERSION.SDK_INT >= 16) {
                photoSize = 1280;
            } else {
                photoSize = 800;
            }
        }
        return photoSize;
    }

    public static String formatTTLString(int ttl) {
        if (ttl < 60) {
            return LocaleController.formatPluralString("Seconds", ttl);
        } else if (ttl < 60 * 60) {
            return LocaleController.formatPluralString("Minutes", ttl / 60);
        } else if (ttl < 60 * 60 * 24) {
            return LocaleController.formatPluralString("Hours", ttl / 60 / 60);
        } else if (ttl < 60 * 60 * 24 * 7) {
            return LocaleController.formatPluralString("Days", ttl / 60 / 60 / 24);
        } else {
            int days = ttl / 60 / 60 / 24;
            if (ttl % 7 == 0) {
                return LocaleController.formatPluralString("Weeks", days / 7);
            } else {
                return String.format(
                    "%s %s",
                    LocaleController.formatPluralString("Weeks", days / 7),
                    LocaleController.formatPluralString("Days", days % 7));
            }
        }
    }

    public static AlertDialog.Builder buildTTLAlert(
        final Context context, final TLRPC.EncryptedChat encryptedChat) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString("MessageLifetime", R.string.MessageLifetime));
        final NumberPicker numberPicker = new NumberPicker(context);
        numberPicker.setMinValue(0);
        numberPicker.setMaxValue(20);
        if (encryptedChat.ttl > 0 && encryptedChat.ttl < 16) {
            numberPicker.setValue(encryptedChat.ttl);
        } else if (encryptedChat.ttl == 30) {
            numberPicker.setValue(16);
        } else if (encryptedChat.ttl == 60) {
            numberPicker.setValue(17);
        } else if (encryptedChat.ttl == 60 * 60) {
            numberPicker.setValue(18);
        } else if (encryptedChat.ttl == 60 * 60 * 24) {
            numberPicker.setValue(19);
        } else if (encryptedChat.ttl == 60 * 60 * 24 * 7) {
            numberPicker.setValue(20);
        } else if (encryptedChat.ttl == 0) {
            numberPicker.setValue(0);
        }
        numberPicker.setFormatter(
            new NumberPicker.Formatter() {
                @Override
                public String format(int value) {
                    if (value == 0) {
                        return LocaleController.getString(
                            "ShortMessageLifetimeForever", R.string.ShortMessageLifetimeForever);
                    } else if (value >= 1 && value < 16) {
                        return AndroidUtilities.formatTTLString(value);
                    } else if (value == 16) {
                        return AndroidUtilities.formatTTLString(30);
                    } else if (value == 17) {
                        return AndroidUtilities.formatTTLString(60);
                    } else if (value == 18) {
                        return AndroidUtilities.formatTTLString(60 * 60);
                    } else if (value == 19) {
                        return AndroidUtilities.formatTTLString(60 * 60 * 24);
                    } else if (value == 20) {
                        return AndroidUtilities.formatTTLString(60 * 60 * 24 * 7);
                    }
                    return "";
                }
            });
        builder.setView(numberPicker);
        builder.setNegativeButton(
            LocaleController.getString("Done", R.string.Done),
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    int oldValue = encryptedChat.ttl;
                    which = numberPicker.getValue();
                    if (which >= 0 && which < 16) {
                        encryptedChat.ttl = which;
                    } else if (which == 16) {
                        encryptedChat.ttl = 30;
                    } else if (which == 17) {
                        encryptedChat.ttl = 60;
                    } else if (which == 18) {
                        encryptedChat.ttl = 60 * 60;
                    } else if (which == 19) {
                        encryptedChat.ttl = 60 * 60 * 24;
                    } else if (which == 20) {
                        encryptedChat.ttl = 60 * 60 * 24 * 7;
                    }
                    if (oldValue != encryptedChat.ttl) {
                        SecretChatHelper.getInstance().sendTTLMessage(encryptedChat, null);
                        MessagesStorage.getInstance().updateEncryptedChatTTL(encryptedChat);
                    }
                }
            });
        return builder;
    }

    public static void clearCursorDrawable(EditText editText) {
        if (editText == null || Build.VERSION.SDK_INT < 12) {
            return;
        }
        try {
            Field mCursorDrawableRes = TextView.class.getDeclaredField("mCursorDrawableRes");
            mCursorDrawableRes.setAccessible(true);
            mCursorDrawableRes.setInt(editText, 0);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    public static void setProgressBarAnimationDuration(ProgressBar progressBar, int duration) {
        if (progressBar == null) {
            return;
        }
        try {
            Field mCursorDrawableRes = ProgressBar.class.getDeclaredField("mDuration");
            mCursorDrawableRes.setAccessible(true);
            mCursorDrawableRes.setInt(progressBar, duration);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    public static int getViewInset(View view) {
        if (view == null || Build.VERSION.SDK_INT < 21) {
            return 0;
        }
        try {
            Field mAttachInfoField = View.class.getDeclaredField("mAttachInfo");
            mAttachInfoField.setAccessible(true);
            Object mAttachInfo = mAttachInfoField.get(view);
            if (mAttachInfo != null) {
                Field mStableInsetsField = mAttachInfo.getClass().getDeclaredField("mStableInsets");
                mStableInsetsField.setAccessible(true);
                Rect insets = (Rect) mStableInsetsField.get(mAttachInfo);
                return insets.bottom;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return 0;
    }

    public static Point getRealScreenSize() {
        Point size = new Point();
        try {
            WindowManager windowManager =
                (WindowManager)
                    ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                windowManager.getDefaultDisplay().getRealSize(size);
            } else {
                try {
                    Method mGetRawW = Display.class.getMethod("getRawWidth");
                    Method mGetRawH = Display.class.getMethod("getRawHeight");
                    size.set(
                        (Integer) mGetRawW.invoke(windowManager.getDefaultDisplay()),
                        (Integer) mGetRawH.invoke(windowManager.getDefaultDisplay()));
                } catch (Exception e) {
                    size.set(
                        windowManager.getDefaultDisplay().getWidth(),
                        windowManager.getDefaultDisplay().getHeight());
                    FileLog.e("tmessages", e);
                }
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return size;
    }

    public static void setListViewEdgeEffectColor(AbsListView listView, int color) {
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                Field field = AbsListView.class.getDeclaredField("mEdgeGlowTop");
                field.setAccessible(true);
                EdgeEffect mEdgeGlowTop = (EdgeEffect) field.get(listView);
                if (mEdgeGlowTop != null) {
                    mEdgeGlowTop.setColor(color);
                }

                field = AbsListView.class.getDeclaredField("mEdgeGlowBottom");
                field.setAccessible(true);
                EdgeEffect mEdgeGlowBottom = (EdgeEffect) field.get(listView);
                if (mEdgeGlowBottom != null) {
                    mEdgeGlowBottom.setColor(color);
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }
    }

    @SuppressLint("NewApi")
    public static void clearDrawableAnimation(View view) {
        if (Build.VERSION.SDK_INT < 21 || view == null) {
            return;
        }
        Drawable drawable;
        if (view instanceof ListView) {
            drawable = ((ListView) view).getSelector();
            if (drawable != null) {
                drawable.setState(StateSet.NOTHING);
            }
        } else {
            drawable = view.getBackground();
            if (drawable != null) {
                drawable.setState(StateSet.NOTHING);
                drawable.jumpToCurrentState();
            }
        }
    }

    public static final int FLAG_TAG_BR = 1;
    public static final int FLAG_TAG_BOLD = 2;
    public static final int FLAG_TAG_COLOR = 4;
    public static final int FLAG_TAG_ALL = FLAG_TAG_BR | FLAG_TAG_BOLD | FLAG_TAG_COLOR;

    public static Spannable replaceTags(String str) {
        return replaceTags(str, FLAG_TAG_ALL);
    }

    public static Spannable replaceTags(String str, int flag) {
        try {
            int start;
            int end;
            StringBuilder stringBuilder = new StringBuilder(str);
            if ((flag & FLAG_TAG_BR) != 0) {
                while ((start = stringBuilder.indexOf("<br>")) != -1) {
                    stringBuilder.replace(start, start + 4, "\n");
                }
                while ((start = stringBuilder.indexOf("<br/>")) != -1) {
                    stringBuilder.replace(start, start + 5, "\n");
                }
            }
            ArrayList<Integer> bolds = new ArrayList<>();
            if ((flag & FLAG_TAG_BOLD) != 0) {
                while ((start = stringBuilder.indexOf("<b>")) != -1) {
                    stringBuilder.replace(start, start + 3, "");
                    end = stringBuilder.indexOf("</b>");
                    if (end == -1) {
                        end = stringBuilder.indexOf("<b>");
                    }
                    stringBuilder.replace(end, end + 4, "");
                    bolds.add(start);
                    bolds.add(end);
                }
            }
            ArrayList<Integer> colors = new ArrayList<>();
            if ((flag & FLAG_TAG_COLOR) != 0) {
                while ((start = stringBuilder.indexOf("<c#")) != -1) {
                    stringBuilder.replace(start, start + 2, "");
                    end = stringBuilder.indexOf(">", start);
                    int color = Color.parseColor(stringBuilder.substring(start, end));
                    stringBuilder.replace(start, end + 1, "");
                    end = stringBuilder.indexOf("</c>");
                    stringBuilder.replace(end, end + 4, "");
                    colors.add(start);
                    colors.add(end);
                    colors.add(color);
                }
            }
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(stringBuilder);
            for (int a = 0; a < bolds.size() / 2; a++) {
                spannableStringBuilder.setSpan(
                    new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf")),
                    bolds.get(a * 2),
                    bolds.get(a * 2 + 1),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            for (int a = 0; a < colors.size() / 3; a++) {
                spannableStringBuilder.setSpan(
                    new ForegroundColorSpan(colors.get(a * 3 + 2)),
                    colors.get(a * 3),
                    colors.get(a * 3 + 1),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return spannableStringBuilder;
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return new SpannableStringBuilder(str);
    }

    public static boolean needShowPasscode(boolean reset) {
        boolean wasInBackground;
        if (Build.VERSION.SDK_INT >= 14) {
            wasInBackground = ForegroundDetector.getInstance().isWasInBackground(reset);
            if (reset) {
                ForegroundDetector.getInstance().resetBackgroundVar();
            }
        } else {
            wasInBackground = UserConfig.lastPauseTime != 0;
        }
        return UserConfig.passcodeHash.length() > 0
            && wasInBackground
            && (UserConfig.appLocked
            || UserConfig.autoLockIn != 0
            && UserConfig.lastPauseTime != 0
            && !UserConfig.appLocked
            && (UserConfig.lastPauseTime + UserConfig.autoLockIn)
            <= ConnectionsManager.getInstance().getCurrentTime());
    }

    public static void shakeTextView(final TextView textView, final float x, final int num) {
        if (num == 6) {
            ViewProxy.setTranslationX(textView, 0);
            textView.clearAnimation();
            return;
        }
        AnimatorSetProxy animatorSetProxy = new AnimatorSetProxy();
        animatorSetProxy.playTogether(
            ObjectAnimatorProxy.ofFloat(textView, "translationX", AndroidUtilities.dp(x)));
        animatorSetProxy.setDuration(50);
        animatorSetProxy.addListener(
            new AnimatorListenerAdapterProxy() {
                @Override
                public void onAnimationEnd(Object animation) {
                    shakeTextView(textView, num == 5 ? 0 : -x, num + 1);
                }
            });
        animatorSetProxy.start();
    }

    public static void addMediaToGallery(String fromPath) {
        if (fromPath == null) {
            return;
        }
        File f = new File(fromPath);
        Uri contentUri = Uri.fromFile(f);
        addMediaToGallery(contentUri);
    }

    public static void addMediaToGallery(Uri uri) {
        if (uri == null) {
            return;
        }
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(uri);
        ApplicationLoader.applicationContext.sendBroadcast(mediaScanIntent);
    }

    private static File getAlbumDir() {
        File storageDir = null;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            storageDir =
                new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "Telegram");
            if (!storageDir.mkdirs()) {
                if (!storageDir.exists()) {
                    FileLog.d("tmessages", "failed to create directory");
                    return null;
                }
            }
        } else {
            FileLog.d("tmessages", "External storage is not mounted READ/WRITE.");
        }

        return storageDir;
    }

    @SuppressLint("NewApi")
    public static String getPath(final Uri uri) {
        try {
            final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
            if (isKitKat && DocumentsContract.isDocumentUri(ApplicationLoader.applicationContext, uri)) {
                if (isExternalStorageDocument(uri)) {
                    final String docId = DocumentsContract.getDocumentId(uri);
                    final String[] split = docId.split(":");
                    final String type = split[0];
                    if ("primary".equalsIgnoreCase(type)) {
                        return Environment.getExternalStorageDirectory() + "/" + split[1];
                    }
                } else if (isDownloadsDocument(uri)) {
                    final String id = DocumentsContract.getDocumentId(uri);
                    final Uri contentUri =
                        ContentUris.withAppendedId(
                            Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
                    return getDataColumn(ApplicationLoader.applicationContext, contentUri, null, null);
                } else if (isMediaDocument(uri)) {
                    final String docId = DocumentsContract.getDocumentId(uri);
                    final String[] split = docId.split(":");
                    final String type = split[0];

                    Uri contentUri = null;
                    switch (type) {
                        case "image":
                            contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                            break;
                        case "video":
                            contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                            break;
                        case "audio":
                            contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                            break;
                    }

                    final String selection = "_id=?";
                    final String[] selectionArgs = new String[]{split[1]};

                    return getDataColumn(
                        ApplicationLoader.applicationContext, contentUri, selection, selectionArgs);
                }
            } else if ("content".equalsIgnoreCase(uri.getScheme())) {
                return getDataColumn(ApplicationLoader.applicationContext, uri, null, null);
            } else if ("file".equalsIgnoreCase(uri.getScheme())) {
                return uri.getPath();
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return null;
    }

    public static String getDataColumn(
        Context context, Uri uri, String selection, String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {column};

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    public static File generatePicturePath() {
        try {
            File storageDir = getAlbumDir();
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            return new File(storageDir, "IMG_" + timeStamp + ".jpg");
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return null;
    }

    public static CharSequence generateSearchName(String name, String name2, String q) {
        if (name == null && name2 == null) {
            return "";
        }
        SpannableStringBuilder builder = new SpannableStringBuilder();
        String wholeString = name;
        if (wholeString == null || wholeString.length() == 0) {
            wholeString = name2;
        } else if (name2 != null && name2.length() != 0) {
            wholeString += " " + name2;
        }
        wholeString = wholeString.trim();
        String lower = " " + wholeString.toLowerCase();

        int index;
        int lastIndex = 0;
        while ((index = lower.indexOf(" " + q, lastIndex)) != -1) {
            int idx = index - (index == 0 ? 0 : 1);
            int end = q.length() + (index == 0 ? 0 : 1) + idx;

            if (lastIndex != 0 && lastIndex != idx + 1) {
                builder.append(wholeString.substring(lastIndex, idx));
            } else if (lastIndex == 0 && idx != 0) {
                builder.append(wholeString.substring(0, idx));
            }

            String query = wholeString.substring(idx, end);
            if (query.startsWith(" ")) {
                builder.append(" ");
            }
            query = query.trim();
            builder.append(AndroidUtilities.replaceTags("<c#ff4d83b3>" + query + "</c>"));

            lastIndex = end;
        }

        if (lastIndex != -1 && lastIndex != wholeString.length()) {
            builder.append(wholeString.substring(lastIndex, wholeString.length()));
        }

        return builder;
    }

    public static File generateVideoPath() {
        try {
            File storageDir = getAlbumDir();
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            return new File(storageDir, "VID_" + timeStamp + ".mp4");
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return null;
    }

    public static String formatFileSize(long size) {
        if (size < 1024) {
            return String.format("%d B", size);
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0f);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / 1024.0f / 1024.0f);
        } else {
            return String.format("%.1f GB", size / 1024.0f / 1024.0f / 1024.0f);
        }
    }

    public static byte[] decodeQuotedPrintable(final byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        for (int i = 0; i < bytes.length; i++) {
            final int b = bytes[i];
            if (b == '=') {
                try {
                    final int u = Character.digit((char) bytes[++i], 16);
                    final int l = Character.digit((char) bytes[++i], 16);
                    buffer.write((char) ((u << 4) + l));
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                    return null;
                }
            } else {
                buffer.write(b);
            }
        }
        byte[] array = buffer.toByteArray();
        try {
            buffer.close();
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return array;
    }

    public static boolean copyFile(InputStream sourceFile, File destFile) throws IOException {
        OutputStream out = new FileOutputStream(destFile);
        byte[] buf = new byte[4096];
        int len;
        while ((len = sourceFile.read(buf)) > 0) {
            Thread.yield();
            out.write(buf, 0, len);
        }
        out.close();
        return true;
    }

    public static boolean copyFile(File sourceFile, File destFile) throws IOException {
        if (!destFile.exists()) {
            destFile.createNewFile();
        }
        FileInputStream source = null;
        FileOutputStream destination = null;
        try {
            source = new FileInputStream(sourceFile);
            destination = new FileOutputStream(destFile);
            destination.getChannel().transferFrom(source.getChannel(), 0, source.getChannel().size());
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            return false;
        } finally {
            if (source != null) {
                source.close();
            }
            if (destination != null) {
                destination.close();
            }
        }
        return true;
    }

    public static ArrayList getContactsToSend(ContentResolver cr, Uri uri) {
        try {
            if (uri != null) {
                InputStream stream = cr.openInputStream(uri);

                String name = null;
                String nameEncoding = null;
                String nameCharset = null;
                ArrayList<String> phones = new ArrayList<>();
                BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(stream, "UTF-8"));
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    String[] args = line.split(":");
                    if (args.length != 2) {
                        continue;
                    }
                    if (args[0].startsWith("FN")) {
                        String[] params = args[0].split(";");
                        for (String param : params) {
                            String[] args2 = param.split("=");
                            if (args2.length != 2) {
                                continue;
                            }
                            if (args2[0].equals("CHARSET")) {
                                nameCharset = args2[1];
                            } else if (args2[0].equals("ENCODING")) {
                                nameEncoding = args2[1];
                            }
                        }
                        name = args[1];
                        if (nameEncoding != null &&
                            nameEncoding.equalsIgnoreCase("QUOTED-PRINTABLE")) {
                            while (name.endsWith("=") && nameEncoding != null) {
                                name = name.substring(0, name.length() - 1);
                                line = bufferedReader.readLine();
                                if (line == null) {
                                    break;
                                }
                                name += line;
                            }
                            byte[] bytes = AndroidUtilities.decodeQuotedPrintable(name.getBytes());
                            if (bytes != null && bytes.length != 0) {
                                String decodedName = new String(bytes, nameCharset);
                                if (decodedName != null) {
                                    name = decodedName;
                                }
                            }
                        }
                    } else if (args[0].startsWith("TEL")) {
                        String phone = PhoneFormat.stripExceptNumbers(args[1], true);
                        if (phone.length() > 0) {
                            phones.add(phone);
                        }
                    }
                }
                try {
                    bufferedReader.close();
                    stream.close();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                ArrayList contactsToSend = new ArrayList<>();

                if (name != null && !phones.isEmpty()) {
                    for (String phone : phones) {
                        TLRPC.User user = new TLRPC.TL_userContact_old2();
                        user.phone = phone;
                        user.first_name = name;
                        user.last_name = "";
                        user.id = 0;
                        contactsToSend.add(user);
                    }
                }
                return contactsToSend;
            } else {
                return null;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            return null;
        }
    }
}