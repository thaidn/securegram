/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.adapters;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.ContactsController;
import org.telegram.android.LocaleController;
import org.telegram.android.MessageObject;
import org.telegram.android.MessagesController;
import org.telegram.android.MessagesStorage;
import org.telegram.android.support.widget.RecyclerView;
import org.telegram.messenger.ByteBufferDesc;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.SQLite.SQLiteCursor;
import org.telegram.ui.cells.DialogCell;
import org.telegram.ui.cells.GreySectionCell;
import org.telegram.ui.cells.HashtagSearchCell;
import org.telegram.ui.cells.LoadingCell;
import org.telegram.ui.cells.ProfileSearchCell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class DialogsSearchAdapter extends BaseSearchAdapterRecycler {

  private Context mContext;
  private Timer searchTimer;
  private ArrayList<TLObject> searchResult = new ArrayList<>();
  private ArrayList<CharSequence> searchResultNames = new ArrayList<>();
  private ArrayList<MessageObject> searchResultMessages = new ArrayList<>();
  private ArrayList<String> searchResultHashtags = new ArrayList<>();
  private String lastSearchText;
  private long reqId = 0;
  private int lastReqId;
  private MessagesActivitySearchAdapterDelegate delegate;
  private int needMessagesSearch;
  private boolean messagesSearchEndReached;
  private String lastMessagesSearchString;
  private int lastSearchId = 0;

  private class Holder extends RecyclerView.ViewHolder {

    public Holder(View itemView) {
      super(itemView);
    }
  }

  private class DialogSearchResult {
    public TLObject object;
    public int date;
    public CharSequence name;
  }

  public interface MessagesActivitySearchAdapterDelegate {
    void searchStateChanged(boolean searching);
  }

  public DialogsSearchAdapter(Context context, int messagesSearch) {
    mContext = context;
    needMessagesSearch = messagesSearch;
  }

  public void setDelegate(MessagesActivitySearchAdapterDelegate delegate) {
    this.delegate = delegate;
  }

  public boolean isMessagesSearchEndReached() {
    return messagesSearchEndReached;
  }

  public void loadMoreSearchMessages() {
    searchMessagesInternal(lastMessagesSearchString);
  }

  public String getLastSearchString() {
    return lastMessagesSearchString;
  }

  private void searchMessagesInternal(final String query) {
    if (needMessagesSearch == 0) {
      return;
    }
    if (reqId != 0) {
      ConnectionsManager.getInstance().cancelRpc(reqId, true);
      reqId = 0;
    }
    if (query == null || query.length() == 0) {
      searchResultMessages.clear();
      lastReqId = 0;
      lastMessagesSearchString = null;
      notifyDataSetChanged();
      if (delegate != null) {
        delegate.searchStateChanged(false);
      }
      return;
    }
    final TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
    req.limit = 20;
    req.peer = new TLRPC.TL_inputPeerEmpty();
    req.q = query;
    if (lastMessagesSearchString != null
        && query.equals(lastMessagesSearchString)
        && !searchResultMessages.isEmpty()) {
      req.max_id = searchResultMessages.get(searchResultMessages.size() - 1).getId();
    }
    lastMessagesSearchString = query;
    req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
    final int currentReqId = ++lastReqId;
    if (delegate != null) {
      delegate.searchStateChanged(true);
    }
    reqId =
        ConnectionsManager.getInstance()
            .performRpc(
                req,
                new RPCRequest.RPCRequestDelegate() {
                  @Override
                  public void run(final TLObject response, final TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(
                        new Runnable() {
                          @Override
                          public void run() {
                            if (currentReqId == lastReqId) {
                              if (error == null) {
                                TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                                MessagesStorage.getInstance()
                                    .putUsersAndChats(res.users, res.chats, true, true);
                                MessagesController.getInstance().putUsers(res.users, false);
                                MessagesController.getInstance().putChats(res.chats, false);
                                if (req.max_id == 0) {
                                  searchResultMessages.clear();
                                }
                                for (TLRPC.Message message : res.messages) {
                                  searchResultMessages.add(new MessageObject(message, null, false));
                                }
                                messagesSearchEndReached = res.messages.size() != 20;
                                notifyDataSetChanged();
                              }
                            }
                            if (delegate != null) {
                              delegate.searchStateChanged(false);
                            }
                            reqId = 0;
                          }
                        });
                  }
                },
                true,
                RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors);
  }

  private void searchDialogsInternal(
      final String query, final int dialogsType, final int searchId) {
    if (needMessagesSearch == 2) {
      return;
    }
    MessagesStorage.getInstance()
        .getStorageQueue()
        .postRunnable(
            new Runnable() {
              @Override
              public void run() {
                try {
                  String search1 = query.trim().toLowerCase();
                  if (search1.length() == 0) {
                    lastSearchId = -1;
                    updateSearchResults(
                        new ArrayList<TLObject>(),
                        new ArrayList<CharSequence>(),
                        new ArrayList<TLRPC.User>(),
                        lastSearchId);
                    return;
                  }
                  String search2 = LocaleController.getInstance().getTranslitString(search1);
                  if (search1.equals(search2) || search2.length() == 0) {
                    search2 = null;
                  }
                  String search[] = new String[1 + (search2 != null ? 1 : 0)];
                  search[0] = search1;
                  if (search2 != null) {
                    search[1] = search2;
                  }

                  ArrayList<Integer> usersToLoad = new ArrayList<>();
                  ArrayList<Integer> chatsToLoad = new ArrayList<>();
                  ArrayList<Integer> encryptedToLoad = new ArrayList<>();
                  ArrayList<TLRPC.User> encUsers = new ArrayList<>();
                  int resultCount = 0;

                  HashMap<Long, DialogSearchResult> dialogsResult = new HashMap<>();
                  SQLiteCursor cursor =
                      MessagesStorage.getInstance()
                          .getDatabase()
                          .queryFinalized(
                              "SELECT did, date FROM dialogs ORDER BY date DESC LIMIT 200");
                  while (cursor.next()) {
                    long id = cursor.longValue(0);
                    DialogSearchResult dialogSearchResult = new DialogSearchResult();
                    dialogSearchResult.date = cursor.intValue(1);
                    dialogsResult.put(id, dialogSearchResult);

                    int lower_id = (int) id;
                    int high_id = (int) (id >> 32);
                    if (lower_id != 0) {
                      if (high_id == 1) {
                        if (dialogsType == 0 && !chatsToLoad.contains(lower_id)) {
                          chatsToLoad.add(lower_id);
                        }
                      } else {
                        if (lower_id > 0) {
                          if (dialogsType != 2 && !usersToLoad.contains(lower_id)) {
                            usersToLoad.add(lower_id);
                          }
                        } else {
                          if (!chatsToLoad.contains(-lower_id)) {
                            chatsToLoad.add(-lower_id);
                          }
                        }
                      }
                    } else if (dialogsType == 0) {
                      if (!encryptedToLoad.contains(high_id)) {
                        encryptedToLoad.add(high_id);
                      }
                    }
                  }
                  cursor.dispose();

                  if (!usersToLoad.isEmpty()) {
                    cursor =
                        MessagesStorage.getInstance()
                            .getDatabase()
                            .queryFinalized(
                                String.format(
                                    Locale.US,
                                    "SELECT data, status, name FROM users WHERE uid IN(%s)",
                                    TextUtils.join(",", usersToLoad)));
                    while (cursor.next()) {
                      String name = cursor.stringValue(2);
                      String tName = LocaleController.getInstance().getTranslitString(name);
                      if (name.equals(tName)) {
                        tName = null;
                      }
                      String username = null;
                      int usernamePos = name.lastIndexOf(";;;");
                      if (usernamePos != -1) {
                        username = name.substring(usernamePos + 3);
                      }
                      int found = 0;
                      for (String q : search) {
                        if (name.startsWith(q)
                            || name.contains(" " + q)
                            || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                          found = 1;
                        } else if (username != null && username.startsWith(q)) {
                          found = 2;
                        }
                        if (found != 0) {
                          ByteBufferDesc data =
                              MessagesStorage.getInstance()
                                  .getBuffersStorage()
                                  .getFreeBuffer(cursor.byteArrayLength(0));
                          if (data != null && cursor.byteBufferValue(0, data.buffer) != 0) {
                            TLRPC.User user =
                                TLRPC.User.TLdeserialize(data, data.readInt32(false), false);
                            DialogSearchResult dialogSearchResult =
                                dialogsResult.get((long) user.id);
                            if (user.status != null) {
                              user.status.expires = cursor.intValue(1);
                            }
                            if (found == 1) {
                              dialogSearchResult.name =
                                  AndroidUtilities.generateSearchName(
                                      user.first_name, user.last_name, q);
                            } else {
                              dialogSearchResult.name =
                                  AndroidUtilities.generateSearchName(
                                      "@" + user.username, null, "@" + q);
                            }
                            dialogSearchResult.object = user;
                            resultCount++;
                          }
                          MessagesStorage.getInstance().getBuffersStorage().reuseFreeBuffer(data);
                          break;
                        }
                      }
                    }
                    cursor.dispose();
                  }

                  if (!chatsToLoad.isEmpty()) {
                    cursor =
                        MessagesStorage.getInstance()
                            .getDatabase()
                            .queryFinalized(
                                String.format(
                                    Locale.US,
                                    "SELECT data, name FROM chats WHERE uid IN(%s)",
                                    TextUtils.join(",", chatsToLoad)));
                    while (cursor.next()) {
                      String name = cursor.stringValue(1);
                      String tName = LocaleController.getInstance().getTranslitString(name);
                      if (name.equals(tName)) {
                        tName = null;
                      }
                      for (String q : search) {
                        if (name.startsWith(q)
                            || name.contains(" " + q)
                            || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                          ByteBufferDesc data =
                              MessagesStorage.getInstance()
                                  .getBuffersStorage()
                                  .getFreeBuffer(cursor.byteArrayLength(0));
                          if (data != null && cursor.byteBufferValue(0, data.buffer) != 0) {
                            TLRPC.Chat chat =
                                TLRPC.Chat.TLdeserialize(data, data.readInt32(false), false);
                            long dialog_id;
                            if (chat.id > 0) {
                              dialog_id = -chat.id;
                            } else {
                              dialog_id = AndroidUtilities.makeBroadcastId(chat.id);
                            }
                            DialogSearchResult dialogSearchResult = dialogsResult.get(dialog_id);
                            dialogSearchResult.name =
                                AndroidUtilities.generateSearchName(chat.title, null, q);
                            dialogSearchResult.object = chat;
                            resultCount++;
                          }
                          MessagesStorage.getInstance().getBuffersStorage().reuseFreeBuffer(data);
                          break;
                        }
                      }
                    }
                    cursor.dispose();
                  }

                  if (!encryptedToLoad.isEmpty()) {
                    cursor =
                        MessagesStorage.getInstance()
                            .getDatabase()
                            .queryFinalized(
                                String.format(
                                    Locale.US,
                                    "SELECT q.data, u.name, q.user, q.g, q.authkey, q.ttl, u.data, u.status, q.layer, q.seq_in, q.seq_out, q.use_count, q.exchange_id, q.key_date, q.fprint, q.fauthkey, q.khash FROM enc_chats as q INNER JOIN users as u ON q.user = u.uid WHERE q.uid IN(%s)",
                                    TextUtils.join(",", encryptedToLoad)));
                    while (cursor.next()) {
                      String name = cursor.stringValue(1);
                      String tName = LocaleController.getInstance().getTranslitString(name);
                      if (name.equals(tName)) {
                        tName = null;
                      }

                      String username = null;
                      int usernamePos = name.lastIndexOf(";;;");
                      if (usernamePos != -1) {
                        username = name.substring(usernamePos + 2);
                      }
                      int found = 0;
                      for (String q : search) {
                        if (name.startsWith(q)
                            || name.contains(" " + q)
                            || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                          found = 1;
                        } else if (username != null && username.startsWith(q)) {
                          found = 2;
                        }

                        if (found != 0) {
                          ByteBufferDesc data =
                              MessagesStorage.getInstance()
                                  .getBuffersStorage()
                                  .getFreeBuffer(cursor.byteArrayLength(0));
                          ByteBufferDesc data2 =
                              MessagesStorage.getInstance()
                                  .getBuffersStorage()
                                  .getFreeBuffer(cursor.byteArrayLength(6));
                          if (data != null
                              && cursor.byteBufferValue(0, data.buffer) != 0
                              && cursor.byteBufferValue(6, data2.buffer) != 0) {
                            TLRPC.EncryptedChat chat =
                                TLRPC.EncryptedChat.TLdeserialize(
                                    data, data.readInt32(false), false);
                            DialogSearchResult dialogSearchResult =
                                dialogsResult.get((long) chat.id << 32);

                            chat.user_id = cursor.intValue(2);
                            chat.a_or_b = cursor.byteArrayValue(3);
                            chat.auth_key = cursor.byteArrayValue(4);
                            chat.ttl = cursor.intValue(5);
                            chat.layer = cursor.intValue(8);
                            chat.seq_in = cursor.intValue(9);
                            chat.seq_out = cursor.intValue(10);
                            int use_count = cursor.intValue(11);
                            chat.key_use_count_in = (short) (use_count >> 16);
                            chat.key_use_count_out = (short) (use_count);
                            chat.exchange_id = cursor.longValue(12);
                            chat.key_create_date = cursor.intValue(13);
                            chat.future_key_fingerprint = cursor.longValue(14);
                            chat.future_auth_key = cursor.byteArrayValue(15);
                            chat.key_hash = cursor.byteArrayValue(16);

                            TLRPC.User user =
                                TLRPC.User.TLdeserialize(data2, data2.readInt32(false), false);
                            if (user.status != null) {
                              user.status.expires = cursor.intValue(7);
                            }
                            if (found == 1) {
                              dialogSearchResult.name =
                                  AndroidUtilities.replaceTags(
                                      "<c#ff00a60e>"
                                          + ContactsController.formatName(
                                              user.first_name, user.last_name)
                                          + "</c>");
                            } else {
                              dialogSearchResult.name =
                                  AndroidUtilities.generateSearchName(
                                      "@" + user.username, null, "@" + q);
                            }
                            dialogSearchResult.object = chat;
                            encUsers.add(user);
                            resultCount++;
                          }
                          MessagesStorage.getInstance().getBuffersStorage().reuseFreeBuffer(data);
                          MessagesStorage.getInstance().getBuffersStorage().reuseFreeBuffer(data2);
                          break;
                        }
                      }
                    }
                    cursor.dispose();
                  }

                  ArrayList<DialogSearchResult> searchResults = new ArrayList<>(resultCount);
                  for (DialogSearchResult dialogSearchResult : dialogsResult.values()) {
                    if (dialogSearchResult.object != null && dialogSearchResult.name != null) {
                      searchResults.add(dialogSearchResult);
                    }
                  }

                  Collections.sort(
                      searchResults,
                      new Comparator<DialogSearchResult>() {
                        @Override
                        public int compare(DialogSearchResult lhs, DialogSearchResult rhs) {
                          if (lhs.date < rhs.date) {
                            return 1;
                          } else if (lhs.date > rhs.date) {
                            return -1;
                          }
                          return 0;
                        }
                      });

                  ArrayList<TLObject> resultArray = new ArrayList<>();
                  ArrayList<CharSequence> resultArrayNames = new ArrayList<>();

                  for (DialogSearchResult dialogSearchResult : searchResults) {
                    resultArray.add(dialogSearchResult.object);
                    resultArrayNames.add(dialogSearchResult.name);
                  }

                  if (dialogsType != 2) {
                    cursor =
                        MessagesStorage.getInstance()
                            .getDatabase()
                            .queryFinalized(
                                "SELECT u.data, u.status, u.name, u.uid FROM users as u INNER JOIN contacts as c ON u.uid = c.uid");
                    while (cursor.next()) {
                      int uid = cursor.intValue(3);
                      if (dialogsResult.containsKey((long) uid)) {
                        continue;
                      }
                      String name = cursor.stringValue(2);
                      String tName = LocaleController.getInstance().getTranslitString(name);
                      if (name.equals(tName)) {
                        tName = null;
                      }
                      String username = null;
                      int usernamePos = name.lastIndexOf(";;;");
                      if (usernamePos != -1) {
                        username = name.substring(usernamePos + 3);
                      }
                      int found = 0;
                      for (String q : search) {
                        if (name.startsWith(q)
                            || name.contains(" " + q)
                            || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                          found = 1;
                        } else if (username != null && username.startsWith(q)) {
                          found = 2;
                        }
                        if (found != 0) {
                          ByteBufferDesc data =
                              MessagesStorage.getInstance()
                                  .getBuffersStorage()
                                  .getFreeBuffer(cursor.byteArrayLength(0));
                          if (data != null && cursor.byteBufferValue(0, data.buffer) != 0) {
                            TLRPC.User user =
                                TLRPC.User.TLdeserialize(data, data.readInt32(false), false);
                            if (user.status != null) {
                              user.status.expires = cursor.intValue(1);
                            }
                            if (found == 1) {
                              resultArrayNames.add(
                                  AndroidUtilities.generateSearchName(
                                      user.first_name, user.last_name, q));
                            } else {
                              resultArrayNames.add(
                                  AndroidUtilities.generateSearchName(
                                      "@" + user.username, null, "@" + q));
                            }
                            resultArray.add(user);
                          }
                          MessagesStorage.getInstance().getBuffersStorage().reuseFreeBuffer(data);
                          break;
                        }
                      }
                    }
                    cursor.dispose();
                  }

                  updateSearchResults(resultArray, resultArrayNames, encUsers, searchId);
                } catch (Exception e) {
                  FileLog.e("tmessages", e);
                }
              }
            });
  }

  private void updateSearchResults(
      final ArrayList<TLObject> result,
      final ArrayList<CharSequence> names,
      final ArrayList<TLRPC.User> encUsers,
      final int searchId) {
    AndroidUtilities.runOnUIThread(
        new Runnable() {
          @Override
          public void run() {
            if (searchId != lastSearchId) {
              return;
            }
            for (TLObject obj : result) {
              if (obj instanceof TLRPC.User) {
                TLRPC.User user = (TLRPC.User) obj;
                MessagesController.getInstance().putUser(user, true);
              } else if (obj instanceof TLRPC.Chat) {
                TLRPC.Chat chat = (TLRPC.Chat) obj;
                MessagesController.getInstance().putChat(chat, true);
              } else if (obj instanceof TLRPC.EncryptedChat) {
                TLRPC.EncryptedChat chat = (TLRPC.EncryptedChat) obj;
                MessagesController.getInstance().putEncryptedChat(chat, true);
              }
            }
            for (TLRPC.User user : encUsers) {
              MessagesController.getInstance().putUser(user, true);
            }
            searchResult = result;
            searchResultNames = names;
            notifyDataSetChanged();
          }
        });
  }

  public String getLastSearchText() {
    return lastSearchText;
  }

  public boolean isGlobalSearch(int i) {
    return i > searchResult.size() && i <= globalSearch.size() + searchResult.size();
  }

  @Override
  public void clearRecentHashtags() {
    super.clearRecentHashtags();
    searchResultHashtags.clear();
    notifyDataSetChanged();
  }

  @Override
  protected void setHashtags(
      ArrayList<HashtagObject> arrayList, HashMap<String, HashtagObject> hashMap) {
    super.setHashtags(arrayList, hashMap);
    for (HashtagObject hashtagObject : arrayList) {
      searchResultHashtags.add(hashtagObject.hashtag);
    }
    if (delegate != null) {
      delegate.searchStateChanged(false);
    }
    notifyDataSetChanged();
  }

  public void searchDialogs(final String query, final int dialogsType) {
    if (query != null && lastSearchText != null && query.equals(lastSearchText)) {
      return;
    }
    try {
      if (searchTimer != null) {
        searchTimer.cancel();
        searchTimer = null;
      }
    } catch (Exception e) {
      FileLog.e("tmessages", e);
    }
    if (query == null || query.length() == 0) {
      hashtagsLoadedFromDb = false;
      searchResult.clear();
      searchResultNames.clear();
      searchResultHashtags.clear();
      if (needMessagesSearch != 2) {
        queryServerSearch(null);
      }
      searchMessagesInternal(null);
      notifyDataSetChanged();
    } else {
      if (needMessagesSearch != 2 && (query.startsWith("#") && query.length() == 1)) {
        messagesSearchEndReached = true;
        if (!hashtagsLoadedFromDb) {
          loadRecentHashtags();
          if (delegate != null) {
            delegate.searchStateChanged(true);
          }
          notifyDataSetChanged();
          return;
        }
        searchResultMessages.clear();
        searchResultHashtags.clear();
        for (HashtagObject hashtagObject : hashtags) {
          searchResultHashtags.add(hashtagObject.hashtag);
        }
        if (delegate != null) {
          delegate.searchStateChanged(false);
        }
        notifyDataSetChanged();
        return;
      } else {
        searchResultHashtags.clear();
      }
      final int searchId = ++lastSearchId;
      searchTimer = new Timer();
      searchTimer.schedule(
          new TimerTask() {
            @Override
            public void run() {
              try {
                cancel();
                searchTimer.cancel();
                searchTimer = null;
              } catch (Exception e) {
                FileLog.e("tmessages", e);
              }
              searchDialogsInternal(query, dialogsType, searchId);
              AndroidUtilities.runOnUIThread(
                  new Runnable() {
                    @Override
                    public void run() {
                      if (needMessagesSearch != 2) {
                        queryServerSearch(query);
                      }
                      searchMessagesInternal(query);
                    }
                  });
            }
          },
          200,
          300);
    }
  }

  @Override
  public int getItemCount() {
    if (!searchResultHashtags.isEmpty()) {
      return searchResultHashtags.size() + 1;
    }
    int count = searchResult.size();
    int globalCount = globalSearch.size();
    int messagesCount = searchResultMessages.size();
    if (globalCount != 0) {
      count += globalCount + 1;
    }
    if (messagesCount != 0) {
      count += messagesCount + 1 + (messagesSearchEndReached ? 0 : 1);
    }
    return count;
  }

  public Object getItem(int i) {
    if (!searchResultHashtags.isEmpty()) {
      return searchResultHashtags.get(i - 1);
    }
    int localCount = searchResult.size();
    int globalCount = globalSearch.isEmpty() ? 0 : globalSearch.size() + 1;
    int messagesCount = searchResultMessages.isEmpty() ? 0 : searchResultMessages.size() + 1;
    if (i >= 0 && i < localCount) {
      return searchResult.get(i);
    } else if (i > localCount && i < globalCount + localCount) {
      return globalSearch.get(i - localCount - 1);
    } else if (i > globalCount + localCount && i < globalCount + localCount + messagesCount) {
      return searchResultMessages.get(i - localCount - globalCount - 1);
    }
    return null;
  }

  @Override
  public long getItemId(int i) {
    return i;
  }

  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    View view = null;
    switch (viewType) {
      case 0:
        view = new ProfileSearchCell(mContext);
        view.setBackgroundResource(R.drawable.list_selector);
        break;
      case 1:
        view = new GreySectionCell(mContext);
        break;
      case 2:
        view = new DialogCell(mContext);
        break;
      case 3:
        view = new LoadingCell(mContext);
        break;
      case 4:
        view = new HashtagSearchCell(mContext);
        break;
    }
    return new Holder(view);
  }

  @Override
  public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
    switch (holder.getItemViewType()) {
      case 0:
        {
          ProfileSearchCell cell = (ProfileSearchCell) holder.itemView;

          TLRPC.User user = null;
          TLRPC.Chat chat = null;
          TLRPC.EncryptedChat encryptedChat = null;

          int localCount = searchResult.size();
          int globalCount = globalSearch.isEmpty() ? 0 : globalSearch.size() + 1;

          cell.useSeparator =
              (position != getItemCount() - 1
                  && position != localCount - 1
                  && position != localCount + globalCount - 1);
          Object obj = getItem(position);
          if (obj instanceof TLRPC.User) {
            user = (TLRPC.User) obj;
          } else if (obj instanceof TLRPC.Chat) {
            chat = MessagesController.getInstance().getChat(((TLRPC.Chat) obj).id);
          } else if (obj instanceof TLRPC.EncryptedChat) {
            encryptedChat =
                MessagesController.getInstance().getEncryptedChat(((TLRPC.EncryptedChat) obj).id);
            user = MessagesController.getInstance().getUser(encryptedChat.user_id);
          }

          CharSequence username = null;
          CharSequence name = null;
          if (position < searchResult.size()) {
            name = searchResultNames.get(position);
            if (name != null
                && user != null
                && user.username != null
                && user.username.length() > 0) {
              if (name.toString().startsWith("@" + user.username)) {
                username = name;
                name = null;
              }
            }
          } else if (position > searchResult.size() && user != null && user.username != null) {
            String foundUserName = lastFoundUsername;
            if (foundUserName.startsWith("@")) {
              foundUserName = foundUserName.substring(1);
            }
            try {
              username =
                  AndroidUtilities.replaceTags(
                      String.format(
                          "<c#ff4d83b3>@%s</c>%s",
                          user.username.substring(0, foundUserName.length()),
                          user.username.substring(foundUserName.length())));
            } catch (Exception e) {
              username = user.username;
              FileLog.e("tmessages", e);
            }
          }

          cell.setData(user, chat, encryptedChat, name, username);
          break;
        }
      case 1:
        {
          GreySectionCell cell = (GreySectionCell) holder.itemView;
          if (!searchResultHashtags.isEmpty()) {
            cell.setText(LocaleController.getString("Hashtags", R.string.Hashtags).toUpperCase());
          } else if (!globalSearch.isEmpty() && position == searchResult.size()) {
            cell.setText(LocaleController.getString("GlobalSearch", R.string.GlobalSearch));
          } else {
            cell.setText(LocaleController.getString("SearchMessages", R.string.SearchMessages));
          }
          break;
        }
      case 2:
        {
          DialogCell cell = (DialogCell) holder.itemView;
          cell.useSeparator = (position != getItemCount() - 1);
          MessageObject messageObject = (MessageObject) getItem(position);
          cell.setDialog(
              messageObject.getDialogId(), messageObject, messageObject.messageOwner.date);
          break;
        }
      case 3:
        {
          break;
        }
      case 4:
        {
          HashtagSearchCell cell = (HashtagSearchCell) holder.itemView;
          cell.setText(searchResultHashtags.get(position - 1));
          cell.setNeedDivider(position != searchResultHashtags.size());
          break;
        }
    }
  }

  @Override
  public int getItemViewType(int i) {
    if (!searchResultHashtags.isEmpty()) {
      return i == 0 ? 1 : 4;
    }
    int localCount = searchResult.size();
    int globalCount = globalSearch.isEmpty() ? 0 : globalSearch.size() + 1;
    int messagesCount = searchResultMessages.isEmpty() ? 0 : searchResultMessages.size() + 1;
    if (i >= 0 && i < localCount || i > localCount && i < globalCount + localCount) {
      return 0;
    } else if (i > globalCount + localCount && i < globalCount + localCount + messagesCount) {
      return 2;
    } else if (messagesCount != 0 && i == globalCount + localCount + messagesCount) {
      return 3;
    }
    return 1;
  }
}
