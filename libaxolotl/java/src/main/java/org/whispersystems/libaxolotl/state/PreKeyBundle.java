package org.whispersystems.libaxolotl.state;

import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;

/**
 * A class that contains a remote PreKey and collection
 * of associated items.
 *
 * @author Moxie Marlinspike
 */
public class PreKeyBundle {

  private int         deviceId;

  private ECPublicKey signedPreKeyPublic;
  private byte[]      signedPreKeySignature;

  private IdentityKey identityKey;

  public PreKeyBundle(int deviceId, ECPublicKey signedPreKeyPublic, byte[] signedPreKeySignature,
                      IdentityKey identityKey)
  {
    this.deviceId              = deviceId;
    this.signedPreKeyPublic    = signedPreKeyPublic;
    this.signedPreKeySignature = signedPreKeySignature;
    this.identityKey           = identityKey;
  }

  /**
   * @return the device ID this PreKey belongs to.
   */
  public int getDeviceId() {
    return deviceId;
  }

  /**
   * @return the signed prekey for this PreKeyBundle.
   */
  public ECPublicKey getSignedPreKey() {
    return signedPreKeyPublic;
  }

  /**
   * @return the signature over the signed  prekey.
   */
  public byte[] getSignedPreKeySignature() {
    return signedPreKeySignature;
  }

  /**
   * @return the {@link org.whispersystems.libaxolotl.IdentityKey} of this PreKeys owner.
   */
  public IdentityKey getIdentityKey() {
    return identityKey;
  }
}
