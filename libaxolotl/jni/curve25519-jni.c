/**
 * Copyright (C) 2013-2014 Open Whisper Systems
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

#include <string.h>
#include <stdint.h>

#include <jni.h>
#include "curve25519-donna.h"
#include "curve_sigs.h"

JNIEXPORT jbyteArray JNICALL Java_org_whispersystems_libaxolotl_ecc_Curve25519_generatePrivateKey
  (JNIEnv *env, jclass clazz, jbyteArray random)
{
  uint8_t* privateKey = (uint8_t*)(*env)->GetByteArrayElements(env, random, 0);

  privateKey[0] &= 248;
  privateKey[31] &= 127;
  privateKey[31] |= 64;

  (*env)->ReleaseByteArrayElements(env, random, privateKey, 0);

  return random;
}

JNIEXPORT jbyteArray JNICALL Java_org_whispersystems_libaxolotl_ecc_Curve25519_generatePublicKey
  (JNIEnv *env, jclass clazz, jbyteArray privateKey)
{
    static const uint8_t  basepoint[32] = {9};

    jbyteArray publicKey       = (*env)->NewByteArray(env, 32);
    uint8_t*   publicKeyBytes  = (uint8_t*)(*env)->GetByteArrayElements(env, publicKey, 0);
    uint8_t*   privateKeyBytes = (uint8_t*)(*env)->GetByteArrayElements(env, privateKey, 0);

    curve25519_donna(publicKeyBytes, privateKeyBytes, basepoint);

    (*env)->ReleaseByteArrayElements(env, publicKey, publicKeyBytes, 0);
    (*env)->ReleaseByteArrayElements(env, privateKey, privateKeyBytes, 0);

    return publicKey;
}

JNIEXPORT jbyteArray JNICALL Java_org_whispersystems_libaxolotl_ecc_Curve25519_calculateAgreement
  (JNIEnv *env, jclass clazz, jbyteArray privateKey, jbyteArray publicKey)
{
    jbyteArray sharedKey       = (*env)->NewByteArray(env, 32);
    uint8_t*   sharedKeyBytes  = (uint8_t*)(*env)->GetByteArrayElements(env, sharedKey, 0);
    uint8_t*   privateKeyBytes = (uint8_t*)(*env)->GetByteArrayElements(env, privateKey, 0);
    uint8_t*   publicKeyBytes  = (uint8_t*)(*env)->GetByteArrayElements(env, publicKey, 0);

    curve25519_donna(sharedKeyBytes, privateKeyBytes, publicKeyBytes);

    (*env)->ReleaseByteArrayElements(env, sharedKey, sharedKeyBytes, 0);
    (*env)->ReleaseByteArrayElements(env, publicKey, publicKeyBytes, 0);
    (*env)->ReleaseByteArrayElements(env, privateKey, privateKeyBytes, 0);

    return sharedKey;
}

JNIEXPORT jbyteArray JNICALL Java_org_whispersystems_libaxolotl_ecc_Curve25519_calculateSignature
  (JNIEnv *env, jclass clazz, jbyteArray random, jbyteArray privateKey, jbyteArray message)
{
    jbyteArray signature       = (*env)->NewByteArray(env, 64);
    uint8_t*   signatureBytes  = (uint8_t*)(*env)->GetByteArrayElements(env, signature, 0);
    uint8_t*   randomBytes     = (uint8_t*)(*env)->GetByteArrayElements(env, random, 0);
    uint8_t*   privateKeyBytes = (uint8_t*)(*env)->GetByteArrayElements(env, privateKey, 0);
    uint8_t*   messageBytes    = (uint8_t*)(*env)->GetByteArrayElements(env, message, 0);
    jsize      messageLength   = (*env)->GetArrayLength(env, message);

    int result = curve25519_sign(signatureBytes, privateKeyBytes, messageBytes, messageLength, randomBytes);

    (*env)->ReleaseByteArrayElements(env, signature, signatureBytes, 0);
    (*env)->ReleaseByteArrayElements(env, random, randomBytes, 0);
    (*env)->ReleaseByteArrayElements(env, privateKey, privateKeyBytes, 0);
    (*env)->ReleaseByteArrayElements(env, message, messageBytes, 0);

    if (result == 0) return signature;
    else             (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/AssertionError"), "Signature failed!");
}

JNIEXPORT jboolean JNICALL Java_org_whispersystems_libaxolotl_ecc_Curve25519_verifySignature
  (JNIEnv *env, jclass clazz, jbyteArray publicKey, jbyteArray message, jbyteArray signature)
{
    uint8_t*   signatureBytes = (uint8_t*)(*env)->GetByteArrayElements(env, signature, 0);
    uint8_t*   publicKeyBytes = (uint8_t*)(*env)->GetByteArrayElements(env, publicKey, 0);
    uint8_t*   messageBytes   = (uint8_t*)(*env)->GetByteArrayElements(env, message, 0);
    jsize      messageLength  = (*env)->GetArrayLength(env, message);

    jboolean result = (curve25519_verify(signatureBytes, publicKeyBytes, messageBytes, messageLength) == 0);

    (*env)->ReleaseByteArrayElements(env, signature, signatureBytes, 0);
    (*env)->ReleaseByteArrayElements(env, publicKey, publicKeyBytes, 0);
    (*env)->ReleaseByteArrayElements(env, message, messageBytes, 0);

    return result;
}
