package xyz.securegram.axolotl;

import android.util.Log;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.android.AndroidUtilities;
import org.telegram.android.MessagesStorage;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.Utilities;
import org.whispersystems.libaxolotl.AxolotlAddress;
import org.whispersystems.libaxolotl.state.SessionRecord;
import org.whispersystems.libaxolotl.state.SessionStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;

public class AbelianSessionStore extends MessagesStorage implements SessionStore {
  private static final String TAG = AbelianSessionStore.class.getName();

  public AbelianSessionStore() {
    super();
  }

  @Override
  public SessionRecord loadSession(final AxolotlAddress address) {
    Semaphore semaphore = new Semaphore(0);
    ArrayList<SessionRecord> result = new ArrayList<>();
    loadSession(address, semaphore, result);
    try {
      semaphore.acquire();
    } catch (Exception e) {
      Log.e(TAG, "Cannot load session of " + address.toString(), e);
    }
    if (result.size() == 1) {
      return result.get(0);
    }
    return new SessionRecord();
  }

  private void loadSession(final AxolotlAddress address, final Semaphore semaphore,
                           final ArrayList<SessionRecord> result) {
    if (semaphore == null || result == null) {
      return;
    }

    getStorageQueue().postRunnable(
        new Runnable() {
          @Override
          public void run() {
            SQLiteCursor cursor = null;
            try {
              cursor =
                  getDatabase().queryFinalized(
                      String.format(Locale.US,
                          "SELECT session_record FROM sessions WHERE uid = %d AND device_id = %d",
                          Integer.valueOf(address.getName()), address.getDeviceId()));
              while (cursor != null && cursor.next()) {
                byte serializedSessionRecord[] = cursor.stringValue(0).getBytes("UTF-8");
                result.add(new SessionRecord(Utilities.base64DecodeBytes(serializedSessionRecord)));
              }
            } catch (Exception e) {
              Log.e(TAG, "Cannot load session of " + address.toString(), e);
            } finally {
              if (cursor != null) {
                cursor.dispose();
              }
              semaphore.release();
            }
          }
        });
  }

  @Override
  public List<Integer> getSubDeviceSessions(String name) {
    Semaphore semaphore = new Semaphore(0);
    ArrayList<Integer> result = new ArrayList<>();
    getSubDeviceSessions(name, semaphore, result);
    try {
      semaphore.acquire();
    } catch (Exception e) {
      Log.e(TAG, "Cannot get device ids of " + name, e);
    }
    return result;
  }

  private void getSubDeviceSessions(final String name, final Semaphore semaphore,
                                    final ArrayList<Integer> result) {
    if (semaphore == null || result == null) {
      return;
    }

    getStorageQueue().postRunnable(
        new Runnable() {
          @Override
          public void run() {
            SQLiteCursor cursor = null;
            try {
              cursor =
                  getDatabase().queryFinalized(
                      String.format(Locale.US,
                          "SELECT device_id FROM sessions WHERE uid = %d",
                          Integer.valueOf(name)));
              while (cursor != null && cursor.next()) {
                result.add(cursor.intValue(0));
              }
            } catch (Exception e) {
              Log.e(TAG, "Cannot get device ids of " + name, e);
            } finally {
              if (cursor != null) {
                cursor.dispose();
              }
              semaphore.release();
            }
          }
        });
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
              AndroidUtilities.runOnUIThread(
                  new Runnable() {
                    @Override
                    public void run() {
                      NotificationCenter.getInstance()
                          .postNotificationName(NotificationCenter.ABELIAN_IDENTITY_LOADED,
                              address);
                    }
                  });
            } catch (Exception e) {
              Log.e(TAG, "Cannot store session of " + address, e);
            }
          }
        });
  }

  @Override
  public boolean containsSession(AxolotlAddress address) {
    SessionRecord record = loadSession(address);
    return !record.isFresh();
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
              Log.e(TAG, "Cannot delete session of " + address, e);
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
