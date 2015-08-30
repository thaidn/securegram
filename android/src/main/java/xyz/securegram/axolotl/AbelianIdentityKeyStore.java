package xyz.securegram.axolotl;


import android.util.Log;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.android.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.whispersystems.libaxolotl.AxolotlAddress;
import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.state.IdentityKeyStore;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;

import java.util.Locale;

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
                  getDatabase().executeFast("REPLACE INTO identities VALUES(?, ?, ?)");
              state.requery();
              state.bindInteger(1, Integer.valueOf(address.getName()));
              state.bindInteger(2, address.getDeviceId());
              state.bindString(3, Utilities.base64EncodeBytes(identityKey.serialize()));
              state.step();
              state.dispose();
              getDatabase().commitTransaction();
            } catch (Exception e) {
              Log.e(TAG, "Cannot save identity for " + address.toString(), e);
            }
          }
        });
  }

  @Override
  public boolean isTrustedIdentity(final AxolotlAddress address, final IdentityKey theirIdentity) {
    SQLiteCursor cursor = null;
    try {
      cursor =
          getDatabase().queryFinalized(
              String.format(Locale.US,
                  "SELECT identity_key FROM identities WHERE uid = %d AND device_id = %d",
                  Integer.valueOf(address.getName()), address.getDeviceId()));
      while (cursor != null && cursor.next()) {
        byte serializedIdentity[] = cursor.stringValue(0).getBytes("UTF-8");
        IdentityKey ourIdentity = new IdentityKey(
            Utilities.base64DecodeBytes(serializedIdentity), 0);
        return ourIdentity.equals(theirIdentity);
      }
      cursor.dispose();
      return true;
    } catch (Exception e) {
      Log.e(TAG, "Cannot know if the identity of " + address.toString() + " is trusted or not", e);
      return false;
    } finally {
      if (cursor != null) {
        cursor.dispose();
      }
    }
  }

}
