/*
 * This is the source code of Telegram for Android v. 2.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.android.query;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.MessagesStorage;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.ByteBufferDesc;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.TLRPC;
import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLitePreparedStatement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class BotQuery {

  private static HashMap<Integer, TLRPC.BotInfo> botInfos = new HashMap<>();
  private static HashMap<Long, TLRPC.Message> botKeyboards = new HashMap<>();
  private static HashMap<Integer, Long> botKeyboardsByMids = new HashMap<>();

  public static void cleanup() {
    botInfos.clear();
  }

  public static void clearBotKeyboard(final long did, final ArrayList<Integer> messages) {
    AndroidUtilities.runOnUIThread(
        new Runnable() {
          @Override
          public void run() {
            if (messages != null) {
              for (int a = 0; a < messages.size(); a++) {
                Long did = botKeyboardsByMids.get(messages.get(a));
                if (did != null) {
                  botKeyboards.remove(did);
                  botKeyboardsByMids.remove(messages.get(a));
                  NotificationCenter.getInstance()
                      .postNotificationName(NotificationCenter.botKeyboardDidLoaded, null, did);
                }
              }
            } else {
              botKeyboards.remove(did);
              NotificationCenter.getInstance()
                  .postNotificationName(NotificationCenter.botKeyboardDidLoaded, null, did);
            }
          }
        });
  }

  public static void loadBotKeyboard(final long did) {
    TLRPC.Message keyboard = botKeyboards.get(did);
    if (keyboard != null) {
      NotificationCenter.getInstance()
          .postNotificationName(NotificationCenter.botKeyboardDidLoaded, keyboard, did);
      return;
    }
    MessagesStorage.getInstance()
        .getStorageQueue()
        .postRunnable(
            new Runnable() {
              @Override
              public void run() {
                try {
                  TLRPC.Message botKeyboard = null;
                  SQLiteCursor cursor =
                      MessagesStorage.getInstance()
                          .getDatabase()
                          .queryFinalized(
                              String.format(
                                  Locale.US, "SELECT info FROM bot_keyboard WHERE uid = %d", did));
                  if (cursor.next()) {
                    ByteBufferDesc data;

                    if (!cursor.isNull(0)) {
                      data =
                          MessagesStorage.getInstance()
                              .getBuffersStorage()
                              .getFreeBuffer(cursor.byteArrayLength(0));
                      if (data != null && cursor.byteBufferValue(0, data.buffer) != 0) {
                        botKeyboard =
                            TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                      }
                      MessagesStorage.getInstance().getBuffersStorage().reuseFreeBuffer(data);
                    }
                  }
                  cursor.dispose();

                  if (botKeyboard != null) {
                    final TLRPC.Message botKeyboardFinal = botKeyboard;
                    AndroidUtilities.runOnUIThread(
                        new Runnable() {
                          @Override
                          public void run() {
                            NotificationCenter.getInstance()
                                .postNotificationName(
                                    NotificationCenter.botKeyboardDidLoaded, botKeyboardFinal, did);
                          }
                        });
                  }
                } catch (Exception e) {
                  FileLog.e("tmessages", e);
                }
              }
            });
  }

  public static void loadBotInfo(final int uid, boolean cache, final int classGuid) {
    if (cache) {
      TLRPC.BotInfo botInfo = botInfos.get(uid);
      if (botInfo != null) {
        NotificationCenter.getInstance()
            .postNotificationName(NotificationCenter.botInfoDidLoaded, botInfo, classGuid);
        return;
      }
    }
    MessagesStorage.getInstance()
        .getStorageQueue()
        .postRunnable(
            new Runnable() {
              @Override
              public void run() {
                try {
                  TLRPC.BotInfo botInfo = null;
                  SQLiteCursor cursor =
                      MessagesStorage.getInstance()
                          .getDatabase()
                          .queryFinalized(
                              String.format(
                                  Locale.US, "SELECT info FROM bot_info WHERE uid = %d", uid));
                  if (cursor.next()) {
                    ByteBufferDesc data;

                    if (!cursor.isNull(0)) {
                      data =
                          MessagesStorage.getInstance()
                              .getBuffersStorage()
                              .getFreeBuffer(cursor.byteArrayLength(0));
                      if (data != null && cursor.byteBufferValue(0, data.buffer) != 0) {
                        botInfo = TLRPC.BotInfo.TLdeserialize(data, data.readInt32(false), false);
                      }
                      MessagesStorage.getInstance().getBuffersStorage().reuseFreeBuffer(data);
                    }
                  }
                  cursor.dispose();

                  if (botInfo != null) {
                    final TLRPC.BotInfo botInfoFinal = botInfo;
                    AndroidUtilities.runOnUIThread(
                        new Runnable() {
                          @Override
                          public void run() {
                            NotificationCenter.getInstance()
                                .postNotificationName(
                                    NotificationCenter.botInfoDidLoaded, botInfoFinal, classGuid);
                          }
                        });
                  }
                } catch (Exception e) {
                  FileLog.e("tmessages", e);
                }
              }
            });
  }

  public static void putBotKeyboard(final long did, final TLRPC.Message message) {
    if (message == null) {
      return;
    }
    try {
      int mid = 0;
      SQLiteCursor cursor =
          MessagesStorage.getInstance()
              .getDatabase()
              .queryFinalized(
                  String.format(Locale.US, "SELECT mid FROM bot_keyboard WHERE uid = %d", did));
      if (cursor.next()) {
        mid = cursor.intValue(0);
      }
      cursor.dispose();
      if (mid >= message.id) {
        return;
      }

      SQLitePreparedStatement state =
          MessagesStorage.getInstance()
              .getDatabase()
              .executeFast("REPLACE INTO bot_keyboard VALUES(?, ?, ?)");
      state.requery();
      ByteBufferDesc data =
          MessagesStorage.getInstance().getBuffersStorage().getFreeBuffer(message.getObjectSize());
      message.serializeToStream(data);
      state.bindLong(1, did);
      state.bindInteger(2, message.id);
      state.bindByteBuffer(3, data.buffer);
      state.step();
      MessagesStorage.getInstance().getBuffersStorage().reuseFreeBuffer(data);
      state.dispose();

      AndroidUtilities.runOnUIThread(
          new Runnable() {
            @Override
            public void run() {
              TLRPC.Message old = botKeyboards.put(did, message);
              if (old != null) {
                botKeyboardsByMids.remove(old.id);
              }
              botKeyboardsByMids.put(message.id, did);
              NotificationCenter.getInstance()
                  .postNotificationName(NotificationCenter.botKeyboardDidLoaded, message, did);
            }
          });
    } catch (Exception e) {
      FileLog.e("tmessages", e);
    }
  }

  public static void putBotInfo(final TLRPC.BotInfo botInfo) {
    if (botInfo == null || botInfo instanceof TLRPC.TL_botInfoEmpty) {
      return;
    }
    botInfos.put(botInfo.user_id, botInfo);
    MessagesStorage.getInstance()
        .getStorageQueue()
        .postRunnable(
            new Runnable() {
              @Override
              public void run() {
                try {
                  SQLitePreparedStatement state =
                      MessagesStorage.getInstance()
                          .getDatabase()
                          .executeFast("REPLACE INTO bot_info(uid, info) VALUES(?, ?)");
                  state.requery();
                  ByteBufferDesc data =
                      MessagesStorage.getInstance()
                          .getBuffersStorage()
                          .getFreeBuffer(botInfo.getObjectSize());
                  botInfo.serializeToStream(data);
                  state.bindInteger(1, botInfo.user_id);
                  state.bindByteBuffer(2, data.buffer);
                  state.step();
                  MessagesStorage.getInstance().getBuffersStorage().reuseFreeBuffer(data);
                  state.dispose();
                } catch (Exception e) {
                  FileLog.e("tmessages", e);
                }
              }
            });
  }
}
