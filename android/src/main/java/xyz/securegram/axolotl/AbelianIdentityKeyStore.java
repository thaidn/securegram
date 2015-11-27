package xyz.securegram.axolotl;


import android.util.Log;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.android.MessagesStorage;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.whispersystems.libaxolotl.AxolotlAddress;
import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.state.IdentityKeyStore;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.Semaphore;

public class AbelianIdentityKeyStore extends MessagesStorage implements IdentityKeyStore {
  private static final String TAG = AbelianIdentityKeyStore.class.getName();

  public AbelianIdentityKeyStore() {
    super();
  }

  @Override
  public IdentityKeyPair getIdentityKeyPair() {
    return UserConfig.identityKeyPair;
  }

  @Override
  public SignedPreKeyRecord getSignedPreKeyRecord() {
    return UserConfig.signedPreKeyRecord;
  }

  @Override
  public int getLocalDeviceId() {
    return UserConfig.deviceId;
  }

  @Override
  public void saveIdentity(final AxolotlAddress address, final IdentityKey identityKey) {
    getStorageQueue().postRunnable(
        new Runnable() {
          @Override
          public void run() {
            try {
              getDatabase().beginTransaction();
              SQLitePreparedStatement state =
                  getDatabase().executeFast("REPLACE INTO identities VALUES(?, ?)");
              state.requery();
              state.bindInteger(1, Integer.valueOf(address.getName()));
              state.bindString(2, Utilities.base64EncodeBytes(identityKey.serialize()));
              state.step();
              state.dispose();
              getDatabase().commitTransaction();
            } catch (Exception e) {
              Log.e(TAG, "Cannot save identity for " + address, e);
            }
          }
        });
  }

  @Override
  public boolean isTrustedIdentity(final AxolotlAddress address, final IdentityKey theirIdentity) {
    Semaphore semaphore = new Semaphore(0);
    ArrayList<Boolean> result = new ArrayList<>();
    isTrustedIdentity(address, theirIdentity, semaphore, result);
    try {
      semaphore.acquire();
    } catch (Exception e) {
      Log.e(TAG, "Cannot know if the identity of " + address + " is trusted or not", e);
    }
    if (result.size() == 1) {
      return result.get(0);
    }
    return false;
  }

  private void isTrustedIdentity(final AxolotlAddress address, final IdentityKey theirIdentity,
                                 final Semaphore semaphore, final ArrayList<Boolean> result) {
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
                          "SELECT identity_key FROM identities WHERE uid = %d",
                          Integer.valueOf(address.getName())));
              while (cursor != null && cursor.next()) {
                byte serializedIdentity[] = cursor.stringValue(0).getBytes("UTF-8");
                IdentityKey ourIdentity = new IdentityKey(
                    Utilities.base64DecodeBytes(serializedIdentity), 0);
                result.add(ourIdentity.equals(theirIdentity));
              }
              if (result.size() == 0) {
                // non-existence, trust on first use.
                result.add(Boolean.TRUE);
              }
            } catch (Exception e) {
              Log.e(TAG, "Cannot know if the identity of " + address + " is trusted or not", e);
            } finally {
              if (cursor != null) {
                cursor.dispose();
              }
              semaphore.release();
            }
          }
        });
  }

  public void deleteIdentity(final String name) {
    getStorageQueue().postRunnable(
        new Runnable() {
          @Override
          public void run() {
            try {
              getDatabase()
                  .executeFast("DELETE FROM identities WHERE uid = " + Integer.valueOf(name))
                  .stepThis()
                  .dispose();
              Log.e(TAG, "finally remove " + name);
              new ExportDatabaseFileTask().execute("");
            } catch (Exception e) {
              Log.e(TAG, "Cannot delete identity of " + name, e);
            }
          }
        });
  }

}
