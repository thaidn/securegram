package xyz.securegram.axolotl;

import android.graphics.Bitmap;
import android.util.Log;

import com.google.protobuf.ByteString;

import org.telegram.android.ImageLoader;
import org.telegram.android.MessagesController;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;

import org.whispersystems.libaxolotl.AxolotlAddress;
import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.SessionBuilder;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.protocol.CiphertextMessage;
import org.whispersystems.libaxolotl.protocol.PreKeyWhisperMessage;
import org.whispersystems.libaxolotl.protocol.WhisperMessage;

import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.SessionCipher;
import org.whispersystems.libaxolotl.state.AxolotlStore;
import org.whispersystems.libaxolotl.state.PreKeyBundle;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;

import java.io.File;
import java.util.HashMap;

import xyz.securegram.QrCode;
import xyz.securegram.axolotl.AbelianProtos.AbelianEnvelope;
import xyz.securegram.axolotl.AbelianProtos.AbelianIdentity;

public class AxolotlController {
  private static final String TAG = AxolotlController.class.getName();
  private static volatile AxolotlController Instance = null;
  private HashMap<Integer, AbelianIdentity> ABELIAN_IDENTITIES;

  private AbelianIdentity myAbelianIdentity;
  private AxolotlStore axolotlStore;

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
    ABELIAN_IDENTITIES = new HashMap<>();

    axolotlStore = new AbelianAxolotlStore();
    IdentityKeyPair identityKeyPair = UserConfig.identityKeyPair;
    int deviceId = UserConfig.deviceId;
    SignedPreKeyRecord signedPreKey = UserConfig.signedPreKeyRecord;

    myAbelianIdentity = AbelianIdentity.newBuilder()
        .setDeviceId(deviceId)
        .setIdentityKey(ByteString.copyFrom(identityKeyPair.getPublicKey().serialize()))
        .setSignedPreKey(
            ByteString.copyFrom(signedPreKey.getKeyPair().getPublicKey().serialize()))
        .setSignedPreKeySignature(ByteString.copyFrom(signedPreKey.getSignature()))
        .build();
  }

  public AxolotlStore getStore() {
    return axolotlStore;
  }

  public void registerAxolotlIdentity() {
    if (!UserConfig.registeredForAbelian) {
      try {
        Bitmap bitmap = QrCode.encodeAsBitmap(
            Utilities.base64EncodeBytes(myAbelianIdentity.toByteArray()), 640);
        TLRPC.PhotoSize bigPhoto = ImageLoader.scaleAndSaveImage(
            bitmap, 640 /* maxWidth */, 640 /* maxHeight */, 100 /* quality */,
            false /* cached */, 320 /* minWidth */, 320 /* minHeight */);

        // TODO(thaidn): do not overwrite existing identities.
        MessagesController.getInstance().uploadAndApplyUserAvatar(bigPhoto);
        UserConfig.registeredForAbelian = true;
        UserConfig.saveIdentity();
      } catch (Exception e) {
        Log.e(TAG, "Cannot register", e);
      }
    }
  }

  public String loadAbelianAvatar(TLRPC.User user) {
    MessagesController.getInstance()
        .loadFullUser(MessagesController.getInstance().getUser(user.id),
            ConnectionsManager.getInstance().generateClassGuid());
    if (user.photo != null) {
      String avatarPath =
          FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE)
              + "/"
              + user.photo.photo_big.volume_id
              + "_"
              + user.photo.photo_big.local_id
              + ".jpg";
      File avatarFile = new File(avatarPath);
      if (!avatarFile.exists()) {
        FileLoader.getInstance().loadFile(user.photo.photo_big,
            "jpg", 0 /* size */, true /* cacheOnly */);
      }
      return avatarPath;
    }
    return null;
  }

  public boolean loadAbelianIdentity(TLRPC.User user) {
    if (ABELIAN_IDENTITIES.containsKey(user.id)) {
      return true;
    }

    try {
      if (user.photo != null) {
        String avatarPath =
            FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE)
                + "/"
                + user.photo.photo_big.volume_id
                + "_"
                + user.photo.photo_big.local_id
                + ".jpg";
        String decodedIdentity = QrCode.decodeAsString(avatarPath);
        if (decodedIdentity == null) {
          return false;
        }
        AbelianIdentity identity = AbelianIdentity.parseFrom(
            Utilities.base64DecodeBytes(decodedIdentity.getBytes("UTF-8")));
        ABELIAN_IDENTITIES.put(user.id, identity);

        AxolotlAddress theirAddress = new AxolotlAddress(String.valueOf(user.id),
            identity.getDeviceId());
        if (!axolotlStore.containsSession(theirAddress)) {
          SessionBuilder sessionBuilder = new SessionBuilder(axolotlStore, theirAddress);
          PreKeyBundle theirPreKey = new PreKeyBundle(
              identity.getDeviceId(),
              Curve.decodePoint(identity.getSignedPreKey().toByteArray(), 0),
              identity.getSignedPreKeySignature().toByteArray(),
              new IdentityKey(Curve.decodePoint(identity.getIdentityKey().toByteArray(), 0)));
          sessionBuilder.process(theirPreKey);
          // identity and session would be saved to database, but maybe not immediately.
          return false;
        }
        return true;
      }
    } catch (Exception e) {
      Log.e(TAG, "Can't load Abelian identity for user " + user.id, e);
    }
    return false;
  }

  public String encryptMessage(String message, int userId) {
    if (ABELIAN_IDENTITIES.containsKey(userId)) {
      try {
        AbelianIdentity theirIdentity = ABELIAN_IDENTITIES.get(userId);
        AxolotlAddress theirAddress = new AxolotlAddress(String.valueOf(userId),
            theirIdentity.getDeviceId());
        // TODO(thaidn): send to multiple devices.
        SessionCipher aliceSessionCipher = new SessionCipher(axolotlStore, theirAddress);
        CiphertextMessage ciphertextMessage = aliceSessionCipher.encrypt(message.getBytes("UTF-8"));
        AbelianEnvelope.Builder builder = AbelianEnvelope.newBuilder().setContent(
            ByteString.copyFrom(ciphertextMessage.serialize()));
        if (ciphertextMessage.getType() == CiphertextMessage.PREKEY_TYPE) {
          builder.setType(AbelianEnvelope.Type.PREKEY_BUNDLE);
        } else {
          builder.setType(AbelianEnvelope.Type.CIPHERTEXT);
        }
        return Utilities.base64EncodeBytes(builder.build().toByteArray());
      } catch (Exception e) {
        Log.e(TAG, "Cannot encrypt for " + userId, e);
      }
    }

    return "Not encrypted: " + message;
  }

  public String decryptMessage(String ciphertext, int userId) {
    if (ABELIAN_IDENTITIES.containsKey(userId)) {
      try {
        AbelianIdentity theirIdentity = ABELIAN_IDENTITIES.get(userId);
        AxolotlAddress theirAddress = new AxolotlAddress(String.valueOf(userId),
            theirIdentity.getDeviceId());
        SessionCipher sessionCipher = new SessionCipher(axolotlStore, theirAddress);
        AbelianEnvelope envelope = AbelianEnvelope.parseFrom(
            Utilities.base64DecodeBytes(ciphertext.getBytes("UTF-8")));
        String result;
        if (envelope.getType() == AbelianEnvelope.Type.CIPHERTEXT) {
          result = new String(sessionCipher.decrypt(new WhisperMessage(
              envelope.getContent().toByteArray())));
        } else {
          result = new String(sessionCipher.decrypt(new PreKeyWhisperMessage(
              envelope.getContent().toByteArray())));
        }
        return result;
      } catch (Exception e) {
        Log.e(TAG, "Cannot decrypt for " + userId, e);
      }
    }

    return "Can't decrypt: " + ciphertext;
  }
}
