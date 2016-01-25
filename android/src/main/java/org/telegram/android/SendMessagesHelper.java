/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.android;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import org.telegram.android.audioinfo.AudioInfo;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import xyz.securegram.R;
import xyz.securegram.axolotl.AxolotlController;

import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;

public class SendMessagesHelper implements NotificationCenter.NotificationCenterDelegate {

  private TLRPC.ChatParticipants currentChatInfo = null;
  private HashMap<String, ArrayList<DelayedMessage>> delayedMessages = new HashMap<>();
  private HashMap<Integer, MessageObject> unsentMessages = new HashMap<>();
  private HashMap<Integer, TLRPC.Message> sendingMessages = new HashMap<>();

  protected class DelayedMessage {
    public TLObject sendRequest;
    public TLRPC.TL_decryptedMessage sendEncryptedRequest;
    public int type;
    public String originalPath;
    public TLRPC.FileLocation location;
    public TLRPC.TL_video videoLocation;
    public TLRPC.TL_audio audioLocation;
    public TLRPC.TL_document documentLocation;
    public String httpLocation;
    public MessageObject obj;
    public TLRPC.EncryptedChat encryptedChat;
    public VideoEditedInfo videoEditedInfo;
  }

  private static volatile SendMessagesHelper Instance = null;

  public static SendMessagesHelper getInstance() {
    SendMessagesHelper localInstance = Instance;
    if (localInstance == null) {
      synchronized (SendMessagesHelper.class) {
        localInstance = Instance;
        if (localInstance == null) {
          Instance = localInstance = new SendMessagesHelper();
        }
      }
    }
    return localInstance;
  }

  public SendMessagesHelper() {
    NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileDidUpload);
    NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileDidFailUpload);
    NotificationCenter.getInstance().addObserver(this, NotificationCenter.FilePreparingStarted);
    NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileNewChunkAvailable);
    NotificationCenter.getInstance().addObserver(this, NotificationCenter.FilePreparingFailed);
    NotificationCenter.getInstance().addObserver(this, NotificationCenter.httpFileDidFailedLoad);
    NotificationCenter.getInstance().addObserver(this, NotificationCenter.httpFileDidLoaded);
  }

  public void cleanUp() {
    delayedMessages.clear();
    unsentMessages.clear();
    sendingMessages.clear();
    currentChatInfo = null;
  }

  public void setCurrentChatInfo(TLRPC.ChatParticipants info) {
    currentChatInfo = info;
  }

  @Override
  public void didReceivedNotification(int id, final Object... args) {
    if (id == NotificationCenter.FileDidUpload) {
      final String location = (String) args[0];
      final TLRPC.InputFile file = (TLRPC.InputFile) args[1];
      final TLRPC.InputEncryptedFile encryptedFile = (TLRPC.InputEncryptedFile) args[2];
      ArrayList<DelayedMessage> arr = delayedMessages.get(location);
      if (arr != null) {
        for (int a = 0; a < arr.size(); a++) {
          DelayedMessage message = arr.get(a);
          TLRPC.InputMedia media = null;
          if (message.sendRequest instanceof TLRPC.TL_messages_sendMedia) {
            media = ((TLRPC.TL_messages_sendMedia) message.sendRequest).media;
          } else if (message.sendRequest instanceof TLRPC.TL_messages_sendBroadcast) {
            media = ((TLRPC.TL_messages_sendBroadcast) message.sendRequest).media;
          }

          if (file != null && media != null) {
            if (message.type == 0) {
              media.file = file;
              performSendMessageRequest(
                  message.sendRequest, message.obj.messageOwner, message.originalPath);
            } else if (message.type == 1) {
              if (media.file == null) {
                media.file = file;
                if (media.thumb == null && message.location != null) {
                  performSendDelayedMessage(message);
                } else {
                  performSendMessageRequest(
                      message.sendRequest, message.obj.messageOwner, message.originalPath);
                }
              } else {
                media.thumb = file;
                performSendMessageRequest(
                    message.sendRequest, message.obj.messageOwner, message.originalPath);
              }
            } else if (message.type == 2) {
              if (media.file == null) {
                media.file = file;
                if (media.thumb == null && message.location != null) {
                  performSendDelayedMessage(message);
                } else {
                  performSendMessageRequest(
                      message.sendRequest, message.obj.messageOwner, message.originalPath);
                }
              } else {
                media.thumb = file;
                performSendMessageRequest(
                    message.sendRequest, message.obj.messageOwner, message.originalPath);
              }
            } else if (message.type == 3) {
              media.file = file;
              performSendMessageRequest(
                  message.sendRequest, message.obj.messageOwner, message.originalPath);
            }
            arr.remove(a);
            a--;
          } else if (encryptedFile != null && message.sendEncryptedRequest != null) {
            message.sendEncryptedRequest.media.key = (byte[]) args[3];
            message.sendEncryptedRequest.media.iv = (byte[]) args[4];
            SecretChatHelper.getInstance()
                .performSendEncryptedRequest(
                    message.sendEncryptedRequest,
                    message.obj.messageOwner,
                    message.encryptedChat,
                    encryptedFile,
                    message.originalPath);
            arr.remove(a);
            a--;
          }
        }
        if (arr.isEmpty()) {
          delayedMessages.remove(location);
        }
      }
    } else if (id == NotificationCenter.FileDidFailUpload) {
      final String location = (String) args[0];
      final boolean enc = (Boolean) args[1];
      ArrayList<DelayedMessage> arr = delayedMessages.get(location);
      if (arr != null) {
        for (int a = 0; a < arr.size(); a++) {
          DelayedMessage obj = arr.get(a);
          if (enc && obj.sendEncryptedRequest != null || !enc && obj.sendRequest != null) {
            MessagesStorage.getInstance().markMessageAsSendError(obj.obj.getId());
            obj.obj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
            arr.remove(a);
            a--;
            NotificationCenter.getInstance()
                .postNotificationName(NotificationCenter.messageSendError, obj.obj.getId());
            processSentMessage(obj.obj.getId());
          }
        }
        if (arr.isEmpty()) {
          delayedMessages.remove(location);
        }
      }
    } else if (id == NotificationCenter.FilePreparingStarted) {
      MessageObject messageObject = (MessageObject) args[0];
      String finalPath = (String) args[1];

      ArrayList<DelayedMessage> arr = delayedMessages.get(messageObject.messageOwner.attachPath);
      if (arr != null) {
        for (int a = 0; a < arr.size(); a++) {
          DelayedMessage message = arr.get(a);
          if (message.obj == messageObject) {
            message.videoEditedInfo = null;
            performSendDelayedMessage(message);
            arr.remove(a);
            break;
          }
        }
        if (arr.isEmpty()) {
          delayedMessages.remove(messageObject.messageOwner.attachPath);
        }
      }
    } else if (id == NotificationCenter.FileNewChunkAvailable) {
      MessageObject messageObject = (MessageObject) args[0];
      String finalPath = (String) args[1];
      long finalSize = (Long) args[2];
      boolean isEncrypted = ((int) messageObject.getDialogId()) == 0;
      FileLoader.getInstance().checkUploadNewDataAvailable(finalPath, isEncrypted, finalSize);
      if (finalSize != 0) {
        ArrayList<DelayedMessage> arr = delayedMessages.get(messageObject.messageOwner.attachPath);
        if (arr != null) {
          for (DelayedMessage message : arr) {
            if (message.obj == messageObject) {
              message.obj.videoEditedInfo = null;
              message.obj.messageOwner.message = "-1";
              message.obj.messageOwner.media.video.size = (int) finalSize;

              ArrayList<TLRPC.Message> messages = new ArrayList<>();
              messages.add(message.obj.messageOwner);
              MessagesStorage.getInstance().putMessages(messages, false, true, false, 0);
              break;
            }
          }
          if (arr.isEmpty()) {
            delayedMessages.remove(messageObject.messageOwner.attachPath);
          }
        }
      }
    } else if (id == NotificationCenter.FilePreparingFailed) {
      MessageObject messageObject = (MessageObject) args[0];
      String finalPath = (String) args[1];
      stopVideoService(messageObject.messageOwner.attachPath);

      ArrayList<DelayedMessage> arr = delayedMessages.get(finalPath);
      if (arr != null) {
        for (int a = 0; a < arr.size(); a++) {
          DelayedMessage message = arr.get(a);
          if (message.obj == messageObject) {
            MessagesStorage.getInstance().markMessageAsSendError(message.obj.getId());
            message.obj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
            arr.remove(a);
            a--;
            NotificationCenter.getInstance()
                .postNotificationName(NotificationCenter.messageSendError, message.obj.getId());
            processSentMessage(message.obj.getId());
          }
        }
        if (arr.isEmpty()) {
          delayedMessages.remove(finalPath);
        }
      }
    } else if (id == NotificationCenter.httpFileDidLoaded) {
      String path = (String) args[0];
      String file = (String) args[1];
      ArrayList<DelayedMessage> arr = delayedMessages.get(path);
      if (arr != null) {
        for (final DelayedMessage message : arr) {
          if (message.type == 0) {
            String md5 = Utilities.MD5(message.httpLocation) + ".jpg";
            final File cacheFile =
                new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), md5);
            Utilities.globalQueue.postRunnable(
                new Runnable() {
                  @Override
                  public void run() {
                    final TLRPC.TL_photo photo =
                        SendMessagesHelper.getInstance()
                            .generatePhotoSizes(cacheFile.toString(), null);
                    AndroidUtilities.runOnUIThread(
                        new Runnable() {
                          @Override
                          public void run() {
                            if (photo != null) {
                              message.httpLocation = null;
                              message.obj.messageOwner.media.photo = photo;
                              message.obj.messageOwner.attachPath = cacheFile.toString();
                              message.location = photo.sizes.get(photo.sizes.size() - 1).location;
                              ArrayList<TLRPC.Message> messages = new ArrayList<>();
                              messages.add(message.obj.messageOwner);
                              MessagesStorage.getInstance()
                                  .putMessages(messages, false, true, false, 0);
                              performSendDelayedMessage(message);
                              NotificationCenter.getInstance()
                                  .postNotificationName(
                                      NotificationCenter.updateMessageMedia, message.obj);
                            } else {
                              FileLog.e(
                                  "tmessages",
                                  "can't load image "
                                      + message.httpLocation
                                      + " to file "
                                      + cacheFile.toString());
                              MessagesStorage.getInstance()
                                  .markMessageAsSendError(message.obj.getId());
                              message.obj.messageOwner.send_state =
                                  MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                              NotificationCenter.getInstance()
                                  .postNotificationName(
                                      NotificationCenter.messageSendError, message.obj.getId());
                              processSentMessage(message.obj.getId());
                            }
                          }
                        });
                  }
                });
          } else if (message.type == 2) {
            String md5 = Utilities.MD5(message.httpLocation) + ".gif";
            final File cacheFile =
                new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), md5);
            Utilities.globalQueue.postRunnable(
                new Runnable() {
                  @Override
                  public void run() {
                    if (message.documentLocation.thumb.location
                        instanceof TLRPC.TL_fileLocationUnavailable) {
                      try {
                        Bitmap bitmap =
                            ImageLoader.loadBitmap(cacheFile.getAbsolutePath(), null, 90, 90, true);
                        if (bitmap != null) {
                          message.documentLocation.thumb =
                              ImageLoader.scaleAndSaveImage(
                                  bitmap, 90, 90, 55, message.sendEncryptedRequest != null);
                        }
                      } catch (Exception e) {
                        message.documentLocation.thumb = null;
                        FileLog.e("tmessages", e);
                      }
                      if (message.documentLocation.thumb == null) {
                        message.documentLocation.thumb = new TLRPC.TL_photoSizeEmpty();
                        message.documentLocation.thumb.type = "s";
                      }
                    }
                    AndroidUtilities.runOnUIThread(
                        new Runnable() {
                          @Override
                          public void run() {
                            message.httpLocation = null;
                            message.obj.messageOwner.attachPath = cacheFile.toString();
                            message.location = message.documentLocation.thumb.location;
                            ArrayList<TLRPC.Message> messages = new ArrayList<>();
                            messages.add(message.obj.messageOwner);
                            MessagesStorage.getInstance()
                                .putMessages(messages, false, true, false, 0);
                            performSendDelayedMessage(message);
                            NotificationCenter.getInstance()
                                .postNotificationName(
                                    NotificationCenter.updateMessageMedia, message.obj);
                          }
                        });
                  }
                });
          }
        }
        delayedMessages.remove(path);
      }
    } else if (id == NotificationCenter.httpFileDidFailedLoad) {
      String path = (String) args[0];

      ArrayList<DelayedMessage> arr = delayedMessages.get(path);
      if (arr != null) {
        for (DelayedMessage message : arr) {
          MessagesStorage.getInstance().markMessageAsSendError(message.obj.getId());
          message.obj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
          NotificationCenter.getInstance()
              .postNotificationName(NotificationCenter.messageSendError, message.obj.getId());
          processSentMessage(message.obj.getId());
        }
        delayedMessages.remove(path);
      }
    }
  }

  public void cancelSendingMessage(MessageObject object) {
    String keyToRemvoe = null;
    boolean enc = false;
    for (HashMap.Entry<String, ArrayList<DelayedMessage>> entry : delayedMessages.entrySet()) {
      ArrayList<DelayedMessage> messages = entry.getValue();
      for (int a = 0; a < messages.size(); a++) {
        DelayedMessage message = messages.get(a);
        if (message.obj.getId() == object.getId()) {
          messages.remove(a);
          MediaController.getInstance().cancelVideoConvert(message.obj);
          if (messages.size() == 0) {
            keyToRemvoe = entry.getKey();
            if (message.sendEncryptedRequest != null) {
              enc = true;
            }
          }
          break;
        }
      }
    }
    if (keyToRemvoe != null) {
      if (keyToRemvoe.startsWith("http")) {
        ImageLoader.getInstance().cancelLoadHttpFile(keyToRemvoe);
      } else {
        FileLoader.getInstance().cancelUploadFile(keyToRemvoe, enc);
      }
      stopVideoService(keyToRemvoe);
    }
    ArrayList<Integer> messages = new ArrayList<>();
    messages.add(object.getId());
    MessagesController.getInstance().deleteMessages(messages, null, null);
  }

  public boolean retrySendMessage(MessageObject messageObject, boolean unsent) {
    if (messageObject.getId() >= 0) {
      return false;
    }
    if (messageObject.messageOwner.action instanceof TLRPC.TL_messageEncryptedAction) {
      int enc_id = (int) (messageObject.getDialogId() >> 32);
      TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance().getEncryptedChat(enc_id);
      if (encryptedChat == null) {
        MessagesStorage.getInstance().markMessageAsSendError(messageObject.getId());
        messageObject.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
        NotificationCenter.getInstance()
            .postNotificationName(NotificationCenter.messageSendError, messageObject.getId());
        processSentMessage(messageObject.getId());
        return false;
      }
      if (messageObject.messageOwner.random_id == 0) {
        messageObject.messageOwner.random_id = getNextRandomId();
      }
      if (messageObject.messageOwner.action.encryptedAction
          instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL) {
        SecretChatHelper.getInstance().sendTTLMessage(encryptedChat, messageObject.messageOwner);
      } else if (messageObject.messageOwner.action.encryptedAction
          instanceof TLRPC.TL_decryptedMessageActionDeleteMessages) {
        SecretChatHelper.getInstance()
            .sendMessagesDeleteMessage(encryptedChat, null, messageObject.messageOwner);
      } else if (messageObject.messageOwner.action.encryptedAction
          instanceof TLRPC.TL_decryptedMessageActionFlushHistory) {
        SecretChatHelper.getInstance()
            .sendClearHistoryMessage(encryptedChat, messageObject.messageOwner);
      } else if (messageObject.messageOwner.action.encryptedAction
          instanceof TLRPC.TL_decryptedMessageActionNotifyLayer) {
        SecretChatHelper.getInstance()
            .sendNotifyLayerMessage(encryptedChat, messageObject.messageOwner);
      } else if (messageObject.messageOwner.action.encryptedAction
          instanceof TLRPC.TL_decryptedMessageActionReadMessages) {
        SecretChatHelper.getInstance()
            .sendMessagesReadMessage(encryptedChat, null, messageObject.messageOwner);
      } else if (messageObject.messageOwner.action.encryptedAction
          instanceof TLRPC.TL_decryptedMessageActionScreenshotMessages) {
        SecretChatHelper.getInstance()
            .sendScreenshotMessage(encryptedChat, null, messageObject.messageOwner);
      } else if (messageObject.messageOwner.action.encryptedAction
          instanceof TLRPC.TL_decryptedMessageActionTyping) {

      } else if (messageObject.messageOwner.action.encryptedAction
          instanceof TLRPC.TL_decryptedMessageActionResend) {

      } else if (messageObject.messageOwner.action.encryptedAction
          instanceof TLRPC.TL_decryptedMessageActionCommitKey) {
        SecretChatHelper.getInstance()
            .sendCommitKeyMessage(encryptedChat, messageObject.messageOwner);
      } else if (messageObject.messageOwner.action.encryptedAction
          instanceof TLRPC.TL_decryptedMessageActionAbortKey) {
        SecretChatHelper.getInstance()
            .sendAbortKeyMessage(encryptedChat, messageObject.messageOwner, 0);
      } else if (messageObject.messageOwner.action.encryptedAction
          instanceof TLRPC.TL_decryptedMessageActionRequestKey) {
        SecretChatHelper.getInstance()
            .sendRequestKeyMessage(encryptedChat, messageObject.messageOwner);
      } else if (messageObject.messageOwner.action.encryptedAction
          instanceof TLRPC.TL_decryptedMessageActionAcceptKey) {
        SecretChatHelper.getInstance()
            .sendAcceptKeyMessage(encryptedChat, messageObject.messageOwner);
      } else if (messageObject.messageOwner.action.encryptedAction
          instanceof TLRPC.TL_decryptedMessageActionNoop) {
        SecretChatHelper.getInstance().sendNoopMessage(encryptedChat, messageObject.messageOwner);
      }
      return true;
    }
    if (unsent) {
      unsentMessages.put(messageObject.getId(), messageObject);
    }
    sendMessage(messageObject);
    return true;
  }

  protected void processSentMessage(int id) {
    int prevSize = unsentMessages.size();
    unsentMessages.remove(id);
    if (prevSize != 0 && unsentMessages.size() == 0) {
      checkUnsentMessages();
    }
  }

  public void processForwardFromMyName(MessageObject messageObject, long did) {
    if (messageObject == null) {
      return;
    }
    if (messageObject.messageOwner.media != null
        && !(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaEmpty)
        && !(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage)) {
      if (messageObject.messageOwner.media.photo instanceof TLRPC.TL_photo) {
        sendMessage(
            (TLRPC.TL_photo) messageObject.messageOwner.media.photo,
            null,
            null,
            did,
            messageObject.replyMessageObject);
      } else if (messageObject.messageOwner.media.audio instanceof TLRPC.TL_audio) {
        sendMessage(
            (TLRPC.TL_audio) messageObject.messageOwner.media.audio,
            messageObject.messageOwner.attachPath,
            did,
            messageObject.replyMessageObject);
      } else if (messageObject.messageOwner.media.video instanceof TLRPC.TL_video) {
        TLRPC.TL_video video = (TLRPC.TL_video) messageObject.messageOwner.media.video;
        sendMessage(
            video,
            messageObject.videoEditedInfo,
            null,
            messageObject.messageOwner.attachPath,
            did,
            messageObject.replyMessageObject);
      } else if (messageObject.messageOwner.media.document instanceof TLRPC.TL_document) {
        sendMessage(
            (TLRPC.TL_document) messageObject.messageOwner.media.document,
            null,
            messageObject.messageOwner.attachPath,
            did,
            messageObject.replyMessageObject);
      } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaVenue
          || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeo) {
        sendMessage(messageObject.messageOwner.media, did, messageObject.replyMessageObject);
      } else if (messageObject.messageOwner.media.phone_number != null) {
        TLRPC.User user = new TLRPC.TL_userContact_old2();
        user.phone = messageObject.messageOwner.media.phone_number;
        user.first_name = messageObject.messageOwner.media.first_name;
        user.last_name = messageObject.messageOwner.media.last_name;
        user.id = messageObject.messageOwner.media.user_id;
        sendMessage(user, did, messageObject.replyMessageObject);
      } else {
        sendMessage(messageObject, did);
      }
    } else if (messageObject.messageOwner.message != null) {
      TLRPC.WebPage webPage = null;
      if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) {
        webPage = messageObject.messageOwner.media.webpage;
      }
      sendMessage(
          messageObject.messageOwner.message, did, messageObject.replyMessageObject, webPage, true);
    } else {
      sendMessage(messageObject, did);
    }
  }

  public void sendSticker(TLRPC.Document document, long peer, MessageObject replyingMessageObject) {
    if (document == null) {
      return;
    }
    if (((int) peer) == 0 && document.thumb instanceof TLRPC.TL_photoSize) {
      File file = FileLoader.getPathToAttach(document.thumb, true);
      if (file.exists()) {
        try {
          int len = (int) file.length();
          byte[] arr = new byte[(int) file.length()];
          RandomAccessFile reader = new RandomAccessFile(file, "r");
          reader.readFully(arr);
          TLRPC.TL_document newDocument = new TLRPC.TL_document();
          newDocument.thumb = new TLRPC.TL_photoCachedSize();
          newDocument.thumb.location = document.thumb.location;
          newDocument.thumb.size = document.thumb.size;
          newDocument.thumb.w = document.thumb.w;
          newDocument.thumb.h = document.thumb.h;
          newDocument.thumb.type = document.thumb.type;
          newDocument.thumb.bytes = arr;

          newDocument.id = document.id;
          newDocument.access_hash = document.access_hash;
          newDocument.date = document.date;
          newDocument.mime_type = document.mime_type;
          newDocument.size = document.size;
          newDocument.dc_id = document.dc_id;
          newDocument.attributes = document.attributes;
          document = newDocument;
        } catch (Exception e) {
          FileLog.e("tmessages", e);
        }
      }
    }
    if ((int) peer == 0) {
      for (int a = 0; a < document.attributes.size(); a++) {
        TLRPC.DocumentAttribute attribute = document.attributes.get(a);
        if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
          document.attributes.remove(a);
          document.attributes.add(new TLRPC.TL_documentAttributeSticker_old());
          break;
        }
      }
    }
    SendMessagesHelper.getInstance()
        .sendMessage((TLRPC.TL_document) document, null, null, peer, replyingMessageObject);
  }

  public void sendMessage(TLRPC.User user, long peer, MessageObject reply_to_msg) {
    sendMessage(
        null,
        null,
        null,
        null,
        null,
        null,
        user,
        null,
        null,
        null,
        peer,
        false,
        null,
        reply_to_msg,
        null,
        true);
  }

  public void sendMessage(ArrayList<MessageObject> messages, long peer) {
    if ((int) peer == 0 || messages == null || messages.isEmpty()) {
      return;
    }
    int lower_id = (int) peer;
    TLRPC.Peer to_id;
    TLRPC.InputPeer sendToPeer;
    if (lower_id < 0) {
      to_id = new TLRPC.TL_peerChat();
      to_id.chat_id = -lower_id;
      sendToPeer = new TLRPC.TL_inputPeerChat();
      sendToPeer.chat_id = -lower_id;
    } else {
      to_id = new TLRPC.TL_peerUser();
      to_id.user_id = lower_id;
      TLRPC.User sendToUser = MessagesController.getInstance().getUser(lower_id);
      if (sendToUser == null) {
        return;
      }
      if (sendToUser.access_hash != 0) {
        sendToPeer = new TLRPC.TL_inputPeerForeign();
        sendToPeer.user_id = sendToUser.id;
        sendToPeer.access_hash = sendToUser.access_hash;
      } else {
        sendToPeer = new TLRPC.TL_inputPeerContact();
        sendToPeer.user_id = sendToUser.id;
      }
    }

    ArrayList<MessageObject> objArr = new ArrayList<>();
    ArrayList<TLRPC.Message> arr = new ArrayList<>();
    ArrayList<Long> randomIds = new ArrayList<>();
    ArrayList<Integer> ids = new ArrayList<>();
    HashMap<Long, TLRPC.Message> messagesByRandomIds = new HashMap<>();

    for (int a = 0; a < messages.size(); a++) {
      MessageObject msgObj = messages.get(a);
      if (msgObj.getId() <= 0) {
        continue;
      }

      final TLRPC.Message newMsg = new TLRPC.TL_message();
      newMsg.flags |= TLRPC.MESSAGE_FLAG_FWD;
      if (msgObj.isForwarded()) {
        newMsg.fwd_from_id = msgObj.messageOwner.fwd_from_id;
        newMsg.fwd_date = msgObj.messageOwner.fwd_date;
      } else {
        newMsg.fwd_from_id = msgObj.messageOwner.from_id;
        newMsg.fwd_date = msgObj.messageOwner.date;
      }
      newMsg.media = msgObj.messageOwner.media;
      newMsg.message = msgObj.messageOwner.message;
      newMsg.fwd_msg_id = msgObj.getId();
      newMsg.attachPath = msgObj.messageOwner.attachPath;
      if (newMsg.attachPath == null) {
        newMsg.attachPath = "";
      }
      newMsg.local_id = newMsg.id = UserConfig.getNewMessageId();
      newMsg.from_id = UserConfig.getClientUserId();
      newMsg.flags |= TLRPC.MESSAGE_FLAG_OUT;
      if (newMsg.random_id == 0) {
        newMsg.random_id = getNextRandomId();
      }
      randomIds.add(newMsg.random_id);
      messagesByRandomIds.put(newMsg.random_id, newMsg);
      ids.add(newMsg.fwd_msg_id);
      newMsg.date = ConnectionsManager.getInstance().getCurrentTime();
      newMsg.flags |= TLRPC.MESSAGE_FLAG_UNREAD;
      if (newMsg.media instanceof TLRPC.TL_messageMediaAudio) {
        newMsg.flags |= TLRPC.MESSAGE_FLAG_CONTENT_UNREAD;
      }
      newMsg.dialog_id = peer;
      newMsg.to_id = to_id;
      MessageObject newMsgObj = new MessageObject(newMsg, null, true);
      newMsgObj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENDING;
      objArr.add(newMsgObj);
      arr.add(newMsg);

      putToSendingMessages(newMsg);

      if (arr.size() == 100 || a == messages.size() - 1) {
        MessagesStorage.getInstance().putMessages(new ArrayList<>(arr), false, true, false, 0);
        MessagesController.getInstance().updateInterfaceWithMessages(peer, objArr);
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
        UserConfig.saveConfig(false);

        TLRPC.TL_messages_forwardMessages req = new TLRPC.TL_messages_forwardMessages();
        req.peer = sendToPeer;
        req.random_id = randomIds;
        req.id = ids;

        final ArrayList<TLRPC.Message> newMsgObjArr = arr;
        final HashMap<Long, TLRPC.Message> messagesByRandomIdsFinal = messagesByRandomIds;
        ConnectionsManager.getInstance()
            .performRpc(
                req,
                new RPCRequest.RPCRequestDelegate() {
                  @Override
                  public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                      HashMap<Integer, Long> newMessagesByIds = new HashMap<>();
                      TLRPC.Updates updates = (TLRPC.Updates) response;
                      for (int a = 0; a < updates.updates.size(); a++) {
                        TLRPC.Update update = updates.updates.get(a);
                        if (update instanceof TLRPC.TL_updateMessageID) {
                          newMessagesByIds.put(update.id, update.random_id);
                          updates.updates.remove(a);
                          a--;
                        }
                      }
                      for (TLRPC.Update update : updates.updates) {
                        if (update instanceof TLRPC.TL_updateNewMessage) {
                          MessagesController.getInstance()
                              .processNewDifferenceParams(-1, update.pts, -1, update.pts_count);
                          TLRPC.Message message = ((TLRPC.TL_updateNewMessage) update).message;
                          Long random_id = newMessagesByIds.get(message.id);
                          if (random_id != null) {
                            final TLRPC.Message newMsgObj = messagesByRandomIdsFinal.get(random_id);
                            if (newMsgObj == null) {
                              continue;
                            }
                            newMsgObjArr.remove(newMsgObj);
                            final int oldId = newMsgObj.id;
                            final ArrayList<TLRPC.Message> sentMessages = new ArrayList<>();
                            sentMessages.add(message);
                            newMsgObj.id = message.id;
                            processSentMessage(newMsgObj, message, null);
                            MessagesStorage.getInstance()
                                .getStorageQueue()
                                .postRunnable(
                                    new Runnable() {
                                      @Override
                                      public void run() {
                                        MessagesStorage.getInstance()
                                            .updateMessageStateAndId(
                                                newMsgObj.random_id, oldId, newMsgObj.id, 0, false);
                                        MessagesStorage.getInstance()
                                            .putMessages(sentMessages, true, false, false, 0);
                                        AndroidUtilities.runOnUIThread(
                                            new Runnable() {
                                              @Override
                                              public void run() {
                                                newMsgObj.send_state =
                                                    MessageObject.MESSAGE_SEND_STATE_SENT;
                                                NotificationCenter.getInstance()
                                                    .postNotificationName(
                                                        NotificationCenter.messageReceivedByServer,
                                                        oldId,
                                                        newMsgObj.id,
                                                        newMsgObj,
                                                        false);
                                                processSentMessage(oldId);
                                                removeFromSendingMessages(oldId);
                                              }
                                            });
                                        if (newMsgObj.media instanceof TLRPC.TL_messageMediaVideo) {
                                          stopVideoService(newMsgObj.attachPath);
                                        }
                                      }
                                    });
                          }
                        }
                      }
                    }
                    for (final TLRPC.Message newMsgObj : newMsgObjArr) {
                      MessagesStorage.getInstance().markMessageAsSendError(newMsgObj.id);
                      AndroidUtilities.runOnUIThread(
                          new Runnable() {
                            @Override
                            public void run() {
                              newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                              NotificationCenter.getInstance()
                                  .postNotificationName(
                                      NotificationCenter.messageSendError, newMsgObj.id);
                              processSentMessage(newMsgObj.id);
                              if (newMsgObj.media instanceof TLRPC.TL_messageMediaVideo) {
                                stopVideoService(newMsgObj.attachPath);
                              }
                              removeFromSendingMessages(newMsgObj.id);
                            }
                          });
                    }
                  }
                },
                null,
                true,
                RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassCanCompress,
                ConnectionsManager.DEFAULT_DATACENTER_ID);

        if (a != messages.size() - 1) {
          objArr = new ArrayList<>();
          arr = new ArrayList<>();
          randomIds = new ArrayList<>();
          ids = new ArrayList<>();
          messagesByRandomIds = new HashMap<>();
        }
      }
    }
  }

  public void sendMessage(MessageObject message) {
    sendMessage(
        null,
        null,
        null,
        null,
        null,
        message,
        null,
        null,
        null,
        null,
        message.getDialogId(),
        true,
        message.messageOwner.attachPath,
        null,
        null,
        true);
  }

  public void sendMessage(MessageObject message, long peer) {
    sendMessage(
        null,
        null,
        null,
        null,
        null,
        message,
        null,
        null,
        null,
        null,
        peer,
        false,
        message.messageOwner.attachPath,
        null,
        null,
        true);
  }

  public void sendMessage(
      TLRPC.TL_document document,
      String originalPath,
      String path,
      long peer,
      MessageObject reply_to_msg) {
    sendMessage(
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        document,
        null,
        originalPath,
        peer,
        false,
        path,
        reply_to_msg,
        null,
        true);
  }

  public void sendMessage(
      String message,
      long peer,
      MessageObject reply_to_msg,
      TLRPC.WebPage webPage,
      boolean searchLinks) {
    sendMessage(
        message,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        peer,
        false,
        null,
        reply_to_msg,
        webPage,
        searchLinks);
  }

  public void sendMessage(TLRPC.MessageMedia location, long peer, MessageObject reply_to_msg) {
    sendMessage(
        null,
        location,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        peer,
        false,
        null,
        reply_to_msg,
        null,
        true);
  }

  public void sendMessage(
      TLRPC.TL_photo photo,
      String originalPath,
      String path,
      long peer,
      MessageObject reply_to_msg) {
    sendMessage(
        null,
        null,
        photo,
        null,
        null,
        null,
        null,
        null,
        null,
        originalPath,
        peer,
        false,
        path,
        reply_to_msg,
        null,
        true);
  }

  public void sendMessage(
      TLRPC.TL_video video,
      VideoEditedInfo videoEditedInfo,
      String originalPath,
      String path,
      long peer,
      MessageObject reply_to_msg) {
    sendMessage(
        null,
        null,
        null,
        video,
        videoEditedInfo,
        null,
        null,
        null,
        null,
        originalPath,
        peer,
        false,
        path,
        reply_to_msg,
        null,
        true);
  }

  public void sendMessage(
      TLRPC.TL_audio audio, String path, long peer, MessageObject reply_to_msg) {
    sendMessage(
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        audio,
        null,
        peer,
        false,
        path,
        reply_to_msg,
        null,
        true);
  }

  private void sendMessage(
      String message,
      TLRPC.MessageMedia location,
      TLRPC.TL_photo photo,
      TLRPC.TL_video video,
      VideoEditedInfo videoEditedInfo,
      MessageObject msgObj,
      TLRPC.User user,
      TLRPC.TL_document document,
      TLRPC.TL_audio audio,
      String originalPath,
      long peer,
      boolean retry,
      String path,
      MessageObject reply_to_msg,
      TLRPC.WebPage webPage,
      boolean searchLinks) {
    if (peer == 0) {
      return;
    }

    TLRPC.Message newMsg;
    MessageObject.Type type = MessageObject.Type.INVALID;
    int lower_id = (int) peer;
    int high_id = (int) (peer >> 32);
    TLRPC.EncryptedChat encryptedChat = null;

    if (lower_id == 0) {
      // encrypted
      encryptedChat = MessagesController.getInstance().getEncryptedChat(high_id);
      if (encryptedChat == null) {
        if (msgObj != null) {
          MessagesStorage.getInstance().markMessageAsSendError(msgObj.getId());
          msgObj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
          NotificationCenter.getInstance()
              .postNotificationName(NotificationCenter.messageSendError, msgObj.getId());
          processSentMessage(msgObj.getId());
        }
        return;
      }
    }

    if (retry) {
      newMsg = msgObj.messageOwner;
      type = msgObj.type;

      if (msgObj.type == MessageObject.Type.TEXT) {
        if (msgObj.isForwarded()) {
          type = MessageObject.Type.FORWARDED;
        } else {
          message = newMsg.message;
        }
      } else if (msgObj.type == MessageObject.Type.LOCATION) {
        location = newMsg.media;
      } else if (msgObj.type == MessageObject.Type.PHOTO) {
        if (msgObj.isForwarded()) {
          type = MessageObject.Type.FORWARDED;
        } else {
          photo = (TLRPC.TL_photo) newMsg.media.photo;
        }
      } else if (msgObj.type == MessageObject.Type.VIDEO) {
        if (msgObj.isForwarded()) {
          type = MessageObject.Type.FORWARDED;;
        } else {
          video = (TLRPC.TL_video) newMsg.media.video;
        }
      } else if (type == MessageObject.Type.CONTACT) {
        user = new TLRPC.TL_userRequest_old2();
        user.phone = newMsg.media.phone_number;
        user.first_name = newMsg.media.first_name;
        user.last_name = newMsg.media.last_name;
        user.id = newMsg.media.user_id;
      } else if (msgObj.type == MessageObject.Type.DOC_GIF ||
          msgObj.type == MessageObject.Type.DOC_GENERIC ||
          msgObj.type == MessageObject.Type.DOC_STICKER_WEBP) {
        document = (TLRPC.TL_document) newMsg.media.document;
        type = MessageObject.Type.DOC;
      } else if (msgObj.type == MessageObject.Type.AUDIO) {
        audio = (TLRPC.TL_audio) newMsg.media.audio;
      }
    } else {
      if (encryptedChat != null
          && AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
        newMsg = new TLRPC.TL_message_secret();
      } else {
        newMsg = new TLRPC.TL_message();
      }

      if (message != null) {
        if (webPage != null) {
          newMsg.media = new TLRPC.TL_messageMediaWebPage();
          newMsg.media.webpage = webPage;
        }
        newMsg.message = message;
        type = MessageObject.Type.TEXT;
      } else if (location != null) {
        newMsg.media = location;
        newMsg.message = "";
        type = MessageObject.Type.LOCATION;
      } else if (photo != null) {
        newMsg.media = new TLRPC.TL_messageMediaPhoto();
        newMsg.media.caption = photo.caption != null ? photo.caption : "";
        newMsg.media.photo = photo;
        newMsg.message = "-1";
        if (path != null && path.length() > 0 && path.startsWith("http")) {
          newMsg.attachPath = path;
        } else {
          TLRPC.FileLocation location1 = photo.sizes.get(photo.sizes.size() - 1).location;
          newMsg.attachPath = FileLoader.getPathToAttach(location1, true).toString();
        }
        type = MessageObject.Type.PHOTO;
      } else if (video != null) {
        newMsg.media = new TLRPC.TL_messageMediaVideo();
        newMsg.media.caption = video.caption != null ? video.caption : "";
        newMsg.media.video = video;
        if (videoEditedInfo == null) {
          newMsg.message = "-1";
        } else {
          newMsg.message = videoEditedInfo.getString();
        }
        newMsg.attachPath = path;
        type = MessageObject.Type.VIDEO;
      } else if (msgObj != null) {
        newMsg = new TLRPC.TL_message();
        newMsg.flags |= TLRPC.MESSAGE_FLAG_FWD;
        if (msgObj.isForwarded()) {
          newMsg.fwd_from_id = msgObj.messageOwner.fwd_from_id;
          newMsg.fwd_date = msgObj.messageOwner.fwd_date;
        } else {
          newMsg.fwd_from_id = msgObj.messageOwner.from_id;
          newMsg.fwd_date = msgObj.messageOwner.date;
        }
        newMsg.media = msgObj.messageOwner.media;
        newMsg.message = msgObj.messageOwner.message;
        newMsg.fwd_msg_id = msgObj.getId();
        newMsg.attachPath = msgObj.messageOwner.attachPath;
        type = MessageObject.Type.FORWARDED;
      } else if (user != null) {
        newMsg.media = new TLRPC.TL_messageMediaContact();
        newMsg.media.phone_number = user.phone;
        newMsg.media.first_name = user.first_name;
        newMsg.media.last_name = user.last_name;
        newMsg.media.user_id = user.id;
        if (newMsg.media.first_name == null) {
          user.first_name = newMsg.media.first_name = "";
        }
        if (newMsg.media.last_name == null) {
          user.last_name = newMsg.media.last_name = "";
        }
        newMsg.message = "";
        type = MessageObject.Type.CONTACT;
      } else if (document != null) {
        newMsg.media = new TLRPC.TL_messageMediaDocument();
        newMsg.media.document = document;
        type = MessageObject.Type.DOC;
        newMsg.message = "-1";
        newMsg.attachPath = path;
      } else if (audio != null) {
        newMsg.media = new TLRPC.TL_messageMediaAudio();
        newMsg.media.audio = audio;
        newMsg.message = "-1";
        newMsg.attachPath = path;
        type = MessageObject.Type.AUDIO;
      }
      if (newMsg.attachPath == null) {
        newMsg.attachPath = "";
      }
      newMsg.local_id = newMsg.id = UserConfig.getNewMessageId();
      newMsg.from_id = UserConfig.getClientUserId();
      newMsg.flags |= TLRPC.MESSAGE_FLAG_OUT;
      UserConfig.saveConfig(false);
    }
    if (newMsg.random_id == 0) {
      newMsg.random_id = getNextRandomId();
    }
    newMsg.date = ConnectionsManager.getInstance().getCurrentTime();
    newMsg.flags |= TLRPC.MESSAGE_FLAG_UNREAD;
    newMsg.dialog_id = peer;
    if (reply_to_msg != null) {
      newMsg.flags |= TLRPC.MESSAGE_FLAG_REPLY;
      newMsg.reply_to_msg_id = reply_to_msg.getId();
    }

    newMsg.to_id = new TLRPC.TL_peerUser();
    if (encryptedChat.participant_id == UserConfig.getClientUserId()) {
      newMsg.to_id.user_id = encryptedChat.admin_id;
    } else {
      newMsg.to_id.user_id = encryptedChat.participant_id;
    }
    newMsg.ttl = encryptedChat.ttl;
    if (newMsg.ttl != 0) {
      if (newMsg.media instanceof TLRPC.TL_messageMediaAudio) {
        newMsg.ttl = Math.max(encryptedChat.ttl, newMsg.media.audio.duration + 1);
      } else if (newMsg.media instanceof TLRPC.TL_messageMediaVideo) {
        newMsg.ttl = Math.max(encryptedChat.ttl, newMsg.media.video.duration + 1);
      }
    }

    MessageObject newMsgObj = new MessageObject(newMsg, null, true);
    newMsgObj.replyMessageObject = reply_to_msg;
    newMsgObj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENDING;

    ArrayList<MessageObject> objArr = new ArrayList<>();
    objArr.add(newMsgObj);
    ArrayList<TLRPC.Message> arr = new ArrayList<>();
    arr.add(newMsg);
    MessagesStorage.getInstance().putMessages(arr, false, true, false, 0);
    MessagesController.getInstance().updateInterfaceWithMessages(peer, objArr);
    NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);

    try {
      TLRPC.TL_decryptedMessage reqSend;
      switch (type) {
        case TEXT:
          if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
            reqSend = new TLRPC.TL_decryptedMessage();
            reqSend.ttl = newMsg.ttl;
          } else {
            reqSend = new TLRPC.TL_decryptedMessage_old();
            reqSend.random_bytes = new byte[15];
            Utilities.random.nextBytes(reqSend.random_bytes);
          }
          reqSend.random_id = newMsg.random_id;
          reqSend.message = message;
          reqSend.media = new TLRPC.TL_decryptedMessageMediaEmpty();
          SecretChatHelper.getInstance()
              .performSendEncryptedRequest(
                  reqSend, newMsgObj.messageOwner, encryptedChat, null, null);
          break;
        case LOCATION:
        case VIDEO:
        case CONTACT:
        case PHOTO:
        case AUDIO:
        case DOC:
          if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
            reqSend = new TLRPC.TL_decryptedMessage();
            reqSend.ttl = newMsg.ttl;
          } else {
            reqSend = new TLRPC.TL_decryptedMessage_old();
            reqSend.random_bytes = new byte[15];
            Utilities.random.nextBytes(reqSend.random_bytes);
          }
          reqSend.random_id = newMsg.random_id;
          reqSend.message = "";


          switch (type) {
            case LOCATION:
              reqSend.media = new TLRPC.TL_decryptedMessageMediaGeoPoint();
              reqSend.media.lat = location.geo.lat;
              reqSend.media._long = location.geo._long;
              SecretChatHelper.getInstance()
                  .performSendEncryptedRequest(
                      reqSend, newMsgObj.messageOwner, encryptedChat, null, null);
              break;
            case PHOTO:
              TLRPC.PhotoSize small = photo.sizes.get(0);
              TLRPC.PhotoSize big = photo.sizes.get(photo.sizes.size() - 1);
              reqSend.media = new TLRPC.TL_decryptedMessageMediaPhoto();
              ImageLoader.fillPhotoSizeWithBytes(small);
              if (small.bytes != null) {
                ((TLRPC.TL_decryptedMessageMediaPhoto) reqSend.media).thumb = small.bytes;
              } else {
                ((TLRPC.TL_decryptedMessageMediaPhoto) reqSend.media).thumb = new byte[0];
              }
              reqSend.media.thumb_h = small.h;
              reqSend.media.thumb_w = small.w;
              reqSend.media.w = big.w;
              reqSend.media.h = big.h;
              reqSend.media.size = big.size;
              if (big.location.key == null) {
                DelayedMessage delayedMessage = new DelayedMessage();
                delayedMessage.originalPath = originalPath;
                delayedMessage.sendEncryptedRequest = reqSend;
                delayedMessage.type = 0;
                delayedMessage.obj = newMsgObj;
                delayedMessage.encryptedChat = encryptedChat;
                if (path != null && path.length() > 0 && path.startsWith("http")) {
                  delayedMessage.httpLocation = path;
                } else {
                  delayedMessage.location = photo.sizes.get(photo.sizes.size() - 1).location;
                }
                performSendDelayedMessage(delayedMessage);
              } else {
                TLRPC.TL_inputEncryptedFile encryptedFile = new TLRPC.TL_inputEncryptedFile();
                encryptedFile.id = big.location.volume_id;
                encryptedFile.access_hash = big.location.secret;
                reqSend.media.key = big.location.key;
                reqSend.media.iv = big.location.iv;
                SecretChatHelper.getInstance()
                    .performSendEncryptedRequest(
                        reqSend, newMsgObj.messageOwner, encryptedChat, encryptedFile, null);
              }
              break;
            case VIDEO:
              ImageLoader.fillPhotoSizeWithBytes(video.thumb);
              if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
                reqSend.media = new TLRPC.TL_decryptedMessageMediaVideo();
                if (video.thumb != null && video.thumb.bytes != null) {
                  ((TLRPC.TL_decryptedMessageMediaVideo) reqSend.media).thumb = video.thumb.bytes;
                } else {
                  ((TLRPC.TL_decryptedMessageMediaVideo) reqSend.media).thumb = new byte[0];
                }
              } else {
                reqSend.media = new TLRPC.TL_decryptedMessageMediaVideo_old();
                if (video.thumb != null && video.thumb.bytes != null) {
                  ((TLRPC.TL_decryptedMessageMediaVideo_old) reqSend.media).thumb = video.thumb.bytes;
                } else {
                  ((TLRPC.TL_decryptedMessageMediaVideo_old) reqSend.media).thumb = new byte[0];
                }
              }
              reqSend.media.duration = video.duration;
              reqSend.media.size = video.size;
              reqSend.media.w = video.w;
              reqSend.media.h = video.h;
              reqSend.media.thumb_h = video.thumb.h;
              reqSend.media.thumb_w = video.thumb.w;
              reqSend.media.mime_type = "video/mp4";
              if (video.access_hash == 0) {
                DelayedMessage delayedMessage = new DelayedMessage();
                delayedMessage.originalPath = originalPath;
                delayedMessage.sendEncryptedRequest = reqSend;
                delayedMessage.type = 1;
                delayedMessage.obj = newMsgObj;
                delayedMessage.encryptedChat = encryptedChat;
                delayedMessage.videoLocation = video;
                delayedMessage.videoEditedInfo = videoEditedInfo;
                performSendDelayedMessage(delayedMessage);
              } else {
                TLRPC.TL_inputEncryptedFile encryptedFile = new TLRPC.TL_inputEncryptedFile();
                encryptedFile.id = video.id;
                encryptedFile.access_hash = video.access_hash;
                reqSend.media.key = video.key;
                reqSend.media.iv = video.iv;
                SecretChatHelper.getInstance()
                    .performSendEncryptedRequest(
                        reqSend, newMsgObj.messageOwner, encryptedChat, encryptedFile, null);
              }
              break;
            case CONTACT:
              reqSend.media = new TLRPC.TL_decryptedMessageMediaContact();
              reqSend.media.phone_number = user.phone;
              reqSend.media.first_name = user.first_name;
              reqSend.media.last_name = user.last_name;
              reqSend.media.user_id = user.id;
              SecretChatHelper.getInstance()
                  .performSendEncryptedRequest(
                      reqSend, newMsgObj.messageOwner, encryptedChat, null, null);
              break;
            case DOC:
              boolean isSticker = false;
              for (TLRPC.DocumentAttribute attribute : document.attributes) {
                if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                  isSticker = true;
                }
              }
              if (isSticker) {
                reqSend.media = new TLRPC.TL_decryptedMessageMediaExternalDocument();
                reqSend.media.id = document.id;
                reqSend.media.date = document.date;
                reqSend.media.access_hash = document.access_hash;
                reqSend.media.mime_type = document.mime_type;
                reqSend.media.size = document.size;
                reqSend.media.dc_id = document.dc_id;
                reqSend.media.attributes = document.attributes;
                if (document.thumb == null) {
                  ((TLRPC.TL_decryptedMessageMediaExternalDocument) reqSend.media).thumb =
                      new TLRPC.TL_photoSizeEmpty();
                } else {
                  ((TLRPC.TL_decryptedMessageMediaExternalDocument) reqSend.media).thumb =
                      document.thumb;
                }
                SecretChatHelper.getInstance()
                    .performSendEncryptedRequest(
                        reqSend, newMsgObj.messageOwner, encryptedChat, null, null);
              } else {
                ImageLoader.fillPhotoSizeWithBytes(document.thumb);
                reqSend.media = new TLRPC.TL_decryptedMessageMediaDocument();
                reqSend.media.size = document.size;
                if (document.thumb != null && document.thumb.bytes != null) {
                  ((TLRPC.TL_decryptedMessageMediaDocument) reqSend.media).thumb =
                      document.thumb.bytes;
                  reqSend.media.thumb_h = document.thumb.h;
                  reqSend.media.thumb_w = document.thumb.w;
                } else {
                  ((TLRPC.TL_decryptedMessageMediaDocument) reqSend.media).thumb = new byte[0];
                  reqSend.media.thumb_h = 0;
                  reqSend.media.thumb_w = 0;
                }
                reqSend.media.file_name = FileLoader.getDocumentFileName(document);
                reqSend.media.mime_type = document.mime_type;

                if (document.access_hash == 0) {
                  DelayedMessage delayedMessage = new DelayedMessage();
                  delayedMessage.originalPath = originalPath;
                  delayedMessage.sendEncryptedRequest = reqSend;
                  delayedMessage.type = 2;
                  delayedMessage.obj = newMsgObj;
                  delayedMessage.encryptedChat = encryptedChat;
                  if (path != null && path.length() > 0 && path.startsWith("http")) {
                    delayedMessage.httpLocation = path;
                  }
                  delayedMessage.documentLocation = document;
                  performSendDelayedMessage(delayedMessage);
                } else {
                  TLRPC.TL_inputEncryptedFile encryptedFile = new TLRPC.TL_inputEncryptedFile();
                  encryptedFile.id = document.id;
                  encryptedFile.access_hash = document.access_hash;
                  reqSend.media.key = document.key;
                  reqSend.media.iv = document.iv;
                  SecretChatHelper.getInstance()
                      .performSendEncryptedRequest(
                          reqSend, newMsgObj.messageOwner, encryptedChat, encryptedFile, null);
                }
              }
              break;
            case AUDIO:
              // audio
              if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 17) {
                reqSend.media = new TLRPC.TL_decryptedMessageMediaAudio();
              } else {
                reqSend.media = new TLRPC.TL_decryptedMessageMediaAudio_old();
              }
              reqSend.media.duration = audio.duration;
              reqSend.media.size = audio.size;
              reqSend.media.mime_type = "audio/ogg";

              DelayedMessage delayedMessage = new DelayedMessage();
              delayedMessage.sendEncryptedRequest = reqSend;
              delayedMessage.type = 3;
              delayedMessage.obj = newMsgObj;
              delayedMessage.encryptedChat = encryptedChat;
              delayedMessage.audioLocation = audio;
              performSendDelayedMessage(delayedMessage);
              break;
          }
      }
    } catch (Exception e) {
      FileLog.e("tmessages", e);
      MessagesStorage.getInstance().markMessageAsSendError(newMsgObj.getId());
      newMsgObj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
      NotificationCenter.getInstance()
          .postNotificationName(NotificationCenter.messageSendError, newMsgObj.getId());
      processSentMessage(newMsgObj.getId());
    }
  }

  private void performSendDelayedMessage(final DelayedMessage message) {
    if (message.type == 0) {
      if (message.httpLocation != null) {
        putToDelayedMessages(message.httpLocation, message);
        ImageLoader.getInstance().loadHttpFile(message.httpLocation, "jpg");
      } else {
        String location = FileLoader.getPathToAttach(message.location, true).toString();
        putToDelayedMessages(location, message);
        if (message.sendRequest != null) {
          FileLoader.getInstance().uploadFile(location, false, true);
        } else {
          FileLoader.getInstance().uploadFile(location, true, true);
        }
      }
    } else if (message.type == 1) {
      if (message.videoEditedInfo != null) {
        String location = message.obj.messageOwner.attachPath;
        if (location == null) {
          location =
              FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE)
                  + "/"
                  + message.videoLocation.id
                  + ".mp4";
        }
        putToDelayedMessages(location, message);
        MediaController.getInstance().scheduleVideoConvert(message.obj);
      } else {
        if (message.sendRequest != null) {
          TLRPC.InputMedia media;
          if (message.sendRequest instanceof TLRPC.TL_messages_sendMedia) {
            media = ((TLRPC.TL_messages_sendMedia) message.sendRequest).media;
          } else {
            media = ((TLRPC.TL_messages_sendBroadcast) message.sendRequest).media;
          }
          if (media.file == null) {
            String location = message.obj.messageOwner.attachPath;
            if (location == null) {
              location =
                  FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE)
                      + "/"
                      + message.videoLocation.id
                      + ".mp4";
            }
            putToDelayedMessages(location, message);
            if (message.obj.videoEditedInfo != null) {
              FileLoader.getInstance()
                  .uploadFile(location, false, false, message.videoLocation.size);
            } else {
              FileLoader.getInstance().uploadFile(location, false, false);
            }
          } else {
            String location =
                FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE)
                    + "/"
                    + message.location.volume_id
                    + "_"
                    + message.location.local_id
                    + ".jpg";
            putToDelayedMessages(location, message);
            FileLoader.getInstance().uploadFile(location, false, true);
          }
        } else {
          String location = message.obj.messageOwner.attachPath;
          if (location == null) {
            location =
                FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE)
                    + "/"
                    + message.videoLocation.id
                    + ".mp4";
          }
          putToDelayedMessages(location, message);
          if (message.obj.videoEditedInfo != null) {
            FileLoader.getInstance().uploadFile(location, true, false, message.videoLocation.size);
          } else {
            FileLoader.getInstance().uploadFile(location, true, false);
          }
        }
      }
    } else if (message.type == 2) {
      if (message.httpLocation != null) {
        putToDelayedMessages(message.httpLocation, message);
        ImageLoader.getInstance().loadHttpFile(message.httpLocation, "gif");
      } else {
        if (message.sendRequest != null) {
          TLRPC.InputMedia media;
          if (message.sendRequest instanceof TLRPC.TL_messages_sendMedia) {
            media = ((TLRPC.TL_messages_sendMedia) message.sendRequest).media;
          } else {
            media = ((TLRPC.TL_messages_sendBroadcast) message.sendRequest).media;
          }
          if (media.file == null) {
            String location = message.obj.messageOwner.attachPath;
            putToDelayedMessages(location, message);
            if (message.sendRequest != null) {
              FileLoader.getInstance().uploadFile(location, false, false);
            } else {
              FileLoader.getInstance().uploadFile(location, true, false);
            }
          } else if (media.thumb == null && message.location != null) {
            String location =
                FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE)
                    + "/"
                    + message.location.volume_id
                    + "_"
                    + message.location.local_id
                    + ".jpg";
            putToDelayedMessages(location, message);
            FileLoader.getInstance().uploadFile(location, false, true);
          }
        } else {
          String location = message.obj.messageOwner.attachPath;
          putToDelayedMessages(location, message);
          FileLoader.getInstance().uploadFile(location, true, false);
        }
      }
    } else if (message.type == 3) {
      String location = message.obj.messageOwner.attachPath;
      putToDelayedMessages(location, message);
      if (message.sendRequest != null) {
        FileLoader.getInstance().uploadFile(location, false, true);
      } else {
        FileLoader.getInstance().uploadFile(location, true, true);
      }
    }
  }

  protected void stopVideoService(final String path) {
    MessagesStorage.getInstance()
        .getStorageQueue()
        .postRunnable(
            new Runnable() {
              @Override
              public void run() {
                AndroidUtilities.runOnUIThread(
                    new Runnable() {
                      @Override
                      public void run() {
                        NotificationCenter.getInstance()
                            .postNotificationName(NotificationCenter.stopEncodingService, path);
                      }
                    });
              }
            });
  }

  protected void putToSendingMessages(TLRPC.Message message) {
    sendingMessages.put(message.id, message);
  }

  protected void removeFromSendingMessages(int mid) {
    sendingMessages.remove(mid);
  }

  public boolean isSendingMessage(int mid) {
    return sendingMessages.containsKey(mid);
  }

  private void performSendMessageRequest(
      final TLObject req, final TLRPC.Message newMsgObj, final String originalPath) {
    putToSendingMessages(newMsgObj);
    ConnectionsManager.getInstance()
        .performRpc(
            req,
            new RPCRequest.RPCRequestDelegate() {
              @Override
              public void run(TLObject response, TLRPC.TL_error error) {
                boolean isSentError = false;
                if (error == null) {
                  final int oldId = newMsgObj.id;
                  final boolean isBroadcast = req instanceof TLRPC.TL_messages_sendBroadcast;
                  final ArrayList<TLRPC.Message> sentMessages = new ArrayList<>();
                  final String attachPath = newMsgObj.attachPath;
                  final boolean mediaUpdated =
                      response instanceof TLRPC.messages_SentMessage
                          && !(((TLRPC.messages_SentMessage) response).media
                              instanceof TLRPC.TL_messageMediaEmpty);
                  if (response instanceof TLRPC.messages_SentMessage) {
                    TLRPC.messages_SentMessage res = (TLRPC.messages_SentMessage) response;
                    newMsgObj.local_id = newMsgObj.id = res.id;
                    newMsgObj.date = res.date;
                    newMsgObj.media = res.media;
                    if (res instanceof TLRPC.TL_messages_sentMessage) {
                      MessagesController.getInstance()
                          .processNewDifferenceParams(-1, res.pts, res.date, res.pts_count);
                    } else if (res instanceof TLRPC.TL_messages_sentMessageLink) {
                      MessagesController.getInstance()
                          .processNewDifferenceParams(res.seq, res.pts, res.date, res.pts_count);
                    }
                    sentMessages.add(newMsgObj);
                  } else if (response instanceof TLRPC.Updates) {
                    TLRPC.TL_updateNewMessage newMessage = null;
                    for (TLRPC.Update update : ((TLRPC.Updates) response).updates) {
                      if (update instanceof TLRPC.TL_updateNewMessage) {
                        newMessage = (TLRPC.TL_updateNewMessage) update;
                        break;
                      }
                    }
                    if (newMessage != null) {
                      sentMessages.add(newMessage.message);
                      newMsgObj.id = newMessage.message.id;
                      processSentMessage(newMsgObj, newMessage.message, originalPath);
                      MessagesController.getInstance()
                          .processNewDifferenceParams(-1, newMessage.pts, -1, newMessage.pts_count);
                    } else {
                      isSentError = true;
                    }
                  }

                  if (!isSentError) {
                    MessagesStorage.getInstance()
                        .getStorageQueue()
                        .postRunnable(
                            new Runnable() {
                              @Override
                              public void run() {
                                MessagesStorage.getInstance()
                                    .updateMessageStateAndId(
                                        newMsgObj.random_id,
                                        oldId,
                                        (isBroadcast ? oldId : newMsgObj.id),
                                        0,
                                        false);
                                MessagesStorage.getInstance()
                                    .putMessages(sentMessages, true, false, isBroadcast, 0);
                                if (isBroadcast) {
                                  ArrayList<TLRPC.Message> currentMessage = new ArrayList<>();
                                  currentMessage.add(newMsgObj);
                                  newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
                                  MessagesStorage.getInstance()
                                      .putMessages(currentMessage, true, false, false, 0);
                                }
                                AndroidUtilities.runOnUIThread(
                                    new Runnable() {
                                      @Override
                                      public void run() {
                                        newMsgObj.send_state =
                                            MessageObject.MESSAGE_SEND_STATE_SENT;
                                        if (isBroadcast) {
                                          for (TLRPC.Message message : sentMessages) {
                                            ArrayList<MessageObject> arr = new ArrayList<>();
                                            MessageObject messageObject =
                                                new MessageObject(message, null, false);
                                            arr.add(messageObject);
                                            MessagesController.getInstance()
                                                .updateInterfaceWithMessages(
                                                    messageObject.getDialogId(), arr, true);
                                          }
                                          NotificationCenter.getInstance()
                                              .postNotificationName(
                                                  NotificationCenter.dialogsNeedReload);
                                        }
                                        NotificationCenter.getInstance()
                                            .postNotificationName(
                                                NotificationCenter.messageReceivedByServer,
                                                oldId,
                                                (isBroadcast ? oldId : newMsgObj.id),
                                                newMsgObj,
                                                mediaUpdated);
                                        processSentMessage(oldId);
                                        removeFromSendingMessages(oldId);
                                      }
                                    });
                                if (newMsgObj.media instanceof TLRPC.TL_messageMediaVideo) {
                                  stopVideoService(attachPath);
                                }
                              }
                            });
                  }
                } else {
                  isSentError = true;
                }
                if (isSentError) {
                  MessagesStorage.getInstance().markMessageAsSendError(newMsgObj.id);
                  AndroidUtilities.runOnUIThread(
                      new Runnable() {
                        @Override
                        public void run() {
                          newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
                          NotificationCenter.getInstance()
                              .postNotificationName(
                                  NotificationCenter.messageSendError, newMsgObj.id);
                          processSentMessage(newMsgObj.id);
                          if (newMsgObj.media instanceof TLRPC.TL_messageMediaVideo) {
                            stopVideoService(newMsgObj.attachPath);
                          }
                          removeFromSendingMessages(newMsgObj.id);
                        }
                      });
                }
              }
            },
            new RPCRequest.RPCQuickAckDelegate() {
              @Override
              public void quickAck() {
                final int msg_id = newMsgObj.id;
                AndroidUtilities.runOnUIThread(
                    new Runnable() {
                      @Override
                      public void run() {
                        newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
                        NotificationCenter.getInstance()
                            .postNotificationName(NotificationCenter.messageReceivedByAck, msg_id);
                      }
                    });
              }
            },
            true,
            RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassCanCompress,
            ConnectionsManager.DEFAULT_DATACENTER_ID);
  }

  private void processSentMessage(
      TLRPC.Message newMsg, TLRPC.Message sentMessage, String originalPath) {
    if (sentMessage == null) {
      return;
    }
    if (sentMessage.media instanceof TLRPC.TL_messageMediaPhoto
        && sentMessage.media.photo != null
        && newMsg.media instanceof TLRPC.TL_messageMediaPhoto
        && newMsg.media.photo != null) {
      MessagesStorage.getInstance().putSentFile(originalPath, sentMessage.media.photo, 0);

      for (TLRPC.PhotoSize size : sentMessage.media.photo.sizes) {
        if (size == null || size instanceof TLRPC.TL_photoSizeEmpty || size.type == null) {
          continue;
        }
        for (TLRPC.PhotoSize size2 : newMsg.media.photo.sizes) {
          if (size2 == null || size2.location == null || size2.type == null) {
            continue;
          }
          if (size2.location.volume_id == Integer.MIN_VALUE && size.type.equals(size2.type)
              || size.w == size2.w && size.h == size2.h) {
            String fileName = size2.location.volume_id + "_" + size2.location.local_id;
            String fileName2 = size.location.volume_id + "_" + size.location.local_id;
            if (fileName.equals(fileName2)) {
              break;
            }
            File cacheFile =
                new File(
                    FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE),
                    fileName + ".jpg");
            File cacheFile2;
            if (sentMessage.media.photo.sizes.size() == 1 || size.w > 90 || size.h > 90) {
              cacheFile2 = FileLoader.getPathToAttach(size);
            } else {
              cacheFile2 =
                  new File(
                      FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE),
                      fileName2 + ".jpg");
            }
            cacheFile.renameTo(cacheFile2);
            ImageLoader.getInstance().replaceImageInCache(fileName, fileName2, size.location);
            size2.location = size.location;
            break;
          }
        }
      }
      sentMessage.message = newMsg.message;
      sentMessage.attachPath = newMsg.attachPath;
      newMsg.media.photo.id = sentMessage.media.photo.id;
      newMsg.media.photo.access_hash = sentMessage.media.photo.access_hash;
    } else if (sentMessage.media instanceof TLRPC.TL_messageMediaVideo
        && sentMessage.media.video != null
        && newMsg.media instanceof TLRPC.TL_messageMediaVideo
        && newMsg.media.video != null) {
      MessagesStorage.getInstance().putSentFile(originalPath, sentMessage.media.video, 2);

      TLRPC.PhotoSize size2 = newMsg.media.video.thumb;
      TLRPC.PhotoSize size = sentMessage.media.video.thumb;
      if (size2.location != null
          && size2.location.volume_id == Integer.MIN_VALUE
          && size.location != null
          && !(size instanceof TLRPC.TL_photoSizeEmpty)
          && !(size2 instanceof TLRPC.TL_photoSizeEmpty)) {
        String fileName = size2.location.volume_id + "_" + size2.location.local_id;
        String fileName2 = size.location.volume_id + "_" + size.location.local_id;
        if (!fileName.equals(fileName2)) {
          File cacheFile =
              new File(
                  FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE),
                  fileName + ".jpg");
          File cacheFile2 =
              new File(
                  FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE),
                  fileName2 + ".jpg");
          cacheFile.renameTo(cacheFile2);
          ImageLoader.getInstance().replaceImageInCache(fileName, fileName2, size.location);
          size2.location = size.location;
        }
      }

      sentMessage.message = newMsg.message;
      newMsg.media.video.dc_id = sentMessage.media.video.dc_id;
      newMsg.media.video.id = sentMessage.media.video.id;
      newMsg.media.video.access_hash = sentMessage.media.video.access_hash;

      if (newMsg.attachPath != null
          && newMsg.attachPath.startsWith(
              FileLoader.getInstance()
                  .getDirectory(FileLoader.MEDIA_DIR_CACHE)
                  .getAbsolutePath())) {
        File cacheFile = new File(newMsg.attachPath);
        File cacheFile2 = FileLoader.getPathToAttach(newMsg.media.video);
        if (!cacheFile.renameTo(cacheFile2)) {
          sentMessage.attachPath = newMsg.attachPath;
        }
      } else {
        sentMessage.attachPath = newMsg.attachPath;
      }
    } else if (sentMessage.media instanceof TLRPC.TL_messageMediaDocument
        && sentMessage.media.document != null
        && newMsg.media instanceof TLRPC.TL_messageMediaDocument
        && newMsg.media.document != null) {
      MessagesStorage.getInstance().putSentFile(originalPath, sentMessage.media.document, 1);

      TLRPC.PhotoSize size2 = newMsg.media.document.thumb;
      TLRPC.PhotoSize size = sentMessage.media.document.thumb;
      if (size2.location != null
          && size2.location.volume_id == Integer.MIN_VALUE
          && size.location != null
          && !(size instanceof TLRPC.TL_photoSizeEmpty)
          && !(size2 instanceof TLRPC.TL_photoSizeEmpty)) {
        String fileName = size2.location.volume_id + "_" + size2.location.local_id;
        String fileName2 = size.location.volume_id + "_" + size.location.local_id;
        if (!fileName.equals(fileName2)) {
          File cacheFile =
              new File(
                  FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE),
                  fileName + ".jpg");
          File cacheFile2 =
              new File(
                  FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE),
                  fileName2 + ".jpg");
          cacheFile.renameTo(cacheFile2);
          ImageLoader.getInstance().replaceImageInCache(fileName, fileName2, size.location);
          size2.location = size.location;
        }
      } else if (MessageObject.isStickerMessage(sentMessage) && size2.location != null) {
        size.location = size2.location;
      }

      newMsg.media.document.dc_id = sentMessage.media.document.dc_id;
      newMsg.media.document.id = sentMessage.media.document.id;
      newMsg.media.document.access_hash = sentMessage.media.document.access_hash;
      newMsg.media.document.attributes = sentMessage.media.document.attributes;

      if (newMsg.attachPath != null
          && newMsg.attachPath.startsWith(
              FileLoader.getInstance()
                  .getDirectory(FileLoader.MEDIA_DIR_CACHE)
                  .getAbsolutePath())) {
        File cacheFile = new File(newMsg.attachPath);
        File cacheFile2 = FileLoader.getPathToAttach(sentMessage.media.document);
        if (!cacheFile.renameTo(cacheFile2)) {
          sentMessage.attachPath = newMsg.attachPath;
          sentMessage.message = newMsg.message;
        } else {
          newMsg.attachPath = "";
          if (originalPath != null && originalPath.startsWith("http")) {
            MessagesStorage.getInstance().addRecentLocalFile(originalPath, cacheFile2.toString());
          }
        }
      } else {
        sentMessage.attachPath = newMsg.attachPath;
        sentMessage.message = newMsg.message;
      }
    } else if (sentMessage.media instanceof TLRPC.TL_messageMediaAudio
        && sentMessage.media.audio != null
        && newMsg.media instanceof TLRPC.TL_messageMediaAudio
        && newMsg.media.audio != null) {
      sentMessage.message = newMsg.message;

      String fileName = newMsg.media.audio.dc_id + "_" + newMsg.media.audio.id + ".ogg";
      newMsg.media.audio.dc_id = sentMessage.media.audio.dc_id;
      newMsg.media.audio.id = sentMessage.media.audio.id;
      newMsg.media.audio.access_hash = sentMessage.media.audio.access_hash;
      String fileName2 = sentMessage.media.audio.dc_id + "_" + sentMessage.media.audio.id + ".ogg";
      if (!fileName.equals(fileName2)) {
        File cacheFile =
            new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
        File cacheFile2 = FileLoader.getPathToAttach(sentMessage.media.audio);
        if (!cacheFile.renameTo(cacheFile2)) {
          sentMessage.attachPath = newMsg.attachPath;
        }
      }
    } else if (sentMessage.media instanceof TLRPC.TL_messageMediaContact
        && newMsg.media instanceof TLRPC.TL_messageMediaContact) {
      newMsg.media = sentMessage.media;
    }
  }

  private void putToDelayedMessages(String location, DelayedMessage message) {
    ArrayList<DelayedMessage> arrayList = delayedMessages.get(location);
    if (arrayList == null) {
      arrayList = new ArrayList<>();
      delayedMessages.put(location, arrayList);
    }
    arrayList.add(message);
  }

  protected ArrayList<DelayedMessage> getDelayedMessages(String location) {
    return delayedMessages.get(location);
  }

  protected long getNextRandomId() {
    long val = 0;
    while (val == 0) {
      val = Utilities.random.nextLong();
    }
    return val;
  }

  public void checkUnsentMessages() {
    MessagesStorage.getInstance().getUnsentMessages(1000);
  }

  protected void processUnsentMessages(
      final ArrayList<TLRPC.Message> messages,
      final ArrayList<TLRPC.User> users,
      final ArrayList<TLRPC.Chat> chats,
      final ArrayList<TLRPC.EncryptedChat> encryptedChats) {
    AndroidUtilities.runOnUIThread(
        new Runnable() {
          @Override
          public void run() {
            MessagesController.getInstance().putUsers(users, true);
            MessagesController.getInstance().putChats(chats, true);
            MessagesController.getInstance().putEncryptedChats(encryptedChats, true);
            for (TLRPC.Message message : messages) {
              MessageObject messageObject = new MessageObject(message, null, false);
              retrySendMessage(messageObject, true);
            }
          }
        });
  }

  public TLRPC.TL_photo generatePhotoSizes(String path, Uri imageUri) {
    Bitmap bitmap =
        ImageLoader.loadBitmap(
            path, imageUri, AndroidUtilities.getPhotoSize(), AndroidUtilities.getPhotoSize(), true);
    if (bitmap == null && AndroidUtilities.getPhotoSize() != 800) {
      bitmap = ImageLoader.loadBitmap(path, imageUri, 800, 800, true);
    }
    ArrayList<TLRPC.PhotoSize> sizes = new ArrayList<>();
    TLRPC.PhotoSize size = ImageLoader.scaleAndSaveImage(bitmap, 90, 90, 55, true);
    if (size != null) {
      sizes.add(size);
    }
    size =
        ImageLoader.scaleAndSaveImage(
            bitmap,
            AndroidUtilities.getPhotoSize(),
            AndroidUtilities.getPhotoSize(),
            80,
            false,
            101,
            101);
    if (size != null) {
      sizes.add(size);
    }
    if (bitmap != null) {
      bitmap.recycle();
    }
    if (sizes.isEmpty()) {
      return null;
    } else {
      UserConfig.saveConfig(false);
      TLRPC.TL_photo photo = new TLRPC.TL_photo();
      photo.user_id = UserConfig.getClientUserId();
      photo.date = ConnectionsManager.getInstance().getCurrentTime();
      photo.sizes = sizes;
      photo.geo = new TLRPC.TL_geoPointEmpty();
      return photo;
    }
  }

  private static boolean prepareSendingDocumentInternal(
      String path,
      String originalPath,
      Uri uri,
      String mime,
      final long dialog_id,
      final MessageObject reply_to_msg) {
    if ((path == null || path.length() == 0) && uri == null) {
      return false;
    }
    MimeTypeMap myMime = MimeTypeMap.getSingleton();
    TLRPC.TL_documentAttributeAudio attributeAudio = null;
    if (uri != null) {
      String extension = null;
      if (mime != null) {
        extension = myMime.getExtensionFromMimeType(mime);
      }
      if (extension == null) {
        extension = "txt";
      }
      path = MediaController.copyDocumentToCache(uri, extension);
      if (path == null) {
        return false;
      }
    }
    final File f = new File(path);
    if (!f.exists() || f.length() == 0) {
      return false;
    }

    boolean isEncrypted = (int) dialog_id == 0;
    boolean allowSticker = !isEncrypted;

    String name = f.getName();
    String ext = "";
    int idx = path.lastIndexOf(".");
    if (idx != -1) {
      ext = path.substring(idx + 1);
    }
    if (ext.toLowerCase().equals("mp3") || ext.toLowerCase().equals("m4a")) {
      AudioInfo audioInfo = AudioInfo.getAudioInfo(f);
      if (audioInfo != null && audioInfo.getDuration() != 0) {
        if (isEncrypted) {
          attributeAudio = new TLRPC.TL_documentAttributeAudio_old();
        } else {
          attributeAudio = new TLRPC.TL_documentAttributeAudio();
        }
        attributeAudio.duration = (int) (audioInfo.getDuration() / 1000);
        attributeAudio.title = audioInfo.getTitle();
        attributeAudio.performer = audioInfo.getArtist();
        if (attributeAudio.title == null) {
          attributeAudio.title = "";
        }
        if (attributeAudio.performer == null) {
          attributeAudio.performer = "";
        }
      }
    }
    if (originalPath != null) {
      if (attributeAudio != null) {
        originalPath += "audio" + f.length();
      } else {
        originalPath += "" + f.length();
      }
    }

    TLRPC.TL_document document = null;
    if (!isEncrypted) {
      document =
          (TLRPC.TL_document)
              MessagesStorage.getInstance().getSentFile(originalPath, !isEncrypted ? 1 : 4);
      if (document == null && !path.equals(originalPath) && !isEncrypted) {
        document =
            (TLRPC.TL_document)
                MessagesStorage.getInstance().getSentFile(path + f.length(), !isEncrypted ? 1 : 4);
      }
    }
    if (document == null) {
      document = new TLRPC.TL_document();
      document.id = 0;
      document.date = ConnectionsManager.getInstance().getCurrentTime();
      TLRPC.TL_documentAttributeFilename fileName = new TLRPC.TL_documentAttributeFilename();
      fileName.file_name = name;
      document.attributes.add(fileName);
      document.size = (int) f.length();
      document.dc_id = 0;
      if (attributeAudio != null) {
        document.attributes.add(attributeAudio);
      }
      if (ext.length() != 0) {
        if (ext.toLowerCase().equals("webp")) {
          document.mime_type = "image/webp";
        } else {
          String mimeType = myMime.getMimeTypeFromExtension(ext.toLowerCase());
          if (mimeType != null) {
            document.mime_type = mimeType;
          } else {
            document.mime_type = "application/octet-stream";
          }
        }
      } else {
        document.mime_type = "application/octet-stream";
      }
      if (document.mime_type.equals("image/gif")) {
        try {
          Bitmap bitmap = ImageLoader.loadBitmap(f.getAbsolutePath(), null, 90, 90, true);
          if (bitmap != null) {
            fileName.file_name = "animation.gif";
            document.thumb = ImageLoader.scaleAndSaveImage(bitmap, 90, 90, 55, isEncrypted);
          }
        } catch (Exception e) {
          FileLog.e("tmessages", e);
        }
      }
      if (document.mime_type.equals("image/webp") && allowSticker) {
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        try {
          bmOptions.inJustDecodeBounds = true;
          RandomAccessFile file = new RandomAccessFile(path, "r");
          ByteBuffer buffer =
              file.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, path.length());
          Utilities.loadWebpImage(buffer, buffer.limit(), bmOptions);
          file.close();
        } catch (Exception e) {
          FileLog.e("tmessages", e);
        }
        if (bmOptions.outWidth != 0
            && bmOptions.outHeight != 0
            && bmOptions.outWidth <= 800
            && bmOptions.outHeight <= 800) {
          TLRPC.TL_documentAttributeSticker attributeSticker;
          if (isEncrypted) {
            attributeSticker = new TLRPC.TL_documentAttributeSticker_old();
          } else {
            attributeSticker = new TLRPC.TL_documentAttributeSticker();
            attributeSticker.alt = "";
            attributeSticker.stickerset = new TLRPC.TL_inputStickerSetEmpty();
          }
          document.attributes.add(attributeSticker);
          TLRPC.TL_documentAttributeImageSize attributeImageSize =
              new TLRPC.TL_documentAttributeImageSize();
          attributeImageSize.w = bmOptions.outWidth;
          attributeImageSize.h = bmOptions.outHeight;
          document.attributes.add(attributeImageSize);
        }
      }
      if (document.thumb == null) {
        document.thumb = new TLRPC.TL_photoSizeEmpty();
        document.thumb.type = "s";
      }
    }

    final TLRPC.TL_document documentFinal = document;
    final String originalPathFinal = originalPath;
    final String pathFinal = path;
    AndroidUtilities.runOnUIThread(
        new Runnable() {
          @Override
          public void run() {
            SendMessagesHelper.getInstance()
                .sendMessage(documentFinal, originalPathFinal, pathFinal, dialog_id, reply_to_msg);
          }
        });
    return true;
  }

  public static void prepareSendingDocument(
      String path,
      String originalPath,
      Uri uri,
      String mine,
      long dialog_id,
      MessageObject reply_to_msg) {
    if ((path == null || originalPath == null) && uri == null) {
      return;
    }
    ArrayList<String> paths = new ArrayList<>();
    ArrayList<String> originalPaths = new ArrayList<>();
    ArrayList<Uri> uris = null;
    if (uri != null) {
      uris = new ArrayList<>();
    }
    paths.add(path);
    originalPaths.add(originalPath);
    prepareSendingDocuments(paths, originalPaths, uris, mine, dialog_id, reply_to_msg);
  }

  public static void prepareSendingAudioDocuments(
      final ArrayList<MessageObject> messageObjects,
      final long dialog_id,
      final MessageObject reply_to_msg) {
    new Thread(
            new Runnable() {
              @Override
              public void run() {
                int size = messageObjects.size();
                for (int a = 0; a < size; a++) {
                  final MessageObject messageObject = messageObjects.get(a);
                  String originalPath = messageObject.messageOwner.attachPath;
                  final File f = new File(originalPath);

                  boolean isEncrypted = (int) dialog_id == 0;

                  if (originalPath != null) {
                    originalPath += "audio" + f.length();
                  }

                  TLRPC.TL_document document = null;
                  if (!isEncrypted) {
                    document =
                        (TLRPC.TL_document)
                            MessagesStorage.getInstance()
                                .getSentFile(originalPath, !isEncrypted ? 1 : 4);
                  }
                  if (document == null) {
                    document = (TLRPC.TL_document) messageObject.messageOwner.media.document;
                  }

                  if (isEncrypted) {
                    for (int b = 0; b < document.attributes.size(); b++) {
                      if (document.attributes.get(b) instanceof TLRPC.TL_documentAttributeAudio) {
                        TLRPC.TL_documentAttributeAudio_old old =
                            new TLRPC.TL_documentAttributeAudio_old();
                        old.duration = document.attributes.get(b).duration;
                        document.attributes.remove(b);
                        document.attributes.add(old);
                        break;
                      }
                    }
                  }

                  final String originalPathFinal = originalPath;
                  final TLRPC.TL_document documentFinal = document;
                  AndroidUtilities.runOnUIThread(
                      new Runnable() {
                        @Override
                        public void run() {
                          SendMessagesHelper.getInstance()
                              .sendMessage(
                                  documentFinal,
                                  originalPathFinal,
                                  messageObject.messageOwner.attachPath,
                                  dialog_id,
                                  reply_to_msg);
                        }
                      });
                }
              }
            })
        .start();
  }

  public static void prepareSendingDocuments(
      final ArrayList<String> paths,
      final ArrayList<String> originalPaths,
      final ArrayList<Uri> uris,
      final String mime,
      final long dialog_id,
      final MessageObject reply_to_msg) {
    if (paths == null && originalPaths == null && uris == null
        || paths != null && originalPaths != null && paths.size() != originalPaths.size()) {
      return;
    }
    new Thread(
            new Runnable() {
              @Override
              public void run() {
                boolean error = false;
                if (paths != null) {
                  for (int a = 0; a < paths.size(); a++) {
                    if (!prepareSendingDocumentInternal(
                        paths.get(a), originalPaths.get(a), null, mime, dialog_id, reply_to_msg)) {
                      error = true;
                    }
                  }
                }
                if (uris != null) {
                  for (int a = 0; a < uris.size(); a++) {
                    if (!prepareSendingDocumentInternal(
                        null, null, uris.get(a), mime, dialog_id, reply_to_msg)) {
                      error = true;
                    }
                  }
                }
                if (error) {
                  AndroidUtilities.runOnUIThread(
                      new Runnable() {
                        @Override
                        public void run() {
                          try {
                            Toast toast =
                                Toast.makeText(
                                    ApplicationLoader.applicationContext,
                                    LocaleController.getString(
                                        "UnsupportedAttachment", R.string.UnsupportedAttachment),
                                    Toast.LENGTH_SHORT);
                            toast.show();
                          } catch (Exception e) {
                            FileLog.e("tmessages", e);
                          }
                        }
                      });
                }
              }
            })
        .start();
  }

  public static void prepareSendingPhoto(
      String imageFilePath,
      Uri imageUri,
      long dialog_id,
      MessageObject reply_to_msg,
      CharSequence caption) {
    ArrayList<String> paths = null;
    ArrayList<Uri> uris = null;
    ArrayList<String> captions = null;
    if (imageFilePath != null && imageFilePath.length() != 0) {
      paths = new ArrayList<>();
      paths.add(imageFilePath);
    }
    if (imageUri != null) {
      uris = new ArrayList<>();
      uris.add(imageUri);
    }
    if (caption != null) {
      captions = new ArrayList<>();
      captions.add(caption.toString());
    }
    prepareSendingPhotos(paths, uris, dialog_id, reply_to_msg, captions);
  }

  public static void prepareSendingPhotosSearch(
      final ArrayList<MediaController.SearchImage> photos,
      final long dialog_id,
      final MessageObject reply_to_msg) {
    if (photos == null || photos.isEmpty()) {
      return;
    }
    new Thread(
            new Runnable() {
              @Override
              public void run() {
                boolean isEncrypted = (int) dialog_id == 0;
                for (int a = 0; a < photos.size(); a++) {
                  final MediaController.SearchImage searchImage = photos.get(a);
                  if (searchImage.type == 1) {
                    TLRPC.TL_document document = null;
                    if (!isEncrypted) {
                      document =
                          (TLRPC.TL_document)
                              MessagesStorage.getInstance()
                                  .getSentFile(searchImage.imageUrl, !isEncrypted ? 1 : 4);
                    }
                    String md5 =
                        Utilities.MD5(searchImage.imageUrl)
                            + "."
                            + ImageLoader.getHttpUrlExtension(searchImage.imageUrl);
                    File cacheFile =
                        new File(
                            FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), md5);
                    if (document == null) {
                      File thumbFile;
                      document = new TLRPC.TL_document();
                      document.id = 0;
                      document.date = ConnectionsManager.getInstance().getCurrentTime();
                      TLRPC.TL_documentAttributeFilename fileName =
                          new TLRPC.TL_documentAttributeFilename();
                      fileName.file_name = "animation.gif";
                      document.attributes.add(fileName);
                      document.size = searchImage.size;
                      document.dc_id = 0;
                      document.mime_type = "image/gif";
                      if (cacheFile.exists()) {
                        thumbFile = cacheFile;
                      } else {
                        cacheFile = null;
                        String thumb =
                            Utilities.MD5(searchImage.thumbUrl)
                                + "."
                                + ImageLoader.getHttpUrlExtension(searchImage.imageUrl);
                        thumbFile =
                            new File(
                                FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE),
                                thumb);
                        if (!thumbFile.exists()) {
                          thumbFile = null;
                        }
                      }
                      if (thumbFile != null) {
                        try {
                          Bitmap bitmap =
                              ImageLoader.loadBitmap(
                                  thumbFile.getAbsolutePath(), null, 90, 90, true);
                          if (bitmap != null) {
                            document.thumb =
                                ImageLoader.scaleAndSaveImage(bitmap, 90, 90, 55, isEncrypted);
                          }
                        } catch (Exception e) {
                          FileLog.e("tmessages", e);
                        }
                      } else {
                        document.thumb = new TLRPC.TL_photoSize();
                        document.thumb.w = searchImage.width;
                        document.thumb.h = searchImage.height;
                        document.thumb.size = 0;
                        document.thumb.location = new TLRPC.TL_fileLocationUnavailable();
                        document.thumb.type = "x";
                      }
                    }

                    final TLRPC.TL_document documentFinal = document;
                    final String originalPathFinal = searchImage.imageUrl;
                    final String pathFinal =
                        cacheFile == null ? searchImage.imageUrl : cacheFile.toString();
                    AndroidUtilities.runOnUIThread(
                        new Runnable() {
                          @Override
                          public void run() {
                            SendMessagesHelper.getInstance()
                                .sendMessage(
                                    documentFinal,
                                    originalPathFinal,
                                    pathFinal,
                                    dialog_id,
                                    reply_to_msg);
                          }
                        });
                  } else {
                    boolean needDownloadHttp = true;
                    TLRPC.TL_photo photo = null;
                    if (!isEncrypted) {
                      photo =
                          (TLRPC.TL_photo)
                              MessagesStorage.getInstance()
                                  .getSentFile(searchImage.imageUrl, !isEncrypted ? 0 : 3);
                    }
                    if (photo == null) {
                      String md5 =
                          Utilities.MD5(searchImage.imageUrl)
                              + "."
                              + ImageLoader.getHttpUrlExtension(searchImage.imageUrl);
                      File cacheFile =
                          new File(
                              FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE),
                              md5);
                      if (cacheFile.exists() && cacheFile.length() != 0) {
                        photo =
                            SendMessagesHelper.getInstance()
                                .generatePhotoSizes(cacheFile.toString(), null);
                        if (photo != null) {
                          needDownloadHttp = false;
                        }
                      }
                      if (photo == null) {
                        md5 =
                            Utilities.MD5(searchImage.thumbUrl)
                                + "."
                                + ImageLoader.getHttpUrlExtension(searchImage.thumbUrl);
                        cacheFile =
                            new File(
                                FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE),
                                md5);
                        if (cacheFile.exists()) {
                          photo =
                              SendMessagesHelper.getInstance()
                                  .generatePhotoSizes(cacheFile.toString(), null);
                        }
                        if (photo == null) {
                          photo = new TLRPC.TL_photo();
                          photo.user_id = UserConfig.getClientUserId();
                          photo.date = ConnectionsManager.getInstance().getCurrentTime();
                          photo.geo = new TLRPC.TL_geoPointEmpty();
                          TLRPC.TL_photoSize photoSize = new TLRPC.TL_photoSize();
                          photoSize.w = searchImage.width;
                          photoSize.h = searchImage.height;
                          photoSize.size = 0;
                          photoSize.location = new TLRPC.TL_fileLocationUnavailable();
                          photoSize.type = "x";
                          photo.sizes.add(photoSize);
                        }
                      }
                    }
                    if (photo != null) {
                      if (searchImage.caption != null) {
                        photo.caption = searchImage.caption.toString();
                      }
                      final String originalPathFinal = searchImage.imageUrl;
                      final TLRPC.TL_photo photoFinal = photo;
                      final boolean needDownloadHttpFinal = needDownloadHttp;
                      AndroidUtilities.runOnUIThread(
                          new Runnable() {
                            @Override
                            public void run() {
                              SendMessagesHelper.getInstance()
                                  .sendMessage(
                                      photoFinal,
                                      originalPathFinal,
                                      needDownloadHttpFinal ? searchImage.imageUrl : null,
                                      dialog_id,
                                      reply_to_msg);
                            }
                          });
                    }
                  }
                }
              }
            })
        .start();
  }

  private static String getTrimmedString(String src) {
    String result = src.trim();
    if (result.length() == 0) {
      return result;
    }
    while (src.startsWith("\n")) {
      src = src.substring(1);
    }
    while (src.endsWith("\n")) {
      src = src.substring(0, src.length() - 1);
    }
    return src;
  }

  public static void prepareSendingText(final String text, final long dialog_id) {
    MessagesStorage.getInstance()
        .getStorageQueue()
        .postRunnable(
            new Runnable() {
              @Override
              public void run() {
                Utilities.stageQueue.postRunnable(
                    new Runnable() {
                      @Override
                      public void run() {
                        AndroidUtilities.runOnUIThread(
                            new Runnable() {
                              @Override
                              public void run() {
                                String textFinal = getTrimmedString(text);
                                if (textFinal.length() != 0) {
                                  int count = (int) Math.ceil(textFinal.length() / 4096.0f);
                                  for (int a = 0; a < count; a++) {
                                    String mess =
                                        textFinal.substring(
                                            a * 4096, Math.min((a + 1) * 4096, textFinal.length()));
                                    SendMessagesHelper.getInstance()
                                        .sendMessage(mess, dialog_id, null, null, true);
                                  }
                                }
                              }
                            });
                      }
                    });
              }
            });
  }

  public static void prepareSendingPhotos(
      ArrayList<String> paths,
      ArrayList<Uri> uris,
      final long dialog_id,
      final MessageObject reply_to_msg,
      final ArrayList<String> captions) {
    if (paths == null && uris == null
        || paths != null && paths.isEmpty()
        || uris != null && uris.isEmpty()) {
      return;
    }
    final ArrayList<String> pathsCopy = new ArrayList<>();
    final ArrayList<Uri> urisCopy = new ArrayList<>();
    if (paths != null) {
      pathsCopy.addAll(paths);
    }
    if (uris != null) {
      urisCopy.addAll(uris);
    }
    new Thread(
            new Runnable() {
              @Override
              public void run() {
                boolean isEncrypted = (int) dialog_id == 0;

                ArrayList<String> sendAsDocuments = null;
                ArrayList<String> sendAsDocumentsOriginal = null;
                int count = !pathsCopy.isEmpty() ? pathsCopy.size() : urisCopy.size();
                String path = null;
                Uri uri = null;
                String extension = null;
                for (int a = 0; a < count; a++) {
                  if (!pathsCopy.isEmpty()) {
                    path = pathsCopy.get(a);
                  } else if (!urisCopy.isEmpty()) {
                    uri = urisCopy.get(a);
                  }

                  String originalPath = path;
                  String tempPath = path;
                  if (tempPath == null && uri != null) {
                    tempPath = AndroidUtilities.getPath(uri);
                    originalPath = uri.toString();
                  }

                  boolean isDocument = false;
                  if (tempPath != null
                      && (tempPath.endsWith(".gif") || tempPath.endsWith(".webp"))) {
                    if (tempPath.endsWith(".gif")) {
                      extension = "gif";
                    } else {
                      extension = "webp";
                    }
                    isDocument = true;
                  } else if (tempPath == null && uri != null) {
                    if (MediaController.isGif(uri)) {
                      isDocument = true;
                      originalPath = uri.toString();
                      tempPath = MediaController.copyDocumentToCache(uri, "gif");
                      extension = "gif";
                    } else if (MediaController.isWebp(uri)) {
                      isDocument = true;
                      originalPath = uri.toString();
                      tempPath = MediaController.copyDocumentToCache(uri, "webp");
                      extension = "webp";
                    }
                  }

                  if (isDocument) {
                    if (sendAsDocuments == null) {
                      sendAsDocuments = new ArrayList<>();
                      sendAsDocumentsOriginal = new ArrayList<>();
                    }
                    sendAsDocuments.add(tempPath);
                    sendAsDocumentsOriginal.add(originalPath);
                  } else {
                    if (tempPath != null) {
                      File temp = new File(tempPath);
                      originalPath += temp.length() + "_" + temp.lastModified();
                    } else {
                      originalPath = null;
                    }
                    TLRPC.TL_photo photo = null;
                    if (!isEncrypted) {
                      photo =
                          (TLRPC.TL_photo)
                              MessagesStorage.getInstance()
                                  .getSentFile(originalPath, !isEncrypted ? 0 : 3);
                      if (photo == null && uri != null) {
                        photo =
                            (TLRPC.TL_photo)
                                MessagesStorage.getInstance()
                                    .getSentFile(
                                        AndroidUtilities.getPath(uri), !isEncrypted ? 0 : 3);
                      }
                    }
                    if (photo == null) {
                      photo = SendMessagesHelper.getInstance().generatePhotoSizes(path, uri);
                    }
                    if (photo != null) {
                      if (captions != null) {
                        photo.caption = captions.get(a);
                      }
                      final String originalPathFinal = originalPath;
                      final TLRPC.TL_photo photoFinal = photo;
                      AndroidUtilities.runOnUIThread(
                          new Runnable() {
                            @Override
                            public void run() {
                              SendMessagesHelper.getInstance()
                                  .sendMessage(
                                      photoFinal, originalPathFinal, null, dialog_id, reply_to_msg);
                            }
                          });
                    }
                  }
                }
                if (sendAsDocuments != null && !sendAsDocuments.isEmpty()) {
                  for (int a = 0; a < sendAsDocuments.size(); a++) {
                    prepareSendingDocumentInternal(
                        sendAsDocuments.get(a),
                        sendAsDocumentsOriginal.get(a),
                        null,
                        extension,
                        dialog_id,
                        reply_to_msg);
                  }
                }
              }
            })
        .start();
  }

  public static void prepareSendingVideo(
      final String videoPath,
      final long estimatedSize,
      final long duration,
      final int width,
      final int height,
      final VideoEditedInfo videoEditedInfo,
      final long dialog_id,
      final MessageObject reply_to_msg) {
    if (videoPath == null || videoPath.length() == 0) {
      return;
    }
    new Thread(
            new Runnable() {
              @Override
              public void run() {

                boolean isEncrypted = (int) dialog_id == 0;

                if (videoEditedInfo != null || videoPath.endsWith("mp4")) {
                  String path = videoPath;
                  String originalPath = videoPath;
                  File temp = new File(originalPath);
                  originalPath += temp.length() + "_" + temp.lastModified();
                  if (videoEditedInfo != null) {
                    originalPath +=
                        duration + "_" + videoEditedInfo.startTime + "_" + videoEditedInfo.endTime;
                    if (videoEditedInfo.resultWidth == videoEditedInfo.originalWidth) {
                      originalPath += "_" + videoEditedInfo.resultWidth;
                    }
                  }
                  TLRPC.TL_video video = null;
                  if (!isEncrypted) {
                    video =
                        (TLRPC.TL_video)
                            MessagesStorage.getInstance()
                                .getSentFile(originalPath, !isEncrypted ? 2 : 5);
                  }
                  if (video == null) {
                    Bitmap thumb =
                        ThumbnailUtils.createVideoThumbnail(
                            videoPath, MediaStore.Video.Thumbnails.MINI_KIND);
                    TLRPC.PhotoSize size =
                        ImageLoader.scaleAndSaveImage(thumb, 90, 90, 55, isEncrypted);
                    video = new TLRPC.TL_video();
                    video.thumb = size;
                    if (video.thumb == null) {
                      video.thumb = new TLRPC.TL_photoSizeEmpty();
                      video.thumb.type = "s";
                    } else {
                      video.thumb.type = "s";
                    }
                    video.mime_type = "video/mp4";
                    video.id = 0;
                    UserConfig.saveConfig(false);

                    if (videoEditedInfo != null) {
                      video.duration = (int) (duration / 1000);
                      if (videoEditedInfo.rotationValue == 90
                          || videoEditedInfo.rotationValue == 270) {
                        video.w = height;
                        video.h = width;
                      } else {
                        video.w = width;
                        video.h = height;
                      }
                      video.size = (int) estimatedSize;
                      String fileName = Integer.MIN_VALUE + "_" + UserConfig.lastLocalId + ".mp4";
                      UserConfig.lastLocalId--;
                      File cacheFile =
                          new File(
                              FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE),
                              fileName);
                      UserConfig.saveConfig(false);
                      path = cacheFile.getAbsolutePath();
                    } else {
                      if (temp.exists()) {
                        video.size = (int) temp.length();
                      }
                      boolean infoObtained = false;
                      if (Build.VERSION.SDK_INT >= 14) {
                        MediaMetadataRetriever mediaMetadataRetriever = null;
                        try {
                          mediaMetadataRetriever = new MediaMetadataRetriever();
                          mediaMetadataRetriever.setDataSource(videoPath);
                          String width =
                              mediaMetadataRetriever.extractMetadata(
                                  MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                          if (width != null) {
                            video.w = Integer.parseInt(width);
                          }
                          String height =
                              mediaMetadataRetriever.extractMetadata(
                                  MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                          if (height != null) {
                            video.h = Integer.parseInt(height);
                          }
                          String duration =
                              mediaMetadataRetriever.extractMetadata(
                                  MediaMetadataRetriever.METADATA_KEY_DURATION);
                          if (duration != null) {
                            video.duration = (int) Math.ceil(Long.parseLong(duration) / 1000.0f);
                          }
                          infoObtained = true;
                        } catch (Exception e) {
                          FileLog.e("tmessages", e);
                        } finally {
                          try {
                            if (mediaMetadataRetriever != null) {
                              mediaMetadataRetriever.release();
                            }
                          } catch (Exception e) {
                            FileLog.e("tmessages", e);
                          }
                        }
                      }
                      if (!infoObtained) {
                        try {
                          MediaPlayer mp =
                              MediaPlayer.create(
                                  ApplicationLoader.applicationContext,
                                  Uri.fromFile(new File(videoPath)));
                          if (mp != null) {
                            video.duration = (int) Math.ceil(mp.getDuration() / 1000.0f);
                            video.w = mp.getVideoWidth();
                            video.h = mp.getVideoHeight();
                            mp.release();
                          }
                        } catch (Exception e) {
                          FileLog.e("tmessages", e);
                        }
                      }
                    }
                  }
                  final TLRPC.TL_video videoFinal = video;
                  final String originalPathFinal = originalPath;
                  final String finalPath = path;
                  AndroidUtilities.runOnUIThread(
                      new Runnable() {
                        @Override
                        public void run() {
                          SendMessagesHelper.getInstance()
                              .sendMessage(
                                  videoFinal,
                                  videoEditedInfo,
                                  originalPathFinal,
                                  finalPath,
                                  dialog_id,
                                  reply_to_msg);
                        }
                      });
                } else {
                  prepareSendingDocumentInternal(
                      videoPath, videoPath, null, null, dialog_id, reply_to_msg);
                }
              }
            })
        .start();
  }
}
