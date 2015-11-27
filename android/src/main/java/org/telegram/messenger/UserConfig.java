/*
 * This is the source code of Telegram for Android v. 2.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;
import org.whispersystems.libaxolotl.util.KeyHelper;

public class UserConfig {

  private static TLRPC.User currentUser;
  public static boolean registeredForPush = false;
  public static boolean registeredForInternalPush = false;
  public static String pushString = "";
  public static int lastSendMessageId = -210000;
  public static int lastLocalId = -210000;
  public static int lastBroadcastId = -1;
  public static String contactsHash = "";
  public static String importHash = "";
  public static boolean blockedUsersLoaded = false;
  private static final Object sync = new Object();
  public static boolean saveIncomingPhotos = false;
  public static int contactsVersion = 1;
  public static String passcodeHash = "";
  public static byte[] passcodeSalt = new byte[0];
  public static boolean appLocked = false;
  public static int passcodeType = 0;
  public static int autoLockIn = 60 * 60;
  public static int lastPauseTime = 0;
  public static boolean isWaitingForPasscodeEnter = false;
  public static int lastUpdateVersion;

  public static IdentityKeyPair identityKeyPair = null;
  public static SignedPreKeyRecord signedPreKeyRecord = null;
  public static int deviceId = 0;
  public static boolean registeredForAbelian = false;

  public static int getNewMessageId() {
    int id;
    synchronized (sync) {
      id = lastSendMessageId;
      lastSendMessageId--;
    }
    return id;
  }

  public static void saveConfig(boolean shouldSaveUser) {
    synchronized (sync) {
      try {
        SharedPreferences preferences =
            ApplicationLoader.applicationContext.getSharedPreferences(
                "userconfing", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("registeredForPush", registeredForPush);
        editor.putString("pushString", pushString);
        editor.putInt("lastSendMessageId", lastSendMessageId);
        editor.putInt("lastLocalId", lastLocalId);
        editor.putString("contactsHash", contactsHash);
        editor.putString("importHash", importHash);
        editor.putBoolean("saveIncomingPhotos", saveIncomingPhotos);
        editor.putInt("contactsVersion", contactsVersion);
        editor.putInt("lastBroadcastId", lastBroadcastId);
        editor.putBoolean("registeredForInternalPush", registeredForInternalPush);
        editor.putBoolean("blockedUsersLoaded", blockedUsersLoaded);
        editor.putString("passcodeHash1", passcodeHash);
        editor.putString(
            "passcodeSalt",
            passcodeSalt.length > 0 ? Base64.encodeToString(passcodeSalt, Base64.DEFAULT) : "");
        editor.putBoolean("appLocked", appLocked);
        editor.putInt("passcodeType", passcodeType);
        editor.putInt("autoLockIn", autoLockIn);
        editor.putInt("lastPauseTime", lastPauseTime);
        editor.putInt("lastUpdateVersion", lastUpdateVersion);

        if (currentUser != null) {
          if (shouldSaveUser) {
            SerializedData data = new SerializedData();
            currentUser.serializeToStream(data);
            String userString = Base64.encodeToString(data.toByteArray(), Base64.DEFAULT);
            editor.putString("user", userString);
            data.cleanup();
          }
        } else {
          editor.remove("user");
        }

        editor.commit();
      } catch (Exception e) {
        FileLog.e("tmessages", e);
      }
    }
  }

  public static void saveAbelianIdentity() {
    synchronized (sync) {
      try {
        SharedPreferences preferences =
            ApplicationLoader.applicationContext.getSharedPreferences(
                "userconfing", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        if (identityKeyPair != null) {
          editor.putString("identityKeyPair",
              Utilities.base64EncodeBytes(identityKeyPair.serialize()));
        }

        if (signedPreKeyRecord != null) {
          editor.putString("signedPreKeyRecord",
              Utilities.base64EncodeBytes(signedPreKeyRecord.serialize()));
        }

        if (deviceId != 0) {
          editor.putInt("deviceId", deviceId);
        }

        editor.putBoolean("registeredForAbelian", registeredForAbelian);

        editor.commit();
      } catch (Exception e) {
        FileLog.e("tmessages", e);
      }
    }
  }

  public static boolean isClientActivated() {
    synchronized (sync) {
      return currentUser != null;
    }
  }

  public static int getClientUserId() {
    synchronized (sync) {
      return currentUser != null ? currentUser.id : 0;
    }
  }

  public static TLRPC.User getCurrentUser() {
    synchronized (sync) {
      return currentUser;
    }
  }

  public static void setCurrentUser(TLRPC.User user) {
    synchronized (sync) {
      currentUser = user;
    }
  }

  private static void loadDefaultConfigOrFromPreferences() {
    SharedPreferences preferences =
        ApplicationLoader.applicationContext.getSharedPreferences(
            "userconfing", Context.MODE_PRIVATE);
    registeredForPush = preferences.getBoolean("registeredForPush", false);
    pushString = preferences.getString("pushString", "");
    lastSendMessageId = preferences.getInt("lastSendMessageId", -210000);
    lastLocalId = preferences.getInt("lastLocalId", -210000);
    contactsHash = preferences.getString("contactsHash", "");
    importHash = preferences.getString("importHash", "");
    saveIncomingPhotos = preferences.getBoolean("saveIncomingPhotos", false);
    contactsVersion = preferences.getInt("contactsVersion", 0);
    lastBroadcastId = preferences.getInt("lastBroadcastId", -1);
    registeredForInternalPush = preferences.getBoolean("registeredForInternalPush", false);
    blockedUsersLoaded = preferences.getBoolean("blockedUsersLoaded", false);
    passcodeHash = preferences.getString("passcodeHash1", "");
    appLocked = preferences.getBoolean("appLocked", false);
    passcodeType = preferences.getInt("passcodeType", 0);
    autoLockIn = preferences.getInt("autoLockIn", 60 * 60);
    lastPauseTime = preferences.getInt("lastPauseTime", 0);
    lastUpdateVersion = preferences.getInt("lastUpdateVersion", 511);
    String user = preferences.getString("user", null);
    if (user != null) {
      byte[] userBytes = Base64.decode(user, Base64.DEFAULT);
      if (userBytes != null) {
        SerializedData data = new SerializedData(userBytes);
        currentUser = TLRPC.User.TLdeserialize(data, data.readInt32(false), false);
        data.cleanup();
      }
    }
    String passcodeSaltString = preferences.getString("passcodeSalt", "");
    if (passcodeSaltString.length() > 0) {
      passcodeSalt = Base64.decode(passcodeSaltString, Base64.DEFAULT);
    } else {
      passcodeSalt = new byte[0];
    }

    registeredForAbelian = preferences.getBoolean("registeredForAbelian", false);

    String identity = preferences.getString("identityKeyPair", "");
    if (identity.length() > 0) {
      try {
        identityKeyPair = new IdentityKeyPair(
            Utilities.base64DecodeBytes(identity.getBytes("UTF-8")));
      } catch (Exception e) {
        ; // we are in deep shit.
      }
    } else {
      identityKeyPair = KeyHelper.generateIdentityKeyPair();
    }

    deviceId = preferences.getInt("deviceId", 0);
    if (deviceId == 0) {
      deviceId = KeyHelper.generateDeviceId();
    }

    String signedPreKey = preferences.getString("signedPreKeyRecord", "");
    if (signedPreKey.length() > 0) {
      try {
        signedPreKeyRecord = new SignedPreKeyRecord(
            Utilities.base64DecodeBytes(signedPreKey.getBytes("UTF-8")));
      } catch (Exception e) {
        ; // we are in deep shit.
      }
    } else {
      try {
        signedPreKeyRecord = KeyHelper.generateSignedPreKey(
            identityKeyPair);
      } catch (Exception e) {
        signedPreKeyRecord = null;
      }
    }
  }

  public static void loadConfig() {
    synchronized (sync) {
        loadDefaultConfigOrFromPreferences();
    }
  }

  public static boolean checkPasscode(String passcode) {
    if (passcodeSalt.length == 0) {
      boolean result = Utilities.MD5(passcode).equals(passcodeHash);
      if (result) {
        try {
          passcodeSalt = new byte[16];
          Utilities.random.nextBytes(passcodeSalt);
          byte[] passcodeBytes = passcode.getBytes("UTF-8");
          byte[] bytes = new byte[32 + passcodeBytes.length];
          System.arraycopy(passcodeSalt, 0, bytes, 0, 16);
          System.arraycopy(passcodeBytes, 0, bytes, 16, passcodeBytes.length);
          System.arraycopy(passcodeSalt, 0, bytes, passcodeBytes.length + 16, 16);
          passcodeHash = Utilities.bytesToHex(Utilities.computeSHA256(bytes, 0, bytes.length));
          saveConfig(false);
        } catch (Exception e) {
          FileLog.e("tmessages", e);
        }
      }
      return result;
    } else {
      try {
        byte[] passcodeBytes = passcode.getBytes("UTF-8");
        byte[] bytes = new byte[32 + passcodeBytes.length];
        System.arraycopy(passcodeSalt, 0, bytes, 0, 16);
        System.arraycopy(passcodeBytes, 0, bytes, 16, passcodeBytes.length);
        System.arraycopy(passcodeSalt, 0, bytes, passcodeBytes.length + 16, 16);
        String hash = Utilities.bytesToHex(Utilities.computeSHA256(bytes, 0, bytes.length));
        return passcodeHash.equals(hash);
      } catch (Exception e) {
        FileLog.e("tmessages", e);
      }
    }
    return false;
  }

  public static void clearConfig() {
    currentUser = null;
    registeredForInternalPush = false;
    registeredForPush = false;
    contactsHash = "";
    importHash = "";
    lastSendMessageId = -210000;
    contactsVersion = 1;
    lastBroadcastId = -1;
    saveIncomingPhotos = false;
    blockedUsersLoaded = false;
    appLocked = false;
    passcodeType = 0;
    passcodeHash = "";
    passcodeSalt = new byte[0];
    autoLockIn = 60 * 60;
    lastPauseTime = 0;
    isWaitingForPasscodeEnter = false;
    lastUpdateVersion = BuildVars.BUILD_VERSION;
    saveConfig(true);
  }
}
