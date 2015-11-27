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

import org.whispersystems.libaxolotl.SessionCipher;
import org.whispersystems.libaxolotl.state.PreKeyBundle;
import org.whispersystems.libaxolotl.util.KeyHelper;

import java.io.File;
import java.util.List;

import xyz.securegram.axolotl.AbelianProtos.AbelianEnvelope;
import xyz.securegram.axolotl.AbelianProtos.AbelianIdentity;
import xyz.securegram.QRCode;

public class AxolotlController {
  private static final String TAG = AxolotlController.class.getName();
  private static volatile AxolotlController Instance = null;

  private AbelianIdentity myAbelianIdentity;
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

  public void registerAxolotlIdentity() {
    while (!UserConfig.registeredForAbelian) {
      try {
        UserConfig.identityKeyPair =  KeyHelper.generateIdentityKeyPair();
        UserConfig.deviceId = KeyHelper.generateDeviceId();
        UserConfig.signedPreKeyRecord = KeyHelper.generateSignedPreKey(UserConfig.identityKeyPair);

        myAbelianIdentity = AbelianIdentity.newBuilder()
            .setDeviceId(UserConfig.deviceId)
            .setIdentityKey(
                ByteString.copyFrom(UserConfig.identityKeyPair.getPublicKey().serialize()))
            .setSignedPreKey(
                ByteString.copyFrom(
                    UserConfig.signedPreKeyRecord.getKeyPair().getPublicKey().serialize()))
            .setSignedPreKeySignature(
                ByteString.copyFrom(UserConfig.signedPreKeyRecord.getSignature()))
            .build();

        String encoded = Utilities.base64EncodeBytes(myAbelianIdentity.toByteArray());
        Bitmap bitmap = QRCode.encodeAsBitmap(encoded, 640);
        String decoded = QRCode.decodeAsString(bitmap);
        if (decoded == null || !decoded.equals(encoded)) {
          // bad luck, try again.
          continue;
        }
        TLRPC.PhotoSize bigPhoto = ImageLoader.scaleAndSaveImage(
            QRCode.encodeAsBitmap(encoded, 640),
            640 /* maxWidth */, 640 /* maxHeight */, 100 /* quality */,
            false /* cached */, 320 /* minWidth */, 320 /* minHeight */);
        // TODO(thaidn): do not overwrite existing identities.
        MessagesController.getInstance().uploadAndApplyUserAvatar(bigPhoto);
        UserConfig.registeredForAbelian = true;
        UserConfig.saveAbelianIdentity();
      } catch (Exception e) {
        Log.e(TAG, "Cannot register Abelian Identity", e);
      }
    }
  }

  public String loadAbelianAvatar(TLRPC.User user) {
    MessagesController.getInstance()
        .loadFullUser(user, ConnectionsManager.getInstance().generateClassGuid());
    if (user.photo != null) {
      String avatarPath = getPhotoPath(user);
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
    try {
      if (user.photo != null) {
        String avatarPath = getPhotoPath(user);
        String decodedIdentity = QRCode.decodeAsString(avatarPath);
        if (decodedIdentity == null) {
          return false;
        }
        AbelianIdentity identity = AbelianIdentity.parseFrom(
            Utilities.base64DecodeBytes(decodedIdentity.getBytes("UTF-8")));
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
    List<Integer> deviceIds = axolotlStore.getSubDeviceSessions(String.valueOf(userId));
    for (Integer deviceId : deviceIds) {
      // TODO(thaidn): send to multiple devices.
      try {
        AxolotlAddress theirAddress = new AxolotlAddress(String.valueOf(userId), deviceId);
        SessionCipher aliceSessionCipher = new SessionCipher(axolotlStore, theirAddress);

        CiphertextMessage ciphertextMessage = aliceSessionCipher.encrypt(message.getBytes("UTF-8"));
        AbelianEnvelope.Builder builder = AbelianEnvelope.newBuilder()
            .setContent(ByteString.copyFrom(ciphertextMessage.serialize()))
            .setSource(String.valueOf(UserConfig.getCurrentUser().id))
            .setSourceDevice(UserConfig.deviceId);

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
    try {
      AbelianEnvelope envelope = AbelianEnvelope.parseFrom(
          Utilities.base64DecodeBytes(ciphertext.getBytes("UTF-8")));
      AxolotlAddress theirAddress = new AxolotlAddress(envelope.getSource(),
          envelope.getSourceDevice());

      SessionCipher sessionCipher = new SessionCipher(axolotlStore, theirAddress);
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

    return "Can't decrypt: " + ciphertext;
  }

  private String getPhotoPath(TLRPC.User user) {
    return FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE)
        + "/"
        + user.photo.photo_big.volume_id
        + "_"
        + user.photo.photo_big.local_id
        + ".jpg";
  }
}
