package xyz.securegram.axolotl;

import android.graphics.Bitmap;
import android.util.Log;

import com.google.protobuf.ByteString;

import org.telegram.android.ImageLoader;
import org.telegram.android.MessagesController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.TLRPC;
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
import org.whispersystems.libaxolotl.state.impl.InMemoryAxolotlStore;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;
import org.whispersystems.libaxolotl.util.KeyHelper;

import xyz.securegram.axolotl.AbelianProtos.AbelianEnvelope;
import xyz.securegram.axolotl.AbelianProtos.AbelianIdentity;
import xyz.securegram.QrCode;

public class AxolotlController {
  private static final String TAG = AxolotlController.class.getName();
  private static volatile AxolotlController Instance = null;

  private static final int DEVICE_ID = 0;
  private static final int PRE_KEY_ID = 1;
  private static final int SIGNED_PRE_KEY_ID = 2;

  private AbelianIdentity abelianIdentity;
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
    try {
      IdentityKeyPair identityKeyPair = KeyHelper.generateIdentityKeyPair();
      int registrationId = KeyHelper.generateRegistrationId(false /* extendedRange */);
      axolotlStore = new InMemoryAxolotlStore(identityKeyPair, registrationId);
      SignedPreKeyRecord signedPreKey = KeyHelper.generateSignedPreKey(
          identityKeyPair, SIGNED_PRE_KEY_ID);
      axolotlStore.storeSignedPreKey(SIGNED_PRE_KEY_ID, signedPreKey);

      abelianIdentity = AbelianIdentity.newBuilder()
          .setRegistrationId(registrationId)
          .setIdentityKey(ByteString.copyFrom(identityKeyPair.getPublicKey().serialize()))
          .setSignedPreKey(
              ByteString.copyFrom(signedPreKey.getKeyPair().getPublicKey().serialize()))
          .setSignPreKeySiganture(ByteString.copyFrom(signedPreKey.getSignature()))
          .build();

    } catch (Exception e) {
      Log.e(TAG, e.toString());
    }
  }

  public AbelianIdentity getLocalAbelianIdentity() {
    return abelianIdentity;
  }

  public void registerLocalAxolotlIdentity() {
    try {
      Bitmap bitmap = QrCode.encodeAsBitmap(
          Utilities.base64EncodeToString(abelianIdentity.toByteArray()), 640);
      TLRPC.PhotoSize bigPhoto = ImageLoader.scaleAndSaveImage(
          bitmap, 640 /* maxWidth */, 640 /* maxHeight */, 100 /* quality */,
          false /* cached */, 320 /* minWidth */, 320 /* minHeight */);
      MessagesController.getInstance().uploadAndApplyUserAvatar(bigPhoto);
    } catch (Exception e) {
      Log.e(TAG, e.toString());
    }
  }

  public AbelianIdentity getRemoteAbelianIdentity(int userId) {
    TLRPC.User user = MessagesController.getInstance().getUser(userId);
    try {
      String avatar =
          FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE)
              + "/"
              + user.photo.photo_big.volume_id
              + "_"
              + user.photo.photo_big.local_id
              + ".jpg";
      String identity = QrCode.decodeAsString(avatar);
      return AbelianIdentity.parseFrom(Utilities.base64Decode(identity.getBytes("UTF-8")));
    } catch (Exception e) {
      Log.e(TAG, e.toString());
      return null;
    }
  }

  public String encryptMessage(String message, int userId) {
    try {
      AbelianIdentity theirIdentity = getRemoteAbelianIdentity(userId);
      AxolotlAddress theirAddress = new AxolotlAddress(String.valueOf(userId),
          theirIdentity.getRegistrationId());
      if (!axolotlStore.containsSession(theirAddress)) {
        SessionBuilder sessionBuilder = new SessionBuilder(axolotlStore, theirAddress);
        PreKeyBundle theirPreKey = new PreKeyBundle(
            theirIdentity.getRegistrationId(),
            DEVICE_ID,
            PRE_KEY_ID, null,
            SIGNED_PRE_KEY_ID, Curve.decodePoint(theirIdentity.getSignedPreKey().toByteArray(), 0),
            theirIdentity.getSignPreKeySiganture().toByteArray(),
            new IdentityKey(Curve.decodePoint(theirIdentity.getIdentityKey().toByteArray(), 0)));
        sessionBuilder.process(theirPreKey);
      }

      SessionCipher aliceSessionCipher = new SessionCipher(axolotlStore, theirAddress);
      CiphertextMessage ciphertextMessage = aliceSessionCipher.encrypt(message.getBytes("UTF-8"));
      AbelianEnvelope.Builder builder = AbelianEnvelope.newBuilder().setContent(ByteString.copyFrom(
          ciphertextMessage.serialize()));
      if (ciphertextMessage.getType() == CiphertextMessage.PREKEY_TYPE) {
        builder.setType(AbelianEnvelope.Type.PREKEY_BUNDLE);
      } else {
        builder.setType(AbelianEnvelope.Type.CIPHERTEXT);
      }
      return Utilities.base64EncodeToString(builder.build().toByteArray());
    } catch (Exception e) {
      Log.e(TAG, e.toString());
    }
    return "Not encrypted: " + message;
  }

  public String decryptMessage(String ciphertext, int userId) {
    AxolotlAddress myAddress = new AxolotlAddress(String.valueOf(userId),
        axolotlStore.getLocalRegistrationId());

    SessionCipher sessionCipher = new SessionCipher(axolotlStore, myAddress);
    try {
      AbelianEnvelope envelope = AbelianEnvelope.parseFrom(
          Utilities.base64Decode(ciphertext.getBytes("UTF-8")));
      String result;
      if (envelope.getType() == AbelianEnvelope.Type.CIPHERTEXT) {
        result = new String(sessionCipher.decrypt(new WhisperMessage(
            envelope.getContent().toByteArray())));
      } else {
        result = new String(sessionCipher.decrypt(new PreKeyWhisperMessage(
            envelope.getContent().toByteArray())));
      }
      Log.i(TAG, "Decrypted message: " + result);
      return result;
    } catch (Exception e) {
      Log.e(TAG, e.toString());
    }
    return "Can't decrypt: " + ciphertext;
  }
}
