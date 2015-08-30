package xyz.securegram.axolotl;

import android.graphics.Bitmap;
import android.util.Log;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

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
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import xyz.securegram.QrCode;
import xyz.securegram.axolotl.AbelianProtos.AbelianEnvelope;
import xyz.securegram.axolotl.AbelianProtos.AbelianIdentity;
import xyz.securegram.axolotl.AbelianProtos.AbelianIdentityStructure;

public class AxolotlController {
  private static final String TAG = AxolotlController.class.getName();
  private static volatile AxolotlController Instance = null;
  private HashMap<String, List<AbelianIdentity>> theirAbelianIdentities;

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
    theirAbelianIdentities = new HashMap<>();

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

  public void registerAxolotlIdentity() {
    if (!UserConfig.registeredForAbelian) {
      try {
        AbelianIdentityStructure structure = AbelianIdentityStructure.newBuilder()
            .addIdentities(myAbelianIdentity)
            .build();
        Bitmap bitmap = QrCode.encodeAsBitmap(
            Utilities.base64EncodeBytes(structure.toByteArray()), 640);
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

  public void loadAbelianAvatar(TLRPC.User user) {
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
    }
  }

  public List<AbelianIdentity> getAbelianIdentities(TLRPC.User user)
      throws UnsupportedEncodingException, InvalidProtocolBufferException {
    if (user.photo != null) {
      String avatarPath =
          FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE)
              + "/"
              + user.photo.photo_big.volume_id
              + "_"
              + user.photo.photo_big.local_id
              + ".jpg";
      if (theirAbelianIdentities.containsKey(avatarPath)) {
        return theirAbelianIdentities.get(avatarPath);
      }

      File avatarFile = new File(avatarPath);
      if (!avatarFile.exists()) {
        loadAbelianAvatar(user);
      }

      String identity = QrCode.decodeAsString(avatarPath);
      List<AbelianIdentity> identities = AbelianIdentityStructure.parseFrom(
          Utilities.base64DecodeBytes(identity.getBytes("UTF-8"))).getIdentitiesList();
      theirAbelianIdentities.put(avatarPath, identities);
      return identities;
    }
    return new ArrayList<AbelianIdentity>();
  }

  public String encryptMessage(String message, int userId) {
    try {
      List<AbelianIdentity> theirIdentities = getAbelianIdentities(
          MessagesController.getInstance().getUser(userId));
      // TODO(thaidn): send to multiple devices.
      // TODO(thaidn): handle UI when a new device is added for an existing user.
      for (AbelianIdentity theirIdentity : theirIdentities) {
        AxolotlAddress theirAddress = new AxolotlAddress(String.valueOf(userId),
            theirIdentity.getDeviceId());
        if (!axolotlStore.containsSession(theirAddress)) {
          SessionBuilder sessionBuilder = new SessionBuilder(axolotlStore, theirAddress);
          PreKeyBundle theirPreKey = new PreKeyBundle(
              theirIdentity.getDeviceId(),
              Curve.decodePoint(theirIdentity.getSignedPreKey().toByteArray(), 0),
              theirIdentity.getSignedPreKeySignature().toByteArray(),
              new IdentityKey(Curve.decodePoint(theirIdentity.getIdentityKey().toByteArray(), 0)));
          sessionBuilder.process(theirPreKey);
        }

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
      }
    } catch (Exception e) {
      Log.e(TAG, "Cannot encrypt for " + userId, e);
    }
    return "Not encrypted: " + message;
  }

  public String decryptMessage(String ciphertext, int userId) {
    try {
      List<AbelianIdentity> theirIdentities = getAbelianIdentities(
          MessagesController.getInstance().getUser(userId));
      for (AbelianIdentity theirIdentity : theirIdentities) {
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
      }
    } catch (Exception e) {
      Log.e(TAG, "Cannot decrypt for " + userId, e);
    }
    return "Can't decrypt: " + ciphertext;
  }
}
