package org.whispersystems.libaxolotl.state.impl;

import org.whispersystems.libaxolotl.AxolotlAddress;
import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.state.IdentityKeyStore;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;
import org.whispersystems.libaxolotl.util.KeyHelper;

import java.util.HashMap;
import java.util.Map;

public class InMemoryIdentityKeyStore implements IdentityKeyStore {

  private final Map<AxolotlAddress, IdentityKey> trustedKeys = new HashMap<>();

  private IdentityKeyPair identityKeyPair;
  private SignedPreKeyRecord signedPreKeyRecord;
  private final int localDeviceId;

  public InMemoryIdentityKeyStore() {
    this.identityKeyPair = generateIdentityKeyPair();
    this.signedPreKeyRecord = generateSignedPreKeyRecord(identityKeyPair);
    this.localDeviceId = generateDeviceId();
  }

  public InMemoryIdentityKeyStore(IdentityKeyPair identityKeyPair,
                                  SignedPreKeyRecord signedPreKeyRecord, int localDeviceId) {
    this.identityKeyPair     = identityKeyPair;
    this.signedPreKeyRecord = signedPreKeyRecord;
    this.localDeviceId       = localDeviceId;
  }

  @Override
  public IdentityKeyPair getIdentityKeyPair() {
    return identityKeyPair;
  }

  @Override
  public SignedPreKeyRecord getSignedPreKeyRecord() { return signedPreKeyRecord; }

  @Override
  public int getLocalDeviceId() {
    return localDeviceId;
  }

  @Override
  public void saveIdentity(AxolotlAddress address, IdentityKey identityKey) {
    trustedKeys.put(address, identityKey);
  }

  @Override
  public boolean isTrustedIdentity(AxolotlAddress address, IdentityKey identityKey) {
    IdentityKey trusted = trustedKeys.get(address);
    return (trusted == null || trusted.equals(identityKey));
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

  private static int generateDeviceId() {
    return KeyHelper.generateDeviceId();
  }

}
