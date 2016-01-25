package xyz.securegram.axolotl;

import android.util.Log;


import org.telegram.messenger.Utilities;

import org.whispersystems.libaxolotl.AxolotlAddress;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.InvalidVersionException;
import org.whispersystems.libaxolotl.LegacyMessageException;
import org.whispersystems.libaxolotl.SessionBuilder;
import org.whispersystems.libaxolotl.StaleKeyExchangeException;
import org.whispersystems.libaxolotl.UntrustedIdentityException;
import org.whispersystems.libaxolotl.protocol.CiphertextMessage;
import org.whispersystems.libaxolotl.protocol.KeyExchangeMessage;
import org.whispersystems.libaxolotl.protocol.WhisperMessage;

import org.whispersystems.libaxolotl.SessionCipher;

import java.util.List;


public class AxolotlController {
  private static final String TAG = AxolotlController.class.getName();
  private static volatile AxolotlController Instance = null;


  private AbelianAxolotlStore axolotlStore;

  public static AxolotlController getInstance() {
    AxolotlController localInstance = Instance;
    if (localInstance == null) {
      synchronized (AxolotlController.class) {
        localInstance = Instance;
        if (localInstance == null) {
          Instance = localInstance = new AxolotlController();
        }
      }
    }
    return localInstance;
  }

  public AxolotlController() {
    axolotlStore = new AbelianAxolotlStore();
  }

  public AbelianAxolotlStore getStore() {
    return axolotlStore;
  }

  public byte[] getHandShakeMessage(int userId) {
    AxolotlAddress theirAddress = new AxolotlAddress(String.valueOf(userId), 0);
    SessionBuilder sessionBuilder = new SessionBuilder(axolotlStore, theirAddress);
    KeyExchangeMessage keyExchangeMessage      = sessionBuilder.process();

    return keyExchangeMessage.serialize();
  }

  public byte[] getHandShakeMessage(int userId, byte[] request)  {
    AxolotlAddress theirAddress = new AxolotlAddress(String.valueOf(userId), 0);
    SessionBuilder sessionBuilder = new SessionBuilder(axolotlStore, theirAddress);
    try {
      KeyExchangeMessage keyExchangeMessage = sessionBuilder.process(
          new KeyExchangeMessage(request));
      if (keyExchangeMessage != null) {
        return keyExchangeMessage.serialize();
      }
      return null;
    } catch (InvalidKeyException|LegacyMessageException|InvalidMessageException|
        UntrustedIdentityException|StaleKeyExchangeException| InvalidVersionException e){
      Log.e(TAG, "Can't handshake", e);
      return null;
    }
  }

  public byte[] encryptMessage(int userId, byte[] message) {
    List<Integer> deviceIds = axolotlStore.getSubDeviceSessions(String.valueOf(userId));
    for (Integer deviceId : deviceIds) {
      try {
        AxolotlAddress theirAddress = new AxolotlAddress(String.valueOf(userId), deviceId);
        SessionCipher aliceSessionCipher = new SessionCipher(axolotlStore, theirAddress);
        CiphertextMessage ciphertextMessage = aliceSessionCipher.encrypt(message);
        return ciphertextMessage.serialize();
      } catch (Exception e) {
        Log.e(TAG, "Cannot encrypt for " + userId, e);
      }
    }
    Log.e(TAG, "Cannot encrypt for " + userId + ", returning clear text msg" +
        Utilities.base64EncodeBytes(message));
    return message;
  }

  public byte[] decryptMessage(int userId, byte[] ciphertext) {
    try {
      AxolotlAddress theirAddress = new AxolotlAddress(String.valueOf(userId), 0);
      SessionCipher sessionCipher = new SessionCipher(axolotlStore, theirAddress);
      return sessionCipher.decrypt(new WhisperMessage(ciphertext));
    } catch (Exception e) {
      Log.e(TAG, "Cannot decrypt for " + userId, e);
    }
    Log.e(TAG, "Cannot decrypt for " + userId);
    return ciphertext;
  }
}
