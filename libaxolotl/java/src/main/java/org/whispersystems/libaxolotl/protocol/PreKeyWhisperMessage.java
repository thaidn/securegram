/**
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.libaxolotl.protocol;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.InvalidVersionException;
import org.whispersystems.libaxolotl.LegacyMessageException;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.util.ByteUtil;
import org.whispersystems.libaxolotl.util.guava.Optional;


public class PreKeyWhisperMessage implements CiphertextMessage {

  private final int               version;
  private final int               deviceId;
  private final ECPublicKey       baseKey;
  private final IdentityKey       identityKey;
  private final WhisperMessage    message;
  private final byte[]            serialized;

  public PreKeyWhisperMessage(byte[] serialized)
      throws InvalidMessageException, InvalidVersionException
  {
    try {
      this.version = ByteUtil.highBitsToInt(serialized[0]);

      if (this.version > CiphertextMessage.CURRENT_VERSION) {
        throw new InvalidVersionException("Unknown version: " + this.version);
      }

      WhisperProtos.PreKeyWhisperMessage preKeyWhisperMessage
          = WhisperProtos.PreKeyWhisperMessage.parseFrom(ByteString.copyFrom(serialized, 1,
                                                                             serialized.length-1));

      if (!preKeyWhisperMessage.hasBaseKey()                           ||
          !preKeyWhisperMessage.hasIdentityKey()                       ||
          !preKeyWhisperMessage.hasMessage()                           ||
          !preKeyWhisperMessage.hasDeviceId())
      {
        throw new InvalidMessageException("Incomplete message.");
      }

      this.serialized     = serialized;
      this.deviceId       = preKeyWhisperMessage.getDeviceId();
      this.baseKey        = Curve.decodePoint(preKeyWhisperMessage.getBaseKey().toByteArray(), 0);
      this.identityKey    = new IdentityKey(Curve.decodePoint(preKeyWhisperMessage.getIdentityKey().toByteArray(), 0));
      this.message        = new WhisperMessage(preKeyWhisperMessage.getMessage().toByteArray());
    } catch (InvalidProtocolBufferException | InvalidKeyException | LegacyMessageException e) {
      throw new InvalidMessageException(e);
    }
  }

  public PreKeyWhisperMessage(int messageVersion, int deviceId, ECPublicKey baseKey,
                              IdentityKey identityKey, WhisperMessage message) {
    this.version        = messageVersion;
    this.deviceId       = deviceId;
    this.baseKey        = baseKey;
    this.identityKey    = identityKey;
    this.message        = message;

    WhisperProtos.PreKeyWhisperMessage.Builder builder =
        WhisperProtos.PreKeyWhisperMessage.newBuilder()
                                          .setBaseKey(ByteString.copyFrom(baseKey.serialize()))
                                          .setIdentityKey(ByteString.copyFrom(identityKey.serialize()))
                                          .setMessage(ByteString.copyFrom(message.serialize()))
                                          .setDeviceId(deviceId);

    byte[] versionBytes = {ByteUtil.intsToByteHighAndLow(this.version, CURRENT_VERSION)};
    byte[] messageBytes = builder.build().toByteArray();

    this.serialized = ByteUtil.combine(versionBytes, messageBytes);
  }

  public int getMessageVersion() {
    return version;
  }

  public IdentityKey getIdentityKey() {
    return identityKey;
  }

  public int getDeviceId() {
    return deviceId;
  }

  public ECPublicKey getBaseKey() {
    return baseKey;
  }

  public WhisperMessage getWhisperMessage() {
    return message;
  }

  @Override
  public byte[] serialize() {
    return serialized;
  }

  @Override
  public int getType() {
    return CiphertextMessage.PREKEY_TYPE;
  }

}
