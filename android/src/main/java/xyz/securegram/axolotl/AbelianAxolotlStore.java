package xyz.securegram.axolotl;

import android.util.Log;

import org.whispersystems.libaxolotl.AxolotlAddress;
import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.state.AxolotlStore;
import org.whispersystems.libaxolotl.state.SessionRecord;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;

import java.util.List;

public class AbelianAxolotlStore implements AxolotlStore {
  private static final String TAG = AbelianAxolotlStore.class.getName();

  private final AbelianIdentityKeyStore identityKeyStore      = new AbelianIdentityKeyStore();
  private final AbelianSessionStore sessionStore = new AbelianSessionStore();

  public AbelianAxolotlStore() {
  }

  @Override
  public IdentityKeyPair getIdentityKeyPair() {
    return identityKeyStore.getIdentityKeyPair();
  }

  @Override
  public SignedPreKeyRecord getSignedPreKeyRecord() {
    return identityKeyStore.getSignedPreKeyRecord();
  }

  @Override
  public int getLocalDeviceId() {
    return identityKeyStore.getLocalDeviceId();
  }

  @Override
  public void saveIdentity(AxolotlAddress name, IdentityKey identityKey) {
    identityKeyStore.saveIdentity(name, identityKey);
  }

  @Override
  public boolean isTrustedIdentity(AxolotlAddress name, IdentityKey identityKey) {
    return identityKeyStore.isTrustedIdentity(name, identityKey);
  }

  @Override
  public SessionRecord loadSession(AxolotlAddress address) {
    return sessionStore.loadSession(address);
  }

  @Override
  public List<Integer> getSubDeviceSessions(String name) {
    return sessionStore.getSubDeviceSessions(name);
  }

  @Override
  public void storeSession(AxolotlAddress address, SessionRecord record) {
    sessionStore.storeSession(address, record);
  }

  @Override
  public boolean containsSession(AxolotlAddress address) {
    return sessionStore.containsSession(address);
  }

  @Override
  public void deleteSession(AxolotlAddress address) {
    sessionStore.deleteSession(address);
  }

  @Override
  public void deleteAllSessions(String name) {
    sessionStore.deleteAllSessions(name);
  }

  public void deleteIdentity(String name) {
    identityKeyStore.deleteIdentity(name);
  }
}
