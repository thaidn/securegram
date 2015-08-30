package xyz.securegram.axolotl;

import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.InvalidKeyException;

import xyz.securegram.axolotl.AbelianProtos.AbelianIdentity;

/**
 * Created by thaidn on 8/29/15.
 */
public class AbelianIdentityKey extends IdentityKey {
  final AbelianIdentity abelianIdentity;

  public AbelianIdentityKey(AbelianIdentity abelianIdentity) throws InvalidKeyException {
    super(abelianIdentity.getIdentityKey().toByteArray(), 0);
    this.abelianIdentity = abelianIdentity;
  }

  public AbelianIdentity getAbelianIdentity() {
    return abelianIdentity;
  }

  @Override
  public byte[] serialize() {
    return abelianIdentity.toByteArray();
  }

  @Override
  public boolean equals(Object other) {
    if (other == null)                   return false;
    if (!(other instanceof AbelianIdentityKey)) return false;

    return abelianIdentity.equals(((AbelianIdentityKey) other).getAbelianIdentity());
  }

  @Override
  public int hashCode() {
    return abelianIdentity.hashCode();
  }
}
