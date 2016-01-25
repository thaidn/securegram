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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;

public class AbelianSessionStore extends MessagesStorage implements SessionStore {

  private static final String TAG = AbelianSessionStore.class.getName();

  private Map<AxolotlAddress, byte[]> sessions = new HashMap<>();

  public AbelianSessionStore() {
    super();
  }

  @Override
  public synchronized SessionRecord loadSession(final AxolotlAddress address) {
    try {
      if (sessions.containsKey(address)) {
        return new SessionRecord(sessions.get(address));
      }
    } catch (IOException e) {
      ;
    }

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
  public synchronized List<Integer> getSubDeviceSessions(String name) {
    List<Integer> deviceIds = new LinkedList<>();

    for (AxolotlAddress key : sessions.keySet()) {
      if (key.getName().equals(name) &&
          key.getDeviceId() != 1)
      {
        deviceIds.add(key.getDeviceId());
      }
    }
    if (!deviceIds.isEmpty()) {
      return deviceIds;
    }

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
  public synchronized void storeSession(final AxolotlAddress address, final SessionRecord record) {
    sessions.put(address, record.serialize());

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
  public synchronized boolean containsSession(AxolotlAddress address) {
    if (sessions.containsKey(address)) {
      return true;
    }
    SessionRecord record = loadSession(address);
    return !record.isFresh();
  }

  @Override
  public synchronized void deleteSession(final AxolotlAddress address) {
    sessions.remove(address);

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
  public synchronized void deleteAllSessions(final String name) {
    for (AxolotlAddress key : sessions.keySet()) {
      if (key.getName().equals(name)) {
        sessions.remove(key);
      }
    }

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

  public void deleteAllSessions() {
    getStorageQueue().postRunnable(
        new Runnable() {
          @Override
          public void run() {
            String query = String.format(Locale.US,
                "DELETE FROM sessions WHERE 1");
            try {
              getDatabase()
                  .executeFast(query)
                  .stepThis()
                  .dispose();
            } catch (Exception e) {
              Log.e(TAG, "Cannot delete any sessions", e);
            }
          }
        });
  }
}
