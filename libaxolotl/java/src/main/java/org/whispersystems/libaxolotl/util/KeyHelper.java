package org.whispersystems.libaxolotl.util;

import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Helper class for generating keys of different types.
 *
 * @author Moxie Marlinspike
 */
public class KeyHelper {

  private KeyHelper() {}

  /**
   * Generate an identity key pair.  Clients should only do this once,
   * at install time.
   *
   * @return the generated IdentityKeyPair.
   */
  public static IdentityKeyPair generateIdentityKeyPair() {
    ECKeyPair   keyPair   = Curve.generateKeyPair();
    IdentityKey publicKey = new IdentityKey(keyPair.getPublicKey());
    return new IdentityKeyPair(publicKey, keyPair.getPrivateKey());
  }

  /**
   * Generate a device ID.  Clients should only do this once,
   * at install time.
   *
   * @return the generated device ID.
   */
  public static int generateDeviceId() {
    try {
      SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
      return secureRandom.nextInt(Integer.MAX_VALUE - 1) + 1;
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  public static int getRandomSequence(int max) {
    try {
      return SecureRandom.getInstance("SHA1PRNG").nextInt(max);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  /**
   * Generate a signed PreKey
   *
   * @param identityKeyPair The local client's identity key pair.
   *
   * @return the generated signed PreKey
   * @throws InvalidKeyException when the provided identity key is invalid
   */
  public static SignedPreKeyRecord generateSignedPreKey(IdentityKeyPair identityKeyPair)
      throws InvalidKeyException
  {
    ECKeyPair keyPair   = Curve.generateKeyPair();
    byte[]    signature = Curve.calculateSignature(identityKeyPair.getPrivateKey(), keyPair.getPublicKey().serialize());

    return new SignedPreKeyRecord(keyPair, signature);
  }


  public static ECKeyPair generateSenderSigningKey() {
    return Curve.generateKeyPair();
  }

  public static byte[] generateSenderKey() {
    try {
      byte[] key = new byte[32];
      SecureRandom.getInstance("SHA1PRNG").nextBytes(key);

      return key;
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  public static int generateSenderKeyId() {
    try {
      return SecureRandom.getInstance("SHA1PRNG").nextInt(Integer.MAX_VALUE);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

}
