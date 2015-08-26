package org.whispersystems.libaxolotl;


import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.logging.Log;
import org.whispersystems.libaxolotl.protocol.CiphertextMessage;
import org.whispersystems.libaxolotl.protocol.KeyExchangeMessage;
import org.whispersystems.libaxolotl.protocol.PreKeyWhisperMessage;
import org.whispersystems.libaxolotl.ratchet.AliceAxolotlParameters;
import org.whispersystems.libaxolotl.ratchet.BobAxolotlParameters;
import org.whispersystems.libaxolotl.ratchet.RatchetingSession;
import org.whispersystems.libaxolotl.ratchet.SymmetricAxolotlParameters;
import org.whispersystems.libaxolotl.state.AxolotlStore;
import org.whispersystems.libaxolotl.state.IdentityKeyStore;
import org.whispersystems.libaxolotl.state.PreKeyBundle;
import org.whispersystems.libaxolotl.state.PreKeyStore;
import org.whispersystems.libaxolotl.state.SessionRecord;
import org.whispersystems.libaxolotl.state.SessionState;
import org.whispersystems.libaxolotl.state.SessionStore;
import org.whispersystems.libaxolotl.state.SignedPreKeyStore;
import org.whispersystems.libaxolotl.util.KeyHelper;
import org.whispersystems.libaxolotl.util.Medium;
import org.whispersystems.libaxolotl.util.guava.Optional;

/**
 * SessionBuilder is responsible for setting up encrypted sessions.
 * Once a session has been established, {@link org.whispersystems.libaxolotl.SessionCipher}
 * can be used to encrypt/decrypt messages in that session.
 * <p>
 * Sessions are built from one of three different possible vectors:
 * <ol>
 *   <li>A {@link org.whispersystems.libaxolotl.state.PreKeyBundle} retrieved from a server.</li>
 *   <li>A {@link org.whispersystems.libaxolotl.protocol.PreKeyWhisperMessage} received from a client.</li>
 *   <li>A {@link org.whispersystems.libaxolotl.protocol.KeyExchangeMessage} sent to or received from a client.</li>
 * </ol>
 *
 * Sessions are constructed per recipientId + deviceId tuple.  Remote logical users are identified
 * by their recipientId, and each logical recipientId can have multiple physical devices.
 *
 * @author Moxie Marlinspike
 */
public class SessionBuilder {

  private static final String TAG = SessionBuilder.class.getSimpleName();

  private final SessionStore      sessionStore;
  private final PreKeyStore       preKeyStore;
  private final SignedPreKeyStore signedPreKeyStore;
  private final IdentityKeyStore  identityKeyStore;
  private final AxolotlAddress    remoteAddress;

  /**
   * Constructs a SessionBuilder.
   *
   * @param sessionStore The {@link org.whispersystems.libaxolotl.state.SessionStore} to store the constructed session in.
   * @param preKeyStore The {@link  org.whispersystems.libaxolotl.state.PreKeyStore} where the client's local {@link org.whispersystems.libaxolotl.state.PreKeyRecord}s are stored.
   * @param identityKeyStore The {@link org.whispersystems.libaxolotl.state.IdentityKeyStore} containing the client's identity key information.
   * @param remoteAddress The address of the remote user to build a session with.
   */
  public SessionBuilder(SessionStore sessionStore,
                        PreKeyStore preKeyStore,
                        SignedPreKeyStore signedPreKeyStore,
                        IdentityKeyStore identityKeyStore,
                        AxolotlAddress remoteAddress)
  {
    this.sessionStore      = sessionStore;
    this.preKeyStore       = preKeyStore;
    this.signedPreKeyStore = signedPreKeyStore;
    this.identityKeyStore  = identityKeyStore;
    this.remoteAddress     = remoteAddress;
  }

  /**
   * Constructs a SessionBuilder
   * @param store The {@link org.whispersystems.libaxolotl.state.AxolotlStore} to store all state information in.
   * @param remoteAddress The address of the remote user to build a session with.
   */
  public SessionBuilder(AxolotlStore store, AxolotlAddress remoteAddress) {
    this(store, store, store, store, remoteAddress);
  }

  /**
   * Build a new session from a received {@link org.whispersystems.libaxolotl.protocol.PreKeyWhisperMessage}.
   *
   * After a session is constructed in this way, the embedded {@link org.whispersystems.libaxolotl.protocol.WhisperMessage}
   * can be decrypted.
   *
   * @param message The received {@link org.whispersystems.libaxolotl.protocol.PreKeyWhisperMessage}.
   * @throws org.whispersystems.libaxolotl.InvalidKeyIdException when there is no local
   *                                                             {@link org.whispersystems.libaxolotl.state.PreKeyRecord}
   *                                                             that corresponds to the PreKey ID in
   *                                                             the message.
   * @throws org.whispersystems.libaxolotl.InvalidKeyException when the message is formatted incorrectly.
   * @throws org.whispersystems.libaxolotl.UntrustedIdentityException when the {@link IdentityKey} of the sender is untrusted.
   */
  /*package*/ Optional<Integer> process(SessionRecord sessionRecord, PreKeyWhisperMessage message)
      throws InvalidKeyIdException, InvalidKeyException, UntrustedIdentityException
  {
    int         messageVersion   = message.getMessageVersion();
    IdentityKey theirIdentityKey = message.getIdentityKey();

    Optional<Integer> unsignedPreKeyId;

    if (!identityKeyStore.isTrustedIdentity(remoteAddress.getName(), theirIdentityKey)) {
      throw new UntrustedIdentityException(remoteAddress.getName(), theirIdentityKey);
    }

    switch (messageVersion) {
      case 2:  unsignedPreKeyId = processV2(sessionRecord, message); break;
      case 3:  unsignedPreKeyId = processV3(sessionRecord, message); break;
      default: throw new AssertionError("Unknown version: " + messageVersion);
    }

    identityKeyStore.saveIdentity(remoteAddress.getName(), theirIdentityKey);
    return unsignedPreKeyId;
  }

  private Optional<Integer> processV3(SessionRecord sessionRecord, PreKeyWhisperMessage message)
      throws UntrustedIdentityException, InvalidKeyIdException, InvalidKeyException
  {

    if (sessionRecord.hasSessionState(message.getMessageVersion(), message.getBaseKey().serialize())) {
      Log.w(TAG, "We've already setup a session for this V3 message, letting bundled message fall through...");
      return Optional.absent();
    }

    ECKeyPair ourSignedPreKey = signedPreKeyStore.loadSignedPreKey(message.getSignedPreKeyId()).getKeyPair();

    BobAxolotlParameters.Builder parameters = BobAxolotlParameters.newBuilder();

    parameters.setTheirBaseKey(message.getBaseKey())
              .setTheirIdentityKey(message.getIdentityKey())
              .setOurIdentityKey(identityKeyStore.getIdentityKeyPair())
              .setOurSignedPreKey(ourSignedPreKey)
              .setOurRatchetKey(ourSignedPreKey);

    if (message.getPreKeyId().isPresent()) {
      parameters.setOurOneTimePreKey(Optional.of(preKeyStore.loadPreKey(message.getPreKeyId().get()).getKeyPair()));
    } else {
      parameters.setOurOneTimePreKey(Optional.<ECKeyPair>absent());
    }

    if (!sessionRecord.isFresh()) sessionRecord.archiveCurrentState();

    RatchetingSession.initializeSession(sessionRecord.getSessionState(), message.getMessageVersion(), parameters.create());

    sessionRecord.getSessionState().setLocalRegistrationId(identityKeyStore.getLocalRegistrationId());
    sessionRecord.getSessionState().setRemoteRegistrationId(message.getRegistrationId());
    sessionRecord.getSessionState().setAliceBaseKey(message.getBaseKey().serialize());

    if (message.getPreKeyId().isPresent() && message.getPreKeyId().get() != Medium.MAX_VALUE) {
      return message.getPreKeyId();
    } else {
      return Optional.absent();
    }
  }

  private Optional<Integer> processV2(SessionRecord sessionRecord, PreKeyWhisperMessage message)
      throws UntrustedIdentityException, InvalidKeyIdException, InvalidKeyException
  {
    if (!message.getPreKeyId().isPresent()) {
      throw new InvalidKeyIdException("V2 message requires one time prekey id!");
    }

    if (!preKeyStore.containsPreKey(message.getPreKeyId().get()) &&
        sessionStore.containsSession(remoteAddress))
    {
      Log.w(TAG, "We've already processed the prekey part of this V2 session, letting bundled message fall through...");
      return Optional.absent();
    }

    ECKeyPair ourPreKey = preKeyStore.loadPreKey(message.getPreKeyId().get()).getKeyPair();

    BobAxolotlParameters.Builder parameters = BobAxolotlParameters.newBuilder();

    parameters.setOurIdentityKey(identityKeyStore.getIdentityKeyPair())
              .setOurSignedPreKey(ourPreKey)
              .setOurRatchetKey(ourPreKey)
              .setOurOneTimePreKey(Optional.<ECKeyPair>absent())
              .setTheirIdentityKey(message.getIdentityKey())
              .setTheirBaseKey(message.getBaseKey());

    if (!sessionRecord.isFresh()) sessionRecord.archiveCurrentState();

    RatchetingSession.initializeSession(sessionRecord.getSessionState(), message.getMessageVersion(), parameters.create());

    sessionRecord.getSessionState().setLocalRegistrationId(identityKeyStore.getLocalRegistrationId());
    sessionRecord.getSessionState().setRemoteRegistrationId(message.getRegistrationId());
    sessionRecord.getSessionState().setAliceBaseKey(message.getBaseKey().serialize());

    if (message.getPreKeyId().get() != Medium.MAX_VALUE) {
      return message.getPreKeyId();
    } else {
      return Optional.absent();
    }
  }

  /**
   * Build a new session from a {@link org.whispersystems.libaxolotl.state.PreKeyBundle} retrieved from
   * a server.
   *
   * @param preKey A PreKey for the destination recipient, retrieved from a server.
   * @throws InvalidKeyException when the {@link org.whispersystems.libaxolotl.state.PreKeyBundle} is
   *                             badly formatted.
   * @throws org.whispersystems.libaxolotl.UntrustedIdentityException when the sender's
   *                                                                  {@link IdentityKey} is not
   *                                                                  trusted.
   */
  public void process(PreKeyBundle preKey) throws InvalidKeyException, UntrustedIdentityException {
    synchronized (SessionCipher.SESSION_LOCK) {
      if (!identityKeyStore.isTrustedIdentity(remoteAddress.getName(), preKey.getIdentityKey())) {
        throw new UntrustedIdentityException(remoteAddress.getName(), preKey.getIdentityKey());
      }

      if (preKey.getSignedPreKey() != null &&
          !Curve.verifySignature(preKey.getIdentityKey().getPublicKey(),
                                 preKey.getSignedPreKey().serialize(),
                                 preKey.getSignedPreKeySignature()))
      {
        throw new InvalidKeyException("Invalid signature on device key!");
      }

      if (preKey.getSignedPreKey() == null && preKey.getPreKey() == null) {
        throw new InvalidKeyException("Both signed and unsigned prekeys are absent!");
      }

      boolean               supportsV3           = preKey.getSignedPreKey() != null;
      SessionRecord         sessionRecord        = sessionStore.loadSession(remoteAddress);
      ECKeyPair             ourBaseKey           = Curve.generateKeyPair();
      ECPublicKey           theirSignedPreKey    = supportsV3 ? preKey.getSignedPreKey() : preKey.getPreKey();
      Optional<ECPublicKey> theirOneTimePreKey   = Optional.fromNullable(preKey.getPreKey());
      Optional<Integer>     theirOneTimePreKeyId = theirOneTimePreKey.isPresent() ? Optional.of(preKey.getPreKeyId()) :
                                                                                    Optional.<Integer>absent();

      AliceAxolotlParameters.Builder parameters = AliceAxolotlParameters.newBuilder();

      parameters.setOurBaseKey(ourBaseKey)
                .setOurIdentityKey(identityKeyStore.getIdentityKeyPair())
                .setTheirIdentityKey(preKey.getIdentityKey())
                .setTheirSignedPreKey(theirSignedPreKey)
                .setTheirRatchetKey(theirSignedPreKey)
                .setTheirOneTimePreKey(supportsV3 ? theirOneTimePreKey : Optional.<ECPublicKey>absent());

      if (!sessionRecord.isFresh()) sessionRecord.archiveCurrentState();

      RatchetingSession.initializeSession(sessionRecord.getSessionState(),
                                          supportsV3 ? 3 : 2,
                                          parameters.create());

      sessionRecord.getSessionState().setUnacknowledgedPreKeyMessage(theirOneTimePreKeyId, preKey.getSignedPreKeyId(), ourBaseKey.getPublicKey());
      sessionRecord.getSessionState().setLocalRegistrationId(identityKeyStore.getLocalRegistrationId());
      sessionRecord.getSessionState().setRemoteRegistrationId(preKey.getRegistrationId());
      sessionRecord.getSessionState().setAliceBaseKey(ourBaseKey.getPublicKey().serialize());

      sessionStore.storeSession(remoteAddress, sessionRecord);
      identityKeyStore.saveIdentity(remoteAddress.getName(), preKey.getIdentityKey());
    }
  }

  /**
   * Build a new session from a {@link org.whispersystems.libaxolotl.protocol.KeyExchangeMessage}
   * received from a remote client.
   *
   * @param message The received KeyExchangeMessage.
   * @return The KeyExchangeMessage to respond with, or null if no response is necessary.
   * @throws InvalidKeyException if the received KeyExchangeMessage is badly formatted.
   */
  public KeyExchangeMessage process(KeyExchangeMessage message)
      throws InvalidKeyException, UntrustedIdentityException, StaleKeyExchangeException
  {
    synchronized (SessionCipher.SESSION_LOCK) {
      if (!identityKeyStore.isTrustedIdentity(remoteAddress.getName(), message.getIdentityKey())) {
        throw new UntrustedIdentityException(remoteAddress.getName(), message.getIdentityKey());
      }

      KeyExchangeMessage responseMessage = null;

      if (message.isInitiate()) responseMessage = processInitiate(message);
      else                      processResponse(message);

      return responseMessage;
    }
  }

  private KeyExchangeMessage processInitiate(KeyExchangeMessage message) throws InvalidKeyException {
    int           flags         = KeyExchangeMessage.RESPONSE_FLAG;
    SessionRecord sessionRecord = sessionStore.loadSession(remoteAddress);

    if (message.getVersion() >= 3 &&
        !Curve.verifySignature(message.getIdentityKey().getPublicKey(),
                               message.getBaseKey().serialize(),
                               message.getBaseKeySignature()))
    {
      throw new InvalidKeyException("Bad signature!");
    }

    SymmetricAxolotlParameters.Builder builder = SymmetricAxolotlParameters.newBuilder();

    if (!sessionRecord.getSessionState().hasPendingKeyExchange()) {
      builder.setOurIdentityKey(identityKeyStore.getIdentityKeyPair())
             .setOurBaseKey(Curve.generateKeyPair())
             .setOurRatchetKey(Curve.generateKeyPair());
    } else {
      builder.setOurIdentityKey(sessionRecord.getSessionState().getPendingKeyExchangeIdentityKey())
             .setOurBaseKey(sessionRecord.getSessionState().getPendingKeyExchangeBaseKey())
             .setOurRatchetKey(sessionRecord.getSessionState().getPendingKeyExchangeRatchetKey());
      flags |= KeyExchangeMessage.SIMULTAENOUS_INITIATE_FLAG;
    }

    builder.setTheirBaseKey(message.getBaseKey())
           .setTheirRatchetKey(message.getRatchetKey())
           .setTheirIdentityKey(message.getIdentityKey());

    SymmetricAxolotlParameters parameters = builder.create();

    if (!sessionRecord.isFresh()) sessionRecord.archiveCurrentState();

    RatchetingSession.initializeSession(sessionRecord.getSessionState(),
                                        Math.min(message.getMaxVersion(), CiphertextMessage.CURRENT_VERSION),
                                        parameters);

    sessionStore.storeSession(remoteAddress, sessionRecord);
    identityKeyStore.saveIdentity(remoteAddress.getName(), message.getIdentityKey());

    byte[] baseKeySignature = Curve.calculateSignature(parameters.getOurIdentityKey().getPrivateKey(),
                                                       parameters.getOurBaseKey().getPublicKey().serialize());

    return new KeyExchangeMessage(sessionRecord.getSessionState().getSessionVersion(),
                                  message.getSequence(), flags,
                                  parameters.getOurBaseKey().getPublicKey(),
                                  baseKeySignature, parameters.getOurRatchetKey().getPublicKey(),
                                  parameters.getOurIdentityKey().getPublicKey());
  }

  private void processResponse(KeyExchangeMessage message)
      throws StaleKeyExchangeException, InvalidKeyException
  {
    SessionRecord sessionRecord                  = sessionStore.loadSession(remoteAddress);
    SessionState  sessionState                   = sessionRecord.getSessionState();
    boolean       hasPendingKeyExchange          = sessionState.hasPendingKeyExchange();
    boolean       isSimultaneousInitiateResponse = message.isResponseForSimultaneousInitiate();

    if (!hasPendingKeyExchange || sessionState.getPendingKeyExchangeSequence() != message.getSequence()) {
      Log.w(TAG, "No matching sequence for response. Is simultaneous initiate response: " + isSimultaneousInitiateResponse);
      if (!isSimultaneousInitiateResponse) throw new StaleKeyExchangeException();
      else                                 return;
    }

    SymmetricAxolotlParameters.Builder parameters = SymmetricAxolotlParameters.newBuilder();

    parameters.setOurBaseKey(sessionRecord.getSessionState().getPendingKeyExchangeBaseKey())
              .setOurRatchetKey(sessionRecord.getSessionState().getPendingKeyExchangeRatchetKey())
              .setOurIdentityKey(sessionRecord.getSessionState().getPendingKeyExchangeIdentityKey())
              .setTheirBaseKey(message.getBaseKey())
              .setTheirRatchetKey(message.getRatchetKey())
              .setTheirIdentityKey(message.getIdentityKey());

    if (!sessionRecord.isFresh()) sessionRecord.archiveCurrentState();

    RatchetingSession.initializeSession(sessionRecord.getSessionState(),
                                        Math.min(message.getMaxVersion(), CiphertextMessage.CURRENT_VERSION),
                                        parameters.create());

    if (sessionRecord.getSessionState().getSessionVersion() >= 3 &&
        !Curve.verifySignature(message.getIdentityKey().getPublicKey(),
                               message.getBaseKey().serialize(),
                               message.getBaseKeySignature()))
    {
      throw new InvalidKeyException("Base key signature doesn't match!");
    }

    sessionStore.storeSession(remoteAddress, sessionRecord);
    identityKeyStore.saveIdentity(remoteAddress.getName(), message.getIdentityKey());

  }

  /**
   * Initiate a new session by sending an initial KeyExchangeMessage to the recipient.
   *
   * @return the KeyExchangeMessage to deliver.
   */
  public KeyExchangeMessage process() {
    synchronized (SessionCipher.SESSION_LOCK) {
      try {
        int             sequence         = KeyHelper.getRandomSequence(65534) + 1;
        int             flags            = KeyExchangeMessage.INITIATE_FLAG;
        ECKeyPair       baseKey          = Curve.generateKeyPair();
        ECKeyPair       ratchetKey       = Curve.generateKeyPair();
        IdentityKeyPair identityKey      = identityKeyStore.getIdentityKeyPair();
        byte[]          baseKeySignature = Curve.calculateSignature(identityKey.getPrivateKey(), baseKey.getPublicKey().serialize());
        SessionRecord   sessionRecord    = sessionStore.loadSession(remoteAddress);

        sessionRecord.getSessionState().setPendingKeyExchange(sequence, baseKey, ratchetKey, identityKey);
        sessionStore.storeSession(remoteAddress, sessionRecord);

        return new KeyExchangeMessage(2, sequence, flags, baseKey.getPublicKey(), baseKeySignature,
                                      ratchetKey.getPublicKey(), identityKey.getPublicKey());
      } catch (InvalidKeyException e) {
        throw new AssertionError(e);
      }
    }
  }


}
