package org.whispersystems.libaxolotl;

import junit.framework.TestCase;

import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.protocol.CiphertextMessage;
import org.whispersystems.libaxolotl.protocol.KeyExchangeMessage;
import org.whispersystems.libaxolotl.protocol.PreKeyWhisperMessage;
import org.whispersystems.libaxolotl.protocol.WhisperMessage;
import org.whispersystems.libaxolotl.state.AxolotlStore;
import org.whispersystems.libaxolotl.state.IdentityKeyStore;
import org.whispersystems.libaxolotl.state.PreKeyBundle;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;
import org.whispersystems.libaxolotl.util.Pair;

import java.util.HashSet;
import java.util.Set;

public class SessionBuilderTest extends TestCase {

  private static final AxolotlAddress ALICE_ADDRESS = new AxolotlAddress("+14151111111", 1);
  private static final AxolotlAddress BOB_ADDRESS   = new AxolotlAddress("+14152222222", 1);

  public void testBasicPreKeyV4()
      throws InvalidKeyException, InvalidVersionException, InvalidMessageException, InvalidKeyIdException, DuplicateMessageException, LegacyMessageException, UntrustedIdentityException, NoSessionException {
    AxolotlStore   aliceStore          = new TestInMemoryAxolotlStore();
    SessionBuilder aliceSessionBuilder = new SessionBuilder(aliceStore, BOB_ADDRESS);

    final AxolotlStore bobStore                 = new TestInMemoryAxolotlStore();

    PreKeyBundle bobPreKey = new PreKeyBundle(bobStore.getLocalDeviceId(),
        bobStore.getSignedPreKeyRecord().getKeyPair().getPublicKey(),
        bobStore.getSignedPreKeyRecord().getSignature(),
        bobStore.getIdentityKeyPair().getPublicKey());

    aliceSessionBuilder.process(bobPreKey);

    assertTrue(aliceStore.containsSession(BOB_ADDRESS));
    assertTrue(aliceStore.loadSession(BOB_ADDRESS).getSessionState().getSessionVersion() == 3);

    final String            originalMessage    = "L'homme est condamné à être libre";
          SessionCipher     aliceSessionCipher = new SessionCipher(aliceStore, BOB_ADDRESS);
          CiphertextMessage outgoingMessage    = aliceSessionCipher.encrypt(originalMessage.getBytes());

    assertTrue(outgoingMessage.getType() == CiphertextMessage.PREKEY_TYPE);

    PreKeyWhisperMessage incomingMessage = new PreKeyWhisperMessage(outgoingMessage.serialize());

    SessionCipher bobSessionCipher = new SessionCipher(bobStore, ALICE_ADDRESS);
    byte[] plaintext = bobSessionCipher.decrypt(incomingMessage, new DecryptionCallback() {
      @Override
      public void handlePlaintext(byte[] plaintext) {
        assertTrue(originalMessage.equals(new String(plaintext)));
        assertFalse(bobStore.containsSession(ALICE_ADDRESS));
      }
    });

    assertTrue(bobStore.containsSession(ALICE_ADDRESS));
    assertTrue(bobStore.loadSession(ALICE_ADDRESS).getSessionState().getSessionVersion() == 3);
    assertTrue(bobStore.loadSession(ALICE_ADDRESS).getSessionState().getAliceBaseKey() != null);
    assertTrue(originalMessage.equals(new String(plaintext)));

    CiphertextMessage bobOutgoingMessage = bobSessionCipher.encrypt(originalMessage.getBytes());
    assertTrue(bobOutgoingMessage.getType() == CiphertextMessage.WHISPER_TYPE);

    byte[] alicePlaintext = aliceSessionCipher.decrypt(new WhisperMessage(bobOutgoingMessage.serialize()));
    assertTrue(new String(alicePlaintext).equals(originalMessage));

    runInteraction(aliceStore, bobStore);

    aliceStore          = new TestInMemoryAxolotlStore();
    aliceSessionBuilder = new SessionBuilder(aliceStore, BOB_ADDRESS);
    aliceSessionCipher  = new SessionCipher(aliceStore, BOB_ADDRESS);

    bobPreKey = new PreKeyBundle(bobStore.getLocalDeviceId(),
        bobStore.getSignedPreKeyRecord().getKeyPair().getPublicKey(),
        bobStore.getSignedPreKeyRecord().getSignature(),
        bobStore.getIdentityKeyPair().getPublicKey());

    aliceSessionBuilder.process(bobPreKey);

    outgoingMessage = aliceSessionCipher.encrypt(originalMessage.getBytes());

    try {
      plaintext = bobSessionCipher.decrypt(new PreKeyWhisperMessage(outgoingMessage.serialize()));
      throw new AssertionError("shouldn't be trusted!");
    } catch (UntrustedIdentityException uie) {
      bobStore.saveIdentity(ALICE_ADDRESS, new PreKeyWhisperMessage(outgoingMessage.serialize()).getIdentityKey());
    }

    plaintext = bobSessionCipher.decrypt(new PreKeyWhisperMessage(outgoingMessage.serialize()));
    assertTrue(new String(plaintext).equals(originalMessage));

    bobPreKey = new PreKeyBundle(bobStore.getLocalDeviceId(),
        bobStore.getSignedPreKeyRecord().getKeyPair().getPublicKey(),
        bobStore.getSignedPreKeyRecord().getSignature(),
        aliceStore.getIdentityKeyPair().getPublicKey());

    try {
      aliceSessionBuilder.process(bobPreKey);
      throw new AssertionError("shoulnd't be trusted!");
    } catch (UntrustedIdentityException uie) {
      // good
    }
  }

  public void testBadSignedPreKeySignature() throws InvalidKeyException, UntrustedIdentityException {
    AxolotlStore   aliceStore          = new TestInMemoryAxolotlStore();
    SessionBuilder aliceSessionBuilder = new SessionBuilder(aliceStore, BOB_ADDRESS);

    IdentityKeyStore bobIdentityKeyStore = new TestInMemoryIdentityKeyStore();

    ECKeyPair bobPreKeyPair            = Curve.generateKeyPair();
    ECKeyPair bobSignedPreKeyPair      = Curve.generateKeyPair();
    byte[]    bobSignedPreKeySignature = Curve.calculateSignature(bobIdentityKeyStore.getIdentityKeyPair().getPrivateKey(),
                                                                  bobSignedPreKeyPair.getPublicKey().serialize());


    for (int i=0;i<bobSignedPreKeySignature.length * 8;i++) {
      byte[] modifiedSignature = new byte[bobSignedPreKeySignature.length];
      System.arraycopy(bobSignedPreKeySignature, 0, modifiedSignature, 0, modifiedSignature.length);

      modifiedSignature[i/8] ^= (0x01 << (i % 8));

      PreKeyBundle bobPreKey = new PreKeyBundle(bobIdentityKeyStore.getLocalDeviceId(),
          bobSignedPreKeyPair.getPublicKey(), modifiedSignature,
          bobIdentityKeyStore.getIdentityKeyPair().getPublicKey());

      try {
        aliceSessionBuilder.process(bobPreKey);
        throw new AssertionError("Accepted modified device key signature!");
      } catch (InvalidKeyException ike) {
        // good
      }
    }

    PreKeyBundle bobPreKey = new PreKeyBundle(bobIdentityKeyStore.getLocalDeviceId(),
        bobSignedPreKeyPair.getPublicKey(), bobSignedPreKeySignature,
        bobIdentityKeyStore.getIdentityKeyPair().getPublicKey());

    aliceSessionBuilder.process(bobPreKey);
  }

  public void testRepeatBundleMessageV4() throws InvalidKeyException, UntrustedIdentityException, InvalidVersionException, InvalidMessageException, InvalidKeyIdException, DuplicateMessageException, LegacyMessageException, NoSessionException {
    AxolotlStore   aliceStore          = new TestInMemoryAxolotlStore();
    SessionBuilder aliceSessionBuilder = new SessionBuilder(aliceStore, BOB_ADDRESS);

    AxolotlStore bobStore = new TestInMemoryAxolotlStore();

    PreKeyBundle bobPreKey = new PreKeyBundle(bobStore.getLocalDeviceId(),
        bobStore.getSignedPreKeyRecord().getKeyPair().getPublicKey(),
        bobStore.getSignedPreKeyRecord().getSignature(),
        bobStore.getIdentityKeyPair().getPublicKey());

    aliceSessionBuilder.process(bobPreKey);

    String            originalMessage    = "L'homme est condamné à être libre";
    SessionCipher     aliceSessionCipher = new SessionCipher(aliceStore, BOB_ADDRESS);
    CiphertextMessage outgoingMessageOne = aliceSessionCipher.encrypt(originalMessage.getBytes());
    CiphertextMessage outgoingMessageTwo = aliceSessionCipher.encrypt(originalMessage.getBytes());

    assertTrue(outgoingMessageOne.getType() == CiphertextMessage.PREKEY_TYPE);
    assertTrue(outgoingMessageTwo.getType() == CiphertextMessage.PREKEY_TYPE);

    PreKeyWhisperMessage incomingMessage = new PreKeyWhisperMessage(outgoingMessageOne.serialize());

    SessionCipher bobSessionCipher = new SessionCipher(bobStore, ALICE_ADDRESS);

    byte[]        plaintext        = bobSessionCipher.decrypt(incomingMessage);
    assertTrue(originalMessage.equals(new String(plaintext)));

    CiphertextMessage bobOutgoingMessage = bobSessionCipher.encrypt(originalMessage.getBytes());

    byte[] alicePlaintext = aliceSessionCipher.decrypt(new WhisperMessage(bobOutgoingMessage.serialize()));
    assertTrue(originalMessage.equals(new String(alicePlaintext)));

    // The test

    PreKeyWhisperMessage incomingMessageTwo = new PreKeyWhisperMessage(outgoingMessageTwo.serialize());

    plaintext = bobSessionCipher.decrypt(new PreKeyWhisperMessage(incomingMessageTwo.serialize()));
    assertTrue(originalMessage.equals(new String(plaintext)));

    bobOutgoingMessage = bobSessionCipher.encrypt(originalMessage.getBytes());
    alicePlaintext = aliceSessionCipher.decrypt(new WhisperMessage(bobOutgoingMessage.serialize()));
    assertTrue(originalMessage.equals(new String(alicePlaintext)));

  }

  public void testBadMessageBundle() throws InvalidKeyException, UntrustedIdentityException, InvalidVersionException, InvalidMessageException, DuplicateMessageException, LegacyMessageException, InvalidKeyIdException {
    AxolotlStore   aliceStore          = new TestInMemoryAxolotlStore();
    SessionBuilder aliceSessionBuilder = new SessionBuilder(aliceStore, BOB_ADDRESS);

    AxolotlStore bobStore = new TestInMemoryAxolotlStore();

    PreKeyBundle bobPreKey = new PreKeyBundle(bobStore.getLocalDeviceId(),
        bobStore.getSignedPreKeyRecord().getKeyPair().getPublicKey(),
        bobStore.getSignedPreKeyRecord().getSignature(),
        bobStore.getIdentityKeyPair().getPublicKey());

    aliceSessionBuilder.process(bobPreKey);

    String            originalMessage    = "L'homme est condamné à être libre";
    SessionCipher     aliceSessionCipher = new SessionCipher(aliceStore, BOB_ADDRESS);
    CiphertextMessage outgoingMessageOne = aliceSessionCipher.encrypt(originalMessage.getBytes());

    assertTrue(outgoingMessageOne.getType() == CiphertextMessage.PREKEY_TYPE);

    byte[] goodMessage = outgoingMessageOne.serialize();
    byte[] badMessage  = new byte[goodMessage.length];
    System.arraycopy(goodMessage, 0, badMessage, 0, badMessage.length);

    badMessage[badMessage.length-10] ^= 0x01;

    PreKeyWhisperMessage incomingMessage  = new PreKeyWhisperMessage(badMessage);
    SessionCipher        bobSessionCipher = new SessionCipher(bobStore, ALICE_ADDRESS);

    byte[] plaintext = new byte[0];

    try {
      plaintext = bobSessionCipher.decrypt(incomingMessage);
      throw new AssertionError("Decrypt should have failed!");
    } catch (InvalidMessageException e) {
      // good.
    }

    plaintext = bobSessionCipher.decrypt(new PreKeyWhisperMessage(goodMessage));

    assertTrue(originalMessage.equals(new String(plaintext)));
  }

  public void testBasicKeyExchange() throws InvalidKeyException, LegacyMessageException, InvalidMessageException, DuplicateMessageException, UntrustedIdentityException, StaleKeyExchangeException, InvalidVersionException, NoSessionException {
    AxolotlStore   aliceStore          = new TestInMemoryAxolotlStore();
    SessionBuilder aliceSessionBuilder = new SessionBuilder(aliceStore, BOB_ADDRESS);

    AxolotlStore   bobStore          = new TestInMemoryAxolotlStore();
    SessionBuilder bobSessionBuilder = new SessionBuilder(bobStore, ALICE_ADDRESS);

    KeyExchangeMessage aliceKeyExchangeMessage      = aliceSessionBuilder.process();
    assertTrue(aliceKeyExchangeMessage != null);

    byte[]             aliceKeyExchangeMessageBytes = aliceKeyExchangeMessage.serialize();
    KeyExchangeMessage bobKeyExchangeMessage        = bobSessionBuilder.process(new KeyExchangeMessage(aliceKeyExchangeMessageBytes));

    assertTrue(bobKeyExchangeMessage != null);

    byte[]             bobKeyExchangeMessageBytes = bobKeyExchangeMessage.serialize();
    KeyExchangeMessage response                   = aliceSessionBuilder.process(new KeyExchangeMessage(bobKeyExchangeMessageBytes));

    assertTrue(response == null);
    assertTrue(aliceStore.containsSession(BOB_ADDRESS));
    assertTrue(bobStore.containsSession(ALICE_ADDRESS));

    runInteraction(aliceStore, bobStore);

    aliceStore              = new TestInMemoryAxolotlStore();
    aliceSessionBuilder     = new SessionBuilder(aliceStore, BOB_ADDRESS);
    aliceKeyExchangeMessage = aliceSessionBuilder.process();

    try {
      bobKeyExchangeMessage = bobSessionBuilder.process(aliceKeyExchangeMessage);
      throw new AssertionError("This identity shouldn't be trusted!");
    } catch (UntrustedIdentityException uie) {
      bobStore.saveIdentity(ALICE_ADDRESS, aliceKeyExchangeMessage.getIdentityKey());
      bobKeyExchangeMessage = bobSessionBuilder.process(aliceKeyExchangeMessage);
    }

    assertTrue(aliceSessionBuilder.process(bobKeyExchangeMessage) == null);

    runInteraction(aliceStore, bobStore);
  }

  public void testSimultaneousKeyExchange()
      throws InvalidKeyException, DuplicateMessageException, LegacyMessageException, InvalidMessageException, UntrustedIdentityException, StaleKeyExchangeException, NoSessionException {
    AxolotlStore   aliceStore          = new TestInMemoryAxolotlStore();
    SessionBuilder aliceSessionBuilder = new SessionBuilder(aliceStore, BOB_ADDRESS);

    AxolotlStore   bobStore          = new TestInMemoryAxolotlStore();
    SessionBuilder bobSessionBuilder = new SessionBuilder(bobStore, ALICE_ADDRESS);

    KeyExchangeMessage aliceKeyExchange = aliceSessionBuilder.process();
    KeyExchangeMessage bobKeyExchange   = bobSessionBuilder.process();

    assertTrue(aliceKeyExchange != null);
    assertTrue(bobKeyExchange != null);

    KeyExchangeMessage aliceResponse = aliceSessionBuilder.process(bobKeyExchange);
    KeyExchangeMessage bobResponse   = bobSessionBuilder.process(aliceKeyExchange);

    assertTrue(aliceResponse != null);
    assertTrue(bobResponse != null);

    KeyExchangeMessage aliceAck = aliceSessionBuilder.process(bobResponse);
    KeyExchangeMessage bobAck   = bobSessionBuilder.process(aliceResponse);

    assertTrue(aliceAck == null);
    assertTrue(bobAck == null);

    runInteraction(aliceStore, bobStore);
  }

  private void runInteraction(AxolotlStore aliceStore, AxolotlStore bobStore)
      throws DuplicateMessageException, LegacyMessageException, InvalidMessageException, NoSessionException
  {
    SessionCipher aliceSessionCipher = new SessionCipher(aliceStore, BOB_ADDRESS);
    SessionCipher bobSessionCipher   = new SessionCipher(bobStore, ALICE_ADDRESS);

    String originalMessage = "smert ze smert";
    CiphertextMessage aliceMessage = aliceSessionCipher.encrypt(originalMessage.getBytes());

    assertTrue(aliceMessage.getType() == CiphertextMessage.WHISPER_TYPE);

    byte[] plaintext = bobSessionCipher.decrypt(new WhisperMessage(aliceMessage.serialize()));
    assertTrue(new String(plaintext).equals(originalMessage));

    CiphertextMessage bobMessage = bobSessionCipher.encrypt(originalMessage.getBytes());

    assertTrue(bobMessage.getType() == CiphertextMessage.WHISPER_TYPE);

    plaintext = aliceSessionCipher.decrypt(new WhisperMessage(bobMessage.serialize()));
    assertTrue(new String(plaintext).equals(originalMessage));

    for (int i=0;i<10;i++) {
      String loopingMessage = ("What do we mean by saying that existence precedes essence? " +
                               "We mean that man first of all exists, encounters himself, " +
                               "surges up in the world--and defines himself aftward. " + i);
      CiphertextMessage aliceLoopingMessage = aliceSessionCipher.encrypt(loopingMessage.getBytes());

      byte[] loopingPlaintext = bobSessionCipher.decrypt(new WhisperMessage(aliceLoopingMessage.serialize()));
      assertTrue(new String(loopingPlaintext).equals(loopingMessage));
    }

    for (int i=0;i<10;i++) {
      String loopingMessage = ("What do we mean by saying that existence precedes essence? " +
                               "We mean that man first of all exists, encounters himself, " +
                               "surges up in the world--and defines himself aftward. " + i);
      CiphertextMessage bobLoopingMessage = bobSessionCipher.encrypt(loopingMessage.getBytes());

      byte[] loopingPlaintext = aliceSessionCipher.decrypt(new WhisperMessage(bobLoopingMessage.serialize()));
      assertTrue(new String(loopingPlaintext).equals(loopingMessage));
    }

    Set<Pair<String, CiphertextMessage>> aliceOutOfOrderMessages = new HashSet<>();

    for (int i=0;i<10;i++) {
      String loopingMessage = ("What do we mean by saying that existence precedes essence? " +
                               "We mean that man first of all exists, encounters himself, " +
                               "surges up in the world--and defines himself aftward. " + i);
      CiphertextMessage aliceLoopingMessage = aliceSessionCipher.encrypt(loopingMessage.getBytes());

      aliceOutOfOrderMessages.add(new Pair<>(loopingMessage, aliceLoopingMessage));
    }

    for (int i=0;i<10;i++) {
      String loopingMessage = ("What do we mean by saying that existence precedes essence? " +
                               "We mean that man first of all exists, encounters himself, " +
                               "surges up in the world--and defines himself aftward. " + i);
      CiphertextMessage aliceLoopingMessage = aliceSessionCipher.encrypt(loopingMessage.getBytes());

      byte[] loopingPlaintext = bobSessionCipher.decrypt(new WhisperMessage(aliceLoopingMessage.serialize()));
      assertTrue(new String(loopingPlaintext).equals(loopingMessage));
    }

    for (int i=0;i<10;i++) {
      String loopingMessage = ("You can only desire based on what you know: " + i);
      CiphertextMessage bobLoopingMessage = bobSessionCipher.encrypt(loopingMessage.getBytes());

      byte[] loopingPlaintext = aliceSessionCipher.decrypt(new WhisperMessage(bobLoopingMessage.serialize()));
      assertTrue(new String(loopingPlaintext).equals(loopingMessage));
    }

    for (Pair<String, CiphertextMessage> aliceOutOfOrderMessage : aliceOutOfOrderMessages) {
      byte[] outOfOrderPlaintext = bobSessionCipher.decrypt(new WhisperMessage(aliceOutOfOrderMessage.second().serialize()));
      assertTrue(new String(outOfOrderPlaintext).equals(aliceOutOfOrderMessage.first()));
    }
  }


}
