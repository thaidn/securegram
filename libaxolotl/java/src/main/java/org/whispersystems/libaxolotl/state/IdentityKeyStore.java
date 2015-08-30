package org.whispersystems.libaxolotl.state;

import org.whispersystems.libaxolotl.AxolotlAddress;
import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.IdentityKeyPair;

/**
 * Provides an interface to identity information.
 *
 * @author Moxie Marlinspike
 */
public interface IdentityKeyStore {

  /**
   * Get the local client's identity key pair.
   *
   * @return The local client's persistent identity key pair.
   */
  public IdentityKeyPair getIdentityKeyPair();

  /**
   * Get the local client's signed pre key record.
   *
   * @return The local client's persistent signed pre key record.
   */
  public SignedPreKeyRecord getSignedPreKeyRecord();

  /**
   * Return the local client's device ID.
   *
   * @return the local client's device ID.
   */
  public int getLocalDeviceId();

  /**
   * Save a remote client's identity key
   * <p>
   * Store a remote client's identity key as trusted.
   *
   * @param address        The address of the remote client.
   * @param identityKey The remote client's identity key.
   */
  public void            saveIdentity(AxolotlAddress address, IdentityKey identityKey);


  /**
   * Verify a remote client's identity key.
   * <p>
   * Determine whether a remote client's identity is trusted.  Convention is
   * that the TextSecure protocol is 'trust on first use.'  This means that
   * an identity key is considered 'trusted' if there is no entry for the recipient
   * in the local store, or if it matches the saved key for a recipient in the local
   * store.  Only if it mismatches an entry in the local store is it considered
   * 'untrusted.'
   *
   * @param address        The address of the remote client.
   * @param identityKey    The identity key to verify.
   * @return true if trusted, false if untrusted.
   */
  public boolean         isTrustedIdentity(AxolotlAddress address, IdentityKey identityKey);

}
