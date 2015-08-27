package org.whispersystems.libaxolotl.state.impl;

import org.whispersystems.libaxolotl.AxolotlAddress;
import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.state.IdentityKeyStore;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;

import java.util.HashMap;
import java.util.Map;

public class InMemoryIdentityKeyStore implements IdentityKeyStore {

  private final Map<AxolotlAddress, IdentityKey> trustedKeys = new HashMap<>();

  private IdentityKeyPair identityKeyPair;
  private SignedPreKeyRecord signedPreKeyRecord;
  private final int localDeviceId;

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
  public void saveIdentityKeyPair(IdentityKeyPair keypair) {
    this.identityKeyPair = keypair;
  }

  @Override
  public SignedPreKeyRecord getSignedPreKeyRecord() { return signedPreKeyRecord; }

  @Override
  public void saveSignedPreKeyRecord(SignedPreKeyRecord keyRecord) {
    this.signedPreKeyRecord = keyRecord;
  }

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
}
