package org.whispersystems.libaxolotl;

import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;
import org.whispersystems.libaxolotl.util.KeyHelper;

public class TestInMemoryAxolotlStore extends org.whispersystems.libaxolotl.state.impl.InMemoryAxolotlStore {
  public TestInMemoryAxolotlStore() {
    super(null, null, generateRegistrationId());
    IdentityKeyPair keyPair = generateIdentityKeyPair();
    SignedPreKeyRecord signedPreKeyRecord = generateSignedPreKeyRecord(keyPair);
    this.saveIdentityKeyPair(keyPair);
    this.saveSignedPreKeyRecord(signedPreKeyRecord);
  }

  private static IdentityKeyPair generateIdentityKeyPair() {
    ECKeyPair identityKeyPairKeys = Curve.generateKeyPair();

    return new IdentityKeyPair(new IdentityKey(identityKeyPairKeys.getPublicKey()),
                                               identityKeyPairKeys.getPrivateKey());
  }

  private static SignedPreKeyRecord generateSignedPreKeyRecord(IdentityKeyPair keyPair) {
    try {
      ECKeyPair    bobSignedPreKeyPair      = Curve.generateKeyPair();
      byte[]       bobSignedPreKeySignature = Curve.calculateSignature(keyPair.getPrivateKey(),
          bobSignedPreKeyPair.getPublicKey().serialize());
      return new SignedPreKeyRecord(bobSignedPreKeyPair, bobSignedPreKeySignature);
    } catch (Exception e) {
      return null;
    }

  }

  private static int generateRegistrationId() {
    return KeyHelper.generateDeviceId();
  }
}
