package xyz.securegram.axolotl;

import android.util.Log;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.android.MessagesStorage;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.whispersystems.libaxolotl.AxolotlAddress;
import org.whispersystems.libaxolotl.state.SessionRecord;
import org.whispersystems.libaxolotl.state.SessionStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AbelianSessionStore extends MessagesStorage implements SessionStore {
  private static final String TAG = AbelianIdentityKeyStore.class.getName();

  public AbelianSessionStore() {
    super();
  }

  @Override
  public SessionRecord loadSession(AxolotlAddress address) {
    SQLiteCursor cursor = null;
    try {
      cursor =
          getDatabase().queryFinalized(
              String.format(Locale.US,
                  "SELECT session_record FROM sessions WHERE uid = %d AND device_id = %d",
                  Integer.valueOf(address.getName()), address.getDeviceId()));
      while (cursor != null && cursor.next()) {
        byte serializedSessionRecord[] = cursor.stringValue(0).getBytes("UTF-8");
        cursor.dispose();
        return new SessionRecord(Utilities.base64DecodeBytes(serializedSessionRecord));
      }
    } catch (Exception e) {
      Log.e(TAG, "Cannot load session of " + address.toString(), e);
    } finally {
      if (cursor != null) {
        cursor.dispose();
      }
    }
    return new SessionRecord();
  }

  @Override
  public List<Integer> getSubDeviceSessions(String name) {
    SQLiteCursor cursor = null;
    try {
      cursor =
          getDatabase().queryFinalized(
              String.format(Locale.US,
                  "SELECT device_id FROM sessions WHERE uid = %d",
                  Integer.valueOf(name)));
      ArrayList<Integer> devices = new ArrayList<Integer>();
      while (cursor != null && cursor.next()) {
        devices.add(cursor.intValue(0));
      }
      cursor.dispose();
      return devices;
    } catch (Exception e) {
      Log.e(TAG, "Cannot get device ids of " + name, e);
    } finally {
      if (cursor != null) {
        cursor.dispose();
      }
    }
    return null;
  }

  @Override
  public void storeSession(final AxolotlAddress address, final SessionRecord record) {
    getStorageQueue().postRunnable(
        new Runnable() {
          @Override
          public void run() {
            try {
              getDatabase().beginTransaction();
              SQLitePreparedStatement state =
                  getDatabase().executeFast("REPLACE INTO sessions VALUES(?, ?, ?)");
              state.requery();
              state.bindInteger(1, Integer.valueOf(address.getName()));
              state.bindInteger(2, address.getDeviceId());
              state.bindString(3, Utilities.base64EncodeBytes(record.serialize()));
              state.step();
              state.dispose();
              getDatabase().commitTransaction();
              Log.e(TAG, "Session of " + address + " stored");
              NotificationCenter.getInstance()
                  .postNotificationName(NotificationCenter.ABELIAN_IDENTITY_LOADED, address);
            } catch (Exception e) {
              Log.e(TAG, "Cannot store session of " + address, e);
            }
          }
        });
  }

  @Override
  public boolean containsSession(AxolotlAddress address) {
    SQLiteCursor cursor = null;
    try {
      cursor =
          getDatabase().queryFinalized(
              String.format(Locale.US,
                  "SELECT session_record FROM sessions WHERE uid = %d AND device_id = %d",
                  Integer.valueOf(address.getName()), address.getDeviceId()));
      if (cursor != null && cursor.next()) {
        cursor.dispose();
        return true;
      }
    } catch (Exception e) {
      Log.e(TAG, "Cannot check existence of session of " + address.toString(), e);
    } finally {
      if (cursor != null) {
        cursor.dispose();
      }
    }
    return false;
  }

  @Override
  public void deleteSession(final AxolotlAddress address) {
    getStorageQueue().postRunnable(
        new Runnable() {
          @Override
          public void run() {
            String query = String.format(Locale.US,
                "DELETE FROM sessions WHERE uid = %d AND device_id = %d",
                Integer.valueOf(address.getName()), address.getDeviceId());
            try {
              getDatabase()
                  .executeFast(query)
                  .stepThis()
                  .dispose();
            } catch (Exception e) {
              Log.e(TAG, "Cannot delete session of " + address.toString(), e);
            }
          }
        });
  }

  @Override
  public void deleteAllSessions(final String name) {
    getStorageQueue().postRunnable(
        new Runnable() {
          @Override
          public void run() {
            String query = String.format(Locale.US,
                "DELETE FROM sessions WHERE uid = %d",
                Integer.valueOf(name));
            try {
              getDatabase()
                  .executeFast(query)
                  .stepThis()
                  .dispose();
            } catch (Exception e) {
              Log.e(TAG, "Cannot delete session of " + name, e);
            }
          }
        });
  }
}
