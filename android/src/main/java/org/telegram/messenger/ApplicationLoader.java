/*
 * This is the source code of Telegram for Android v. 2.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.messenger;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.ContactsController;
import org.telegram.android.LocaleController;
import org.telegram.android.MediaController;
import org.telegram.android.MessagesController;
import org.telegram.android.NativeLoader;
import org.telegram.android.NotificationsService;
import org.telegram.android.ScreenReceiver;
import org.telegram.android.SendMessagesHelper;
import org.telegram.ui.components.ForegroundDetector;

import xyz.securegram.R;
import xyz.securegram.axolotl.AxolotlController;

import java.io.File;

public class ApplicationLoader extends Application {

  private GoogleCloudMessaging gcm;
  private String regid;
  public static final String PROPERTY_REG_ID = "registration_id";
  private static final String PROPERTY_APP_VERSION = "appVersion";
  private static Drawable cachedWallpaper;
  private static boolean isCustomTheme;
  private static final Object sync = new Object();

  public static volatile Context applicationContext;
  public static volatile Handler applicationHandler;
  private static volatile boolean applicationInited = false;

  public static volatile boolean isScreenOn = false;
  public static volatile boolean mainInterfacePaused = true;

  public static boolean isCustomTheme() {
    return isCustomTheme;
  }

  public static void reloadWallpaper() {
    cachedWallpaper = null;
    loadWallpaper();
  }

  public static void loadWallpaper() {
    if (cachedWallpaper != null) {
      return;
    }
    Utilities.searchQueue.postRunnable(
        new Runnable() {
          @Override
          public void run() {
            synchronized (sync) {
              int selectedColor = 0;
              try {
                SharedPreferences preferences =
                    ApplicationLoader.applicationContext.getSharedPreferences(
                        "mainconfig", Activity.MODE_PRIVATE);
                int selectedBackground = preferences.getInt("selectedBackground", 1000001);
                selectedColor = preferences.getInt("selectedColor", 0);
                if (selectedColor == 0) {
                  if (selectedBackground == 1000001) {
                    cachedWallpaper =
                        applicationContext.getResources().getDrawable(R.drawable.background_hd);
                    isCustomTheme = false;
                  } else {
                    File toFile =
                        new File(
                            ApplicationLoader.applicationContext.getFilesDir(), "wallpaper.jpg");
                    if (toFile.exists()) {
                      cachedWallpaper = Drawable.createFromPath(toFile.getAbsolutePath());
                      isCustomTheme = true;
                    } else {
                      cachedWallpaper =
                          applicationContext.getResources().getDrawable(R.drawable.background_hd);
                      isCustomTheme = false;
                    }
                  }
                }
              } catch (Throwable throwable) {
                //ignore
              }
              if (cachedWallpaper == null) {
                if (selectedColor == 0) {
                  selectedColor = -2693905;
                }
                cachedWallpaper = new ColorDrawable(selectedColor);
              }
            }
          }
        });
  }

  public static Drawable getCachedWallpaper() {
    synchronized (sync) {
      return cachedWallpaper;
    }
  }

  public static void postInitApplication() {
    if (applicationInited) {
      return;
    }

    try {
      LocaleController.getInstance();
    } catch (Exception e) {
      e.printStackTrace();
    }

    try {
      final IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
      filter.addAction(Intent.ACTION_SCREEN_OFF);
      final BroadcastReceiver mReceiver = new ScreenReceiver();
      applicationContext.registerReceiver(mReceiver, filter);
    } catch (Exception e) {
      e.printStackTrace();
    }

    try {
      PowerManager pm =
          (PowerManager)
              ApplicationLoader.applicationContext.getSystemService(Context.POWER_SERVICE);
      isScreenOn = pm.isScreenOn();
      FileLog.e("tmessages", "screen state = " + isScreenOn);
    } catch (Exception e) {
      FileLog.e("tmessages", e);
    }

    UserConfig.loadConfig();
    MessagesController.getInstance();
    if (UserConfig.getCurrentUser() != null) {
      MessagesController.getInstance().putUser(UserConfig.getCurrentUser(), true);
      ConnectionsManager.getInstance().applyCountryPortNumber(UserConfig.getCurrentUser().phone);
      ConnectionsManager.getInstance().initPushConnection();
      AxolotlController.getInstance().registerLocalAxolotlIdentity();
      MessagesController.getInstance().getBlockedUsers(true);
      SendMessagesHelper.getInstance().checkUnsentMessages();
    }

    ApplicationLoader app = (ApplicationLoader) ApplicationLoader.applicationContext;
    app.initPlayServices();
    FileLog.e("tmessages", "app initied");

    ContactsController.getInstance().checkAppAccount();
    MediaController.getInstance();

    applicationInited = true;
  }

  @Override
  public void onCreate() {
    super.onCreate();

    if (Build.VERSION.SDK_INT < 11) {
      java.lang.System.setProperty("java.net.preferIPv4Stack", "true");
      java.lang.System.setProperty("java.net.preferIPv6Addresses", "false");
    }

    applicationContext = getApplicationContext();
    NativeLoader.initNativeLibs(ApplicationLoader.applicationContext);

    if (Build.VERSION.SDK_INT >= 14) {
      new ForegroundDetector(this);
    }

    applicationHandler = new Handler(applicationContext.getMainLooper());

    startPushService();
  }

  public static void startPushService() {
    SharedPreferences preferences =
        applicationContext.getSharedPreferences("Notifications", MODE_PRIVATE);

    if (preferences.getBoolean("pushService", true)) {
      applicationContext.startService(new Intent(applicationContext,
          NotificationsService.class));
    } else {
      stopPushService();
    }
  }

  public static void stopPushService() {
    applicationContext.stopService(new Intent(applicationContext, NotificationsService.class));

    PendingIntent pintent =
        PendingIntent.getService(
            applicationContext,
            0,
            new Intent(applicationContext, NotificationsService.class), 0);
    AlarmManager alarm = (AlarmManager) applicationContext.getSystemService(
        Context.ALARM_SERVICE);
    alarm.cancel(pintent);
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    try {
      LocaleController.getInstance().onDeviceConfigurationChange(newConfig);
      AndroidUtilities.checkDisplaySize();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void initPlayServices() {
    if (checkPlayServices()) {
      gcm = GoogleCloudMessaging.getInstance(this);
      regid = getRegistrationId();

      if (regid.length() == 0) {
        registerInBackground();
      } else {
        sendRegistrationIdToBackend(false);
      }
    } else {
      FileLog.d("tmessages", "No valid Google Play Services APK found.");
    }
  }

  private boolean checkPlayServices() {
    int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
    return resultCode == ConnectionResult.SUCCESS;
  }

  private String getRegistrationId() {
    final SharedPreferences prefs = getGCMPreferences(applicationContext);
    String registrationId = prefs.getString(PROPERTY_REG_ID, "");
    if (registrationId.length() == 0) {
      FileLog.d("tmessages", "Registration not found.");
      return "";
    }
    int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
    if (registeredVersion != BuildVars.BUILD_VERSION) {
      FileLog.d("tmessages", "App version changed.");
      return "";
    }
    return registrationId;
  }

  private SharedPreferences getGCMPreferences(Context context) {
    return getSharedPreferences(ApplicationLoader.class.getSimpleName(), Context.MODE_PRIVATE);
  }

  private void registerInBackground() {
    AsyncTask<String, String, Boolean> task =
        new AsyncTask<String, String, Boolean>() {
          @Override
          protected Boolean doInBackground(String... objects) {
            if (gcm == null) {
              gcm = GoogleCloudMessaging.getInstance(applicationContext);
            }
            int count = 0;
            while (count < 1000) {
              try {
                count++;
                regid = gcm.register(BuildVars.GCM_SENDER_ID);
                sendRegistrationIdToBackend(true);
                storeRegistrationId(applicationContext, regid);
                return true;
              } catch (Exception e) {
                FileLog.e("tmessages", e);
              }
              try {
                if (count % 20 == 0) {
                  Thread.sleep(60000 * 30);
                } else {
                  Thread.sleep(5000);
                }
              } catch (InterruptedException e) {
                FileLog.e("tmessages", e);
              }
            }
            return false;
          }
        };

    if (android.os.Build.VERSION.SDK_INT >= 11) {
      task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null);
    } else {
      task.execute(null, null, null);
    }
  }

  private void sendRegistrationIdToBackend(final boolean isNew) {
    Utilities.stageQueue.postRunnable(
        new Runnable() {
          @Override
          public void run() {
            UserConfig.pushString = regid;
            UserConfig.registeredForPush = !isNew;
            UserConfig.saveConfig(false /* shouldSaveUser */);
            if (UserConfig.getClientUserId() != 0) {
              AndroidUtilities.runOnUIThread(
                  new Runnable() {
                    @Override
                    public void run() {
                      MessagesController.getInstance().registerForPush(regid);
                    }
                  });
            }
          }
        });
  }

  private void storeRegistrationId(Context context, String regId) {
    final SharedPreferences prefs = getGCMPreferences(context);
    int appVersion = BuildVars.BUILD_VERSION;
    FileLog.e("tmessages", "Saving regId on app version " + appVersion);
    SharedPreferences.Editor editor = prefs.edit();
    editor.putString(PROPERTY_REG_ID, regId);
    editor.putInt(PROPERTY_APP_VERSION, appVersion);
    editor.commit();
  }
}
