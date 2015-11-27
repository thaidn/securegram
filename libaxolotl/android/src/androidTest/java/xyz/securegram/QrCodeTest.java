package xyz.securegram;

import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import com.google.protobuf.ByteString;

import junit.framework.TestCase;

import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;
import org.whispersystems.libaxolotl.util.KeyHelper;

import java.util.Arrays;

import xyz.securegram.axolotl.AbelianProtos;

public class QRCodeTest extends TestCase {

  public void testSimpleEncodeDecode() {
    final String CONTENT = "blah";

    for (int i = 0; i < 100; i++) {
      try {
        Bitmap bitmap = QRCode.encodeAsBitmap(CONTENT, 640);
        String decoded = QRCode.decodeAsString(bitmap);
        assertEquals(CONTENT, decoded);
      } catch (Exception e) {
        fail();
      }
    }
  }

  public void testEncodeAbelianIdentity() {
    for (int i = 0; i < 100; i++) {
      try {
        IdentityKeyPair identityKeyPair =  KeyHelper.generateIdentityKeyPair();
        int deviceId = KeyHelper.generateDeviceId();
        SignedPreKeyRecord signedPreKeyRecord = KeyHelper.generateSignedPreKey(identityKeyPair);

        AbelianProtos.AbelianIdentity myAbelianIdentity = AbelianProtos.AbelianIdentity.newBuilder()
            .setDeviceId(deviceId)
            .setIdentityKey(
                ByteString.copyFrom(identityKeyPair.getPublicKey().serialize()))
            .setSignedPreKey(
                ByteString.copyFrom(
                    signedPreKeyRecord.getKeyPair().getPublicKey().serialize()))
            .setSignedPreKeySignature(
                ByteString.copyFrom(signedPreKeyRecord.getSignature()))
            .build();

        String encoded = myAbelianIdentity.toByteString().toString();

        Bitmap bitmap = QRCode.encodeAsBitmap(encoded, 640);
        String decoded = QRCode.decodeAsString(bitmap);
        assertNotNull(decoded);
        assertEquals(encoded, decoded);
      } catch (Exception e) {
        throw new AssertionError(e);
      }
    }
  }

  private byte[] writeRawIdentity(int deviceId, IdentityKeyPair identityKeyPair,
                                  SignedPreKeyRecord signedPreKeyRecord) {
    byte[] result = new byte[130];
    result[0] = (byte) (deviceId & 0xff00);
    result[1] = (byte) (deviceId & 0x00ff);
    System.arraycopy(identityKeyPair.getPublicKey().serialize(), 0, result, 2, 32);
    System.arraycopy(signedPreKeyRecord.getKeyPair().getPublicKey().serialize(), 0, result, 34, 32);
    System.arraycopy(signedPreKeyRecord.getSignature(), 0, result, 66, 64);
    return result;
  }

  private AbelianProtos.AbelianIdentity readRawIdentity(byte[] input) {
    if (input.length != 130) {
      return null;
    }

    int deviceId = input[1] | input[0];

    byte[] identityPubKey = new byte[32];
    System.arraycopy(input, 2, identityPubKey, 0, 32);

    byte[] signedPreKey = new byte[32];
    System.arraycopy(input, 34, signedPreKey, 0, 32);

    byte[] signPreKeySignature = new byte[64];
    System.arraycopy(input, 66, signPreKeySignature, 0, 64);

    AbelianProtos.AbelianIdentity abelianIdentity = AbelianProtos.AbelianIdentity.newBuilder()
        .setDeviceId(deviceId)
        .setIdentityKey(ByteString.copyFrom(identityPubKey))
        .setSignedPreKey(ByteString.copyFrom(signedPreKey))
        .setSignedPreKeySignature(ByteString.copyFrom(signPreKeySignature))
        .build();
    return abelianIdentity;

  }

  public void testEncodeRawIdentity() {
    for (int i = 0; i < 100; i++) {
      try {
        IdentityKeyPair identityKeyPair =  KeyHelper.generateIdentityKeyPair();
        SignedPreKeyRecord signedPreKeyRecord = KeyHelper.generateSignedPreKey(identityKeyPair);
        int deviceId = KeyHelper.generateDeviceId(false);

        String encoded = Base64.encodeToString(
            writeRawIdentity(deviceId, identityKeyPair, signedPreKeyRecord),
            android.util.Base64.NO_PADDING | Base64.URL_SAFE | Base64.NO_CLOSE);

        Bitmap bitmap = QRCode.encodeAsBitmap(encoded, 640);
        String decoded = QRCode.decodeAsString(bitmap);
        assertNotNull(decoded);
        assertEquals(encoded, decoded);

        AbelianProtos.AbelianIdentity identity = readRawIdentity(
            Base64.decode(decoded,
                android.util.Base64.NO_PADDING | Base64.URL_SAFE | Base64.NO_CLOSE));
        assertNotNull(identity);
        assertEquals(deviceId, identity.getDeviceId());
        assertTrue(Arrays.equals(identityKeyPair.getPublicKey().serialize(),
            identity.getIdentityKey().toByteArray()));
        assertTrue(Arrays.equals(signedPreKeyRecord.getKeyPair().getPublicKey().serialize(),
            identity.getSignedPreKey().toByteArray()));
        assertTrue(Arrays.equals(signedPreKeyRecord.getSignature(),
            identity.getSignedPreKeySignature().toByteArray()));
      } catch (Exception e) {
        throw new AssertionError(e);
      }
    }
  }
}
