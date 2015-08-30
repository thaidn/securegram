package org.whispersystems.libaxolotl;

import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;
import org.whispersystems.libaxolotl.state.impl.InMemoryIdentityKeyStore;
import org.whispersystems.libaxolotl.util.KeyHelper;

public class TestInMemoryIdentityKeyStore extends org.whispersystems.libaxolotl.state.impl.InMemoryIdentityKeyStore {
  public TestInMemoryIdentityKeyStore() {
    super();
  }
}
