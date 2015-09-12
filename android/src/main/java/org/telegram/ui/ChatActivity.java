/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.ContactsController;
import org.telegram.android.Emoji;
import org.telegram.android.ImageReceiver;
import org.telegram.android.LocaleController;
import org.telegram.android.MediaController;
import org.telegram.android.MessageObject;
import org.telegram.android.MessagesController;
import org.telegram.android.MessagesStorage;
import org.telegram.android.NotificationCenter;
import org.telegram.android.NotificationsController;
import org.telegram.android.SecretChatHelper;
import org.telegram.android.SendMessagesHelper;
import org.telegram.android.UserObject;
import org.telegram.android.VideoEditedInfo;
import org.telegram.android.animationcompat.AnimatorListenerAdapterProxy;
import org.telegram.android.animationcompat.AnimatorSetProxy;
import org.telegram.android.animationcompat.ObjectAnimatorProxy;
import org.telegram.android.animationcompat.ViewProxy;
import org.telegram.android.query.BotQuery;
import org.telegram.android.query.MessagesSearchQuery;
import org.telegram.android.query.ReplyMessageQuery;
import org.telegram.android.query.StickersQuery;
import org.telegram.android.support.widget.LinearLayoutManager;
import org.telegram.android.support.widget.RecyclerView;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.SerializedData;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.phoneformat.PhoneFormat;
import org.telegram.ui.actionbar.ActionBar;
import org.telegram.ui.actionbar.ActionBarMenu;
import org.telegram.ui.actionbar.ActionBarMenuItem;
import org.telegram.ui.actionbar.BaseFragment;
import org.telegram.ui.actionbar.BottomSheet;
import org.telegram.ui.adapters.MentionsAdapter;
import org.telegram.ui.adapters.StickersAdapter;
import org.telegram.ui.cells.BotHelpCell;
import org.telegram.ui.cells.ChatActionCell;
import org.telegram.ui.cells.ChatAudioCell;
import org.telegram.ui.cells.ChatBaseCell;
import org.telegram.ui.cells.ChatContactCell;
import org.telegram.ui.cells.ChatLoadingCell;
import org.telegram.ui.cells.ChatMediaCell;
import org.telegram.ui.cells.ChatMessageCell;
import org.telegram.ui.cells.ChatMusicCell;
import org.telegram.ui.cells.ChatUnreadCell;
import org.telegram.ui.components.AlertsCreator;
import org.telegram.ui.components.AvatarDrawable;
import org.telegram.ui.components.BackupImageView;
import org.telegram.ui.components.ChatActivityEnterView;
import org.telegram.ui.components.ChatAttachView;
import org.telegram.ui.components.FrameLayoutFixed;
import org.telegram.ui.components.LayoutHelper;
import org.telegram.ui.components.RecordStatusDrawable;
import org.telegram.ui.components.RecyclerListView;
import org.telegram.ui.components.ResourceLoader;
import org.telegram.ui.components.SendingFileExDrawable;
import org.telegram.ui.components.SizeNotifierFrameLayout;
import org.telegram.ui.components.TimerDrawable;
import org.telegram.ui.components.TypingDotsDrawable;
import org.telegram.ui.components.WebFrameLayout;
import org.whispersystems.libaxolotl.AxolotlAddress;

import xyz.securegram.R;
import xyz.securegram.axolotl.AxolotlController;

import java.io.File;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

public class ChatActivity extends BaseFragment
    implements NotificationCenter.NotificationCenterDelegate,
        DialogsActivity.MessagesActivityDelegate, PhotoViewer.PhotoViewerProvider {
  private static final String TAG = ChatActivity.class.getName();

  protected TLRPC.Chat currentChat;
  protected TLRPC.User currentUser;
  protected TLRPC.EncryptedChat currentEncryptedChat;
  private boolean userBlocked = false;

  private ArrayList<ChatMessageCell> chatMessageCellsCache = new ArrayList<>();
  private ArrayList<ChatMediaCell> chatMediaCellsCache = new ArrayList<>();

  private FrameLayout progressView;
  private FrameLayout bottomOverlay;
  private ChatActivityEnterView chatActivityEnterView;
  private ImageView timeItem;
  private View timeItem2;
  private TimerDrawable timerDrawable;
  private ActionBarMenuItem menuItem;
  private ActionBarMenuItem attachItem;
  private ActionBarMenuItem headerItem;
  private ActionBarMenuItem searchItem;
  private ActionBarMenuItem searchUpItem;
  private ActionBarMenuItem searchDownItem;
  private TextView addContactItem;
  private RecyclerListView chatListView;
  private LinearLayoutManager chatLayoutManager;
  private ChatActivityAdapter chatAdapter;
  private BackupImageView avatarImageView;
  private TextView bottomOverlayChatText;
  private FrameLayout bottomOverlayChat;
  private TypingDotsDrawable typingDotsDrawable;
  private RecordStatusDrawable recordStatusDrawable;
  private SendingFileExDrawable sendingFileDrawable;
  private FrameLayout emptyViewContainer;
  private ArrayList<View> actionModeViews = new ArrayList<>();
  private TextView nameTextView;
  private TextView onlineTextView;
  private FrameLayout avatarContainer;
  private TextView bottomOverlayText;
  private TextView secretViewStatusTextView;
  private TextView selectedMessagesCountTextView;
  private RecyclerListView stickersListView;
  private StickersAdapter stickersAdapter;
  private FrameLayout stickersPanel;
  private TextView muteItem;
  private ImageView pagedownButton;
  private BackupImageView replyImageView;
  private TextView replyNameTextView;
  private TextView replyObjectTextView;
  private ImageView replyIconImageView;
  private MentionsAdapter mentionsAdapter;
  private ListView mentionListView;
  private AnimatorSetProxy mentionListAnimation;
  private ChatAttachView chatAttachView;
  private BottomSheet chatAttachViewSheet;

  private boolean allowStickersPanel;
  private AnimatorSetProxy runningAnimation;

  private MessageObject selectedObject;
  private ArrayList<MessageObject> forwardingMessages;
  private MessageObject forwaringMessage;
  private MessageObject replyingMessageObject;
  private boolean paused = true;
  private boolean wasPaused = false;
  private boolean readWhenResume = false;
  private TLRPC.FileLocation replyImageLocation;
  private long linkSearchRequestId;
  private TLRPC.WebPage foundWebPage;
  private String pendingLinkSearchString;
  private Runnable waitingForCharaterEnterRunnable;

  private boolean openAnimationEnded = false;

  private int readWithDate = 0;
  private int readWithMid = 0;
  private boolean scrollToTopOnResume = false;
  private boolean scrollToTopUnReadOnResume = false;
  private long dialog_id;
  private boolean isBroadcast = false;
  private HashMap<Integer, MessageObject> selectedMessagesIds = new HashMap<>();
  private HashMap<Integer, MessageObject> selectedMessagesCanCopyIds = new HashMap<>();

  private HashMap<Integer, MessageObject> messagesDict = new HashMap<>();
  private HashMap<String, ArrayList<MessageObject>> messagesByDays = new HashMap<>();
  protected ArrayList<MessageObject> messages = new ArrayList<>();
  private int maxMessageId = Integer.MAX_VALUE;
  private int minMessageId = Integer.MIN_VALUE;
  private int maxDate = Integer.MIN_VALUE;
  private boolean endReached = false;
  private boolean loading = false;
  private boolean cacheEndReaced = false;
  private boolean firstLoading = true;
  private int loadsCount = 0;

  private int startLoadFromMessageId = 0;
  private boolean needSelectFromMessageId;
  private int returnToMessageId = 0;

  private int minDate = 0;
  private boolean first = true;
  private int unread_to_load = 0;
  private int first_unread_id = 0;
  private int last_message_id = 0;
  private int first_message_id = 0;
  private boolean forwardEndReached = true;
  private boolean loadingForward = false;
  private MessageObject unreadMessageObject = null;
  private MessageObject scrollToMessage = null;
  private int highlightMessageId = Integer.MAX_VALUE;
  private boolean scrollToMessageMiddleScreen = false;

  private String currentPicturePath;

  private Rect scrollRect = new Rect();

  protected TLRPC.ChatParticipants info = null;
  private int onlineCount = -1;

  private HashMap<Integer, TLRPC.BotInfo> botInfo = new HashMap<>();
  private String botUser;
  private MessageObject botButtons;
  private MessageObject botReplyButtons;
  private int botsCount;
  private boolean hasBotsCommands;

  private CharSequence lastPrintString;
  private String lastStatus;
  private int lastStatusDrawable;

  private long chatEnterTime = 0;
  private long chatLeaveTime = 0;

  private String startVideoEdit = null;

  private String abelianAvatarPath = null;
  private boolean loadingAbelianIdentity = true;


  private Runnable openSecretPhotoRunnable = null;
  private float startX = 0;
  private float startY = 0;

  private static final int copy = 10;
  private static final int forward = 11;
  private static final int delete = 12;
  private static final int chat_enc_timer = 13;
  private static final int chat_menu_attach = 14;
  private static final int clear_history = 15;
  private static final int delete_chat = 16;
  private static final int share_contact = 17;
  private static final int mute = 18;
  private static final int reply = 19;

  private static final int bot_help = 30;
  private static final int bot_settings = 31;

  private static final int attach_photo = 0;
  private static final int attach_gallery = 1;
  private static final int attach_video = 2;
  private static final int attach_audio = 3;
  private static final int attach_document = 4;
  private static final int attach_contact = 5;
  private static final int attach_location = 6;

  private static final int search = 40;
  private static final int search_up = 41;
  private static final int search_down = 42;

  private static final int id_chat_compose_panel = 1000;

  RecyclerListView.OnItemLongClickListener onItemLongClickListener =
      new RecyclerListView.OnItemLongClickListener() {
        @Override
        public void onItemClick(View view, int position) {
          if (!actionBar.isActionModeShowed()) {
            createMenu(view, false);
          }
        }
      };

  RecyclerListView.OnItemClickListener onItemClickListener =
      new RecyclerListView.OnItemClickListener() {
        @Override
        public void onItemClick(View view, int position) {
          if (actionBar.isActionModeShowed()) {
            processRowSelect(view);
            return;
          }
          createMenu(view, true);
        }
      };

  public ChatActivity(Bundle args) {
    super(args);
    
    notificationEvents = new int[] {
        NotificationCenter.messagesDidLoaded,
        NotificationCenter.emojiDidLoaded,
        NotificationCenter.updateInterfaces,
        NotificationCenter.didReceivedNewMessages,
        NotificationCenter.closeChats,
        NotificationCenter.messagesRead,
        NotificationCenter.messagesDeleted,
        NotificationCenter.messageReceivedByServer,
        NotificationCenter.messageReceivedByAck,
        NotificationCenter.messageSendError,
        NotificationCenter.chatInfoDidLoaded,
        NotificationCenter.contactsDidLoaded,
        NotificationCenter.encryptedChatUpdated,
        NotificationCenter.messagesReadEncrypted,
        NotificationCenter.removeAllMessagesFromDialog,
        NotificationCenter.audioProgressDidChanged,
        NotificationCenter.audioDidReset,
        NotificationCenter.audioPlayStateChanged,
        NotificationCenter.screenshotTook,
        NotificationCenter.blockedUsersDidLoaded,
        NotificationCenter.FileNewChunkAvailable,
        NotificationCenter.didCreatedNewDeleteTask,
        NotificationCenter.audioDidStarted,
        NotificationCenter.updateMessageMedia,
        NotificationCenter.replaceMessagesObjects,
        NotificationCenter.notificationsSettingsUpdated,
        NotificationCenter.didLoadedReplyMessages,
        NotificationCenter.didReceivedWebpages,
        NotificationCenter.didReceivedWebpagesInUpdates,
        NotificationCenter.messagesReadContent,
        NotificationCenter.botInfoDidLoaded,
        NotificationCenter.botKeyboardDidLoaded,
        NotificationCenter.chatSearchResultsAvailable,
        NotificationCenter.FileDidLoaded,
        NotificationCenter.FileDidFailedLoad,
        NotificationCenter.ABELIAN_IDENTITY_LOADED,
    };

  }

  private boolean createChatFromChatId(int id) {
    final int chatId = id;

    currentChat = MessagesController.getInstance().getChat(chatId);
    if (currentChat == null) {
      // TODO(thaidn): understand why it needs to use a semaphore.
      final Semaphore semaphore = new Semaphore(0);
      MessagesStorage.getInstance()
          .getStorageQueue()
          .postRunnable(
              new Runnable() {
                @Override
                public void run() {
                  currentChat = MessagesStorage.getInstance().getChat(chatId);
                  semaphore.release();
                }
              });
      try {
        semaphore.acquire();
      } catch (Exception e) {
        FileLog.e("tmessages", e);
      }
      if (currentChat != null) {
        MessagesController.getInstance().putChat(currentChat, true /* fromCache */);
      } else {
        return false;
      }
    }
    if (chatId > 0) {
      dialog_id = -chatId;
    } else {
      isBroadcast = true;
      dialog_id = AndroidUtilities.makeBroadcastId(chatId);
    }
    Semaphore semaphore = null;
    if (isBroadcast) {
      semaphore = new Semaphore(0);
    }
    MessagesController.getInstance().loadChatInfo(currentChat.id, semaphore);
    if (isBroadcast) {
      try {
        semaphore.acquire();
      } catch (Exception e) {
        FileLog.e("tmessages", e);
      }
    }
    return true;
  }

  private boolean createChatFromUserId(int id) {
    final int userId = id;
    currentUser = MessagesController.getInstance().getUser(userId);
    if (currentUser == null) {
      final Semaphore semaphore = new Semaphore(0);
      MessagesStorage.getInstance()
          .getStorageQueue()
          .postRunnable(
              new Runnable() {
                @Override
                public void run() {
                  currentUser = MessagesStorage.getInstance().getUser(userId);
                  semaphore.release();
                }
              });
      try {
        semaphore.acquire();
      } catch (Exception e) {
        FileLog.e("tmessages", e);
      }
      if (currentUser != null) {
        MessagesController.getInstance().putUser(currentUser, true);
      } else {
        return false;
      }
    }
    abelianAvatarPath = AxolotlController.getInstance().loadAbelianAvatar(currentUser);
    if (abelianAvatarPath != null) {
      if (AxolotlController.getInstance().loadAbelianIdentity(currentUser)) {
        loadingAbelianIdentity = false;
      }
    }
    dialog_id = userId;
    botUser = arguments.getString("botUser");
    return true;
  }

  private boolean createChatFromEncId(int id) {
    final int encId = id;

    currentEncryptedChat = MessagesController.getInstance().getEncryptedChat(encId);
    if (currentEncryptedChat == null) {
      final Semaphore semaphore = new Semaphore(0);
      MessagesStorage.getInstance()
          .getStorageQueue()
          .postRunnable(
              new Runnable() {
                @Override
                public void run() {
                  currentEncryptedChat = MessagesStorage.getInstance().getEncryptedChat(encId);
                  semaphore.release();
                }
              });
      try {
        semaphore.acquire();
      } catch (Exception e) {
        FileLog.e("tmessages", e);
      }
      if (currentEncryptedChat != null) {
        MessagesController.getInstance().putEncryptedChat(currentEncryptedChat,
            true /* fromCache */);
      } else {
        return false;
      }
    }
    currentUser = MessagesController.getInstance().getUser(currentEncryptedChat.user_id);
    if (currentUser == null) {
      final Semaphore semaphore = new Semaphore(0);
      MessagesStorage.getInstance()
          .getStorageQueue()
          .postRunnable(
              new Runnable() {
                @Override
                public void run() {
                  currentUser =
                      MessagesStorage.getInstance().getUser(currentEncryptedChat.user_id);
                  semaphore.release();
                }
              });
      try {
        semaphore.acquire();
      } catch (Exception e) {
        FileLog.e("tmessages", e);
      }
      if (currentUser != null) {
        MessagesController.getInstance().putUser(currentUser, true);
      } else {
        return false;
      }
    }
    dialog_id = ((long) encId) << 32;
    maxMessageId = Integer.MIN_VALUE;
    minMessageId = Integer.MAX_VALUE;
    MediaController.getInstance().startMediaObserver();
    return true;
  }

  @Override
  public boolean onFragmentCreate() {
    final int chatId = arguments.getInt("chat_id", 0);
    final int userId = arguments.getInt("user_id", 0);
    final int encId = arguments.getInt("enc_id", 0);
    startLoadFromMessageId = arguments.getInt("message_id", 0);
    scrollToTopOnResume = arguments.getBoolean("scrollToTopOnResume", false);

    if ((chatId != 0 && !createChatFromChatId(chatId)) ||
        (userId != 0 && !createChatFromUserId(userId)) ||
        (encId != 0 && !createChatFromEncId(encId))) {
      return false;
    }

    super.onFragmentCreate();

    if (currentEncryptedChat == null && !isBroadcast) {
      BotQuery.loadBotKeyboard(dialog_id);
    }

    if (userId != 0 && (currentUser.flags & TLRPC.USER_FLAG_BOT) != 0) {
      BotQuery.loadBotInfo(userId, true, classGuid);
    } else if (info != null) {
      for (int a = 0; a < info.participants.size(); a++) {
        TLRPC.TL_chatParticipant participant = info.participants.get(a);
        TLRPC.User user = MessagesController.getInstance().getUser(participant.user_id);
        if (user != null && (user.flags & TLRPC.USER_FLAG_BOT) != 0) {
          BotQuery.loadBotInfo(user.id, true, classGuid);
        }
      }
    }

    loading = true;
    loadingAbelianIdentity = true;

    if (startLoadFromMessageId != 0) {
      needSelectFromMessageId = true;
      MessagesController.getInstance()
          .loadMessages(
              dialog_id,
              20 /* count */,
              startLoadFromMessageId /* maxId */,
              true /* fromCache */,
              0 /* midDate */,
              classGuid,
              3 /* loadType */,
              0 /* lastMessageId */,
              0 /* firstMessageId */,
              false /* allowCache */);
    } else {
      MessagesController.getInstance()
          .loadMessages(
              dialog_id,
              20,
              0,
              true,
              0,
              classGuid,
              2,
              0,
              0,
              true);
    }

    if (currentUser != null) {
      userBlocked = MessagesController.getInstance().blockedUsers.contains(currentUser.id);
    }

    typingDotsDrawable = new TypingDotsDrawable();
    typingDotsDrawable.setIsChat(currentChat != null);
    recordStatusDrawable = new RecordStatusDrawable();
    recordStatusDrawable.setIsChat(currentChat != null);
    sendingFileDrawable = new SendingFileExDrawable();
    sendingFileDrawable.setIsChat(currentChat != null);

    if (currentEncryptedChat != null
        && AndroidUtilities.getMyLayerVersion(currentEncryptedChat.layer)
            != SecretChatHelper.CURRENT_SECRET_CHAT_LAYER) {
      SecretChatHelper.getInstance().sendNotifyLayerMessage(currentEncryptedChat, null);
    }

    return true;
  }

  @Override
  public void onFragmentDestroy() {
    super.onFragmentDestroy();
    if (chatActivityEnterView != null) {
      chatActivityEnterView.onDestroy();
    }

    if (currentEncryptedChat != null) {
      MediaController.getInstance().stopMediaObserver();
    }
    if (currentUser != null) {
      MessagesController.getInstance().cancelLoadFullUser(currentUser.id);
    }
    if (getParentActivity() != null) {
      getParentActivity()
          .getWindow()
          .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
    }
    if (stickersAdapter != null) {
      stickersAdapter.onDestroy();
    }
    if (chatAttachView != null) {
      chatAttachView.onDestroy();
    }
    AndroidUtilities.unlockOrientation(getParentActivity());
    MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
    if (messageObject != null && !messageObject.isMusic()) {
      MediaController.getInstance().stopAudio();
    }
  }

  @Override
  public View createView(Context context) {
    for (int a = 0; a < 8; a++) {
      chatMessageCellsCache.add(new ChatMessageCell(context));
    }
    for (int a = 0; a < 4; a++) {
      chatMediaCellsCache.add(new ChatMediaCell(context));
    }

    lastPrintString = null;
    lastStatus = null;
    hasOwnBackground = true;
    chatAttachView = null;
    chatAttachViewSheet = null;

    ResourceLoader.loadResources(context);

    addHeader(context);
    addActionBarMenu();
    checkActionBarMenu();

    return createContentView(context);
  }

  private void showAttachMenu() {
    if (getParentActivity() == null) {
      return;
    }

    if (chatAttachView == null) {
      BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity());
      chatAttachView = new ChatAttachView(getParentActivity());
      chatAttachView.setDelegate(
          new ChatAttachView.ChatAttachViewDelegate() {
            @Override
            public void didPressedButton(int button) {
              if (button == 7) {
                chatAttachViewSheet.dismiss();
                HashMap<Integer, MediaController.PhotoEntry> selectedPhotos =
                    chatAttachView.getSelectedPhotos();
                if (!selectedPhotos.isEmpty()) {
                  ArrayList<String> photos = new ArrayList<>();
                  ArrayList<String> captions = new ArrayList<>();
                  for (HashMap.Entry<Integer, MediaController.PhotoEntry> entry :
                      selectedPhotos.entrySet()) {
                    photos.add(entry.getValue().path);
                    captions.add("");
                  }
                  SendMessagesHelper.prepareSendingPhotos(
                      photos, null, dialog_id, replyingMessageObject, captions);
                  showReplyPanel(false, null, null, null, false, true);
                }
                return;
              } else {
                chatAttachViewSheet.dismissWithButtonClick(button);
              }
              processSelectedAttach(button);
            }
          });
      builder.setDelegate(
          new BottomSheet.BottomSheetDelegate() {

            @Override
            public void onRevealAnimationStart(boolean open) {
              chatAttachView.onRevealAnimationStart(open);
            }

            @Override
            public void onRevealAnimationProgress(
                boolean open, float radius, int x, int y) {
              chatAttachView.onRevealAnimationProgress(open, radius, x, y);
            }

            @Override
            public void onRevealAnimationEnd(boolean open) {
              chatAttachView.onRevealAnimationEnd(open);
            }

            @Override
            public void onOpenAnimationEnd() {
              chatAttachView.onRevealAnimationEnd(true);
            }

            @Override
            public View getRevealView() {
              return menuItem;
            }
          });
      builder.setApplyTopPaddings(false);
      builder.setUseRevealAnimation();
      builder.setCustomView(chatAttachView);
      chatAttachViewSheet = builder.create();
    }

    chatAttachView.init(ChatActivity.this);
    showDialog(chatAttachViewSheet);
  }

  private void addActionBarMenu() {
    actionBar.setBackButtonImage(R.drawable.ic_ab_back);
    actionBar.setActionBarMenuOnItemClick(
        new ActionBar.ActionBarMenuOnItemClick() {
          @Override
          public void onItemClick(final int id) {
            switch (id) {
              case -1:
                finishFragment();
                break;
              case -2:
                selectedMessagesIds.clear();
                selectedMessagesCanCopyIds.clear();
                actionBar.hideActionMode();
                updateVisibleRows();
                break;
              case copy:
                copyChat();
                break;
              case delete:
                deleteChat();
                break;
              case forward:
                forwardChat();
                break;
              case chat_enc_timer:
                createEncryptedChatTimer();
                break;
              case clear_history:
              case delete_chat:
                clearChat(id);
                // TODO(thaidn): remove this.
                AxolotlController.getInstance().getStore().deleteAllSessions(
                    String.valueOf(currentUser.id));
                break;
              case share_contact:
                shareContact();
                break;
              case mute:
                muteChat();
                break;
              case reply:
                replyChat();
                break;
              case chat_menu_attach:
                showAttachMenu();
                break;
              case bot_help:
                SendMessagesHelper.getInstance().sendMessage("/help", dialog_id, null, null, false);
                break;
              case bot_settings:
                SendMessagesHelper.getInstance()
                    .sendMessage("/settings", dialog_id, null, null, false);
                break;
              case search:
                avatarContainer.setVisibility(View.GONE);
                headerItem.setVisibility(View.GONE);
                attachItem.setVisibility(View.GONE);
                searchItem.setVisibility(View.VISIBLE);
                searchUpItem.setVisibility(View.VISIBLE);
                searchDownItem.setVisibility(View.VISIBLE);
                updateSearchButtons(0);
                searchItem.openSearch();
                break;
              case search_up:
                MessagesSearchQuery.searchMessagesInChat(null, dialog_id, classGuid, 1);
                break;
              case search_down:
                MessagesSearchQuery.searchMessagesInChat(null, dialog_id, classGuid, 2);
                break;
            }
          }
        });

    ActionBarMenu menu = actionBar.createMenu();

    if (currentEncryptedChat == null && !isBroadcast) {
      addSearchMenu(menu);
    }

    headerItem = menu.addItem(0, R.drawable.ic_ab_other);
    if (searchItem != null) {
      headerItem.addSubItem(search, LocaleController.getString("Search", R.string.Search), 0);
    }
    if (currentUser != null) {
      addContactItem = headerItem.addSubItem(share_contact, "", 0);
    }
    if (currentEncryptedChat != null) {
      timeItem2 =
          headerItem.addSubItem(
              chat_enc_timer, LocaleController.getString("SetTimer", R.string.SetTimer), 0);
    }
    headerItem.addSubItem(
        clear_history, LocaleController.getString("ClearHistory", R.string.ClearHistory), 0);
    if (currentChat != null && !isBroadcast) {
      headerItem.addSubItem(
          delete_chat, LocaleController.getString("DeleteAndExit", R.string.DeleteAndExit), 0);
    } else {
      headerItem.addSubItem(
          delete_chat, LocaleController.getString("DeleteChatUser", R.string.DeleteChatUser), 0);
    }
    muteItem = headerItem.addSubItem(mute, null, 0);
    if (currentUser != null
        && currentEncryptedChat == null
        && (currentUser.flags & TLRPC.USER_FLAG_BOT) != 0) {
      headerItem.addSubItem(
          bot_settings, LocaleController.getString("BotSettings", R.string.BotSettings), 0);
      headerItem.addSubItem(bot_help, LocaleController.getString("BotHelp", R.string.BotHelp), 0);
      updateBotButtons();
    }

    updateTitle();
    updateSubtitle();
    updateTitleIcons();

    attachItem =
        menu
            .addItem(chat_menu_attach, R.drawable.ic_ab_other)
            .setOverrideMenuClick(true)
            .setAllowCloseAnimation(false);
    attachItem.setVisibility(View.GONE);
    menuItem =
        menu.addItem(chat_menu_attach, R.drawable.ic_ab_attach).setAllowCloseAnimation(false);
    menuItem.setBackgroundDrawable(null);

    actionModeViews.clear();

    final ActionBarMenu actionMode = actionBar.createActionMode();
    actionModeViews.add(
        actionMode.addItem(
            -2,
            R.drawable.ic_ab_back_grey,
            R.drawable.bar_selector_mode,
            null,
            AndroidUtilities.dp(54)));

    selectedMessagesCountTextView = new TextView(actionMode.getContext());
    selectedMessagesCountTextView.setTextSize(18);
    selectedMessagesCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
    selectedMessagesCountTextView.setTextColor(0xff737373);
    selectedMessagesCountTextView.setSingleLine(true);
    selectedMessagesCountTextView.setLines(1);
    selectedMessagesCountTextView.setEllipsize(TextUtils.TruncateAt.END);
    selectedMessagesCountTextView.setPadding(AndroidUtilities.dp(11), 0, 0, AndroidUtilities.dp(2));
    selectedMessagesCountTextView.setGravity(Gravity.CENTER_VERTICAL);
    actionMode.addView(
        selectedMessagesCountTextView,
        LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f));
    selectedMessagesCountTextView.setOnTouchListener(
        new View.OnTouchListener() {
          @Override
          public boolean onTouch(View v, MotionEvent event) {
            return true;
          }
        });

    if (currentEncryptedChat == null) {
      actionModeViews.add(
          actionMode.addItem(
              copy,
              R.drawable.ic_ab_fwd_copy,
              R.drawable.bar_selector_mode,
              null,
              AndroidUtilities.dp(54)));
      if (!isBroadcast) {
        actionModeViews.add(
            actionMode.addItem(
                reply,
                R.drawable.ic_ab_reply,
                R.drawable.bar_selector_mode,
                null,
                AndroidUtilities.dp(54)));
      }
      actionModeViews.add(
          actionMode.addItem(
              forward,
              R.drawable.ic_ab_fwd_forward,
              R.drawable.bar_selector_mode,
              null,
              AndroidUtilities.dp(54)));
      actionModeViews.add(
          actionMode.addItem(
              delete,
              R.drawable.ic_ab_fwd_delete,
              R.drawable.bar_selector_mode,
              null,
              AndroidUtilities.dp(54)));
    } else {
      actionModeViews.add(
          actionMode.addItem(
              copy,
              R.drawable.ic_ab_fwd_copy,
              R.drawable.bar_selector_mode,
              null,
              AndroidUtilities.dp(54)));
      actionModeViews.add(
          actionMode.addItem(
              delete,
              R.drawable.ic_ab_fwd_delete,
              R.drawable.bar_selector_mode,
              null,
              AndroidUtilities.dp(54)));
    }
    actionMode
        .getItem(copy)
        .setVisibility(selectedMessagesCanCopyIds.size() != 0 ? View.VISIBLE : View.GONE);
    if (actionMode.getItem(reply) != null) {
      actionMode
          .getItem(reply)
          .setVisibility(selectedMessagesIds.size() == 1 ? View.VISIBLE : View.GONE);
    }
  }

  private void copyChat() {
    String str = "";
    ArrayList<Integer> ids = new ArrayList<>(selectedMessagesCanCopyIds.keySet());
    if (currentEncryptedChat == null) {
      Collections.sort(ids);
    } else {
      Collections.sort(ids, Collections.reverseOrder());
    }
    for (Integer messageId : ids) {
      MessageObject messageObject = selectedMessagesCanCopyIds.get(messageId);
      if (str.length() != 0) {
        str += "\n";
      }
      if (messageObject.messageOwner.message != null) {
        str += messageObject.messageOwner.message;
      } else {
        str += messageObject.messageText;
      }
    }
    if (str.length() != 0) {
      if (Build.VERSION.SDK_INT < 11) {
        android.text.ClipboardManager clipboard =
            (android.text.ClipboardManager)
                ApplicationLoader.applicationContext.getSystemService(
                    Context.CLIPBOARD_SERVICE);
        clipboard.setText(str);
      } else {
        android.content.ClipboardManager clipboard =
            (android.content.ClipboardManager)
                ApplicationLoader.applicationContext.getSystemService(
                    Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip =
            android.content.ClipData.newPlainText("label", str);
        clipboard.setPrimaryClip(clip);
      }
    }
    selectedMessagesIds.clear();
    selectedMessagesCanCopyIds.clear();
    actionBar.hideActionMode();
    updateVisibleRows();
  }

  private void deleteChat() {
    if (getParentActivity() == null) {
      return;
    }

    ArrayList<Integer> ids = new ArrayList<>(selectedMessagesIds.keySet());
    ArrayList<Long> random_ids = null;
    if (currentEncryptedChat != null) {
      random_ids = new ArrayList<>();
      for (HashMap.Entry<Integer, MessageObject> entry :
          selectedMessagesIds.entrySet()) {
        MessageObject msg = entry.getValue();
        if (msg.messageOwner.random_id != 0 && msg.type != MessageObject.Type.CHAT_ACTION_PHOTO) {
          random_ids.add(msg.messageOwner.random_id);
        }
      }
    }
    MessagesController.getInstance()
        .deleteMessages(ids, random_ids, currentEncryptedChat);
    actionBar.hideActionMode();
  }

  private void forwardChat() {
    Bundle args = new Bundle();
    args.putBoolean("onlySelect", true);
    args.putInt("dialogsType", 1);
    DialogsActivity fragment = new DialogsActivity(args);
    fragment.setDelegate(ChatActivity.this);
    presentFragment(fragment);
  }

  private void createEncryptedChatTimer() {
    if (getParentActivity() == null) {
      return;
    }
    showDialog(
        AndroidUtilities.buildTTLAlert(getParentActivity(), currentEncryptedChat)
            .create());
  }

  private void clearChat(int id) {
    if (getParentActivity() == null) {
      return;
    }

    final boolean isChat = (int) dialog_id < 0 && (int) (dialog_id >> 32) != 1;
    if (id != clear_history) {
      if (isChat) {
        if (currentChat.left || currentChat instanceof TLRPC.TL_chatForbidden) {
          MessagesController.getInstance().deleteDialog(dialog_id, 0, false);
        } else {
          MessagesController.getInstance()
              .deleteUserFromChat(
                  (int) -dialog_id,
                  MessagesController.getInstance()
                      .getUser(UserConfig.getClientUserId()),
                  null);
        }
      } else {
        MessagesController.getInstance().deleteDialog(dialog_id, 0, false);
      }
      finishFragment();
    } else {
      MessagesController.getInstance().deleteDialog(dialog_id, 0, true);
    }
  }

  private void shareContact() {
    if (currentUser == null || getParentActivity() == null) {
      return;
    }
    if (currentUser.phone != null && currentUser.phone.length() != 0) {
      Bundle args = new Bundle();
      args.putInt("user_id", currentUser.id);
      args.putBoolean("addContact", true);
      presentFragment(new ContactAddActivity(args));
    } else {
      SendMessagesHelper.getInstance()
          .sendMessage(
              UserConfig.getCurrentUser(), dialog_id, replyingMessageObject);
      moveScrollToLastMessage();
      showReplyPanel(false /* show */, null /* messageObject */, null /* messageObjects */,
          null /* webPage */, false /* cancel */, true /* animated */);
    }
  }

  private void muteChat() {
    boolean muted = MessagesController.getInstance().isDialogMuted(dialog_id);
    if (!muted) {
      showDialog(AlertsCreator.createMuteAlert(getParentActivity(), dialog_id));
    } else {
      SharedPreferences preferences =
          ApplicationLoader.applicationContext.getSharedPreferences(
              "Notifications", Activity.MODE_PRIVATE);
      SharedPreferences.Editor editor = preferences.edit();
      editor.putInt("notify2_" + dialog_id, 0);
      MessagesStorage.getInstance().setDialogFlags(dialog_id, 0);
      editor.commit();
      TLRPC.TL_dialog dialog =
          MessagesController.getInstance().dialogs_dict.get(dialog_id);
      if (dialog != null) {
        dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
      }
      NotificationsController.updateServerNotificationsSettings(dialog_id);
    }
  }

  private void replyChat() {
    if (selectedMessagesIds.size() == 1) {
      ArrayList<Integer> ids = new ArrayList<>(selectedMessagesIds.keySet());
      MessageObject messageObject = messagesDict.get(ids.get(0));
      if (messageObject != null && messageObject.messageOwner.id > 0) {
        showReplyPanel(true, messageObject, null, null, false, true);
      }
    }
    selectedMessagesIds.clear();
    selectedMessagesCanCopyIds.clear();
    actionBar.hideActionMode();
    updateVisibleRows();
  }

  private void addHeader(Context context) {
    avatarContainer = new FrameLayoutFixed(context);
    avatarContainer.setBackgroundResource(R.drawable.bar_selector);
    avatarContainer.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
    actionBar.addView(
        avatarContainer,
        LayoutHelper.createFrame(
            LayoutHelper.WRAP_CONTENT,
            LayoutHelper.MATCH_PARENT,
            Gravity.TOP | Gravity.LEFT,
            56,
            0,
            40,
            0));
    avatarContainer.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            if (currentUser != null) {
              Bundle args = new Bundle();
              args.putInt("user_id", currentUser.id);
              if (currentEncryptedChat != null) {
                args.putLong("dialog_id", dialog_id);
              }
              presentFragment(new ProfileActivity(args));
            } else if (currentChat != null) {
              Bundle args = new Bundle();
              args.putInt("chat_id", currentChat.id);
              ProfileActivity fragment = new ProfileActivity(args);
              fragment.setChatInfo(info);
              presentFragment(fragment);
            }
          }
        });

    if (currentChat != null) {
      int count = currentChat.participants_count;
      if (info != null) {
        count = info.participants.size();
      }
      if (count == 0
          || currentChat.left
          || currentChat instanceof TLRPC.TL_chatForbidden
          || info != null && info instanceof TLRPC.TL_chatParticipantsForbidden) {
        avatarContainer.setEnabled(false);
      }
    }

    avatarImageView = new BackupImageView(context);
    avatarImageView.setRoundRadius(AndroidUtilities.dp(21));
    avatarContainer.addView(
        avatarImageView, LayoutHelper.createFrame(42, 42, Gravity.TOP | Gravity.LEFT, 0, 3, 0, 0));

    if (currentEncryptedChat != null) {
      timeItem = new ImageView(context);
      timeItem.setPadding(
          AndroidUtilities.dp(10),
          AndroidUtilities.dp(10),
          AndroidUtilities.dp(5),
          AndroidUtilities.dp(5));
      timeItem.setScaleType(ImageView.ScaleType.CENTER);
      timeItem.setImageDrawable(timerDrawable = new TimerDrawable(context));
      avatarContainer.addView(
          timeItem, LayoutHelper.createFrame(34, 34, Gravity.TOP | Gravity.LEFT, 16, 18, 0, 0));
      timeItem.setOnClickListener(
          new View.OnClickListener() {
            @Override
            public void onClick(View v) {
              if (getParentActivity() == null) {
                return;
              }
              showDialog(
                  AndroidUtilities.buildTTLAlert(getParentActivity(), currentEncryptedChat)
                      .create());
            }
          });
    }

    nameTextView = new TextView(context);
    nameTextView.setTextColor(0xffffffff);
    nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
    nameTextView.setLines(1);
    nameTextView.setMaxLines(1);
    nameTextView.setSingleLine(true);
    nameTextView.setEllipsize(TextUtils.TruncateAt.END);
    nameTextView.setGravity(Gravity.LEFT);
    nameTextView.setCompoundDrawablePadding(AndroidUtilities.dp(4));
    nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
    avatarContainer.addView(
        nameTextView,
        LayoutHelper.createFrame(
            LayoutHelper.WRAP_CONTENT,
            LayoutHelper.WRAP_CONTENT,
            Gravity.LEFT | Gravity.BOTTOM,
            54,
            0,
            0,
            22));

    onlineTextView = new TextView(context);
    onlineTextView.setTextColor(0xffd7e8f7);
    onlineTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
    onlineTextView.setLines(1);
    onlineTextView.setMaxLines(1);
    onlineTextView.setSingleLine(true);
    onlineTextView.setEllipsize(TextUtils.TruncateAt.END);
    onlineTextView.setGravity(Gravity.LEFT);
    avatarContainer.addView(
        onlineTextView,
        LayoutHelper.createFrame(
            LayoutHelper.WRAP_CONTENT,
            LayoutHelper.WRAP_CONTENT,
            Gravity.LEFT | Gravity.BOTTOM,
            54,
            0,
            0,
            4));
  }

  private void addSearchMenu(ActionBarMenu menu) {
    searchItem =
        menu
            .addItem(0, R.drawable.ic_ab_search)
            .setIsSearchField(true, false)
            .setActionBarMenuItemSearchListener(
                new ActionBarMenuItem.ActionBarMenuItemSearchListener() {

                  @Override
                  public void onSearchCollapse() {
                    avatarContainer.setVisibility(View.VISIBLE);
                    if (chatActivityEnterView.hasText()) {
                      if (headerItem != null) {
                        headerItem.setVisibility(View.GONE);
                      }
                      if (attachItem != null) {
                        attachItem.setVisibility(View.VISIBLE);
                      }
                    } else {
                      if (headerItem != null) {
                        headerItem.setVisibility(View.VISIBLE);
                      }
                      if (attachItem != null) {
                        attachItem.setVisibility(View.GONE);
                      }
                    }
                    searchItem.setVisibility(View.GONE);
                    searchUpItem.clearAnimation();
                    searchDownItem.clearAnimation();
                    searchUpItem.setVisibility(View.GONE);
                    searchDownItem.setVisibility(View.GONE);
                    scrollToLastMessage();
                  }

                  @Override
                  public void onSearchExpand() {
                    AndroidUtilities.runOnUIThread(
                        new Runnable() {
                          @Override
                          public void run() {
                            searchItem.getSearchField().requestFocus();
                            AndroidUtilities.showKeyboard(searchItem.getSearchField());
                          }
                        },
                        300); //TODO find a better way to open keyboard
                  }

                  @Override
                  public void onSearchPressed(EditText editText) {
                    updateSearchButtons(0);
                    MessagesSearchQuery.searchMessagesInChat(
                        editText.getText().toString(), dialog_id, classGuid, 0);
                  }
                });
    searchItem.getSearchField().setHint(LocaleController.getString("Search", R.string.Search));
    searchItem.setVisibility(View.GONE);

    searchUpItem = menu.addItem(search_up, R.drawable.search_up);
    searchUpItem.setVisibility(View.GONE);
    searchDownItem = menu.addItem(search_down, R.drawable.search_down);
    searchDownItem.setVisibility(View.GONE);
  }

  private void addChatListView(Context context, SizeNotifierFrameLayout contentView) {
    chatListView =
        new RecyclerListView(context) {
          @Override
          protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            if (chatAdapter.isBot) {
              int childCount = getChildCount();
              for (int a = 0; a < childCount; a++) {
                View child = getChildAt(a);
                if (child instanceof BotHelpCell) {
                  int height = b - t;
                  int top = height / 2 - child.getMeasuredHeight() / 2;
                  if (child.getTop() > top) {
                    child.layout(0, top, r - l, top + child.getMeasuredHeight());
                  }
                  break;
                }
              }
            }
          }
        };
    chatListView.setVerticalScrollBarEnabled(true);
    chatListView.setAdapter(chatAdapter = new ChatActivityAdapter(context));
    chatListView.setClipToPadding(false);
    chatListView.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(3));
    chatListView.setItemAnimator(null);
    chatListView.setLayoutAnimation(null);
    chatLayoutManager =
        new LinearLayoutManager(context) {
          @Override
          public boolean supportsPredictiveItemAnimations() {
            return false;
          }
        };
    chatLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
    chatLayoutManager.setStackFromEnd(true);
    chatListView.setLayoutManager(chatLayoutManager);
    contentView.addView(
        chatListView,
        LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    chatListView.setOnItemLongClickListener(onItemLongClickListener);
    chatListView.setOnItemClickListener(onItemClickListener);
    chatListView.setOnScrollListener(
        new RecyclerView.OnScrollListener() {

          @Override
          public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            if (newState != RecyclerView.SCROLL_STATE_DRAGGING
                && highlightMessageId != Integer.MAX_VALUE) {
              highlightMessageId = Integer.MAX_VALUE;
              updateVisibleRows();
            }
          }

          @Override
          public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            int firstVisibleItem = chatLayoutManager.findFirstVisibleItemPosition();
            int visibleItemCount =
                firstVisibleItem == RecyclerView.NO_POSITION
                    ? 0
                    : Math.abs(chatLayoutManager.findLastVisibleItemPosition() - firstVisibleItem)
                    + 1;
            if (visibleItemCount > 0) {
              int totalItemCount = chatAdapter.getItemCount();
              if (firstVisibleItem <= 10) {
                if (!endReached && !loading) {
                  if (messagesByDays.size() != 0) {
                    MessagesController.getInstance()
                        .loadMessages(
                            dialog_id,
                            20,
                            maxMessageId,
                            !cacheEndReaced && startLoadFromMessageId == 0,
                            minDate,
                            classGuid,
                            0,
                            0,
                            0,
                            startLoadFromMessageId == 0);
                  } else {
                    MessagesController.getInstance()
                        .loadMessages(
                            dialog_id,
                            20,
                            0,
                            !cacheEndReaced && startLoadFromMessageId == 0,
                            minDate,
                            classGuid,
                            0,
                            0,
                            0,
                            startLoadFromMessageId == 0);
                  }
                  loading = true;
                }
              }
              if (firstVisibleItem + visibleItemCount >= totalItemCount - 6) {
                if (!forwardEndReached && !loadingForward) {
                  MessagesController.getInstance()
                      .loadMessages(
                          dialog_id,
                          20,
                          minMessageId,
                          startLoadFromMessageId == 0,
                          maxDate,
                          classGuid,
                          1,
                          0,
                          0,
                          startLoadFromMessageId == 0);
                  loadingForward = true;
                }
              }
              if (firstVisibleItem + visibleItemCount == totalItemCount && forwardEndReached) {
                showPagedownButton(false, true);
              }
            }
            updateMessagesVisisblePart();
          }
        });
    chatListView.setOnTouchListener(
        new View.OnTouchListener() {
          @Override
          public boolean onTouch(View v, MotionEvent event) {
            if (openSecretPhotoRunnable != null || SecretPhotoViewer.getInstance().isVisible()) {
              if (event.getAction() == MotionEvent.ACTION_UP
                  || event.getAction() == MotionEvent.ACTION_CANCEL
                  || event.getAction() == MotionEvent.ACTION_POINTER_UP) {
                AndroidUtilities.runOnUIThread(
                    new Runnable() {
                      @Override
                      public void run() {
                        chatListView.setOnItemClickListener(onItemClickListener);
                      }
                    },
                    150);
                if (openSecretPhotoRunnable != null) {
                  AndroidUtilities.cancelRunOnUIThread(openSecretPhotoRunnable);
                  openSecretPhotoRunnable = null;
                  try {
                    Toast.makeText(
                        v.getContext(),
                        LocaleController.getString("PhotoTip", R.string.PhotoTip),
                        Toast.LENGTH_SHORT)
                        .show();
                  } catch (Exception e) {
                    FileLog.e("tmessages", e);
                  }
                } else {
                  if (SecretPhotoViewer.getInstance().isVisible()) {
                    AndroidUtilities.runOnUIThread(
                        new Runnable() {
                          @Override
                          public void run() {
                            chatListView.setOnItemLongClickListener(onItemLongClickListener);
                            chatListView.setLongClickable(true);
                          }
                        });
                    SecretPhotoViewer.getInstance().closePhoto();
                  }
                }
              } else if (event.getAction() != MotionEvent.ACTION_DOWN) {
                if (SecretPhotoViewer.getInstance().isVisible()) {
                  return true;
                } else if (openSecretPhotoRunnable != null) {
                  if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (Math.hypot(startX - event.getX(), startY - event.getY())
                        > AndroidUtilities.dp(5)) {
                      AndroidUtilities.cancelRunOnUIThread(openSecretPhotoRunnable);
                      openSecretPhotoRunnable = null;
                    }
                  } else {
                    AndroidUtilities.cancelRunOnUIThread(openSecretPhotoRunnable);
                    openSecretPhotoRunnable = null;
                  }
                }
              }
            }
            return false;
          }
        });
    chatListView.setOnInterceptTouchListener(
        new RecyclerListView.OnInterceptTouchListener() {
          @Override
          public boolean onInterceptTouchEvent(MotionEvent event) {
            if (actionBar.isActionModeShowed()) {
              return false;
            }
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
              int x = (int) event.getX();
              int y = (int) event.getY();
              int count = chatListView.getChildCount();
              Rect rect = new Rect();
              for (int a = 0; a < count; a++) {
                View view = chatListView.getChildAt(a);
                int top = view.getTop();
                int bottom = view.getBottom();
                view.getLocalVisibleRect(rect);
                if (top > y || bottom < y) {
                  continue;
                }
                if (!(view instanceof ChatMediaCell)) {
                  break;
                }
                final ChatMediaCell cell = (ChatMediaCell) view;
                final MessageObject messageObject = cell.getMessageObject();
                if (messageObject == null
                    || messageObject.isSending()
                    || !messageObject.isSecretPhoto()
                    || !cell.getPhotoImage().isInsideImage(x, y - top)) {
                  break;
                }
                File file = FileLoader.getPathToMessage(messageObject.messageOwner);
                if (!file.exists()) {
                  break;
                }
                startX = x;
                startY = y;
                chatListView.setOnItemClickListener(null);
                openSecretPhotoRunnable =
                    new Runnable() {
                      @Override
                      public void run() {
                        if (openSecretPhotoRunnable == null) {
                          return;
                        }
                        chatListView.requestDisallowInterceptTouchEvent(true);
                        chatListView.setOnItemLongClickListener(null);
                        chatListView.setLongClickable(false);
                        openSecretPhotoRunnable = null;
                        if (sendSecretMessageRead(messageObject)) {
                          cell.invalidate();
                        }
                        SecretPhotoViewer.getInstance().setParentActivity(getParentActivity());
                        SecretPhotoViewer.getInstance().openPhoto(messageObject);
                      }
                    };
                AndroidUtilities.runOnUIThread(openSecretPhotoRunnable, 100);
                return true;
              }
            }
            return false;
          }
        });
  }

  private void addEmptyContentView(Context context, SizeNotifierFrameLayout contentView) {
    emptyViewContainer = new FrameLayout(context);
    emptyViewContainer.setVisibility(View.INVISIBLE);
    contentView.addView(
        emptyViewContainer,
        LayoutHelper.createFrame(
            LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
    emptyViewContainer.setOnTouchListener(
        new View.OnTouchListener() {
          @Override
          public boolean onTouch(View v, MotionEvent event) {
            return true;
          }
        });

    if (currentEncryptedChat == null) {
      TextView emptyView = new TextView(context);
      if (currentUser != null
          && currentUser.id != 777000
          && currentUser.id != 429000
          && (currentUser.id / 1000 == 333 || currentUser.id % 1000 == 0)) {
        emptyView.setText(LocaleController.getString("GotAQuestion", R.string.GotAQuestion));
      } else {
        emptyView.setText(LocaleController.getString("NoMessages", R.string.NoMessages));
      }
      emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
      emptyView.setGravity(Gravity.CENTER);
      emptyView.setTextColor(0xffffffff);
      emptyView.setBackgroundResource(
          ApplicationLoader.isCustomTheme() ? R.drawable.system_black : R.drawable.system_blue);
      emptyView.setPadding(
          AndroidUtilities.dp(7),
          AndroidUtilities.dp(1),
          AndroidUtilities.dp(7),
          AndroidUtilities.dp(1));
      emptyViewContainer.addView(
          emptyView,
          new FrameLayout.LayoutParams(
              LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
    } else {
      LinearLayout secretChatPlaceholder = new LinearLayout(context);
      secretChatPlaceholder.setBackgroundResource(
          ApplicationLoader.isCustomTheme() ? R.drawable.system_black : R.drawable.system_blue);
      secretChatPlaceholder.setPadding(
          AndroidUtilities.dp(16),
          AndroidUtilities.dp(12),
          AndroidUtilities.dp(16),
          AndroidUtilities.dp(12));
      secretChatPlaceholder.setOrientation(LinearLayout.VERTICAL);
      emptyViewContainer.addView(
          secretChatPlaceholder,
          new FrameLayout.LayoutParams(
              LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

      secretViewStatusTextView = new TextView(context);
      secretViewStatusTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
      secretViewStatusTextView.setTextColor(0xffffffff);
      secretViewStatusTextView.setGravity(Gravity.CENTER_HORIZONTAL);
      secretViewStatusTextView.setMaxWidth(AndroidUtilities.dp(210));
      if (currentEncryptedChat.admin_id == UserConfig.getClientUserId()) {
        secretViewStatusTextView.setText(
            LocaleController.formatString(
                "EncryptedPlaceholderTitleOutgoing",
                R.string.EncryptedPlaceholderTitleOutgoing,
                UserObject.getFirstName(currentUser)));
      } else {
        secretViewStatusTextView.setText(
            LocaleController.formatString(
                "EncryptedPlaceholderTitleIncoming",
                R.string.EncryptedPlaceholderTitleIncoming,
                UserObject.getFirstName(currentUser)));
      }
      secretChatPlaceholder.addView(
          secretViewStatusTextView,
          LayoutHelper.createLinear(
              LayoutHelper.WRAP_CONTENT,
              LayoutHelper.WRAP_CONTENT,
              Gravity.CENTER_HORIZONTAL | Gravity.TOP));

      TextView textView = new TextView(context);
      textView.setText(
          LocaleController.getString(
              "EncryptedDescriptionTitle", R.string.EncryptedDescriptionTitle));
      textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
      textView.setTextColor(0xffffffff);
      textView.setGravity(Gravity.CENTER_HORIZONTAL);
      textView.setMaxWidth(AndroidUtilities.dp(260));
      secretChatPlaceholder.addView(
          textView,
          LayoutHelper.createLinear(
              LayoutHelper.WRAP_CONTENT,
              LayoutHelper.WRAP_CONTENT,
              (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP,
              0,
              8,
              0,
              0));

      for (int a = 0; a < 4; a++) {
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        secretChatPlaceholder.addView(
            linearLayout,
            LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT,
                0,
                8,
                0,
                0));

        ImageView imageView = new ImageView(context);
        imageView.setImageResource(R.drawable.ic_lock_white);

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setTextColor(0xffffffff);
        textView.setGravity(
            Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
        textView.setMaxWidth(AndroidUtilities.dp(260));

        switch (a) {
          case 0:
            textView.setText(
                LocaleController.getString(
                    "EncryptedDescription1", R.string.EncryptedDescription1));
            break;
          case 1:
            textView.setText(
                LocaleController.getString(
                    "EncryptedDescription2", R.string.EncryptedDescription2));
            break;
          case 2:
            textView.setText(
                LocaleController.getString(
                    "EncryptedDescription3", R.string.EncryptedDescription3));
            break;
          case 3:
            textView.setText(
                LocaleController.getString(
                    "EncryptedDescription4", R.string.EncryptedDescription4));
            break;
        }

        if (LocaleController.isRTL) {
          linearLayout.addView(
              textView,
              LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
          linearLayout.addView(
              imageView,
              LayoutHelper.createLinear(
                  LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8, 3, 0, 0));
        } else {
          linearLayout.addView(
              imageView,
              LayoutHelper.createLinear(
                  LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 4, 8, 0));
          linearLayout.addView(
              textView,
              LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        }
      }
    }
  }

  private void addProgressBarView(Context context, SizeNotifierFrameLayout contentView) {
    progressView = new FrameLayout(context);
    progressView.setVisibility(View.INVISIBLE);
    contentView.addView(
        progressView,
        LayoutHelper.createFrame(
            LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

    View view = new View(context);
    view.setBackgroundResource(
        ApplicationLoader.isCustomTheme() ? R.drawable.system_loader2 : R.drawable.system_loader1);
    progressView.addView(view, LayoutHelper.createFrame(36, 36, Gravity.CENTER));

    ProgressBar progressBar = new ProgressBar(context);
    try {
      progressBar.setIndeterminateDrawable(
          context.getResources().getDrawable(R.drawable.loading_animation));
    } catch (Exception e) {
      ;
    }
    progressBar.setIndeterminate(true);
    AndroidUtilities.setProgressBarAnimationDuration(progressBar, 1500);
    progressView.addView(progressBar, LayoutHelper.createFrame(32, 32, Gravity.CENTER));
  }

  private void addMentionListView(Context context, SizeNotifierFrameLayout contentView) {
    if (currentEncryptedChat == null && !isBroadcast) {
      mentionListView = new ListView(context);
      mentionListView.setBackgroundResource(R.drawable.compose_panel);
      mentionListView.setVisibility(View.GONE);
      mentionListView.setPadding(0, AndroidUtilities.dp(2), 0, 0);
      mentionListView.setClipToPadding(true);
      mentionListView.setDividerHeight(0);
      mentionListView.setDivider(null);
      if (Build.VERSION.SDK_INT > 8) {
        mentionListView.setOverScrollMode(ListView.OVER_SCROLL_NEVER);
      }
      contentView.addView(
          mentionListView,
          LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 110, Gravity.LEFT | Gravity.BOTTOM));

      mentionListView.setAdapter(
          mentionsAdapter =
              new MentionsAdapter(
                  context,
                  false,
                  new MentionsAdapter.MentionsAdapterDelegate() {
                    @Override
                    public void needChangePanelVisibility(boolean show) {
                      if (show) {
                        FrameLayout.LayoutParams layoutParams3 =
                            (FrameLayout.LayoutParams) mentionListView.getLayoutParams();
                        int height =
                            36 * Math.min(3, mentionsAdapter.getCount())
                                + (mentionsAdapter.getCount() > 3 ? 18 : 0);
                        layoutParams3.height = AndroidUtilities.dp(2 + height);
                        layoutParams3.topMargin = -AndroidUtilities.dp(height);
                        mentionListView.setLayoutParams(layoutParams3);

                        if (mentionListAnimation != null) {
                          mentionListAnimation.cancel();
                          mentionListAnimation = null;
                        }

                        if (mentionListView.getVisibility() == View.VISIBLE) {
                          ViewProxy.setAlpha(mentionListView, 1.0f);
                          return;
                        }
                        if (allowStickersPanel) {
                          mentionListView.setVisibility(View.VISIBLE);
                          mentionListAnimation = new AnimatorSetProxy();
                          mentionListAnimation.playTogether(
                              ObjectAnimatorProxy.ofFloat(mentionListView, "alpha", 0.0f, 1.0f));
                          mentionListAnimation.addListener(
                              new AnimatorListenerAdapterProxy() {
                                @Override
                                public void onAnimationEnd(Object animation) {
                                  if (mentionListAnimation != null
                                      && mentionListAnimation.equals(animation)) {
                                    mentionListView.clearAnimation();
                                    mentionListAnimation = null;
                                  }
                                }
                              });
                          mentionListAnimation.setDuration(200);
                          mentionListAnimation.start();
                        } else {
                          ViewProxy.setAlpha(mentionListView, 1.0f);
                          mentionListView.clearAnimation();
                          mentionListView.setVisibility(View.INVISIBLE);
                        }
                      } else {
                        if (mentionListAnimation != null) {
                          mentionListAnimation.cancel();
                          mentionListAnimation = null;
                        }

                        if (mentionListView.getVisibility() == View.GONE) {
                          return;
                        }
                        if (allowStickersPanel) {
                          mentionListAnimation = new AnimatorSetProxy();
                          mentionListAnimation.playTogether(
                              ObjectAnimatorProxy.ofFloat(mentionListView, "alpha", 0.0f));
                          mentionListAnimation.addListener(
                              new AnimatorListenerAdapterProxy() {
                                @Override
                                public void onAnimationEnd(Object animation) {
                                  if (mentionListAnimation != null
                                      && mentionListAnimation.equals(animation)) {
                                    mentionListView.clearAnimation();
                                    mentionListView.setVisibility(View.GONE);
                                    mentionListAnimation = null;
                                  }
                                }
                              });
                          mentionListAnimation.setDuration(200);
                          mentionListAnimation.start();
                        } else {
                          mentionListView.clearAnimation();
                          mentionListView.setVisibility(View.GONE);
                        }
                      }
                    }
                  }));
      mentionsAdapter.setBotInfo(botInfo);
      mentionsAdapter.setChatInfo(info);
      mentionsAdapter.setNeedUsernames(currentChat != null);
      mentionsAdapter.setBotsCount(currentChat != null ? botsCount : 1);

      mentionListView.setOnItemClickListener(
          new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
              Object object = mentionsAdapter.getItem(position);
              int start = mentionsAdapter.getResultStartPosition();
              int len = mentionsAdapter.getResultLength();
              if (object instanceof TLRPC.User) {
                TLRPC.User user = (TLRPC.User) object;
                if (user != null) {
                  chatActivityEnterView.replaceWithText(start, len, "@" + user.username + " ");
                }
              } else if (object instanceof String) {
                if (mentionsAdapter.isBotCommands()) {
                  SendMessagesHelper.getInstance()
                      .sendMessage((String) object, dialog_id, null, null, false);
                  chatActivityEnterView.setFieldText("");
                } else {
                  chatActivityEnterView.replaceWithText(start, len, object + " ");
                }
              }
            }
          });

      mentionListView.setOnItemLongClickListener(
          new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(
                AdapterView<?> parent, View view, int position, long id) {
              if (!mentionsAdapter.isLongClickEnabled()) {
                return false;
              }
              Object object = mentionsAdapter.getItem(position);
              if (object instanceof String) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                builder.setMessage(LocaleController.getString("ClearSearch", R.string.ClearSearch));
                builder.setPositiveButton(
                    LocaleController.getString("ClearButton", R.string.ClearButton).toUpperCase(),
                    new DialogInterface.OnClickListener() {
                      @Override
                      public void onClick(DialogInterface dialogInterface, int i) {
                        mentionsAdapter.clearRecentHashtags();
                      }
                    });
                builder.setNegativeButton(
                    LocaleController.getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
                return true;
              }
              return false;
            }
          });
    }
  }

  private void addChatActivityEnterView(Context context, SizeNotifierFrameLayout contentView) {
    chatActivityEnterView = new ChatActivityEnterView(getParentActivity(), contentView, this, true);
    chatActivityEnterView.setDialogId(dialog_id);
    chatActivityEnterView.addToAttachLayout(menuItem);
    chatActivityEnterView.setId(id_chat_compose_panel);
    chatActivityEnterView.setBotsCount(botsCount, hasBotsCommands);
    contentView.addView(
        chatActivityEnterView,
        LayoutHelper.createFrame(
            LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM));
    chatActivityEnterView.setDelegate(
        new ChatActivityEnterView.ChatActivityEnterViewDelegate() {
          @Override
          public void onMessageSend(String message) {
            moveScrollToLastMessage();
            showReplyPanel(false, null, null, null, false, true);
            if (mentionsAdapter != null) {
              mentionsAdapter.addHashtagsFromMessage(message);
            }
          }

          @Override
          public void onTextChanged(final CharSequence text, boolean bigChange) {
            if (stickersAdapter != null) {
              stickersAdapter.loadStikersForEmoji(text);
            }
            if (mentionsAdapter != null) {
              mentionsAdapter.searchUsernameOrHashtag(
                  text.toString(), chatActivityEnterView.getCursorPosition(), messages);
            }
            if (waitingForCharaterEnterRunnable != null) {
              AndroidUtilities.cancelRunOnUIThread(waitingForCharaterEnterRunnable);
              waitingForCharaterEnterRunnable = null;
            }
            if (chatActivityEnterView.isMessageWebPageSearchEnabled()) {
              if (bigChange) {
                searchLinks(text, true);
              } else {
                waitingForCharaterEnterRunnable =
                    new Runnable() {
                      @Override
                      public void run() {
                        if (this == waitingForCharaterEnterRunnable) {
                          searchLinks(text, false);
                          waitingForCharaterEnterRunnable = null;
                        }
                      }
                    };
                AndroidUtilities.runOnUIThread(waitingForCharaterEnterRunnable, 3000);
              }
            }
          }

          @Override
          public void needSendTyping() {
            MessagesController.getInstance().sendTyping(dialog_id, 0, classGuid);
          }

          @Override
          public void onAttachButtonHidden() {
            if (actionBar.isSearchFieldVisible()) {
              return;
            }
            if (attachItem != null) {
              attachItem.setVisibility(View.VISIBLE);
            }
            if (headerItem != null) {
              headerItem.setVisibility(View.GONE);
            }
          }

          @Override
          public void onAttachButtonShow() {
            if (actionBar.isSearchFieldVisible()) {
              return;
            }
            if (attachItem != null) {
              attachItem.setVisibility(View.GONE);
            }
            if (headerItem != null) {
              headerItem.setVisibility(View.VISIBLE);
            }
          }

          @Override
          public void onWindowSizeChanged(int size) {
            if (size < AndroidUtilities.dp(72) + ActionBar.getCurrentActionBarHeight()) {
              allowStickersPanel = false;
              if (stickersPanel.getVisibility() == View.VISIBLE) {
                stickersPanel.clearAnimation();
                stickersPanel.setVisibility(View.INVISIBLE);
              }
              if (mentionListView != null && mentionListView.getVisibility() == View.VISIBLE) {
                mentionListView.clearAnimation();
                mentionListView.setVisibility(View.INVISIBLE);
              }
            } else {
              allowStickersPanel = true;
              if (stickersPanel.getVisibility() == View.INVISIBLE) {
                stickersPanel.clearAnimation();
                stickersPanel.setVisibility(View.VISIBLE);
              }
              if (mentionListView != null && mentionListView.getVisibility() == View.INVISIBLE) {
                mentionListView.clearAnimation();
                mentionListView.setVisibility(View.VISIBLE);
              }
            }
            updateMessagesVisisblePart();
          }
        });

    FrameLayout replyLayout = new FrameLayout(context);
    replyLayout.setClickable(true);
    chatActivityEnterView.addTopView(replyLayout, 48);

    View lineView = new View(context);
    lineView.setBackgroundColor(0xffe8e8e8);
    replyLayout.addView(
        lineView,
        LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.BOTTOM | Gravity.LEFT));

    replyIconImageView = new ImageView(context);
    replyIconImageView.setScaleType(ImageView.ScaleType.CENTER);
    replyLayout.addView(
        replyIconImageView, LayoutHelper.createFrame(52, 46, Gravity.TOP | Gravity.LEFT));

    ImageView imageView = new ImageView(context);
    imageView.setImageResource(R.drawable.delete_reply);
    imageView.setScaleType(ImageView.ScaleType.CENTER);
    replyLayout.addView(
        imageView, LayoutHelper.createFrame(52, 46, Gravity.RIGHT | Gravity.TOP, 0, 0.5f, 0, 0));
    imageView.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            if (forwardingMessages != null) {
              forwardingMessages.clear();
            }
            showReplyPanel(false, null, null, foundWebPage, true, true);
          }
        });

    replyNameTextView = new TextView(context);
    replyNameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
    replyNameTextView.setTextColor(0xff377aae);
    replyNameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
    replyNameTextView.setSingleLine(true);
    replyNameTextView.setEllipsize(TextUtils.TruncateAt.END);
    replyNameTextView.setMaxLines(1);
    replyLayout.addView(
        replyNameTextView,
        LayoutHelper.createFrame(
            LayoutHelper.WRAP_CONTENT,
            LayoutHelper.WRAP_CONTENT,
            Gravity.TOP | Gravity.LEFT,
            52,
            4,
            52,
            0));

    replyObjectTextView = new TextView(context);
    replyObjectTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
    replyObjectTextView.setTextColor(0xff999999);
    replyObjectTextView.setSingleLine(true);
    replyObjectTextView.setEllipsize(TextUtils.TruncateAt.END);
    replyObjectTextView.setMaxLines(1);
    replyLayout.addView(
        replyObjectTextView,
        LayoutHelper.createFrame(
            LayoutHelper.WRAP_CONTENT,
            LayoutHelper.WRAP_CONTENT,
            Gravity.TOP | Gravity.LEFT,
            52,
            22,
            52,
            0));

    replyImageView = new BackupImageView(context);
    replyLayout.addView(
        replyImageView, LayoutHelper.createFrame(34, 34, Gravity.TOP | Gravity.LEFT, 52, 6, 0, 0));

    stickersPanel = new FrameLayout(context);
    stickersPanel.setVisibility(View.GONE);
    contentView.addView(
        stickersPanel,
        LayoutHelper.createFrame(
            LayoutHelper.WRAP_CONTENT, 81.5f, Gravity.LEFT | Gravity.BOTTOM, 0, 0, 0, 38));

    stickersListView = new RecyclerListView(context);
    stickersListView.setDisallowInterceptTouchEvents(true);
    LinearLayoutManager layoutManager = new LinearLayoutManager(context);
    layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
    stickersListView.setLayoutManager(layoutManager);
    stickersListView.setClipToPadding(false);
    if (Build.VERSION.SDK_INT >= 9) {
      stickersListView.setOverScrollMode(RecyclerListView.OVER_SCROLL_NEVER);
    }
    stickersPanel.addView(
        stickersListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 78));
    if (currentEncryptedChat == null
        || currentEncryptedChat != null
        && AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 23) {
      chatActivityEnterView.setAllowStickers(true);
      if (stickersAdapter != null) {
        stickersAdapter.onDestroy();
      }
      stickersListView.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
      stickersListView.setAdapter(
          stickersAdapter =
              new StickersAdapter(
                  context,
                  new StickersAdapter.StickersAdapterDelegate() {
                    @Override
                    public void needChangePanelVisibility(final boolean show) {
                      if (show && stickersPanel.getVisibility() == View.VISIBLE
                          || !show && stickersPanel.getVisibility() == View.GONE) {
                        return;
                      }
                      if (show) {
                        stickersListView.scrollToPosition(0);
                        stickersPanel.clearAnimation();
                        stickersPanel.setVisibility(
                            allowStickersPanel ? View.VISIBLE : View.INVISIBLE);
                      }
                      if (runningAnimation != null) {
                        runningAnimation.cancel();
                        runningAnimation = null;
                      }
                      if (stickersPanel.getVisibility() != View.INVISIBLE) {
                        runningAnimation = new AnimatorSetProxy();
                        runningAnimation.playTogether(
                            ObjectAnimatorProxy.ofFloat(
                                stickersPanel, "alpha", show ? 0.0f : 1.0f, show ? 1.0f : 0.0f));
                        runningAnimation.setDuration(150);
                        runningAnimation.addListener(
                            new AnimatorListenerAdapterProxy() {
                              @Override
                              public void onAnimationEnd(Object animation) {
                                if (runningAnimation != null
                                    && runningAnimation.equals(animation)) {
                                  if (!show) {
                                    stickersAdapter.clearStickers();
                                    stickersPanel.clearAnimation();
                                    stickersPanel.setVisibility(View.GONE);
                                  }
                                  runningAnimation = null;
                                }
                              }
                            });
                        runningAnimation.start();
                      } else if (!show) {
                        stickersPanel.setVisibility(View.GONE);
                      }
                    }
                  }));
      stickersListView.setOnItemClickListener(
          new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
              TLRPC.Document document = stickersAdapter.getItem(position);
              if (document instanceof TLRPC.TL_document) {
                SendMessagesHelper.getInstance()
                    .sendSticker(document, dialog_id, replyingMessageObject);
                showReplyPanel(false, null, null, null, false, true);
              }
              chatActivityEnterView.setFieldText("");
            }
          });
    }

    imageView = new ImageView(context);
    imageView.setImageResource(R.drawable.stickers_back_arrow);
    stickersPanel.addView(
        imageView,
        LayoutHelper.createFrame(
            LayoutHelper.WRAP_CONTENT,
            LayoutHelper.WRAP_CONTENT,
            Gravity.BOTTOM | Gravity.LEFT,
            53,
            0,
            0,
            0));
  }

  private void addBottomOverlayChatView(Context context, SizeNotifierFrameLayout contentView) {
    bottomOverlay = new FrameLayout(context);
    bottomOverlay.setBackgroundColor(0xffffffff);
    bottomOverlay.setVisibility(View.INVISIBLE);
    bottomOverlay.setFocusable(true);
    bottomOverlay.setFocusableInTouchMode(true);
    bottomOverlay.setClickable(true);
    contentView.addView(
        bottomOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM));

    bottomOverlayText = new TextView(context);
    bottomOverlayText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
    bottomOverlayText.setTextColor(0xff7f7f7f);
    bottomOverlay.addView(
        bottomOverlayText,
        LayoutHelper.createFrame(
            LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

    bottomOverlayChat = new FrameLayout(context);
    bottomOverlayChat.setBackgroundColor(0xfffbfcfd);
    bottomOverlayChat.setVisibility(View.INVISIBLE);
    contentView.addView(
        bottomOverlayChat, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM));
    bottomOverlayChat.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            if (getParentActivity() == null) {
              return;
            }
            AlertDialog.Builder builder = null;
            if (currentUser != null && userBlocked) {
              builder = new AlertDialog.Builder(getParentActivity());
              builder.setMessage(
                  LocaleController.getString(
                      "AreYouSureUnblockContact", R.string.AreYouSureUnblockContact));
              builder.setPositiveButton(
                  LocaleController.getString("OK", R.string.OK),
                  new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                      MessagesController.getInstance().unblockUser(currentUser.id);
                    }
                  });
            } else if (currentUser != null && botUser != null) {
              if (botUser.length() != 0) {
                MessagesController.getInstance().sendBotStart(currentUser, botUser);
              } else {
                SendMessagesHelper.getInstance()
                    .sendMessage("/start", dialog_id, null, null, false);
              }
              botUser = null;
              updateBottomOverlay();
            } else {
              builder = new AlertDialog.Builder(getParentActivity());
              builder.setMessage(
                  LocaleController.getString(
                      "AreYouSureDeleteThisChat", R.string.AreYouSureDeleteThisChat));
              builder.setPositiveButton(
                  LocaleController.getString("OK", R.string.OK),
                  new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                      MessagesController.getInstance().deleteDialog(dialog_id, 0, false);
                      finishFragment();
                    }
                  });
            }
            if (builder != null) {
              builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
              builder.setNegativeButton(
                  LocaleController.getString("Cancel", R.string.Cancel), null);
              showDialog(builder.create());
            }
          }
        });

    bottomOverlayChatText = new TextView(context);
    bottomOverlayChatText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
    bottomOverlayChatText.setTextColor(0xff3e6fa1);
    bottomOverlayChat.addView(
        bottomOverlayChatText,
        LayoutHelper.createFrame(
            LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

  }

  private void addPageDownButton(Context context, SizeNotifierFrameLayout contentView) {
    pagedownButton = new ImageView(context);
    pagedownButton.setVisibility(View.INVISIBLE);
    pagedownButton.setImageResource(R.drawable.pagedown);
    contentView.addView(
        pagedownButton,
        LayoutHelper.createFrame(
            LayoutHelper.WRAP_CONTENT,
            LayoutHelper.WRAP_CONTENT,
            Gravity.RIGHT | Gravity.BOTTOM,
            0,
            0,
            6,
            4));
    pagedownButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            if (returnToMessageId > 0) {
              scrollToMessageId(returnToMessageId, 0, true);
            } else {
              scrollToLastMessage();
            }
          }
        });
  }

  private View createFragmentView(Context context) {
    return
        new SizeNotifierFrameLayout(context) {

          int inputFieldHeight = 0;

          @Override
          protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int widthSize = MeasureSpec.getSize(widthMeasureSpec);
            int heightSize = MeasureSpec.getSize(heightMeasureSpec);

            setMeasuredDimension(widthSize, heightSize);

            int keyboardSize = getKeyboardHeight();

            if (keyboardSize <= AndroidUtilities.dp(20)) {
              heightSize -= chatActivityEnterView.getEmojiPadding();
            }

            int childCount = getChildCount();

            measureChildWithMargins(
                chatActivityEnterView, widthMeasureSpec, 0, heightMeasureSpec, 0);
            inputFieldHeight = chatActivityEnterView.getMeasuredHeight();

            for (int i = 0; i < childCount; i++) {
              View child = getChildAt(i);
              if (child.getVisibility() == GONE || child == chatActivityEnterView) {
                continue;
              }
              if (child == chatListView || child == progressView) {
                int contentWidthSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY);
                int contentHeightSpec =
                    MeasureSpec.makeMeasureSpec(
                        Math.max(
                            AndroidUtilities.dp(10),
                            heightSize - inputFieldHeight + AndroidUtilities.dp(2)),
                        MeasureSpec.EXACTLY);
                child.measure(contentWidthSpec, contentHeightSpec);
              } else if (child == emptyViewContainer) {
                int contentWidthSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY);
                int contentHeightSpec =
                    MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY);
                child.measure(contentWidthSpec, contentHeightSpec);
              } else if (chatActivityEnterView.isPopupView(child)) {
                child.measure(
                    MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(
                        child.getLayoutParams().height, MeasureSpec.EXACTLY));
              } else {
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
              }
            }
          }

          @Override
          protected void onLayout(boolean changed, int l, int t, int r, int b) {
            final int count = getChildCount();

            int paddingBottom =
                getKeyboardHeight() <= AndroidUtilities.dp(20)
                    ? chatActivityEnterView.getEmojiPadding()
                    : 0;
            setBottomClip(paddingBottom);

            for (int i = 0; i < count; i++) {
              final View child = getChildAt(i);
              if (child.getVisibility() == GONE) {
                continue;
              }
              final LayoutParams lp = (LayoutParams) child.getLayoutParams();

              final int width = child.getMeasuredWidth();
              final int height = child.getMeasuredHeight();

              int childLeft;
              int childTop;

              int gravity = lp.gravity;
              if (gravity == -1) {
                gravity = Gravity.TOP | Gravity.LEFT;
              }

              final int absoluteGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
              final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

              switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                case Gravity.CENTER_HORIZONTAL:
                  childLeft = (r - l - width) / 2 + lp.leftMargin - lp.rightMargin;
                  break;
                case Gravity.RIGHT:
                  childLeft = r - width - lp.rightMargin;
                  break;
                case Gravity.LEFT:
                default:
                  childLeft = lp.leftMargin;
              }

              switch (verticalGravity) {
                case Gravity.TOP:
                  childTop = lp.topMargin;
                  break;
                case Gravity.CENTER_VERTICAL:
                  childTop =
                      ((b - paddingBottom) - t - height) / 2 + lp.topMargin - lp.bottomMargin;
                  break;
                case Gravity.BOTTOM:
                  childTop = ((b - paddingBottom) - t) - height - lp.bottomMargin;
                  break;
                default:
                  childTop = lp.topMargin;
              }

              if (child == mentionListView) {
                childTop -= chatActivityEnterView.getMeasuredHeight() - AndroidUtilities.dp(2);
              } else if (child == pagedownButton) {
                childTop -= chatActivityEnterView.getMeasuredHeight();
              } else if (child == emptyViewContainer) {
                childTop -= inputFieldHeight / 2;
              } else if (chatActivityEnterView.isPopupView(child)) {
                childTop = chatActivityEnterView.getBottom();
              }
              child.layout(childLeft, childTop, childLeft + width, childTop + height);
            }

            notifyHeightChanged();
          }
        };
  }

  private View createContentView(Context context) {
    fragmentView = createFragmentView(context);
    SizeNotifierFrameLayout contentView = (SizeNotifierFrameLayout) fragmentView;
    contentView.setBackgroundImage(ApplicationLoader.getCachedWallpaper());

    addEmptyContentView(context, contentView);
    if (chatActivityEnterView != null) {
      chatActivityEnterView.onDestroy();
    }
    addChatListView(context, contentView);
    addProgressBarView(context, contentView);
    addMentionListView(context, contentView);
    addChatActivityEnterView(context, contentView);
    addBottomOverlayChatView(context, contentView);
    addPageDownButton(context, contentView);

    if ((loading && messages.isEmpty())) {
      progressView.setVisibility(View.VISIBLE);
      chatListView.setEmptyView(null);
    } else {
      progressView.setVisibility(View.INVISIBLE);
      chatListView.setEmptyView(emptyViewContainer);
    }
    chatActivityEnterView.setButtons(botButtons);

    updateContactStatus();
    updateBottomOverlay();
    updateSecretStatus();

    return fragmentView;
  }

  private void processSelectedAttach(int which) {
    if (which == attach_photo
        || which == attach_gallery
        || which == attach_document
        || which == attach_video) {
      String action;
      if (currentChat != null) {
        if (currentChat.participants_count > MessagesController.getInstance().groupBigSize) {
          if (which == attach_photo || which == attach_gallery) {
            action = "bigchat_upload_photo";
          } else {
            action = "bigchat_upload_document";
          }
        } else {
          if (which == attach_photo || which == attach_gallery) {
            action = "chat_upload_photo";
          } else {
            action = "chat_upload_document";
          }
        }
      } else {
        if (which == attach_photo || which == attach_gallery) {
          action = "pm_upload_photo";
        } else {
          action = "pm_upload_document";
        }
      }
      if (action != null && !MessagesController.isFeatureEnabled(action, ChatActivity.this)) {
        return;
      }
    }

    switch (which) {
      case attach_audio:
        attachAudio();
        break;
      case attach_contact:
        attachContact();
      case attach_document:
        attachDocument();
        break;
      case attach_gallery:
        attachGallery();
        break;
      case attach_location:
        attachLocation();
        break;
      case attach_photo:
        attachPhoto();
        break;
      case attach_video:
        attachVideo();
        break;
    }
  }

  private void attachContact() {
    try {
      Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
      intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
      startActivityForResult(intent, 31);
    } catch (Exception e) {
      FileLog.e("tmessages", e);
    }
  }

  private void attachAudio() {
    AudioSelectActivity fragment = new AudioSelectActivity();
    fragment.setDelegate(
        new AudioSelectActivity.AudioSelectActivityDelegate() {
          @Override
          public void didSelectAudio(ArrayList<MessageObject> audios) {
            SendMessagesHelper.prepareSendingAudioDocuments(
                audios, dialog_id, replyingMessageObject);
            showReplyPanel(false, null, null, null, false, true);
          }
        });
    presentFragment(fragment);
  }

  private void attachPhoto() {
    try {
      Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
      File image = AndroidUtilities.generatePicturePath();
      if (image != null) {
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(image));
        currentPicturePath = image.getAbsolutePath();
      }
      startActivityForResult(takePictureIntent, 0);
    } catch (Exception e) {
      FileLog.e("tmessages", e);
    }
  }

  private void attachGallery() {
    PhotoAlbumPickerActivity fragment = new PhotoAlbumPickerActivity(false, ChatActivity.this);
    fragment.setDelegate(
        new PhotoAlbumPickerActivity.PhotoAlbumPickerActivityDelegate() {
          @Override
          public void didSelectPhotos(
              ArrayList<String> photos,
              ArrayList<String> captions,
              ArrayList<MediaController.SearchImage> webPhotos) {
            SendMessagesHelper.prepareSendingPhotos(
                photos, null, dialog_id, replyingMessageObject, captions);
            SendMessagesHelper.prepareSendingPhotosSearch(
                webPhotos, dialog_id, replyingMessageObject);
            showReplyPanel(false, null, null, null, false, true);
          }

          @Override
          public void startPhotoSelectActivity() {
            try {
              Intent videoPickerIntent = new Intent();
              videoPickerIntent.setType("video/*");
              videoPickerIntent.setAction(Intent.ACTION_GET_CONTENT);
              videoPickerIntent.putExtra(
                  MediaStore.EXTRA_SIZE_LIMIT, (long) (1024 * 1024 * 1536));

              Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
              photoPickerIntent.setType("image/*");
              Intent chooserIntent = Intent.createChooser(photoPickerIntent, null);
              chooserIntent.putExtra(
                  Intent.EXTRA_INITIAL_INTENTS, new Intent[] {videoPickerIntent});

              startActivityForResult(chooserIntent, 1);
            } catch (Exception e) {
              FileLog.e("tmessages", e);
            }
          }

          @Override
          public boolean didSelectVideo(String path) {
            if (Build.VERSION.SDK_INT >= 16) {
              return !openVideoEditor(path, true, true);
            } else {
              SendMessagesHelper.prepareSendingVideo(
                  path, 0, 0, 0, 0, null, dialog_id, replyingMessageObject);
              showReplyPanel(false, null, null, null, false, true);
              return true;
            }
          }
        });
    presentFragment(fragment);
  }

  private void attachVideo() {
    try {
      Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
      File video = AndroidUtilities.generateVideoPath();
      if (video != null) {
        if (Build.VERSION.SDK_INT >= 18) {
          takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(video));
        }
        takeVideoIntent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, (long) (1024 * 1024 * 1536));
        currentPicturePath = video.getAbsolutePath();
      }
      startActivityForResult(takeVideoIntent, 2);
    } catch (Exception e) {
      FileLog.e("tmessages", e);
    }
  }

  private void attachLocation() {
    if (!isGoogleMapsInstalled()) {
      return;
    }
    LocationActivity fragment = new LocationActivity();
    fragment.setDelegate(
        new LocationActivity.LocationActivityDelegate() {
          @Override
          public void didSelectLocation(TLRPC.MessageMedia location) {
            SendMessagesHelper.getInstance()
                .sendMessage(location, dialog_id, replyingMessageObject);
            moveScrollToLastMessage();
            showReplyPanel(false, null, null, null, false, true);
            if (paused) {
              scrollToTopOnResume = true;
            }
          }
        });
    presentFragment(fragment);
  }

  private void attachDocument() {
    DocumentSelectActivity fragment = new DocumentSelectActivity();
    fragment.setDelegate(
        new DocumentSelectActivity.DocumentSelectActivityDelegate() {
          @Override
          public void didSelectFiles(DocumentSelectActivity activity, ArrayList<String> files) {
            activity.finishFragment();
            SendMessagesHelper.prepareSendingDocuments(
                files, files, null, null, dialog_id, replyingMessageObject);
            showReplyPanel(false, null, null, null, false, true);
          }

          @Override
          public void startDocumentSelectActivity() {
            try {
              Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
              photoPickerIntent.setType("*/*");
              startActivityForResult(photoPickerIntent, 21);
            } catch (Exception e) {
              FileLog.e("tmessages", e);
            }
          }
        });
    presentFragment(fragment);
  }

  private void searchLinks(CharSequence charSequence, boolean force) {
    if (currentEncryptedChat != null) {
      return;
    }
    if (linkSearchRequestId != 0) {
      ConnectionsManager.getInstance().cancelRpc(linkSearchRequestId, true);
      linkSearchRequestId = 0;
    }
    if (force && foundWebPage != null) {
      if (foundWebPage.url != null) {
        int index = TextUtils.indexOf(charSequence, foundWebPage.url);
        char lastChar;
        boolean lenEqual;
        if (index == -1) {
          index = TextUtils.indexOf(charSequence, foundWebPage.display_url);
          lenEqual =
              index != -1 && index + foundWebPage.display_url.length() == charSequence.length();
          lastChar =
              index != -1 && !lenEqual
                  ? charSequence.charAt(index + foundWebPage.display_url.length())
                  : 0;
        } else {
          lenEqual = index != -1 && index + foundWebPage.url.length() == charSequence.length();
          lastChar =
              index != -1 && !lenEqual ? charSequence.charAt(index + foundWebPage.url.length()) : 0;
        }
        if (index != -1
            && (lenEqual
                || lastChar == ' '
                || lastChar == ','
                || lastChar == '.'
                || lastChar == '!'
                || lastChar == '/')) {
          return;
        }
      }
      pendingLinkSearchString = null;
      showReplyPanel(false, null, null, foundWebPage, false, true);
    }
    if (charSequence.length() < 13 || !Utilities.searchForHttpInText(charSequence)) {
      return;
    }
    final TLRPC.TL_messages_getWebPagePreview req = new TLRPC.TL_messages_getWebPagePreview();
    if (charSequence instanceof String) {
      req.message = (String) charSequence;
    } else {
      req.message = charSequence.toString();
    }
    linkSearchRequestId =
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
                            linkSearchRequestId = 0;
                            if (error == null) {
                              if (response instanceof TLRPC.TL_messageMediaWebPage) {
                                foundWebPage = ((TLRPC.TL_messageMediaWebPage) response).webpage;
                                if (foundWebPage instanceof TLRPC.TL_webPage
                                    || foundWebPage instanceof TLRPC.TL_webPagePending) {
                                  if (foundWebPage instanceof TLRPC.TL_webPagePending) {
                                    pendingLinkSearchString = req.message;
                                  }
                                  showReplyPanel(true, null, null, foundWebPage, false, true);
                                } else {
                                  if (foundWebPage != null) {
                                    showReplyPanel(false, null, null, foundWebPage, false, true);
                                    foundWebPage = null;
                                  }
                                }
                              } else {
                                if (foundWebPage != null) {
                                  showReplyPanel(false, null, null, foundWebPage, false, true);
                                  foundWebPage = null;
                                }
                              }
                            }
                          }
                        });
                  }
                });
    ConnectionsManager.getInstance().bindRequestToGuid(linkSearchRequestId, classGuid);
  }

  private void forwardMessages(ArrayList<MessageObject> arrayList, boolean fromMyName) {
    if (arrayList == null || arrayList.isEmpty()) {
      return;
    }
    if (!fromMyName) {
      SendMessagesHelper.getInstance().sendMessage(arrayList, dialog_id);
    } else {
      for (MessageObject object : arrayList) {
        SendMessagesHelper.getInstance().processForwardFromMyName(object, dialog_id);
      }
    }
  }

  public void showReplyPanel(
      boolean show,
      MessageObject messageObject,
      ArrayList<MessageObject> messageObjects,
      TLRPC.WebPage webPage,
      boolean cancel,
      boolean animated) {
    if (show) {
      if (messageObject == null && messageObjects == null && webPage == null) {
        return;
      }
      if (messageObject != null) {
        TLRPC.User user =
            MessagesController.getInstance().getUser(messageObject.messageOwner.from_id);
        if (user == null) {
          return;
        }
        forwardingMessages = null;
        replyingMessageObject = messageObject;
        chatActivityEnterView.setReplyingMessageObject(messageObject);

        if (foundWebPage != null) {
          return;
        }
        replyIconImageView.setImageResource(R.drawable.reply);
        replyNameTextView.setText(UserObject.getUserName(user));
        if (messageObject.messageText != null) {
          String mess = messageObject.messageText.toString();
          if (mess.length() > 150) {
            mess = mess.substring(0, 150);
          }
          mess = mess.replace("\n", " ");
          replyObjectTextView.setText(
              Emoji.replaceEmoji(
                  mess,
                  replyObjectTextView.getPaint().getFontMetricsInt(),
                  AndroidUtilities.dp(14),
                  false));
        }
      } else if (messageObjects != null) {
        if (messageObjects.isEmpty()) {
          return;
        }
        replyingMessageObject = null;
        chatActivityEnterView.setReplyingMessageObject(null);
        forwardingMessages = messageObjects;

        if (foundWebPage != null) {
          return;
        }
        chatActivityEnterView.setForceShowSendButton(true, animated);
        ArrayList<Integer> uids = new ArrayList<>();
        replyIconImageView.setImageResource(R.drawable.forward_blue);
        uids.add(messageObjects.get(0).messageOwner.from_id);
        MessageObject.Type type = messageObjects.get(0).type;
        for (int a = 1; a < messageObjects.size(); a++) {
          Integer uid = messageObjects.get(a).messageOwner.from_id;
          if (!uids.contains(uid)) {
            uids.add(uid);
          }
          if (messageObjects.get(a).type != type) {
            type = MessageObject.Type.INVALID;
          }
        }
        StringBuilder userNames = new StringBuilder();
        for (int a = 0; a < uids.size(); a++) {
          Integer uid = uids.get(a);
          TLRPC.User user = MessagesController.getInstance().getUser(uid);
          if (user == null) {
            continue;
          }
          if (uids.size() == 1) {
            userNames.append(UserObject.getUserName(user));
          } else if (uids.size() == 2 || userNames.length() == 0) {
            if (userNames.length() > 0) {
              userNames.append(", ");
            }
            if (user.first_name != null && user.first_name.length() > 0) {
              userNames.append(user.first_name);
            } else if (user.last_name != null && user.last_name.length() > 0) {
              userNames.append(user.last_name);
            } else {
              userNames.append(" ");
            }
          } else {
            userNames.append(" ");
            userNames.append(LocaleController.formatPluralString("AndOther", uids.size() - 1));
            break;
          }
        }
        replyNameTextView.setText(userNames);
        if (type == MessageObject.Type.INVALID || type == MessageObject.Type.TEXT) {
          if (messageObjects.size() == 1 && messageObjects.get(0).messageText != null) {
            String mess = messageObjects.get(0).messageText.toString();
            if (mess.length() > 150) {
              mess = mess.substring(0, 150);
            }
            mess = mess.replace("\n", " ");
            replyObjectTextView.setText(
                Emoji.replaceEmoji(
                    mess,
                    replyObjectTextView.getPaint().getFontMetricsInt(),
                    AndroidUtilities.dp(14),
                    false));
          } else {
            replyObjectTextView.setText(
                LocaleController.formatPluralString("ForwardedMessage", messageObjects.size()));
          }
        } else {
          if (type == MessageObject.Type.PHOTO) {
            replyObjectTextView.setText(
                LocaleController.formatPluralString("ForwardedPhoto", messageObjects.size()));
            if (messageObjects.size() == 1) {
              messageObject = messageObjects.get(0);
            }
          } else if (type == MessageObject.Type.LOCATION) {
            replyObjectTextView.setText(
                LocaleController.formatPluralString("ForwardedLocation", messageObjects.size()));
          } else if (type == MessageObject.Type.VIDEO) {
            replyObjectTextView.setText(
                LocaleController.formatPluralString("ForwardedVideo", messageObjects.size()));
            if (messageObjects.size() == 1) {
              messageObject = messageObjects.get(0);
            }
          } else if (type == MessageObject.Type.CONTACT) {
            replyObjectTextView.setText(
                LocaleController.formatPluralString("ForwardedContact", messageObjects.size()));
          } else if (type == MessageObject.Type.AUDIO || type == MessageObject.Type.DOC_AUDIO) {
            replyObjectTextView.setText(
                LocaleController.formatPluralString("ForwardedAudio", messageObjects.size()));
          } else if (type == MessageObject.Type.DOC_STICKER_WEBP) {
            replyObjectTextView.setText(
                LocaleController.formatPluralString("ForwardedSticker", messageObjects.size()));
          } else if (type == MessageObject.Type.DOC_GIF || type == MessageObject.Type.DOC_GENERIC) {
            if (messageObjects.size() == 1) {
              String name;
              if ((name =
                          FileLoader.getDocumentFileName(
                              messageObjects.get(0).messageOwner.media.document))
                      .length()
                  != 0) {
                replyObjectTextView.setText(name);
              }
              messageObject = messageObjects.get(0);
            } else {
              replyObjectTextView.setText(
                  LocaleController.formatPluralString("ForwardedFile", messageObjects.size()));
            }
          }
        }
      } else if (webPage != null) {
        replyIconImageView.setImageResource(R.drawable.link);
        if (webPage instanceof TLRPC.TL_webPagePending) {
          replyNameTextView.setText(
              LocaleController.getString("GettingLinkInfo", R.string.GettingLinkInfo));
          replyObjectTextView.setText(pendingLinkSearchString);
        } else {
          if (webPage.site_name != null) {
            replyNameTextView.setText(webPage.site_name);
          } else if (webPage.title != null) {
            replyNameTextView.setText(webPage.title);
          }
          if (webPage.description != null) {
            replyObjectTextView.setText(webPage.description);
          } else if (webPage.title != null && webPage.site_name != null) {
            replyObjectTextView.setText(webPage.title);
          } else if (webPage.author != null) {
            replyObjectTextView.setText(webPage.author);
          } else {
            replyObjectTextView.setText(webPage.display_url);
          }
          chatActivityEnterView.setWebPage(webPage, true);
        }
      }
      FrameLayout.LayoutParams layoutParams1 =
          (FrameLayout.LayoutParams) replyNameTextView.getLayoutParams();
      FrameLayout.LayoutParams layoutParams2 =
          (FrameLayout.LayoutParams) replyObjectTextView.getLayoutParams();
      TLRPC.PhotoSize photoSize =
          messageObject != null
              ? FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 80)
              : null;
      if (photoSize == null || messageObject != null && messageObject.type == MessageObject.Type.DOC_STICKER_WEBP) {
        replyImageView.setImageBitmap(null);
        replyImageLocation = null;
        replyImageView.setVisibility(View.INVISIBLE);
        layoutParams1.leftMargin = layoutParams2.leftMargin = AndroidUtilities.dp(52);
      } else {
        replyImageLocation = photoSize.location;
        replyImageView.setImage(replyImageLocation, "50_50", (Drawable) null);
        replyImageView.setVisibility(View.VISIBLE);
        layoutParams1.leftMargin = layoutParams2.leftMargin = AndroidUtilities.dp(96);
      }
      replyNameTextView.setLayoutParams(layoutParams1);
      replyObjectTextView.setLayoutParams(layoutParams2);
      chatActivityEnterView.showTopView(animated);
    } else {
      if (replyingMessageObject == null && forwardingMessages == null && foundWebPage == null) {
        return;
      }
      if (replyingMessageObject != null
          && replyingMessageObject.messageOwner.reply_markup
              instanceof TLRPC.TL_replyKeyboardForceReply) {
        SharedPreferences preferences =
            ApplicationLoader.applicationContext.getSharedPreferences(
                "mainconfig", Activity.MODE_PRIVATE);
        preferences.edit().putInt("answered_" + dialog_id, replyingMessageObject.getId()).commit();
      }
      if (foundWebPage != null) {
        foundWebPage = null;
        chatActivityEnterView.setWebPage(null, !cancel);
        if (webPage != null && (replyingMessageObject != null || forwardingMessages != null)) {
          showReplyPanel(true, replyingMessageObject, forwardingMessages, null, false, true);
          return;
        }
      }
      if (forwardingMessages != null) {
        forwardMessages(forwardingMessages, false);
      }
      chatActivityEnterView.setForceShowSendButton(false, animated);
      chatActivityEnterView.hideTopView(animated);
      chatActivityEnterView.setReplyingMessageObject(null);
      replyingMessageObject = null;
      forwardingMessages = null;
      replyImageLocation = null;
      SharedPreferences preferences =
          ApplicationLoader.applicationContext.getSharedPreferences(
              "mainconfig", Activity.MODE_PRIVATE);
      preferences.edit().remove("reply_" + dialog_id).commit();
    }
  }

  private void moveScrollToLastMessage() {
    if (chatListView != null) {
      chatLayoutManager.scrollToPositionWithOffset(
          messages.size() - 1, -100000 - chatListView.getPaddingTop());
    }
  }

  private boolean sendSecretMessageRead(MessageObject messageObject) {
    if (messageObject == null
        || messageObject.isOut()
        || !messageObject.isSecretMedia()
        || messageObject.messageOwner.destroyTime != 0
        || messageObject.messageOwner.ttl <= 0) {
      return false;
    }
    MessagesController.getInstance()
        .markMessageAsRead(
            dialog_id, messageObject.messageOwner.random_id, messageObject.messageOwner.ttl);
    messageObject.messageOwner.destroyTime =
        messageObject.messageOwner.ttl + ConnectionsManager.getInstance().getCurrentTime();
    return true;
  }

  private void scrollToLastMessage() {
    if (forwardEndReached && first_unread_id == 0 && startLoadFromMessageId == 0) {
      chatLayoutManager.scrollToPositionWithOffset(
          messages.size() - 1, -100000 - chatListView.getPaddingTop());
    } else {
      messages.clear();
      messagesByDays.clear();
      messagesDict.clear();
      progressView.setVisibility(View.VISIBLE);
      chatListView.setEmptyView(null);
      if (currentEncryptedChat == null) {
        maxMessageId = Integer.MAX_VALUE;
        minMessageId = Integer.MIN_VALUE;
      } else {
        maxMessageId = Integer.MIN_VALUE;
        minMessageId = Integer.MAX_VALUE;
      }
      maxDate = Integer.MIN_VALUE;
      minDate = 0;
      forwardEndReached = true;
      first = true;
      firstLoading = true;
      loading = true;
      startLoadFromMessageId = 0;
      needSelectFromMessageId = false;
      chatAdapter.notifyDataSetChanged();
      MessagesController.getInstance()
          .loadMessages(dialog_id, 30, 0, true, 0, classGuid, 0, 0, 0, true);
    }
  }

  private void updateMessagesVisisblePart() {
    if (chatListView == null) {
      return;
    }
    int count = chatListView.getChildCount();
    for (int a = 0; a < count; a++) {
      View view = chatListView.getChildAt(a);
      if (view instanceof ChatMessageCell) {
        ChatMessageCell messageCell = (ChatMessageCell) view;
        messageCell.getLocalVisibleRect(scrollRect);
        messageCell.setVisiblePart(scrollRect.top, scrollRect.bottom - scrollRect.top);
      }
    }
  }

  private void scrollToMessageId(int id, int fromMessageId, boolean select) {
    returnToMessageId = fromMessageId;
    needSelectFromMessageId = select;

    MessageObject object = messagesDict.get(id);
    boolean query = false;
    if (object != null) {
      int index = messages.indexOf(object);
      if (index != -1) {
        if (needSelectFromMessageId) {
          highlightMessageId = id;
        } else {
          highlightMessageId = Integer.MAX_VALUE;
        }
        final int yOffset =
            Math.max(0, (chatListView.getHeight() - object.getApproximateHeight()) / 2);
        if (messages.get(messages.size() - 1) == object) {
          chatLayoutManager.scrollToPositionWithOffset(0, AndroidUtilities.dp(-11) + yOffset);
        } else {
          chatLayoutManager.scrollToPositionWithOffset(
              messages.size() - messages.indexOf(object), AndroidUtilities.dp(-11) + yOffset);
        }
        updateVisibleRows();
        showPagedownButton(true, true);
      } else {
        query = true;
      }
    } else {
      query = true;
    }

    if (query) {
      messagesDict.clear();
      messagesByDays.clear();
      messages.clear();
      if (currentEncryptedChat == null) {
        maxMessageId = Integer.MAX_VALUE;
        minMessageId = Integer.MIN_VALUE;
      } else {
        maxMessageId = Integer.MIN_VALUE;
        minMessageId = Integer.MAX_VALUE;
      }
      maxDate = Integer.MIN_VALUE;
      endReached = false;
      loading = false;
      cacheEndReaced = false;
      firstLoading = true;
      loadsCount = 0;
      minDate = 0;
      first = true;
      unread_to_load = 0;
      first_unread_id = 0;
      last_message_id = 0;
      first_message_id = 0;
      forwardEndReached = true;
      loadingForward = false;
      unreadMessageObject = null;
      scrollToMessage = null;
      highlightMessageId = Integer.MAX_VALUE;
      scrollToMessageMiddleScreen = false;
      loading = true;
      startLoadFromMessageId = id;
      MessagesController.getInstance()
          .loadMessages(
              dialog_id,
              20,
              startLoadFromMessageId,
              true,
              0,
              classGuid,
              3,
              0,
              0,
              false);
      chatAdapter.notifyDataSetChanged();
      progressView.setVisibility(View.VISIBLE);
      chatListView.setEmptyView(null);
      emptyViewContainer.setVisibility(View.INVISIBLE);
    }
  }

  private void showPagedownButton(boolean show, boolean animated) {
    if (pagedownButton == null) {
      return;
    }
    if (show) {
      if (pagedownButton.getVisibility() == View.INVISIBLE) {
        if (animated) {
          pagedownButton.setVisibility(View.VISIBLE);
          ViewProxy.setAlpha(pagedownButton, 0);
          ObjectAnimatorProxy.ofFloatProxy(pagedownButton, "alpha", 1.0f).setDuration(200).start();
        } else {
          pagedownButton.setVisibility(View.VISIBLE);
        }
      }
    } else {
      returnToMessageId = 0;
      if (pagedownButton.getVisibility() == View.VISIBLE) {
        if (animated) {
          ObjectAnimatorProxy.ofFloatProxy(pagedownButton, "alpha", 0.0f)
              .setDuration(200)
              .addListener(
                  new AnimatorListenerAdapterProxy() {
                    @Override
                    public void onAnimationEnd(Object animation) {
                      pagedownButton.setVisibility(View.INVISIBLE);
                    }
                  })
              .start();
        } else {
          pagedownButton.setVisibility(View.INVISIBLE);
        }
      }
    }
  }

  private void updateSecretStatus() {
    if (bottomOverlay == null) {
      return;
    }
    if (currentEncryptedChat == null || secretViewStatusTextView == null) {
      bottomOverlay.setVisibility(View.INVISIBLE);
      return;
    }
    boolean hideKeyboard = false;
    if (currentEncryptedChat instanceof TLRPC.TL_encryptedChatRequested) {
      bottomOverlayText.setText(
          LocaleController.getString("EncryptionProcessing", R.string.EncryptionProcessing));
      bottomOverlay.setVisibility(View.VISIBLE);
      hideKeyboard = true;
    } else if (currentEncryptedChat instanceof TLRPC.TL_encryptedChatWaiting) {
      bottomOverlayText.setText(
          AndroidUtilities.replaceTags(
              LocaleController.formatString(
                  "AwaitingEncryption",
                  R.string.AwaitingEncryption,
                  "<b>" + currentUser.first_name + "</b>")));
      bottomOverlay.setVisibility(View.VISIBLE);
      hideKeyboard = true;
    } else if (currentEncryptedChat instanceof TLRPC.TL_encryptedChatDiscarded) {
      bottomOverlayText.setText(
          LocaleController.getString("EncryptionRejected", R.string.EncryptionRejected));
      bottomOverlay.setVisibility(View.VISIBLE);
      chatActivityEnterView.setFieldText("");
      SharedPreferences preferences =
          ApplicationLoader.applicationContext.getSharedPreferences(
              "mainconfig", Activity.MODE_PRIVATE);
      preferences.edit().remove("dialog_" + dialog_id).commit();
      hideKeyboard = true;
    } else if (currentEncryptedChat instanceof TLRPC.TL_encryptedChat) {
      bottomOverlay.setVisibility(View.INVISIBLE);
    }
    if (hideKeyboard) {
      chatActivityEnterView.hidePopup();
      if (getParentActivity() != null) {
        AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
      }
    }
    checkActionBarMenu();
  }

  private void checkActionBarMenu() {
    if (currentEncryptedChat != null && !(currentEncryptedChat instanceof TLRPC.TL_encryptedChat)
        || currentChat != null
            && (currentChat instanceof TLRPC.TL_chatForbidden || currentChat.left)
        || currentUser != null && UserObject.isDeleted(currentUser)) {

      if (menuItem != null) {
        menuItem.setVisibility(View.GONE);
      }
      if (timeItem != null) {
        timeItem.setVisibility(View.GONE);
      }
      if (timeItem2 != null) {
        timeItem2.setVisibility(View.GONE);
      }
    } else {
      if (menuItem != null) {
        menuItem.setVisibility(View.VISIBLE);
      }
      if (timeItem != null) {
        timeItem.setVisibility(View.VISIBLE);
      }
      if (timeItem2 != null) {
        timeItem2.setVisibility(View.VISIBLE);
      }
    }

    if (timerDrawable != null) {
      timerDrawable.setTime(currentEncryptedChat.ttl);
    }

    checkAndUpdateAvatar();
  }

  private int updateOnlineCount() {
    if (info == null) {
      return 0;
    }
    onlineCount = 0;
    int currentTime = ConnectionsManager.getInstance().getCurrentTime();
    for (TLRPC.TL_chatParticipant participant : info.participants) {
      TLRPC.User user = MessagesController.getInstance().getUser(participant.user_id);
      if (user != null
          && user.status != null
          && (user.status.expires > currentTime || user.id == UserConfig.getClientUserId())
          && user.status.expires > 10000) {
        onlineCount++;
      }
    }
    return onlineCount;
  }

  private int getMessageType(MessageObject messageObject) {
    if (messageObject == null) {
      return -1;
    }
    if (currentEncryptedChat == null) {
      boolean isBroadcastError =
          isBroadcast && messageObject.getId() <= 0 && messageObject.isSendError();
      if (!isBroadcast && messageObject.getId() <= 0 && messageObject.isOut() || isBroadcastError) {
        if (messageObject.isSendError()) {
          if (!messageObject.isMediaEmpty()) {
            return 0;
          } else {
            return 20;
          }
        } else {
          return -1;
        }
      } else {
        if (messageObject.type == MessageObject.Type.CONTACT) {
          return -1;
        } else if (messageObject.type == MessageObject.Type.CHAT_ACTION_PHOTO ||
            messageObject.type == MessageObject.Type.MSG_ACTION) {
          if (messageObject.getId() == 0) {
            return -1;
          }
          return 1;
        } else {
          if (!messageObject.isMediaEmpty()) {
            if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaVideo
                || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto
                || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
              if (messageObject.isSticker()) {
                TLRPC.InputStickerSet inputStickerSet = messageObject.getInputStickerSet();
                if (inputStickerSet != null
                    && !StickersQuery.isStickerPackInstalled(inputStickerSet.id)) {
                  return 7;
                }
              }
              boolean canSave = false;
              if (messageObject.messageOwner.attachPath != null
                  && messageObject.messageOwner.attachPath.length() != 0) {
                File f = new File(messageObject.messageOwner.attachPath);
                if (f.exists()) {
                  canSave = true;
                }
              }
              if (!canSave) {
                File f = FileLoader.getPathToMessage(messageObject.messageOwner);
                if (f.exists()) {
                  canSave = true;
                }
              }
              if (canSave) {
                if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                  String mime = messageObject.messageOwner.media.document.mime_type;
                  if (mime != null) {
                    if (mime.endsWith("/xml")) {
                      return 5;
                    } else if (mime.endsWith("/png")
                        || mime.endsWith("/jpg")
                        || mime.endsWith("/jpeg")) {
                      return 6;
                    }
                  }
                }
                return 4;
              }
            }
            return 2;
          } else {
            return 3;
          }
        }
      }
    } else {
      if (messageObject.isSending()) {
        return -1;
      }
      if (messageObject.type == MessageObject.Type.CONTACT) {
        return -1;
      } else if (messageObject.isSendError()) {
        if (!messageObject.isMediaEmpty()) {
          return 0;
        } else {
          return 20;
        }
      } else if (messageObject.type == MessageObject.Type.CHAT_ACTION_PHOTO ||
          messageObject.type == MessageObject.Type.MSG_ACTION) {
        if (messageObject.getId() == 0 || messageObject.isSending()) {
          return -1;
        } else {
          return 1;
        }
      } else {
        if (!messageObject.isMediaEmpty()) {
          if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaVideo
              || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto
              || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
            if (messageObject.isSticker()) {
              TLRPC.InputStickerSet inputStickerSet = messageObject.getInputStickerSet();
              if (inputStickerSet != null
                  && !StickersQuery.isStickerPackInstalled(inputStickerSet.id)) {
                return 7;
              }
            }
            boolean canSave = false;
            if (messageObject.messageOwner.attachPath != null
                && messageObject.messageOwner.attachPath.length() != 0) {
              File f = new File(messageObject.messageOwner.attachPath);
              if (f.exists()) {
                canSave = true;
              }
            }
            if (!canSave) {
              File f = FileLoader.getPathToMessage(messageObject.messageOwner);
              if (f.exists()) {
                canSave = true;
              }
            }
            if (canSave) {
              if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                String mime = messageObject.messageOwner.media.document.mime_type;
                if (mime != null && mime.endsWith("text/xml")) {
                  return 5;
                }
              }
              if (messageObject.messageOwner.ttl <= 0) {
                return 4;
              }
            }
          }
          return 2;
        } else {
          return 3;
        }
      }
    }
  }

  private void addToSelectedMessages(MessageObject messageObject) {
    if (selectedMessagesIds.containsKey(messageObject.getId())) {
      selectedMessagesIds.remove(messageObject.getId());
      if (messageObject.type == MessageObject.Type.TEXT) {
        selectedMessagesCanCopyIds.remove(messageObject.getId());
      }
    } else {
      selectedMessagesIds.put(messageObject.getId(), messageObject);
      if (messageObject.type == MessageObject.Type.TEXT) {
        selectedMessagesCanCopyIds.put(messageObject.getId(), messageObject);
      }
    }
    if (actionBar.isActionModeShowed()) {
      if (selectedMessagesIds.isEmpty()) {
        actionBar.hideActionMode();
      }
      actionBar
          .createActionMode()
          .getItem(copy)
          .setVisibility(selectedMessagesCanCopyIds.size() != 0 ? View.VISIBLE : View.GONE);
      if (actionBar.createActionMode().getItem(reply) != null) {
        actionBar
            .createActionMode()
            .getItem(reply)
            .setVisibility(selectedMessagesIds.size() == 1 ? View.VISIBLE : View.GONE);
      }
    }
  }

  private void processRowSelect(View view) {
    MessageObject message = null;
    if (view instanceof ChatBaseCell) {
      message = ((ChatBaseCell) view).getMessageObject();
    } else if (view instanceof ChatActionCell) {
      message = ((ChatActionCell) view).getMessageObject();
    }

    int type = getMessageType(message);

    if (type < 2 || type == 8) {
      return;
    }
    addToSelectedMessages(message);
    updateActionModeTitle();
    updateVisibleRows();
  }

  private void updateActionModeTitle() {
    if (!actionBar.isActionModeShowed()) {
      return;
    }
    if (!selectedMessagesIds.isEmpty()) {
      selectedMessagesCountTextView.setText(String.format("%d", selectedMessagesIds.size()));
    }
  }

  private void updateTitle() {
    if (nameTextView == null) {
      return;
    }
    if (currentChat != null) {
      nameTextView.setText(currentChat.title);
    } else if (currentUser != null) {
      if (currentUser.id / 1000 != 777
          && currentUser.id / 1000 != 333
          && ContactsController.getInstance().contactsDict.get(currentUser.id) == null
          && (ContactsController.getInstance().contactsDict.size() != 0
              || !ContactsController.getInstance().isLoadingContacts())) {
        if (currentUser.phone != null && currentUser.phone.length() != 0) {
          nameTextView.setText(PhoneFormat.getInstance().format("+" + currentUser.phone));
        } else {
          nameTextView.setText(UserObject.getUserName(currentUser));
        }
      } else {
        nameTextView.setText(UserObject.getUserName(currentUser));
      }
    }
  }

  private void updateBotButtons() {
    if (headerItem == null
        || currentUser == null
        || currentEncryptedChat != null
        || (currentUser.flags & TLRPC.USER_FLAG_BOT) == 0) {
      return;
    }
    boolean hasHelp = false;
    boolean hasSettings = false;
    if (!botInfo.isEmpty()) {
      for (HashMap.Entry<Integer, TLRPC.BotInfo> entry : botInfo.entrySet()) {
        TLRPC.BotInfo info = entry.getValue();
        for (int a = 0; a < info.commands.size(); a++) {
          TLRPC.TL_botCommand command = info.commands.get(a);
          if (command.command.toLowerCase().equals("help")) {
            hasHelp = true;
          } else if (command.command.toLowerCase().equals("settings")) {
            hasSettings = true;
          }
          if (hasSettings && hasHelp) {
            break;
          }
        }
      }
    }
    if (hasHelp) {
      headerItem.showSubItem(bot_help);
    } else {
      headerItem.hideSubItem(bot_help);
    }
    if (hasSettings) {
      headerItem.showSubItem(bot_settings);
    } else {
      headerItem.hideSubItem(bot_settings);
    }
  }

  private void updateTitleIcons() {
    int leftIcon = currentEncryptedChat != null ? R.drawable.ic_lock_header : 0;
    int rightIcon =
        MessagesController.getInstance().isDialogMuted(dialog_id) ? R.drawable.mute_fixed : 0;
    nameTextView.setCompoundDrawablesWithIntrinsicBounds(leftIcon, 0, rightIcon, 0);

    if (rightIcon != 0) {
      muteItem.setText(
          LocaleController.getString("UnmuteNotifications", R.string.UnmuteNotifications));
    } else {
      muteItem.setText(LocaleController.getString("MuteNotifications", R.string.MuteNotifications));
    }
  }

  private void updateSubtitle() {
    if (onlineTextView == null) {
      return;
    }
    CharSequence printString = MessagesController.getInstance().printingStrings.get(dialog_id);
    if (printString != null) {
      printString = TextUtils.replace(printString, new String[] {"..."}, new String[] {""});
    }
    if (printString == null || printString.length() == 0) {
      setTypingAnimation(false);
      if (currentChat != null) {
        if (currentChat instanceof TLRPC.TL_chatForbidden) {
          onlineTextView.setText(
              LocaleController.getString("YouWereKicked", R.string.YouWereKicked));
        } else if (currentChat.left) {
          onlineTextView.setText(LocaleController.getString("YouLeft", R.string.YouLeft));
        } else {
          int count = currentChat.participants_count;
          if (info != null) {
            count = info.participants.size();
          }
          if (onlineCount > 1 && count != 0) {
            onlineTextView.setText(
                String.format(
                    "%s, %s",
                    LocaleController.formatPluralString("Members", count),
                    LocaleController.formatPluralString("Online", onlineCount)));
          } else {
            onlineTextView.setText(LocaleController.formatPluralString("Members", count));
          }
        }
      } else if (currentUser != null) {
        TLRPC.User user = MessagesController.getInstance().getUser(currentUser.id);
        if (user != null) {
          currentUser = user;
        }
        String newStatus;
        if (currentUser.id == 333000 || currentUser.id == 777000) {
          newStatus =
              LocaleController.getString("ServiceNotifications", R.string.ServiceNotifications);
        } else if ((currentUser.flags & TLRPC.USER_FLAG_BOT) != 0) {
          newStatus = LocaleController.getString("Bot", R.string.Bot);
        } else {
          newStatus = LocaleController.formatUserStatus(currentUser);
        }
        if (lastStatus == null
            || lastPrintString != null
            || lastStatus != null && !lastStatus.equals(newStatus)) {
          lastStatus = newStatus;
          onlineTextView.setText(newStatus);
        }
      }
      lastPrintString = null;
    } else {
      lastPrintString = printString;
      onlineTextView.setText(printString);
      setTypingAnimation(true);
    }
  }

  private void setTypingAnimation(boolean start) {
    if (actionBar == null) {
      return;
    }
    if (start) {
      try {
        Integer type = MessagesController.getInstance().printingStringsTypes.get(dialog_id);
        if (type == 0) {
          if (lastStatusDrawable == 1) {
            return;
          }
          lastStatusDrawable = 1;
          if (onlineTextView != null) {
            onlineTextView.setCompoundDrawablesWithIntrinsicBounds(
                typingDotsDrawable, null, null, null);
            onlineTextView.setCompoundDrawablePadding(AndroidUtilities.dp(4));

            typingDotsDrawable.start();
            recordStatusDrawable.stop();
            sendingFileDrawable.stop();
          }
        } else if (type == 1) {
          if (lastStatusDrawable == 2) {
            return;
          }
          lastStatusDrawable = 2;
          if (onlineTextView != null) {
            onlineTextView.setCompoundDrawablesWithIntrinsicBounds(
                recordStatusDrawable, null, null, null);
            onlineTextView.setCompoundDrawablePadding(AndroidUtilities.dp(4));

            recordStatusDrawable.start();
            typingDotsDrawable.stop();
            sendingFileDrawable.stop();
          }
        } else if (type == 2) {
          if (lastStatusDrawable == 3) {
            return;
          }
          lastStatusDrawable = 3;
          if (onlineTextView != null) {
            onlineTextView.setCompoundDrawablesWithIntrinsicBounds(
                sendingFileDrawable, null, null, null);
            onlineTextView.setCompoundDrawablePadding(AndroidUtilities.dp(4));

            sendingFileDrawable.start();
            typingDotsDrawable.stop();
            recordStatusDrawable.stop();
          }
        }
      } catch (Exception e) {
        FileLog.e("tmessages", e);
      }
    } else {
      if (lastStatusDrawable == 0) {
        return;
      }
      lastStatusDrawable = 0;
      if (onlineTextView != null) {
        onlineTextView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
        onlineTextView.setCompoundDrawablePadding(0);

        typingDotsDrawable.stop();
        recordStatusDrawable.stop();
        sendingFileDrawable.stop();
      }
    }
  }

  private void checkAndUpdateAvatar() {
    TLRPC.FileLocation newPhoto = null;
    AvatarDrawable avatarDrawable = null;
    if (currentUser != null) {
      TLRPC.User user = MessagesController.getInstance().getUser(currentUser.id);
      if (user == null) {
        return;
      }
      currentUser = user;
      if (currentUser.photo != null) {
        newPhoto = currentUser.photo.photo_small;
      }
      avatarDrawable = new AvatarDrawable(currentUser);
    } else if (currentChat != null) {
      TLRPC.Chat chat = MessagesController.getInstance().getChat(currentChat.id);
      if (chat == null) {
        return;
      }
      currentChat = chat;
      if (currentChat.photo != null) {
        newPhoto = currentChat.photo.photo_small;
      }
      avatarDrawable = new AvatarDrawable(currentChat);
    }
    if (avatarImageView != null) {
      avatarImageView.setImage(newPhoto, "50_50", avatarDrawable);
    }
  }

  public boolean openVideoEditor(String videoPath, boolean removeLast, boolean animated) {
    Bundle args = new Bundle();
    args.putString("videoPath", videoPath);
    VideoEditorActivity fragment = new VideoEditorActivity(args);
    fragment.setDelegate(
        new VideoEditorActivity.VideoEditorActivityDelegate() {
          @Override
          public void didFinishEditVideo(
              String videoPath,
              long startTime,
              long endTime,
              int resultWidth,
              int resultHeight,
              int rotationValue,
              int originalWidth,
              int originalHeight,
              int bitrate,
              long estimatedSize,
              long estimatedDuration) {
            VideoEditedInfo videoEditedInfo = new VideoEditedInfo();
            videoEditedInfo.startTime = startTime;
            videoEditedInfo.endTime = endTime;
            videoEditedInfo.rotationValue = rotationValue;
            videoEditedInfo.originalWidth = originalWidth;
            videoEditedInfo.originalHeight = originalHeight;
            videoEditedInfo.bitrate = bitrate;
            videoEditedInfo.resultWidth = resultWidth;
            videoEditedInfo.resultHeight = resultHeight;
            videoEditedInfo.originalPath = videoPath;
            SendMessagesHelper.prepareSendingVideo(
                videoPath,
                estimatedSize,
                estimatedDuration,
                resultWidth,
                resultHeight,
                videoEditedInfo,
                dialog_id,
                replyingMessageObject);
            showReplyPanel(false, null, null, null, false, true);
          }
        });

    if (parentLayout == null || !fragment.onFragmentCreate()) {
      SendMessagesHelper.prepareSendingVideo(
          videoPath, 0, 0, 0, 0, null, dialog_id, replyingMessageObject);
      showReplyPanel(false, null, null, null, false, true);
      return false;
    }
    parentLayout.presentFragment(fragment, removeLast, !animated, true);
    return true;
  }

  private void showAttachmentError() {
    if (getParentActivity() == null) {
      return;
    }
    Toast toast =
        Toast.makeText(
            getParentActivity(),
            LocaleController.getString("UnsupportedAttachment", R.string.UnsupportedAttachment),
            Toast.LENGTH_SHORT);
    toast.show();
  }

  @Override
  public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
    if (resultCode == Activity.RESULT_OK) {
      if (requestCode == 0) {
        PhotoViewer.getInstance().setParentActivity(getParentActivity());
        final ArrayList<Object> arrayList = new ArrayList<>();
        int orientation = 0;
        try {
          ExifInterface ei = new ExifInterface(currentPicturePath);
          int exif =
              ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
          switch (exif) {
            case ExifInterface.ORIENTATION_ROTATE_90:
              orientation = 90;
              break;
            case ExifInterface.ORIENTATION_ROTATE_180:
              orientation = 180;
              break;
            case ExifInterface.ORIENTATION_ROTATE_270:
              orientation = 270;
              break;
          }
        } catch (Exception e) {
          FileLog.e("tmessages", e);
        }
        arrayList.add(
            new MediaController.PhotoEntry(0, 0, 0, currentPicturePath, orientation, false));

        PhotoViewer.getInstance()
            .openPhotoForSelect(
                arrayList,
                0,
                2,
                new PhotoViewer.EmptyPhotoViewerProvider() {
                  @Override
                  public void sendButtonPressed(int index) {
                    MediaController.PhotoEntry photoEntry =
                        (MediaController.PhotoEntry) arrayList.get(0);
                    if (photoEntry.imagePath != null) {
                      SendMessagesHelper.prepareSendingPhoto(
                          photoEntry.imagePath,
                          null,
                          dialog_id,
                          replyingMessageObject,
                          photoEntry.caption);
                      showReplyPanel(false, null, null, null, false, true);
                    } else if (photoEntry.path != null) {
                      SendMessagesHelper.prepareSendingPhoto(
                          photoEntry.path,
                          null,
                          dialog_id,
                          replyingMessageObject,
                          photoEntry.caption);
                      showReplyPanel(false, null, null, null, false, true);
                    }
                  }
                },
                this);
        AndroidUtilities.addMediaToGallery(currentPicturePath);
        currentPicturePath = null;
      } else if (requestCode == 1) {
        if (data == null || data.getData() == null) {
          showAttachmentError();
          return;
        }
        Uri uri = data.getData();
        if (uri.toString().contains("video")) {
          String videoPath = null;
          try {
            videoPath = AndroidUtilities.getPath(uri);
          } catch (Exception e) {
            FileLog.e("tmessages", e);
          }
          if (videoPath == null) {
            showAttachmentError();
          }
          if (Build.VERSION.SDK_INT >= 16) {
            if (paused) {
              startVideoEdit = videoPath;
            } else {
              openVideoEditor(videoPath, false, false);
            }
          } else {
            SendMessagesHelper.prepareSendingVideo(
                videoPath, 0, 0, 0, 0, null, dialog_id, replyingMessageObject);
            showReplyPanel(false, null, null, null, false, true);
          }
        } else {
          SendMessagesHelper.prepareSendingPhoto(null, uri, dialog_id, replyingMessageObject, null);
        }
        showReplyPanel(false, null, null, null, false, true);
      } else if (requestCode == 2) {
        String videoPath = null;
        if (data != null) {
          Uri uri = data.getData();
          if (uri != null) {
            videoPath = uri.getPath();
          } else {
            videoPath = currentPicturePath;
          }
          AndroidUtilities.addMediaToGallery(currentPicturePath);
          currentPicturePath = null;
        }
        if (videoPath == null && currentPicturePath != null) {
          File f = new File(currentPicturePath);
          if (f.exists()) {
            videoPath = currentPicturePath;
          }
          currentPicturePath = null;
        }
        if (Build.VERSION.SDK_INT >= 16) {
          if (paused) {
            startVideoEdit = videoPath;
          } else {
            openVideoEditor(videoPath, false, false);
          }
        } else {
          SendMessagesHelper.prepareSendingVideo(
              videoPath, 0, 0, 0, 0, null, dialog_id, replyingMessageObject);
          showReplyPanel(false, null, null, null, false, true);
        }
      } else if (requestCode == 21) {
        if (data == null || data.getData() == null) {
          showAttachmentError();
          return;
        }
        Uri uri = data.getData();

        String extractUriFrom = uri.toString();
        if (extractUriFrom.contains("com.google.android.apps.photos.contentprovider")) {
          try {
            String firstExtraction = extractUriFrom.split("/1/")[1];
            if (firstExtraction.contains("/ACTUAL")) {
              firstExtraction = firstExtraction.replace("/ACTUAL", "");
              String secondExtraction = URLDecoder.decode(firstExtraction, "UTF-8");
              uri = Uri.parse(secondExtraction);
            }
          } catch (Exception e) {
            FileLog.e("tmessages", e);
          }
        }
        String tempPath = AndroidUtilities.getPath(uri);
        String originalPath = tempPath;
        if (tempPath == null) {
          originalPath = data.toString();
          tempPath = MediaController.copyDocumentToCache(data.getData(), "file");
        }
        if (tempPath == null) {
          showAttachmentError();
          return;
        }
        SendMessagesHelper.prepareSendingDocument(
            tempPath, originalPath, null, null, dialog_id, replyingMessageObject);
        showReplyPanel(false, null, null, null, false, true);
      } else if (requestCode == 31) {
        if (data == null || data.getData() == null) {
          showAttachmentError();
          return;
        }
        Uri uri = data.getData();
        Cursor c = null;
        try {
          c =
              getParentActivity()
                  .getContentResolver()
                  .query(
                      uri,
                      new String[] {
                        ContactsContract.Data.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                      },
                      null,
                      null,
                      null);
          if (c != null) {
            boolean sent = false;
            while (c.moveToNext()) {
              sent = true;
              String name = c.getString(0);
              String number = c.getString(1);
              TLRPC.User user = new TLRPC.User();
              user.first_name = name;
              user.last_name = "";
              user.phone = number;
              SendMessagesHelper.getInstance().sendMessage(user, dialog_id, replyingMessageObject);
            }
            if (sent) {
              showReplyPanel(false, null, null, null, false, true);
            }
          }
        } finally {
          try {
            if (c != null && !c.isClosed()) {
              c.close();
            }
          } catch (Exception e) {
            FileLog.e("tmessages", e);
          }
        }
      }
    }
  }

  @Override
  public void saveSelfArgs(Bundle args) {
    if (currentPicturePath != null) {
      args.putString("path", currentPicturePath);
    }
  }

  @Override
  public void restoreSelfArgs(Bundle args) {
    currentPicturePath = args.getString("path");
  }

  private void removeUnreadPlane() {
    if (unreadMessageObject != null) {
      forwardEndReached = true;
      first_unread_id = 0;
      last_message_id = 0;
      unread_to_load = 0;
      if (chatAdapter != null) {
        chatAdapter.removeMessageObject(unreadMessageObject);
      } else {
        messages.remove(unreadMessageObject);
      }
      unreadMessageObject = null;
    }
  }

  private void processMessagesLoaded(final Object... args) {
    long did = (Long) args[0];
    if (did == dialog_id) {
      loadsCount++;
      int count = (Integer) args[1];
      boolean isCache = (Boolean) args[3];
      int fnid = (Integer) args[4];
      int last_unread_date = (Integer) args[8];
      int load_type = (Integer) args[9];
      boolean wasUnread = false;
      if (fnid != 0) {
        first_unread_id = fnid;
        last_message_id = (Integer) args[5];
        unread_to_load = (Integer) args[7];
      } else if (startLoadFromMessageId != 0 && load_type == 3) {
        last_message_id = (Integer) args[5];
        first_message_id = (Integer) args[6];
      }
      ArrayList<MessageObject> messArr = (ArrayList<MessageObject>) args[2];

      int newRowsCount = 0;

      forwardEndReached = startLoadFromMessageId == 0 && last_message_id == 0;

      if (loadsCount == 1 && messArr.size() > 20) {
        loadsCount++;
      }

      if (firstLoading) {
        if (!forwardEndReached) {
          messages.clear();
          messagesByDays.clear();
          messagesDict.clear();
          if (currentEncryptedChat == null) {
            maxMessageId = Integer.MAX_VALUE;
            minMessageId = Integer.MIN_VALUE;
          } else {
            maxMessageId = Integer.MIN_VALUE;
            minMessageId = Integer.MAX_VALUE;
          }
          maxDate = Integer.MIN_VALUE;
          minDate = 0;
        }
        firstLoading = false;
      }

      if (load_type == 1) {
        Collections.reverse(messArr);
      }
      ReplyMessageQuery.loadReplyMessagesForMessages(messArr, dialog_id);

      for (int a = 0; a < messArr.size(); a++) {
        MessageObject obj = messArr.get(a);
        if (messagesDict.containsKey(obj.getId())) {
          continue;
        }

        if (obj.getId() > 0) {
          maxMessageId = Math.min(obj.getId(), maxMessageId);
          minMessageId = Math.max(obj.getId(), minMessageId);
        } else if (currentEncryptedChat != null) {
          maxMessageId = Math.max(obj.getId(), maxMessageId);
          minMessageId = Math.min(obj.getId(), minMessageId);
        }
        if (obj.messageOwner.date != 0) {
          maxDate = Math.max(maxDate, obj.messageOwner.date);
          if (minDate == 0 || obj.messageOwner.date < minDate) {
            minDate = obj.messageOwner.date;
          }
        }

        if (obj.type.getType() < 0) {
          continue;
        }

        if (!obj.isOut() && obj.isUnread()) {
          wasUnread = true;
        }
        messagesDict.put(obj.getId(), obj);
        ArrayList<MessageObject> dayArray = messagesByDays.get(obj.dateKey);

        if (dayArray == null) {
          dayArray = new ArrayList<>();
          messagesByDays.put(obj.dateKey, dayArray);

          TLRPC.Message dateMsg = new TLRPC.Message();
          dateMsg.message = LocaleController.formatDateChat(obj.messageOwner.date);
          dateMsg.id = 0;
          MessageObject dateObj = new MessageObject(dateMsg, null, false);
          dateObj.type = MessageObject.Type.CHAT_ACTION_PHOTO;
          dateObj.contentType = 4;
          if (load_type == 1) {
            messages.add(0, dateObj);
          } else {
            messages.add(dateObj);
          }
          newRowsCount++;
        }

        newRowsCount++;
        dayArray.add(obj);
        if (load_type == 1) {
          messages.add(0, obj);
        } else {
          messages.add(messages.size() - 1, obj);
        }

        if (load_type == 2 && obj.getId() == first_unread_id) {
          TLRPC.Message dateMsg = new TLRPC.Message();
          dateMsg.message = "";
          dateMsg.id = 0;
          MessageObject dateObj = new MessageObject(dateMsg, null, false);
          dateObj.type = MessageObject.Type.DATE;
          dateObj.contentType = dateObj.type.getType();
          boolean dateAdded = true;
          if (a != messArr.size() - 1) {
            MessageObject next = messArr.get(a + 1);
            dateAdded = !next.dateKey.equals(obj.dateKey);
          }
          messages.add(messages.size() - (dateAdded ? 0 : 1), dateObj);
          unreadMessageObject = dateObj;
          scrollToMessage = unreadMessageObject;
          scrollToMessageMiddleScreen = false;
          newRowsCount++;
        } else if (load_type == 3 && obj.getId() == startLoadFromMessageId) {
          if (needSelectFromMessageId) {
            highlightMessageId = obj.getId();
          } else {
            highlightMessageId = Integer.MAX_VALUE;
          }
          scrollToMessage = obj;
          if (isCache) {
            startLoadFromMessageId = 0;
          }
          scrollToMessageMiddleScreen = true;
        } else if (load_type == 1
            && startLoadFromMessageId != 0
            && first_message_id != 0
            && obj.getId() >= first_message_id) {
          startLoadFromMessageId = 0;
        }

        if (obj.getId() == last_message_id) {
          forwardEndReached = true;
        }
      }

      if (forwardEndReached) {
        first_unread_id = 0;
        first_message_id = 0;
        last_message_id = 0;
      }

      if (load_type == 1) {
        if (messArr.size() != count) {
          forwardEndReached = true;
          first_unread_id = 0;
          last_message_id = 0;
          first_message_id = 0;
          startLoadFromMessageId = 0;
          chatAdapter.notifyItemRemoved(chatAdapter.getItemCount() - 1);
          newRowsCount--;
        }
        if (newRowsCount != 0) {
          int firstVisPos = chatLayoutManager.findLastVisibleItemPosition();
          if (firstVisPos == RecyclerView.NO_POSITION) {
            firstVisPos = 0;
          }
          View firstVisView = chatListView.getChildAt(chatListView.getChildCount() - 1);
          int top =
              ((firstVisView == null) ? 0 : firstVisView.getTop()) - chatListView.getPaddingTop();
          chatAdapter.notifyItemRangeInserted(chatAdapter.getItemCount() - 1, newRowsCount);
          chatLayoutManager.scrollToPositionWithOffset(firstVisPos, top);
        }
        loadingForward = false;
      } else {
        if (messArr.size() != count) {
          if (isCache) {
            cacheEndReaced = true;
            if (currentEncryptedChat != null || isBroadcast) {
              endReached = true;
            }
          } else {
            cacheEndReaced = true;
            endReached = true;
          }
        }
        loading = false;

        if (chatListView != null) {
          if (first || scrollToTopOnResume) {
            chatAdapter.notifyDataSetChanged();
            if (scrollToMessage != null) {
              final int yOffset =
                  scrollToMessageMiddleScreen
                      ? Math.max(
                      0,
                      (chatListView.getHeight() - scrollToMessage.getApproximateHeight()) / 2)
                      : 0;
              if (!messages.isEmpty()) {
                if (messages.get(messages.size() - 1) == scrollToMessage) {
                  chatLayoutManager.scrollToPositionWithOffset(
                      0, AndroidUtilities.dp(-11) + yOffset);
                } else {
                  chatLayoutManager.scrollToPositionWithOffset(
                      messages.size() - messages.indexOf(scrollToMessage),
                      AndroidUtilities.dp(-11) + yOffset);
                }
              }
              chatListView.invalidate();
              showPagedownButton(true, true);
            } else {
              moveScrollToLastMessage();
            }
          } else {
            if (endReached) {
              chatAdapter.notifyItemRemoved(chatAdapter.isBot ? 1 : 0);
            }
            if (newRowsCount != 0) {
              int firstVisPos = chatLayoutManager.findLastVisibleItemPosition();
              if (firstVisPos == RecyclerView.NO_POSITION) {
                firstVisPos = 0;
              }
              View firstVisView = chatListView.getChildAt(chatListView.getChildCount() - 1);
              int top =
                  ((firstVisView == null) ? 0 : firstVisView.getTop())
                      - chatListView.getPaddingTop();
              chatAdapter.notifyItemRangeInserted(chatAdapter.isBot ? 2 : 1, newRowsCount);
              chatLayoutManager.scrollToPositionWithOffset(
                  firstVisPos + newRowsCount - (endReached ? 1 : 0), top);
            }
          }

          if (paused) {
            scrollToTopOnResume = true;
            if (scrollToMessage != null) {
              scrollToTopUnReadOnResume = true;
            }
          }

          if (first) {
            chatListView.setEmptyView(emptyViewContainer);
          }
        } else {
          scrollToTopOnResume = true;
          if (scrollToMessage != null) {
            scrollToTopUnReadOnResume = true;
          }
        }
      }

      if (first && messages.size() > 0) {
        final boolean wasUnreadFinal = wasUnread;
        final int last_unread_date_final = last_unread_date;
        final int lastid = messages.get(0).getId();
        AndroidUtilities.runOnUIThread(
            new Runnable() {
              @Override
              public void run() {
                if (last_message_id != 0) {
                  MessagesController.getInstance()
                      .markDialogAsRead(
                          dialog_id,
                          lastid,
                          last_message_id,
                          0,
                          last_unread_date_final,
                          wasUnreadFinal,
                          false);
                } else {
                  MessagesController.getInstance()
                      .markDialogAsRead(
                          dialog_id, lastid, minMessageId, 0, maxDate, wasUnreadFinal, false);
                }
              }
            },
            700);
        first = false;
      }
      if (messages.isEmpty()
          && currentEncryptedChat == null
          && currentUser != null
          && (currentUser.flags & TLRPC.USER_FLAG_BOT) != 0
          && botUser == null) {
        botUser = "";
        updateBottomOverlay();
      }

      if (progressView != null) {
        progressView.setVisibility(View.INVISIBLE);
      }
    }
  }

  private void processUpdateInterfaces(final Object... args) {
    int updateMask = (Integer) args[0];
    if ((updateMask & MessagesController.UPDATE_MASK_NAME) != 0
        || (updateMask & MessagesController.UPDATE_MASK_CHAT_NAME) != 0) {
      updateTitle();
    }
    boolean updateSubtitle = false;
    if ((updateMask & MessagesController.UPDATE_MASK_CHAT_MEMBERS) != 0
        || (updateMask & MessagesController.UPDATE_MASK_STATUS) != 0) {
      if (currentChat != null) {
        int lastCount = onlineCount;
        if (lastCount != updateOnlineCount()) {
          updateSubtitle = true;
        }
      } else {
        updateSubtitle = true;
      }
    }
    if ((updateMask & MessagesController.UPDATE_MASK_AVATAR) != 0
        || (updateMask & MessagesController.UPDATE_MASK_CHAT_AVATAR) != 0
        || (updateMask & MessagesController.UPDATE_MASK_NAME) != 0) {
      checkAndUpdateAvatar();
      updateVisibleRows();
    }
    if ((updateMask & MessagesController.UPDATE_MASK_USER_PRINT) != 0) {
      CharSequence printString = MessagesController.getInstance().printingStrings.get(dialog_id);
      if (lastPrintString != null && printString == null
          || lastPrintString == null && printString != null
          || lastPrintString != null
          && printString != null
          && !lastPrintString.equals(printString)) {
        updateSubtitle = true;
      }
    }
    if (updateSubtitle) {
      updateSubtitle();
    }
    if ((updateMask & MessagesController.UPDATE_MASK_USER_PHONE) != 0) {
      updateContactStatus();
    }
  }

  private void processNewMessagesReceived(final Object... args) {
    long did = (Long) args[0];
    if (did == dialog_id) {

      boolean updateChat = false;
      boolean hasFromMe = false;
      ArrayList<MessageObject> arr = (ArrayList<MessageObject>) args[1];

      if (currentEncryptedChat != null && arr.size() == 1) {
        MessageObject obj = arr.get(0);

        if (currentEncryptedChat != null
            && obj.isOut()
            && obj.messageOwner.action != null
            && obj.messageOwner.action instanceof TLRPC.TL_messageEncryptedAction
            && obj.messageOwner.action.encryptedAction
            instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL
            && getParentActivity() != null) {
          if (AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) < 17
              && currentEncryptedChat.ttl > 0
              && currentEncryptedChat.ttl <= 60) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
            builder.setMessage(
                LocaleController.formatString(
                    "CompatibilityChat",
                    R.string.CompatibilityChat,
                    currentUser.first_name,
                    currentUser.first_name));
            showDialog(builder.create());
          }
        }
      }

      ReplyMessageQuery.loadReplyMessagesForMessages(arr, dialog_id);
      if (!forwardEndReached) {
        int currentMaxDate = Integer.MIN_VALUE;
        int currentMinMsgId = Integer.MIN_VALUE;
        if (currentEncryptedChat != null) {
          currentMinMsgId = Integer.MAX_VALUE;
        }
        boolean currentMarkAsRead = false;

        for (MessageObject obj : arr) {
          if (currentEncryptedChat != null
              && obj.messageOwner.action != null
              && obj.messageOwner.action instanceof TLRPC.TL_messageEncryptedAction
              && obj.messageOwner.action.encryptedAction
              instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL
              && timerDrawable != null) {
            TLRPC.TL_decryptedMessageActionSetMessageTTL action =
                (TLRPC.TL_decryptedMessageActionSetMessageTTL)
                    obj.messageOwner.action.encryptedAction;
            timerDrawable.setTime(action.ttl_seconds);
          }
          if (obj.isOut() && obj.isSending()) {
            scrollToLastMessage();
            return;
          }
          if (messagesDict.containsKey(obj.getId())) {
            continue;
          }
          currentMaxDate = Math.max(currentMaxDate, obj.messageOwner.date);
          if (obj.getId() > 0) {
            currentMinMsgId = Math.max(obj.getId(), currentMinMsgId);
            last_message_id = Math.max(last_message_id, obj.getId());
          } else if (currentEncryptedChat != null) {
            currentMinMsgId = Math.min(obj.getId(), currentMinMsgId);
            last_message_id = Math.min(last_message_id, obj.getId());
          }

          if (!obj.isOut() && obj.isUnread()) {
            unread_to_load++;
            currentMarkAsRead = true;
          }
          if (obj.type == MessageObject.Type.CHAT_ACTION_PHOTO ||
              obj.type == MessageObject.Type.MSG_ACTION) {
            updateChat = true;
          }
        }

        if (currentMarkAsRead) {
          if (paused) {
            readWhenResume = true;
            readWithDate = currentMaxDate;
            readWithMid = currentMinMsgId;
          } else {
            if (messages.size() > 0) {
              MessagesController.getInstance()
                  .markDialogAsRead(
                      dialog_id,
                      messages.get(0).getId(),
                      currentMinMsgId,
                      0,
                      currentMaxDate,
                      true,
                      false);
            }
          }
        }
        updateVisibleRows();
      } else {
        boolean markAsRead = false;
        boolean unreadUpdated = true;
        int oldCount = messages.size();
        int addedCount = 0;
        for (MessageObject obj : arr) {
          if (currentEncryptedChat != null
              && obj.messageOwner.action != null
              && obj.messageOwner.action instanceof TLRPC.TL_messageEncryptedAction
              && obj.messageOwner.action.encryptedAction
              instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL
              && timerDrawable != null) {
            TLRPC.TL_decryptedMessageActionSetMessageTTL action =
                (TLRPC.TL_decryptedMessageActionSetMessageTTL)
                    obj.messageOwner.action.encryptedAction;
            timerDrawable.setTime(action.ttl_seconds);
          }
          if (messagesDict.containsKey(obj.getId())) {
            continue;
          }
          if (minDate == 0 || obj.messageOwner.date < minDate) {
            minDate = obj.messageOwner.date;
          }

          if (obj.isOut()) {
            removeUnreadPlane();
            hasFromMe = true;
          }

          if (obj.getId() > 0) {
            maxMessageId = Math.min(obj.getId(), maxMessageId);
            minMessageId = Math.max(obj.getId(), minMessageId);
          } else if (currentEncryptedChat != null) {
            maxMessageId = Math.max(obj.getId(), maxMessageId);
            minMessageId = Math.min(obj.getId(), minMessageId);
          }
          maxDate = Math.max(maxDate, obj.messageOwner.date);
          messagesDict.put(obj.getId(), obj);
          ArrayList<MessageObject> dayArray = messagesByDays.get(obj.dateKey);
          if (dayArray == null) {
            dayArray = new ArrayList<>();
            messagesByDays.put(obj.dateKey, dayArray);

            TLRPC.Message dateMsg = new TLRPC.Message();
            dateMsg.message = LocaleController.formatDateChat(obj.messageOwner.date);
            dateMsg.id = 0;
            MessageObject dateObj = new MessageObject(dateMsg, null, false);
            dateObj.type = MessageObject.Type.CHAT_ACTION_PHOTO;
            dateObj.contentType = 4;
            messages.add(0, dateObj);
            addedCount++;
          }
          if (!obj.isOut()) {
            if (paused) {
              if (!scrollToTopUnReadOnResume && unreadMessageObject != null) {
                if (chatAdapter != null) {
                  chatAdapter.removeMessageObject(unreadMessageObject);
                } else {
                  messages.remove(unreadMessageObject);
                }
                unreadMessageObject = null;
              }
              if (unreadMessageObject == null) {
                TLRPC.Message dateMsg = new TLRPC.Message();
                dateMsg.message = "";
                dateMsg.id = 0;
                MessageObject dateObj = new MessageObject(dateMsg, null, false);
                dateObj.type = MessageObject.Type.DATE;
                dateObj.contentType = dateObj.type.getType();
                messages.add(0, dateObj);
                unreadMessageObject = dateObj;
                scrollToMessage = unreadMessageObject;
                scrollToMessageMiddleScreen = false;
                unreadUpdated = false;
                unread_to_load = 0;
                scrollToTopUnReadOnResume = true;
                addedCount++;
              }
            }
            if (unreadMessageObject != null) {
              unread_to_load++;
              unreadUpdated = true;
            }
            if (obj.isUnread()) {
              if (!paused) {
                obj.setIsRead();
              }
              markAsRead = true;
            }
          }

          dayArray.add(0, obj);
          messages.add(0, obj);
          addedCount++;
          if (obj.type == MessageObject.Type.CHAT_ACTION_PHOTO ||
              obj.type == MessageObject.Type.MSG_ACTION) {
            updateChat = true;
          }
        }

        if (progressView != null) {
          progressView.setVisibility(View.INVISIBLE);
        }
        if (chatAdapter != null) {
          if (unreadUpdated) {
            chatAdapter.updateRowWithMessageObject(unreadMessageObject);
          }
          if (addedCount != 0) {
            chatAdapter.notifyItemRangeInserted(chatAdapter.getItemCount(), addedCount);
          }
        } else {
          scrollToTopOnResume = true;
        }

        if (chatListView != null && chatAdapter != null) {
          int lastVisible = chatLayoutManager.findLastVisibleItemPosition();
          if (lastVisible == RecyclerView.NO_POSITION) {
            lastVisible = 0;
          }
          if (endReached) {
            lastVisible++;
          }
          if (chatAdapter.isBot) {
            oldCount++;
          }
          if (lastVisible == oldCount || hasFromMe) {
            if (!firstLoading) {
              if (paused) {
                scrollToTopOnResume = true;
              } else {
                moveScrollToLastMessage();
              }
            }
          } else {
            showPagedownButton(true, true);
          }
        } else {
          scrollToTopOnResume = true;
        }

        if (markAsRead) {
          if (paused) {
            readWhenResume = true;
            readWithDate = maxDate;
            readWithMid = minMessageId;
          } else {
            MessagesController.getInstance()
                .markDialogAsRead(
                    dialog_id, messages.get(0).getId(), minMessageId, 0, maxDate, true, false);
          }
        }
      }
      if (!messages.isEmpty() && botUser != null && botUser.length() == 0) {
        botUser = null;
        updateBottomOverlay();
      }
      if (updateChat) {
        updateTitle();
        checkAndUpdateAvatar();
      }
    }
  }

  private void processMessagesRead(final Object... args) {
    HashMap<Integer, Integer> inbox = (HashMap<Integer, Integer>) args[0];
    HashMap<Integer, Integer> outbox = (HashMap<Integer, Integer>) args[1];
    boolean updated = false;
    for (HashMap.Entry<Integer, Integer> entry : inbox.entrySet()) {
      if (entry.getKey() != dialog_id) {
        continue;
      }
      for (int a = 0; a < messages.size(); a++) {
        MessageObject obj = messages.get(a);
        if (!obj.isOut() && obj.getId() > 0 && obj.getId() <= entry.getValue()) {
          if (!obj.isUnread()) {
            break;
          }
          obj.setIsRead();
          updated = true;
        }
      }
      break;
    }
    for (HashMap.Entry<Integer, Integer> entry : outbox.entrySet()) {
      if (entry.getKey() != dialog_id) {
        continue;
      }
      for (int a = 0; a < messages.size(); a++) {
        MessageObject obj = messages.get(a);
        if (obj.isOut() && obj.getId() > 0 && obj.getId() <= entry.getValue()) {
          if (!obj.isUnread()) {
            break;
          }
          obj.setIsRead();
          updated = true;
        }
      }
      break;
    }
    if (updated) {
      updateVisibleRows();
    }
  }

  private void processMessagesDeleted(final Object... args) {
    ArrayList<Integer> markAsDeletedMessages = (ArrayList<Integer>) args[0];
    boolean updated = false;
    for (Integer ids : markAsDeletedMessages) {
      MessageObject obj = messagesDict.get(ids);
      if (obj != null) {
        int index = messages.indexOf(obj);
        if (index != -1) {
          messages.remove(index);
          messagesDict.remove(ids);
          ArrayList<MessageObject> dayArr = messagesByDays.get(obj.dateKey);
          dayArr.remove(obj);
          if (dayArr.isEmpty()) {
            messagesByDays.remove(obj.dateKey);
            if (index >= 0 && index < messages.size()) { //TODO fix it
              messages.remove(index);
            }
          }
          updated = true;
        }
      }
    }
    if (messages.isEmpty()) {
      if (!endReached && !loading) {
        if (progressView != null) {
          progressView.setVisibility(View.INVISIBLE);
        }
        if (chatListView != null) {
          chatListView.setEmptyView(null);
        }
        if (currentEncryptedChat == null) {
          maxMessageId = Integer.MAX_VALUE;
          minMessageId = Integer.MIN_VALUE;
        } else {
          maxMessageId = Integer.MIN_VALUE;
          minMessageId = Integer.MAX_VALUE;
        }
        maxDate = Integer.MIN_VALUE;
        minDate = 0;
        MessagesController.getInstance()
            .loadMessages(dialog_id, 30, 0, !cacheEndReaced, minDate, classGuid, 0, 0, 0, true);
        loading = true;
      } else {
        if (botButtons != null) {
          botButtons = null;
          if (chatActivityEnterView != null) {
            chatActivityEnterView.setButtons(null, false);
          }
        }
        if (currentEncryptedChat == null
            && currentUser != null
            && (currentUser.flags & TLRPC.USER_FLAG_BOT) != 0
            && botUser == null) {
          botUser = "";
          updateBottomOverlay();
        }
      }
    }
    if (updated && chatAdapter != null) {
      removeUnreadPlane();
      chatAdapter.notifyDataSetChanged();
    }
  }

  private void processMessagesReceivedByServer(final Object... args) {
    Integer msgId = (Integer) args[0];
    MessageObject obj = messagesDict.get(msgId);
    if (obj != null) {
      Integer newMsgId = (Integer) args[1];
      TLRPC.Message newMsgObj = (TLRPC.Message) args[2];
      boolean mediaUpdated = (Boolean) args[3];
      if (newMsgObj != null) {
        obj.messageOwner.media = newMsgObj.media;
        obj.generateThumbs(true);
      }
      int oldCount = messagesDict.size();
      MessageObject removed = messagesDict.remove(msgId);
      messagesDict.put(newMsgId, obj);
      obj.messageOwner.id = newMsgId;
      obj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
      ArrayList<MessageObject> messArr = new ArrayList<>();
      messArr.add(obj);
      ReplyMessageQuery.loadReplyMessagesForMessages(messArr, dialog_id);
      updateVisibleRows();
      if (oldCount != messagesDict.size()) {
        int index = messages.indexOf(removed);
        messages.remove(index);
        ArrayList<MessageObject> dayArr = messagesByDays.get(removed.dateKey);
        dayArr.remove(obj);
        if (dayArr.isEmpty()) {
          messagesByDays.remove(obj.dateKey);
          if (index >= 0 && index < messages.size()) {
            messages.remove(index);
          }
        }
        chatAdapter.notifyDataSetChanged();
      }
      if (mediaUpdated
          && chatLayoutManager.findLastVisibleItemPosition() >= messages.size() - 1) {
        moveScrollToLastMessage();
      }
      NotificationsController.getInstance().playOutChatSound();
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public void didReceivedNotification(int id, final Object... args) {
    if (id == NotificationCenter.messagesDidLoaded) {
      processMessagesLoaded(args);
    } else if (id == NotificationCenter.FileDidLoaded) {
      String location = (String) args[0];
      Log.e(TAG, "Avatar loaded at " + location);
      if (currentUser != null && abelianAvatarPath != null &&
          abelianAvatarPath.endsWith(location)) {
        if (AxolotlController.getInstance().loadAbelianIdentity(currentUser)) {
          loadingAbelianIdentity = false;
        }
      }
    } else if (id == NotificationCenter.ABELIAN_IDENTITY_LOADED) {
      AxolotlAddress address = (AxolotlAddress) args[0];
      Log.e(TAG, "Identity for address " + address + " loaded");
      loadingAbelianIdentity = false;
    } else if (id == NotificationCenter.emojiDidLoaded) {
      if (chatListView != null) {
        chatListView.invalidateViews();
      }
      if (replyObjectTextView != null) {
        replyObjectTextView.invalidate();
      }
    } else if (id == NotificationCenter.updateInterfaces) {
      processUpdateInterfaces(args);
    } else if (id == NotificationCenter.didReceivedNewMessages) {
      processNewMessagesReceived(args);
    } else if (id == NotificationCenter.closeChats) {
      if (args != null && args.length > 0) {
        long did = (Long) args[0];
        if (did == dialog_id) {
          finishFragment();
        }
      } else {
        removeSelfFromStack();
      }
    } else if (id == NotificationCenter.messagesRead) {
      processMessagesRead(args);

    } else if (id == NotificationCenter.messagesDeleted) {
      processMessagesDeleted(args);
    } else if (id == NotificationCenter.messageReceivedByServer) {
      processMessagesReceivedByServer(args);
    } else if (id == NotificationCenter.messageReceivedByAck) {
      Integer msgId = (Integer) args[0];
      MessageObject obj = messagesDict.get(msgId);
      if (obj != null) {
        obj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
        updateVisibleRows();
      }
    } else if (id == NotificationCenter.messageSendError) {
      Integer msgId = (Integer) args[0];
      MessageObject obj = messagesDict.get(msgId);
      if (obj != null) {
        obj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
        updateVisibleRows();
      }
    } else if (id == NotificationCenter.chatInfoDidLoaded) {
      int chatId = (Integer) args[0];
      if (currentChat != null && chatId == currentChat.id) {
        info = (TLRPC.ChatParticipants) args[1];
        if (mentionsAdapter != null) {
          mentionsAdapter.setChatInfo(info);
        }
        updateOnlineCount();
        updateSubtitle();
        if (isBroadcast) {
          SendMessagesHelper.getInstance().setCurrentChatInfo(info);
        }
        if (info != null) {
          hasBotsCommands = false;
          botInfo.clear();
          botsCount = 0;
          for (int a = 0; a < info.participants.size(); a++) {
            TLRPC.TL_chatParticipant participant = info.participants.get(a);
            TLRPC.User user = MessagesController.getInstance().getUser(participant.user_id);
            if (user != null && (user.flags & TLRPC.USER_FLAG_BOT) != 0) {
              botsCount++;
              BotQuery.loadBotInfo(user.id, true, classGuid);
            }
          }
        }
        if (chatActivityEnterView != null) {
          chatActivityEnterView.setBotsCount(botsCount, hasBotsCommands);
        }
        if (mentionsAdapter != null) {
          mentionsAdapter.setBotsCount(botsCount);
        }
      }
    } else if (id == NotificationCenter.contactsDidLoaded) {
      updateContactStatus();
      updateSubtitle();
    } else if (id == NotificationCenter.encryptedChatUpdated) {
      TLRPC.EncryptedChat chat = (TLRPC.EncryptedChat) args[0];
      if (currentEncryptedChat != null && chat.id == currentEncryptedChat.id) {
        currentEncryptedChat = chat;
        updateContactStatus();
        updateSecretStatus();
      }
    } else if (id == NotificationCenter.messagesReadEncrypted) {
      int encId = (Integer) args[0];
      if (currentEncryptedChat != null && currentEncryptedChat.id == encId) {
        int date = (Integer) args[1];
        for (MessageObject obj : messages) {
          if (!obj.isOut()) {
            continue;
          } else if (obj.isOut() && !obj.isUnread()) {
            break;
          }
          if (obj.messageOwner.date - 1 <= date) {
            obj.setIsRead();
          }
        }
        updateVisibleRows();
      }
    } else if (id == NotificationCenter.audioDidReset
        || id == NotificationCenter.audioPlayStateChanged) {
      Integer mid = (Integer) args[0];
      if (chatListView != null) {
        int count = chatListView.getChildCount();
        for (int a = 0; a < count; a++) {
          View view = chatListView.getChildAt(a);
          if (view instanceof ChatAudioCell) {
            ChatAudioCell cell = (ChatAudioCell) view;
            if (cell.getMessageObject() != null && cell.getMessageObject().getId() == mid) {
              cell.updateButtonState(false);
              break;
            }
          } else if (view instanceof ChatMusicCell) {
            ChatMusicCell cell = (ChatMusicCell) view;
            if (cell.getMessageObject() != null && cell.getMessageObject().getId() == mid) {
              cell.updateButtonState(false);
              break;
            }
          }
        }
      }
    } else if (id == NotificationCenter.audioProgressDidChanged) {
      Integer mid = (Integer) args[0];
      if (chatListView != null) {
        int count = chatListView.getChildCount();
        for (int a = 0; a < count; a++) {
          View view = chatListView.getChildAt(a);
          if (view instanceof ChatAudioCell) {
            ChatAudioCell cell = (ChatAudioCell) view;
            if (cell.getMessageObject() != null && cell.getMessageObject().getId() == mid) {
              cell.updateProgress();
              break;
            }
          } else if (view instanceof ChatMusicCell) {
            ChatMusicCell cell = (ChatMusicCell) view;
            if (cell.getMessageObject() != null && cell.getMessageObject().getId() == mid) {
              MessageObject playing = cell.getMessageObject();
              MessageObject player = MediaController.getInstance().getPlayingMessageObject();
              playing.audioProgress = player.audioProgress;
              playing.audioProgressSec = player.audioProgressSec;
              cell.updateProgress();
              break;
            }
          }
        }
      }
    } else if (id == NotificationCenter.removeAllMessagesFromDialog) {
      long did = (Long) args[0];
      if (dialog_id == did) {
        messages.clear();
        messagesByDays.clear();
        messagesDict.clear();
        progressView.setVisibility(View.INVISIBLE);
        chatListView.setEmptyView(emptyViewContainer);
        if (currentEncryptedChat == null) {
          maxMessageId = Integer.MAX_VALUE;
          minMessageId = Integer.MIN_VALUE;
        } else {
          maxMessageId = Integer.MIN_VALUE;
          minMessageId = Integer.MAX_VALUE;
        }
        maxDate = Integer.MIN_VALUE;
        minDate = 0;
        selectedMessagesIds.clear();
        selectedMessagesCanCopyIds.clear();
        actionBar.hideActionMode();
        chatAdapter.notifyDataSetChanged();

        if (messages.isEmpty()) {
          if (botButtons != null) {
            botButtons = null;
            chatActivityEnterView.setButtons(null, false);
          }
          if (currentEncryptedChat == null
              && currentUser != null
              && (currentUser.flags & TLRPC.USER_FLAG_BOT) != 0
              && botUser == null) {
            botUser = "";
            updateBottomOverlay();
          }
        }
      }
    } else if (id == NotificationCenter.screenshotTook) {
      updateInformationForScreenshotDetector();
    } else if (id == NotificationCenter.blockedUsersDidLoaded) {
      if (currentUser != null) {
        boolean oldValue = userBlocked;
        userBlocked = MessagesController.getInstance().blockedUsers.contains(currentUser.id);
        if (oldValue != userBlocked) {
          updateBottomOverlay();
        }
      }
    } else if (id == NotificationCenter.FileNewChunkAvailable) {
      MessageObject messageObject = (MessageObject) args[0];
      long finalSize = (Long) args[2];
      if (finalSize != 0 && dialog_id == messageObject.getDialogId()) {
        MessageObject currentObject = messagesDict.get(messageObject.getId());
        if (currentObject != null) {
          currentObject.messageOwner.media.video.size = (int) finalSize;
          updateVisibleRows();
        }
      }
    } else if (id == NotificationCenter.didCreatedNewDeleteTask) {
      SparseArray<ArrayList<Integer>> mids = (SparseArray<ArrayList<Integer>>) args[0];
      boolean changed = false;
      for (int i = 0; i < mids.size(); i++) {
        int key = mids.keyAt(i);
        ArrayList<Integer> arr = mids.get(key);
        for (Integer mid : arr) {
          MessageObject messageObject = messagesDict.get(mid);
          if (messageObject != null) {
            messageObject.messageOwner.destroyTime = key;
            changed = true;
          }
        }
      }
      if (changed) {
        updateVisibleRows();
      }
    } else if (id == NotificationCenter.audioDidStarted) {
      MessageObject messageObject = (MessageObject) args[0];
      sendSecretMessageRead(messageObject);

      int mid = messageObject.getId();
      if (chatListView != null) {
        int count = chatListView.getChildCount();
        for (int a = 0; a < count; a++) {
          View view = chatListView.getChildAt(a);
          if (view instanceof ChatAudioCell) {
            ChatAudioCell cell = (ChatAudioCell) view;
            if (cell.getMessageObject() != null && cell.getMessageObject().getId() == mid) {
              cell.updateButtonState(false);
              break;
            }
          } else if (view instanceof ChatMusicCell) {
            ChatMusicCell cell = (ChatMusicCell) view;
            if (cell.getMessageObject() != null && cell.getMessageObject().getId() == mid) {
              cell.updateButtonState(false);
              break;
            }
          }
        }
      }
    } else if (id == NotificationCenter.updateMessageMedia) {
      MessageObject messageObject = (MessageObject) args[0];
      MessageObject existMessageObject = messagesDict.get(messageObject.getId());
      if (existMessageObject != null) {
        existMessageObject.messageOwner.media = messageObject.messageOwner.media;
        existMessageObject.messageOwner.attachPath = messageObject.messageOwner.attachPath;
        existMessageObject.generateThumbs(false);
      }
      updateVisibleRows();
    } else if (id == NotificationCenter.replaceMessagesObjects) {
      if (dialog_id == (long) args[0]) {
        boolean changed = false;
        boolean mediaUpdated = false;
        ArrayList<MessageObject> messageObjects = (ArrayList<MessageObject>) args[1];
        for (MessageObject messageObject : messageObjects) {
          MessageObject old = messagesDict.get(messageObject.getId());
          if (old != null) {
            if (!mediaUpdated
                && messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) {
              mediaUpdated = true;
            }
            messagesDict.put(old.getId(), messageObject);
            int index = messages.indexOf(old);
            if (index >= 0) {
              messages.set(index, messageObject);
              chatAdapter.notifyItemChanged(
                  chatAdapter.messagesStartRow + messages.size() - index - 1);
              changed = true;
            }
          }
        }
        if (changed) {
          if (mediaUpdated
              && chatLayoutManager.findLastVisibleItemPosition()
                  >= messages.size() - (chatAdapter.isBot ? 2 : 1)) {
            moveScrollToLastMessage();
          }
        }
      }
    } else if (id == NotificationCenter.notificationsSettingsUpdated) {
      updateTitleIcons();
    } else if (id == NotificationCenter.didLoadedReplyMessages) {
      long did = (Long) args[0];
      if (did == dialog_id) {
        updateVisibleRows();
      }
    } else if (id == NotificationCenter.didReceivedWebpages) {
      ArrayList<TLRPC.Message> arrayList = (ArrayList<TLRPC.Message>) args[0];
      boolean updated = false;
      for (TLRPC.Message message : arrayList) {
        MessageObject currentMessage = messagesDict.get(message.id);
        if (currentMessage != null) {
          currentMessage.messageOwner.media.webpage = message.media.webpage;
          currentMessage.generateThumbs(true);
          updated = true;
        }
      }
      if (updated) {
        updateVisibleRows();
        if (chatLayoutManager.findLastVisibleItemPosition() >= messages.size() - 1) {
          moveScrollToLastMessage();
        }
      }
    } else if (id == NotificationCenter.didReceivedWebpagesInUpdates) {
      if (foundWebPage != null) {
        HashMap<Long, TLRPC.WebPage> hashMap = (HashMap<Long, TLRPC.WebPage>) args[0];
        for (TLRPC.WebPage webPage : hashMap.values()) {
          if (webPage.id == foundWebPage.id) {
            showReplyPanel(
                !(webPage instanceof TLRPC.TL_webPageEmpty), null, null, webPage, false, true);
            break;
          }
        }
      }
    } else if (id == NotificationCenter.messagesReadContent) {
      ArrayList<Integer> arrayList = (ArrayList<Integer>) args[0];
      boolean updated = false;
      for (Integer mid : arrayList) {
        MessageObject currentMessage = messagesDict.get(mid);
        if (currentMessage != null) {
          currentMessage.setContentIsRead();
          updated = true;
        }
      }
      if (updated) {
        updateVisibleRows();
      }
    } else if (id == NotificationCenter.botInfoDidLoaded) {
      int guid = (Integer) args[1];
      if (classGuid == guid) {
        TLRPC.BotInfo info = (TLRPC.BotInfo) args[0];
        if (currentEncryptedChat == null) {
          if (!info.commands.isEmpty()) {
            hasBotsCommands = true;
          }
          botInfo.put(info.user_id, info);
          if (chatAdapter != null) {
            chatAdapter.notifyItemChanged(0);
          }
          if (mentionsAdapter != null) {
            mentionsAdapter.setBotInfo(botInfo);
          }
          if (chatActivityEnterView != null) {
            chatActivityEnterView.setBotsCount(botsCount, hasBotsCommands);
          }
        }
        updateBotButtons();
      }
    } else if (id == NotificationCenter.botKeyboardDidLoaded) {
      if (dialog_id == (Long) args[1]) {
        TLRPC.Message message = (TLRPC.Message) args[0];
        if (message != null) {
          botButtons = new MessageObject(message, null, false);
          if (chatActivityEnterView != null) {
            if (botButtons.messageOwner.reply_markup instanceof TLRPC.TL_replyKeyboardForceReply) {
              SharedPreferences preferences =
                  ApplicationLoader.applicationContext.getSharedPreferences(
                      "mainconfig", Activity.MODE_PRIVATE);
              if (preferences.getInt("answered_" + dialog_id, 0) != botButtons.getId()
                  && (replyingMessageObject == null
                      || chatActivityEnterView.getFieldText() == null)) {
                botReplyButtons = botButtons;
                chatActivityEnterView.setButtons(botButtons);
                showReplyPanel(true, botButtons, null, null, false, true);
              }
            } else {
              if (replyingMessageObject != null && botReplyButtons == replyingMessageObject) {
                botReplyButtons = null;
                showReplyPanel(false, null, null, null, false, true);
              }
              chatActivityEnterView.setButtons(botButtons);
            }
          }
        } else {
          botButtons = null;
          if (chatActivityEnterView != null) {
            if (replyingMessageObject != null && botReplyButtons == replyingMessageObject) {
              botReplyButtons = null;
              showReplyPanel(false, null, null, null, false, true);
            }
            chatActivityEnterView.setButtons(botButtons);
          }
        }
      }
    } else if (id == NotificationCenter.chatSearchResultsAvailable) {
      if (classGuid == (Integer) args[0]) {
        int messageId = (Integer) args[1];
        if (messageId != 0) {
          scrollToMessageId(messageId, 0, true);
        }
        updateSearchButtons((Integer) args[2]);
      }
    }
  }

  private void updateSearchButtons(int mask) {
    if (searchUpItem != null) {
      searchUpItem.setEnabled((mask & 1) != 0);
      searchDownItem.setEnabled((mask & 2) != 0);
      ViewProxy.setAlpha(searchUpItem, searchUpItem.isEnabled() ? 1.0f : 0.6f);
      ViewProxy.setAlpha(searchDownItem, searchDownItem.isEnabled() ? 1.0f : 0.6f);
    }
  }

  @Override
  protected void onOpenAnimationStart() {
    NotificationCenter.getInstance().setAnimationInProgress(true);
    openAnimationEnded = false;
  }

  @Override
  protected void onOpenAnimationEnd() {
    NotificationCenter.getInstance().setAnimationInProgress(false);
    openAnimationEnded = true;
    int count = chatListView.getChildCount();
    for (int a = 0; a < count; a++) {
      View view = chatListView.getChildAt(a);
      if (view instanceof ChatMediaCell) {
        ChatMediaCell cell = (ChatMediaCell) view;
        cell.setAllowedToSetPhoto(true);
      }
    }
  }

  private void updateBottomOverlay() {
    if (bottomOverlayChatText == null) {
      return;
    }
    if (currentUser == null) {
      bottomOverlayChatText.setText(
          LocaleController.getString("DeleteThisGroup", R.string.DeleteThisGroup));
    } else {
      if (userBlocked) {
        bottomOverlayChatText.setText(LocaleController.getString("Unblock", R.string.Unblock));
      } else if (botUser != null) {
        bottomOverlayChatText.setText(LocaleController.getString("BotStart", R.string.BotStart));
        chatActivityEnterView.hidePopup();
        if (getParentActivity() != null) {
          AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
        }
      } else {
        bottomOverlayChatText.setText(
            LocaleController.getString("DeleteThisChat", R.string.DeleteThisChat));
      }
    }
    if (currentChat != null && (currentChat instanceof TLRPC.TL_chatForbidden || currentChat.left)
        || currentUser != null && (UserObject.isDeleted(currentUser) || userBlocked)) {
      bottomOverlayChat.setVisibility(View.VISIBLE);
      muteItem.setVisibility(View.GONE);
      chatActivityEnterView.setFieldFocused(false);
      chatActivityEnterView.setVisibility(View.INVISIBLE);
    } else {
      if (botUser != null) {
        bottomOverlayChat.setVisibility(View.VISIBLE);
        chatActivityEnterView.setVisibility(View.INVISIBLE);
      } else {
        chatActivityEnterView.setVisibility(View.VISIBLE);
        bottomOverlayChat.setVisibility(View.INVISIBLE);
      }
      muteItem.setVisibility(View.VISIBLE);
    }
  }

  private void updateContactStatus() {
    if (addContactItem == null) {
      return;
    }
    if (currentUser == null) {
      addContactItem.setVisibility(View.GONE);
    } else {
      TLRPC.User user = MessagesController.getInstance().getUser(currentUser.id);
      if (user != null) {
        currentUser = user;
      }
      if (currentEncryptedChat != null && !(currentEncryptedChat instanceof TLRPC.TL_encryptedChat)
          || currentUser.id / 1000 == 333
          || currentUser.id / 1000 == 777
          || UserObject.isDeleted(currentUser)
          || ContactsController.getInstance().isLoadingContacts()
          || (currentUser.phone != null
              && currentUser.phone.length() != 0
              && ContactsController.getInstance().contactsDict.get(currentUser.id) != null
              && (ContactsController.getInstance().contactsDict.size() != 0
                  || !ContactsController.getInstance().isLoadingContacts()))) {
        addContactItem.setVisibility(View.GONE);
      } else {
        addContactItem.setVisibility(View.VISIBLE);
        if (currentUser.phone != null && currentUser.phone.length() != 0) {
          addContactItem.setText(
              LocaleController.getString("AddToContacts", R.string.AddToContacts));
        } else {
          addContactItem.setText(
              LocaleController.getString("ShareMyContactInfo", R.string.ShareMyContactInfo));
        }
      }
    }
  }

  @Override
  public void onResume() {
    super.onResume();

    if (!AndroidUtilities.isTablet()) {
      getParentActivity()
          .getWindow()
          .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    checkActionBarMenu();
    if (replyImageLocation != null && replyImageView != null) {
      replyImageView.setImage(replyImageLocation, "50_50", (Drawable) null);
    }

    NotificationsController.getInstance().setOpennedDialogId(dialog_id);
    if (scrollToTopOnResume) {
      if (scrollToTopUnReadOnResume && scrollToMessage != null) {
        if (chatListView != null) {
          final int yOffset =
              scrollToMessageMiddleScreen
                  ? Math.max(
                      0, (chatListView.getHeight() - scrollToMessage.getApproximateHeight()) / 2)
                  : 0;
          chatLayoutManager.scrollToPositionWithOffset(
              messages.size() - messages.indexOf(scrollToMessage),
              -chatListView.getPaddingTop() - AndroidUtilities.dp(7) + yOffset);
        }
      } else {
        moveScrollToLastMessage();
      }
      scrollToTopUnReadOnResume = false;
      scrollToTopOnResume = false;
      scrollToMessage = null;
    }
    paused = false;
    if (readWhenResume && !messages.isEmpty()) {
      for (MessageObject messageObject : messages) {
        if (!messageObject.isUnread() && !messageObject.isOut()) {
          break;
        }
        if (!messageObject.isOut()) {
          messageObject.setIsRead();
        }
      }
      readWhenResume = false;
      MessagesController.getInstance()
          .markDialogAsRead(
              dialog_id, messages.get(0).getId(), readWithMid, 0, readWithDate, true, false);
    }
    if (wasPaused) {
      wasPaused = false;
      if (chatAdapter != null) {
        chatAdapter.notifyDataSetChanged();
      }
    }

    fixLayout(true);
    SharedPreferences preferences =
        ApplicationLoader.applicationContext.getSharedPreferences(
            "mainconfig", Activity.MODE_PRIVATE);
    if (chatActivityEnterView.getFieldText() == null) {
      String lastMessageText = preferences.getString("dialog_" + dialog_id, null);
      if (lastMessageText != null) {
        preferences.edit().remove("dialog_" + dialog_id).commit();
        chatActivityEnterView.setFieldText(lastMessageText);
      }
    } else {
      preferences.edit().remove("dialog_" + dialog_id).commit();
    }
    if (replyingMessageObject == null) {
      String lastReplyMessage = preferences.getString("reply_" + dialog_id, null);
      if (lastReplyMessage != null && lastReplyMessage.length() != 0) {
        preferences.edit().remove("reply_" + dialog_id).commit();
        try {
          byte[] bytes = Base64.decode(lastReplyMessage, Base64.DEFAULT);
          if (bytes != null) {
            SerializedData data = new SerializedData(bytes);
            TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
            if (message != null) {
              replyingMessageObject =
                  new MessageObject(message, MessagesController.getInstance().getUsers(), false);
              showReplyPanel(true, replyingMessageObject, null, null, false, false);
            }
          }
        } catch (Exception e) {
          FileLog.e("tmessages", e);
        }
      }
    } else {
      preferences.edit().remove("reply_" + dialog_id).commit();
    }
    if (bottomOverlayChat.getVisibility() != View.VISIBLE) {
      chatActivityEnterView.setFieldFocused(true);
    }
    chatActivityEnterView.onResume();
    if (currentEncryptedChat != null) {
      chatEnterTime = System.currentTimeMillis();
      chatLeaveTime = 0;
    }

    if (startVideoEdit != null) {
      AndroidUtilities.runOnUIThread(
          new Runnable() {
            @Override
            public void run() {
              openVideoEditor(startVideoEdit, false, false);
              startVideoEdit = null;
            }
          });
    }

    chatListView.setOnItemLongClickListener(onItemLongClickListener);
    chatListView.setOnItemClickListener(onItemClickListener);
    chatListView.setLongClickable(true);
  }

  @Override
  public void onPause() {
    super.onPause();
    if (menuItem != null) {
      menuItem.closeSubMenu();
    }
    paused = true;
    wasPaused = true;
    NotificationsController.getInstance().setOpennedDialogId(0);
    if (chatActivityEnterView != null) {
      chatActivityEnterView.onPause();
      String text = chatActivityEnterView.getFieldText();
      if (text != null) {
        SharedPreferences preferences =
            ApplicationLoader.applicationContext.getSharedPreferences(
                "mainconfig", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("dialog_" + dialog_id, text);
        editor.commit();
      }
      chatActivityEnterView.setFieldFocused(false);
    }
    if (replyingMessageObject != null) {
      SharedPreferences preferences =
          ApplicationLoader.applicationContext.getSharedPreferences(
              "mainconfig", Activity.MODE_PRIVATE);
      SharedPreferences.Editor editor = preferences.edit();
      try {
        SerializedData data = new SerializedData();
        replyingMessageObject.messageOwner.serializeToStream(data);
        String string = Base64.encodeToString(data.toByteArray(), Base64.DEFAULT);
        if (string != null && string.length() != 0) {
          editor.putString("reply_" + dialog_id, string);
        }
      } catch (Exception e) {
        editor.remove("reply_" + dialog_id);
        FileLog.e("tmessages", e);
      }
      editor.commit();
    }

    MessagesController.getInstance().cancelTyping(0, dialog_id);

    if (currentEncryptedChat != null) {
      chatLeaveTime = System.currentTimeMillis();
      updateInformationForScreenshotDetector();
    }
  }

  private void updateInformationForScreenshotDetector() {
    if (currentEncryptedChat == null) {
      return;
    }
    ArrayList<Long> visibleMessages = new ArrayList<>();
    if (chatListView != null) {
      int count = chatListView.getChildCount();
      for (int a = 0; a < count; a++) {
        View view = chatListView.getChildAt(a);
        MessageObject object = null;
        if (view instanceof ChatBaseCell) {
          ChatBaseCell cell = (ChatBaseCell) view;
          object = cell.getMessageObject();
        }
        if (object != null && object.getId() < 0 && object.messageOwner.random_id != 0) {
          visibleMessages.add(object.messageOwner.random_id);
        }
      }
    }
    MediaController.getInstance()
        .setLastEncryptedChatParams(
            chatEnterTime, chatLeaveTime, currentEncryptedChat, visibleMessages);
  }

  private void fixLayout(final boolean resume) {
    if (avatarContainer != null) {
      avatarContainer
          .getViewTreeObserver()
          .addOnPreDrawListener(
              new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                  if (avatarContainer != null) {
                    avatarContainer.getViewTreeObserver().removeOnPreDrawListener(this);
                  }
                  if (!AndroidUtilities.isTablet()
                      && ApplicationLoader.applicationContext
                              .getResources()
                              .getConfiguration()
                              .orientation
                          == Configuration.ORIENTATION_LANDSCAPE) {
                    selectedMessagesCountTextView.setTextSize(18);
                  } else {
                    selectedMessagesCountTextView.setTextSize(20);
                  }
                  if (AndroidUtilities.isTablet()) {
                    if (AndroidUtilities.isSmallTablet()
                        && ApplicationLoader.applicationContext
                                .getResources()
                                .getConfiguration()
                                .orientation
                            == Configuration.ORIENTATION_PORTRAIT) {
                      actionBar.setBackButtonImage(R.drawable.ic_ab_back);
                    } else {
                      actionBar.setBackButtonImage(R.drawable.ic_close_white);
                    }
                  }
                  int padding =
                      (ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(48)) / 2;
                  avatarContainer.setPadding(
                      avatarContainer.getPaddingLeft(),
                      padding,
                      avatarContainer.getPaddingRight(),
                      padding);
                  FrameLayout.LayoutParams layoutParams =
                      (FrameLayout.LayoutParams) avatarContainer.getLayoutParams();
                  layoutParams.topMargin =
                      (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
                  avatarContainer.setLayoutParams(layoutParams);
                  return true;
                }
              });
    }
  }

  @Override
  public void onConfigurationChanged(android.content.res.Configuration newConfig) {
    fixLayout(false);
  }

  public void createMenu(View v, boolean single) {
    if (actionBar.isActionModeShowed()) {
      return;
    }

    MessageObject message = null;
    if (v instanceof ChatBaseCell) {
      message = ((ChatBaseCell) v).getMessageObject();
    } else if (v instanceof ChatActionCell) {
      message = ((ChatActionCell) v).getMessageObject();
    }
    if (message == null) {
      return;
    }
    final int type = getMessageType(message);

    selectedObject = null;
    forwaringMessage = null;
    selectedMessagesCanCopyIds.clear();
    selectedMessagesIds.clear();
    actionBar.hideActionMode();

    if (single || type < 2 || type == 20) {
      if (type >= 0) {
        selectedObject = message;
        if (getParentActivity() == null) {
          return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());

        CharSequence[] items = null;
        int[] options = null;

        if (type == 0) {
          items =
              new CharSequence[] {
                LocaleController.getString("Retry", R.string.Retry),
                LocaleController.getString("Delete", R.string.Delete)
              };
          options = new int[] {0, 1};
        } else if (type == 1) {
          if (currentChat != null && !isBroadcast) {
            items =
                new CharSequence[] {
                  LocaleController.getString("Reply", R.string.Reply),
                  LocaleController.getString("Delete", R.string.Delete)
                };
            options = new int[] {8, 1};
          } else {
            items = new CharSequence[] {LocaleController.getString("Delete", R.string.Delete)};
            options = new int[] {1};
          }
        } else if (type == 20) {
          items =
              new CharSequence[] {
                LocaleController.getString("Retry", R.string.Retry),
                LocaleController.getString("Copy", R.string.Copy),
                LocaleController.getString("Delete", R.string.Delete)
              };
          options = new int[] {0, 3, 1};
        } else {
          if (currentEncryptedChat == null) {
            if (!isBroadcast
                && !(currentChat != null
                    && (currentChat instanceof TLRPC.TL_chatForbidden || currentChat.left))) {
              if (type == 2) {
                items =
                    new CharSequence[] {
                      LocaleController.getString("Reply", R.string.Reply),
                      LocaleController.getString("Forward", R.string.Forward),
                      LocaleController.getString("Delete", R.string.Delete)
                    };
                options = new int[] {8, 2, 1};
              } else if (type == 3) {
                items =
                    new CharSequence[] {
                      LocaleController.getString("Reply", R.string.Reply),
                      LocaleController.getString("Forward", R.string.Forward),
                      LocaleController.getString("Copy", R.string.Copy),
                      LocaleController.getString("Delete", R.string.Delete)
                    };
                options = new int[] {8, 2, 3, 1};
              } else if (type == 4) {
                if (selectedObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                  String saveString;
                  if (selectedObject.isMusic()) {
                    saveString = LocaleController.getString("SaveToMusic", R.string.SaveToMusic);
                  } else {
                    saveString =
                        LocaleController.getString("SaveToDownloads", R.string.SaveToDownloads);
                  }
                  items =
                      new CharSequence[] {
                        LocaleController.getString("Reply", R.string.Reply),
                        saveString,
                        LocaleController.getString("ShareFile", R.string.ShareFile),
                        LocaleController.getString("Forward", R.string.Forward),
                        LocaleController.getString("Delete", R.string.Delete)
                      };
                  options = new int[] {8, 10, 4, 2, 1};
                } else {
                  items =
                      new CharSequence[] {
                        LocaleController.getString("Reply", R.string.Reply),
                        LocaleController.getString("SaveToGallery", R.string.SaveToGallery),
                        LocaleController.getString("Forward", R.string.Forward),
                        LocaleController.getString("Delete", R.string.Delete)
                      };
                  options = new int[] {8, 4, 2, 1};
                }
              } else if (type == 5) {
                items =
                    new CharSequence[] {
                      LocaleController.getString("Reply", R.string.Reply),
                      LocaleController.getString(
                          "ApplyLocalizationFile", R.string.ApplyLocalizationFile),
                      LocaleController.getString("ShareFile", R.string.ShareFile),
                      LocaleController.getString("Forward", R.string.Forward),
                      LocaleController.getString("Delete", R.string.Delete)
                    };
                options = new int[] {8, 5, 4, 2, 1};
              } else if (type == 6) {
                String saveString;
                if (selectedObject.isMusic()) {
                  saveString = LocaleController.getString("SaveToMusic", R.string.SaveToMusic);
                } else {
                  saveString =
                      LocaleController.getString("SaveToDownloads", R.string.SaveToDownloads);
                }
                items =
                    new CharSequence[] {
                      LocaleController.getString("Reply", R.string.Reply),
                      LocaleController.getString("SaveToGallery", R.string.SaveToGallery),
                      saveString,
                      LocaleController.getString("ShareFile", R.string.ShareFile),
                      LocaleController.getString("Forward", R.string.Forward),
                      LocaleController.getString("Delete", R.string.Delete)
                    };
                options = new int[] {8, 7, 10, 6, 2, 1};
              } else if (type == 7) {
                items =
                    new CharSequence[] {
                      LocaleController.getString("Reply", R.string.Reply),
                      LocaleController.getString("Forward", R.string.Forward),
                      LocaleController.getString("AddToStickers", R.string.AddToStickers),
                      LocaleController.getString("Delete", R.string.Delete)
                    };
                options = new int[] {8, 2, 9, 1};
              }
            } else {
              if (type == 2) {
                items =
                    new CharSequence[] {
                      LocaleController.getString("Forward", R.string.Forward),
                      LocaleController.getString("Delete", R.string.Delete)
                    };
                options = new int[] {2, 1};
              } else if (type == 3) {
                items =
                    new CharSequence[] {
                      LocaleController.getString("Forward", R.string.Forward),
                      LocaleController.getString("Copy", R.string.Copy),
                      LocaleController.getString("Delete", R.string.Delete)
                    };
                options = new int[] {2, 3, 1};
              } else if (type == 4) {
                if (selectedObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                  String saveString;
                  if (selectedObject.isMusic()) {
                    saveString = LocaleController.getString("SaveToMusic", R.string.SaveToMusic);
                  } else {
                    saveString =
                        LocaleController.getString("SaveToDownloads", R.string.SaveToDownloads);
                  }
                  items =
                      new CharSequence[] {
                        saveString,
                        LocaleController.getString("ShareFile", R.string.ShareFile),
                        LocaleController.getString("Forward", R.string.Forward),
                        LocaleController.getString("Delete", R.string.Delete)
                      };
                  options = new int[] {10, 4, 2, 1};
                } else {
                  items =
                      new CharSequence[] {
                        LocaleController.getString("SaveToGallery", R.string.SaveToGallery),
                        LocaleController.getString("Forward", R.string.Forward),
                        LocaleController.getString("Delete", R.string.Delete)
                      };
                  options = new int[] {4, 2, 1};
                }
              } else if (type == 5) {
                items =
                    new CharSequence[] {
                      LocaleController.getString(
                          "ApplyLocalizationFile", R.string.ApplyLocalizationFile),
                      LocaleController.getString("ShareFile", R.string.ShareFile),
                      LocaleController.getString("Forward", R.string.Forward),
                      LocaleController.getString("Delete", R.string.Delete)
                    };
                options = new int[] {5, 4, 2, 1};
              } else if (type == 6) {
                String saveString;
                if (selectedObject.isMusic()) {
                  saveString = LocaleController.getString("SaveToMusic", R.string.SaveToMusic);
                } else {
                  saveString =
                      LocaleController.getString("SaveToDownloads", R.string.SaveToDownloads);
                }
                items =
                    new CharSequence[] {
                      LocaleController.getString("SaveToGallery", R.string.SaveToGallery),
                      saveString,
                      LocaleController.getString("ShareFile", R.string.ShareFile),
                      LocaleController.getString("Forward", R.string.Forward),
                      LocaleController.getString("Delete", R.string.Delete)
                    };
                options = new int[] {7, 10, 6, 2, 1};
              } else if (type == 7) {
                items =
                    new CharSequence[] {
                      LocaleController.getString("Reply", R.string.Reply),
                      LocaleController.getString("Forward", R.string.Forward),
                      LocaleController.getString("AddToStickers", R.string.AddToStickers),
                      LocaleController.getString("Delete", R.string.Delete)
                    };
                options = new int[] {8, 2, 9, 1};
              }
            }
          } else {
            if (type == 2) {
              items = new CharSequence[] {LocaleController.getString("Delete", R.string.Delete)};
              options = new int[] {1};
            } else if (type == 3) {
              items =
                  new CharSequence[] {
                    LocaleController.getString("Copy", R.string.Copy),
                    LocaleController.getString("Delete", R.string.Delete)
                  };
              options = new int[] {3, 1};
            } else if (type == 4) {
              if (selectedObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                String saveString;
                if (selectedObject.isMusic()) {
                  saveString = LocaleController.getString("SaveToMusic", R.string.SaveToMusic);
                } else {
                  saveString =
                      LocaleController.getString("SaveToDownloads", R.string.SaveToDownloads);
                }
                items =
                    new CharSequence[] {
                      saveString,
                      LocaleController.getString("ShareFile", R.string.ShareFile),
                      LocaleController.getString("Delete", R.string.Delete)
                    };
                options = new int[] {10, 4, 1};
              } else {
                items =
                    new CharSequence[] {
                      LocaleController.getString("SaveToGallery", R.string.SaveToGallery),
                      LocaleController.getString("Delete", R.string.Delete)
                    };
                options = new int[] {4, 1};
              }
            } else if (type == 5) {
              items =
                  new CharSequence[] {
                    LocaleController.getString(
                        "ApplyLocalizationFile", R.string.ApplyLocalizationFile),
                    LocaleController.getString("Delete", R.string.Delete)
                  };
              options = new int[] {5, 1};
            } else if (type == 7) {
              items =
                  new CharSequence[] {
                    LocaleController.getString("Reply", R.string.Reply),
                    LocaleController.getString("Forward", R.string.Forward),
                    LocaleController.getString("AddToStickers", R.string.AddToStickers),
                    LocaleController.getString("Delete", R.string.Delete)
                  };
              options = new int[] {8, 2, 9, 1};
            }
          }
        }

        final int[] finalOptions = options;
        builder.setItems(
            items,
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialogInterface, int i) {
                if (finalOptions == null
                    || selectedObject == null
                    || i < 0
                    || i >= finalOptions.length) {
                  return;
                }
                processSelectedOption(finalOptions[i]);
              }
            });

        builder.setTitle(LocaleController.getString("Message", R.string.Message));
        showDialog(builder.create());
      }
      return;
    }
    actionBar.showActionMode();

    if (Build.VERSION.SDK_INT >= 11) {
      AnimatorSetProxy animatorSet = new AnimatorSetProxy();
      ArrayList<Object> animators = new ArrayList<>();
      for (int a = 0; a < actionModeViews.size(); a++) {
        View view = actionModeViews.get(a);
        AndroidUtilities.clearDrawableAnimation(view);
        if (a < 1) {
          animators.add(
              ObjectAnimatorProxy.ofFloat(view, "translationX", -AndroidUtilities.dp(56), 0));
        } else {
          animators.add(ObjectAnimatorProxy.ofFloat(view, "scaleY", 0.1f, 1.0f));
        }
      }
      animatorSet.playTogether(animators);
      animatorSet.setDuration(250);
      animatorSet.start();
    }

    addToSelectedMessages(message);
    updateActionModeTitle();
    updateVisibleRows();
  }

  private void processSelectedOption(int option) {
    if (selectedObject == null) {
      return;
    }
    if (option == 0) {
      if (SendMessagesHelper.getInstance().retrySendMessage(selectedObject, false)) {
        moveScrollToLastMessage();
      }
    } else if (option == 1) {
      final MessageObject finalSelectedObject = selectedObject;
      AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
      builder.setMessage(
          LocaleController.formatString(
              "AreYouSureDeleteMessages",
              R.string.AreYouSureDeleteMessages,
              LocaleController.formatPluralString("messages", 1)));
      builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
      builder.setPositiveButton(
          LocaleController.getString("OK", R.string.OK),
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
              ArrayList<Integer> ids = new ArrayList<>();
              ids.add(finalSelectedObject.getId());
              removeUnreadPlane();
              ArrayList<Long> random_ids = null;
              if (currentEncryptedChat != null
                  && finalSelectedObject.messageOwner.random_id != 0
                  && finalSelectedObject.type != MessageObject.Type.CHAT_ACTION_PHOTO) {
                random_ids = new ArrayList<>();
                random_ids.add(finalSelectedObject.messageOwner.random_id);
              }
              MessagesController.getInstance()
                  .deleteMessages(ids, random_ids, currentEncryptedChat);
            }
          });
      builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
      showDialog(builder.create());
    } else if (option == 2) {
      forwaringMessage = selectedObject;
      Bundle args = new Bundle();
      args.putBoolean("onlySelect", true);
      args.putInt("dialogsType", 1);
      DialogsActivity fragment = new DialogsActivity(args);
      fragment.setDelegate(this);
      presentFragment(fragment);
    } else if (option == 3) {
      try {
        if (Build.VERSION.SDK_INT < 11) {
          android.text.ClipboardManager clipboard =
              (android.text.ClipboardManager)
                  ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
          clipboard.setText(selectedObject.messageText);
        } else {
          android.content.ClipboardManager clipboard =
              (android.content.ClipboardManager)
                  ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
          android.content.ClipData clip =
              android.content.ClipData.newPlainText("label", selectedObject.messageText);
          clipboard.setPrimaryClip(clip);
        }
      } catch (Exception e) {
        FileLog.e("tmessages", e);
      }
    } else if (option == 4) {
      String path = selectedObject.messageOwner.attachPath;
      if (path != null && path.length() > 0) {
        File temp = new File(path);
        if (!temp.exists()) {
          path = null;
        }
      }
      if (path == null || path.length() == 0) {
        path = FileLoader.getPathToMessage(selectedObject.messageOwner).toString();
      }
      if (selectedObject.type == MessageObject.Type.VIDEO) {
        MediaController.saveFile(path, getParentActivity(), 1, null);
      } else if (selectedObject.type == MessageObject.Type.PHOTO) {
        MediaController.saveFile(path, getParentActivity(), 0, null);
      } else if (selectedObject.type == MessageObject.Type.DOC_GIF
          || selectedObject.type == MessageObject.Type.DOC_GENERIC
          || selectedObject.type == MessageObject.Type.DOC_AUDIO) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(selectedObject.messageOwner.media.document.mime_type);
        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(path)));
        getParentActivity()
            .startActivityForResult(
                Intent.createChooser(
                    intent, LocaleController.getString("ShareFile", R.string.ShareFile)),
                500);
      }
    } else if (option == 5) {
      File locFile = null;
      if (selectedObject.messageOwner.attachPath != null
          && selectedObject.messageOwner.attachPath.length() != 0) {
        File f = new File(selectedObject.messageOwner.attachPath);
        if (f.exists()) {
          locFile = f;
        }
      }
      if (locFile == null) {
        File f = FileLoader.getPathToMessage(selectedObject.messageOwner);
        if (f.exists()) {
          locFile = f;
        }
      }
      if (locFile != null) {
        if (LocaleController.getInstance().applyLanguageFile(locFile)) {
          presentFragment(new LanguageSelectActivity());
        } else {
          if (getParentActivity() == null) {
            return;
          }
          AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
          builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
          builder.setMessage(
              LocaleController.getString("IncorrectLocalization", R.string.IncorrectLocalization));
          builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
          showDialog(builder.create());
        }
      }
    } else if (option == 6 || option == 7) {
      String path = selectedObject.messageOwner.attachPath;
      if (path != null && path.length() > 0) {
        File temp = new File(path);
        if (!temp.exists()) {
          path = null;
        }
      }
      if (path == null || path.length() == 0) {
        path = FileLoader.getPathToMessage(selectedObject.messageOwner).toString();
      }
      if (selectedObject.type == MessageObject.Type.DOC_GIF ||
          selectedObject.type == MessageObject.Type.DOC_GENERIC ||
          selectedObject.type == MessageObject.Type.DOC_AUDIO) {
        if (option == 6) {
          Intent intent = new Intent(Intent.ACTION_SEND);
          intent.setType(selectedObject.messageOwner.media.document.mime_type);
          intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(path)));
          getParentActivity()
              .startActivityForResult(
                  Intent.createChooser(
                      intent, LocaleController.getString("ShareFile", R.string.ShareFile)),
                  500);
        } else if (option == 7) {
          MediaController.saveFile(path, getParentActivity(), 0, null);
        }
      }
    } else if (option == 8) {
      showReplyPanel(true, selectedObject, null, null, false, true);
    } else if (option == 9) {
      StickersQuery.loadStickers(this, selectedObject.getInputStickerSet());
    } else if (option == 10) {
      String fileName = FileLoader.getDocumentFileName(selectedObject.messageOwner.media.document);
      if (fileName == null || fileName.length() == 0) {
        fileName = selectedObject.getFileName();
      }
      String path = selectedObject.messageOwner.attachPath;
      if (path != null && path.length() > 0) {
        File temp = new File(path);
        if (!temp.exists()) {
          path = null;
        }
      }
      if (path == null || path.length() == 0) {
        path = FileLoader.getPathToMessage(selectedObject.messageOwner).toString();
      }
      MediaController.saveFile(
          path, getParentActivity(), selectedObject.isMusic() ? 3 : 2, fileName);
    }
    selectedObject = null;
  }

  @Override
  public void didSelectDialog(DialogsActivity activity, long did, boolean param) {
    if (dialog_id != 0 && (forwaringMessage != null || !selectedMessagesIds.isEmpty())) {
      ArrayList<MessageObject> fmessages = new ArrayList<>();
      if (forwaringMessage != null) {
        fmessages.add(forwaringMessage);
        forwaringMessage = null;
      } else {
        ArrayList<Integer> ids = new ArrayList<>(selectedMessagesIds.keySet());
        Collections.sort(ids);
        for (Integer id : ids) {
          MessageObject message = selectedMessagesIds.get(id);
          if (message != null && id > 0) {
            fmessages.add(message);
          }
        }
        selectedMessagesCanCopyIds.clear();
        selectedMessagesIds.clear();
        actionBar.hideActionMode();
      }

      if (did != dialog_id) {
        int lower_part = (int) did;
        if (lower_part != 0) {
          Bundle args = new Bundle();
          args.putBoolean("scrollToTopOnResume", scrollToTopOnResume);
          if (lower_part > 0) {
            args.putInt("user_id", lower_part);
          } else if (lower_part < 0) {
            args.putInt("chat_id", -lower_part);
          }
          ChatActivity chatActivity = new ChatActivity(args);
          if (presentFragment(chatActivity, true)) {
            chatActivity.showReplyPanel(true, null, fmessages, null, false, false);
            if (!AndroidUtilities.isTablet()) {
              removeSelfFromStack();
              Activity parentActivity = getParentActivity();
              if (parentActivity == null) {
                parentActivity = chatActivity.getParentActivity();
              }
              if (parentActivity != null) {
                parentActivity
                    .getWindow()
                    .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
              }
            }
          } else {
            activity.finishFragment();
          }
        } else {
          activity.finishFragment();
        }
      } else {
        activity.finishFragment();
        moveScrollToLastMessage();
        showReplyPanel(true, null, fmessages, null, false, AndroidUtilities.isTablet());
        if (AndroidUtilities.isTablet()) {
          actionBar.hideActionMode();
        }
        updateVisibleRows();
      }
    }
  }

  @Override
  public boolean onBackPressed() {
    if (actionBar.isActionModeShowed()) {
      selectedMessagesIds.clear();
      selectedMessagesCanCopyIds.clear();
      actionBar.hideActionMode();
      updateVisibleRows();
      return false;
    } else if (chatActivityEnterView.isPopupShowing()) {
      chatActivityEnterView.hidePopup();
      return false;
    }
    return true;
  }

  public boolean isGoogleMapsInstalled() {
    try {
      ApplicationLoader.applicationContext
          .getPackageManager()
          .getApplicationInfo("com.google.android.apps.maps", 0);
      return true;
    } catch (PackageManager.NameNotFoundException e) {
      if (getParentActivity() == null) {
        return false;
      }
      AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
      builder.setMessage("Install Google Maps?");
      builder.setCancelable(true);
      builder.setPositiveButton(
          LocaleController.getString("OK", R.string.OK),
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
              try {
                Intent intent =
                    new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=com.google.android.apps.maps"));
                getParentActivity().startActivityForResult(intent, 500);
              } catch (Exception e) {
                FileLog.e("tmessages", e);
              }
            }
          });
      builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
      showDialog(builder.create());
      return false;
    }
  }

  private void updateVisibleRows() {
    if (chatListView == null) {
      return;
    }
    int count = chatListView.getChildCount();
    for (int a = 0; a < count; a++) {
      View view = chatListView.getChildAt(a);
      if (view instanceof ChatBaseCell) {
        ChatBaseCell cell = (ChatBaseCell) view;

        boolean disableSelection = false;
        boolean selected = false;
        if (actionBar.isActionModeShowed()) {
          if (selectedMessagesIds.containsKey(cell.getMessageObject().getId())) {
            view.setBackgroundColor(0x6633b5e5);
            selected = true;
          } else {
            view.setBackgroundColor(0);
          }
          disableSelection = true;
        } else {
          view.setBackgroundColor(0);
        }

        cell.setMessageObject(cell.getMessageObject());
        cell.setCheckPressed(!disableSelection, disableSelection && selected);
        cell.setHighlighted(
            highlightMessageId != Integer.MAX_VALUE
                && cell.getMessageObject() != null
                && cell.getMessageObject().getId() == highlightMessageId);
      }
    }
  }

  private void alertUserOpenError(MessageObject message) {
    if (getParentActivity() == null) {
      return;
    }
    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
    if (message.type == MessageObject.Type.VIDEO) {
      builder.setMessage(
          LocaleController.getString("NoPlayerInstalled", R.string.NoPlayerInstalled));
    } else {
      builder.setMessage(
          LocaleController.formatString(
              "NoHandleAppInstalled",
              R.string.NoHandleAppInstalled,
              message.messageOwner.media.document.mime_type));
    }
    showDialog(builder.create());
  }

  @Override
  public void updatePhotoAtIndex(int index) {}

  @Override
  public PhotoViewer.PlaceProviderObject getPlaceForPhoto(
      MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
    if (messageObject == null) {
      return null;
    }
    int count = chatListView.getChildCount();

    for (int a = 0; a < count; a++) {
      MessageObject messageToOpen = null;
      ImageReceiver imageReceiver = null;
      View view = chatListView.getChildAt(a);
      if (view instanceof ChatMediaCell) {
        ChatMediaCell cell = (ChatMediaCell) view;
        MessageObject message = cell.getMessageObject();
        if (message != null && message.getId() == messageObject.getId()) {
          messageToOpen = message;
          imageReceiver = cell.getPhotoImage();
        }
      } else if (view instanceof ChatActionCell) {
        ChatActionCell cell = (ChatActionCell) view;
        MessageObject message = cell.getMessageObject();
        if (message != null && message.getId() == messageObject.getId()) {
          messageToOpen = message;
          imageReceiver = cell.getPhotoImage();
        }
      }

      if (messageToOpen != null) {
        int coords[] = new int[2];
        view.getLocationInWindow(coords);
        PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
        object.viewX = coords[0];
        object.viewY = coords[1] - AndroidUtilities.statusBarHeight;
        object.parentView = chatListView;
        object.imageReceiver = imageReceiver;
        object.thumb = imageReceiver.getBitmap();
        object.radius = imageReceiver.getRoundRadius();
        return object;
      }
    }
    return null;
  }

  @Override
  public Bitmap getThumbForPhoto(
      MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
    return null;
  }

  @Override
  public void willSwitchFromPhoto(
      MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {}

  @Override
  public void willHidePhotoViewer() {}

  @Override
  public boolean isPhotoChecked(int index) {
    return false;
  }

  @Override
  public void setPhotoChecked(int index) {}

  @Override
  public void cancelButtonPressed() {}

  @Override
  public void sendButtonPressed(int index) {}

  @Override
  public int getSelectedCount() {
    return 0;
  }

  public class ChatActivityAdapter extends RecyclerView.Adapter {

    private Context mContext;
    private boolean isBot;
    private int rowCount;
    private int botInfoRow;
    private int loadingUpRow;
    private int loadingDownRow;
    private int messagesStartRow;
    private int messagesEndRow;

    public ChatActivityAdapter(Context context) {
      mContext = context;
      isBot = currentUser != null && (currentUser.flags & TLRPC.USER_FLAG_BOT) != 0;
    }

    public void updateRows() {
      rowCount = 0;
      if (currentUser != null && (currentUser.flags & TLRPC.USER_FLAG_BOT) != 0) {
        botInfoRow = rowCount++;
      } else {
        botInfoRow = -1;
      }
      if (!messages.isEmpty()) {
        if (!endReached) {
          loadingUpRow = rowCount++;
        } else {
          loadingUpRow = -1;
        }
        messagesStartRow = rowCount;
        rowCount += messages.size();
        messagesEndRow = rowCount;
        if (!forwardEndReached) {
          loadingDownRow = rowCount++;
        } else {
          loadingDownRow = -1;
        }
      } else {
        loadingUpRow = -1;
        loadingDownRow = -1;
        messagesStartRow = -1;
        messagesEndRow = -1;
      }
    }

    private class Holder extends RecyclerView.ViewHolder {

      public Holder(View itemView) {
        super(itemView);
      }
    }

    @Override
    public int getItemCount() {
      return rowCount;
    }

    @Override
    public long getItemId(int i) {
      return RecyclerListView.NO_ID;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
      View view = null;
      if (viewType == 0) {
        if (!chatMessageCellsCache.isEmpty()) {
          view = chatMessageCellsCache.get(0);
          chatMessageCellsCache.remove(0);
        } else {
          view = new ChatMessageCell(mContext);
        }
      } else if (viewType == 1) {
        if (!chatMediaCellsCache.isEmpty()) {
          view = chatMediaCellsCache.get(0);
          chatMediaCellsCache.remove(0);
        } else {
          view = new ChatMediaCell(mContext);
        }
      } else if (viewType == 2) {
        view = new ChatAudioCell(mContext);
      } else if (viewType == 3) {
        view = new ChatContactCell(mContext);
      } else if (viewType == 4) {
        view = new ChatActionCell(mContext);
      } else if (viewType == 5) {
        view = new ChatLoadingCell(mContext);
      } else if (viewType == 6) {
        view = new ChatUnreadCell(mContext);
      } else if (viewType == 7) {
        view = new BotHelpCell(mContext);
        ((BotHelpCell) view)
            .setDelegate(
                new BotHelpCell.BotHelpCellDelegate() {
                  @Override
                  public void didPressUrl(String url) {
                    if (url.startsWith("@")) {
                      MessagesController.openByUserName(url.substring(1), ChatActivity.this, 0);
                    } else if (url.startsWith("#")) {
                      DialogsActivity fragment = new DialogsActivity(null);
                      fragment.setSearchString(url);
                      presentFragment(fragment);
                    } else if (url.startsWith("/")) {
                      chatActivityEnterView.setCommand(null, url);
                    }
                  }
                });
      } else if (viewType == 8) {
        view = new ChatMusicCell(mContext);
      }

      if (view instanceof ChatBaseCell) {
        ((ChatBaseCell) view)
            .setDelegate(
                new ChatBaseCell.ChatBaseCellDelegate() {
                  @Override
                  public void didPressedUserAvatar(ChatBaseCell cell, TLRPC.User user) {
                    if (actionBar.isActionModeShowed()) {
                      processRowSelect(cell);
                      return;
                    }
                    if (user != null && user.id != UserConfig.getClientUserId()) {
                      Bundle args = new Bundle();
                      args.putInt("user_id", user.id);
                      presentFragment(new ProfileActivity(args));
                    }
                  }

                  @Override
                  public void didPressedCancelSendButton(ChatBaseCell cell) {
                    MessageObject message = cell.getMessageObject();
                    if (message.messageOwner.send_state != 0) {
                      SendMessagesHelper.getInstance().cancelSendingMessage(message);
                    }
                  }

                  @Override
                  public void didLongPressed(ChatBaseCell cell) {
                    createMenu(cell, false);
                  }

                  @Override
                  public boolean canPerformActions() {
                    return actionBar != null && !actionBar.isActionModeShowed();
                  }

                  @Override
                  public void didPressUrl(MessageObject messageObject, String url) {
                    if (url.startsWith("@")) {
                      MessagesController.openByUserName(url.substring(1), ChatActivity.this, 0);
                    } else if (url.startsWith("#")) {
                      DialogsActivity fragment = new DialogsActivity(null);
                      fragment.setSearchString(url);
                      presentFragment(fragment);
                    } else if (url.startsWith("/")) {
                      chatActivityEnterView.setCommand(messageObject, url);
                    }
                  }

                  @Override
                  public void needOpenWebView(
                      String url, String title, String originalUrl, int w, int h) {
                    BottomSheet.Builder builder = new BottomSheet.Builder(mContext);
                    builder.setCustomView(
                        new WebFrameLayout(
                            mContext, builder.create(), title, originalUrl, url, w, h));
                    builder.setUseFullWidth(true);
                    showDialog(builder.create());
                  }

                  @Override
                  public void didPressReplyMessage(ChatBaseCell cell, int id) {
                    scrollToMessageId(id, cell.getMessageObject().getId(), true);
                  }
                });
        if (view instanceof ChatMediaCell) {
          ((ChatMediaCell) view).setAllowedToSetPhoto(openAnimationEnded);
          ((ChatMediaCell) view)
              .setMediaDelegate(
                  new ChatMediaCell.ChatMediaCellDelegate() {
                    @Override
                    public void didClickedImage(ChatMediaCell cell) {
                      MessageObject message = cell.getMessageObject();
                      if (message.isSendError()) {
                        createMenu(cell, false);
                        return;
                      } else if (message.isSending()) {
                        return;
                      }
                      if (message.type == MessageObject.Type.PHOTO) {
                        PhotoViewer.getInstance().setParentActivity(getParentActivity());
                        PhotoViewer.getInstance().openPhoto(message, ChatActivity.this);
                      } else if (message.type == MessageObject.Type.VIDEO) {
                        sendSecretMessageRead(message);
                        try {
                          File f = null;
                          if (message.messageOwner.attachPath != null
                              && message.messageOwner.attachPath.length() != 0) {
                            f = new File(message.messageOwner.attachPath);
                          }
                          if (f == null || f != null && !f.exists()) {
                            f = FileLoader.getPathToMessage(message.messageOwner);
                          }
                          Intent intent = new Intent(Intent.ACTION_VIEW);
                          intent.setDataAndType(Uri.fromFile(f), "video/mp4");
                          getParentActivity().startActivityForResult(intent, 500);
                        } catch (Exception e) {
                          alertUserOpenError(message);
                        }
                      } else if (message.type == MessageObject.Type.LOCATION) {
                        if (!isGoogleMapsInstalled()) {
                          return;
                        }
                        LocationActivity fragment = new LocationActivity();
                        fragment.setMessageObject(message);
                        presentFragment(fragment);
                      } else if (message.type == MessageObject.Type.DOC_GENERIC) {
                        File f = null;
                        String fileName = message.getFileName();
                        if (message.messageOwner.attachPath != null
                            && message.messageOwner.attachPath.length() != 0) {
                          f = new File(message.messageOwner.attachPath);
                        }
                        if (f == null || f != null && !f.exists()) {
                          f = FileLoader.getPathToMessage(message.messageOwner);
                        }
                        if (f != null && f.exists()) {
                          String realMimeType = null;
                          try {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            if (message.type == MessageObject.Type.DOC_GIF ||
                                message.type == MessageObject.Type.DOC_GENERIC) {
                              MimeTypeMap myMime = MimeTypeMap.getSingleton();
                              int idx = fileName.lastIndexOf(".");
                              if (idx != -1) {
                                String ext = fileName.substring(idx + 1);
                                realMimeType = myMime.getMimeTypeFromExtension(ext.toLowerCase());
                                if (realMimeType == null) {
                                  realMimeType = message.messageOwner.media.document.mime_type;
                                  if (realMimeType == null || realMimeType.length() == 0) {
                                    realMimeType = null;
                                  }
                                }
                                if (realMimeType != null) {
                                  intent.setDataAndType(Uri.fromFile(f), realMimeType);
                                } else {
                                  intent.setDataAndType(Uri.fromFile(f), "text/plain");
                                }
                              } else {
                                intent.setDataAndType(Uri.fromFile(f), "text/plain");
                              }
                            }
                            if (realMimeType != null) {
                              try {
                                getParentActivity().startActivityForResult(intent, 500);
                              } catch (Exception e) {
                                intent.setDataAndType(Uri.fromFile(f), "text/plain");
                                getParentActivity().startActivityForResult(intent, 500);
                              }
                            } else {
                              getParentActivity().startActivityForResult(intent, 500);
                            }
                          } catch (Exception e) {
                            alertUserOpenError(message);
                          }
                        }
                      }
                    }

                    @Override
                    public void didPressedOther(ChatMediaCell cell) {
                      createMenu(cell, true);
                    }
                  });
        } else if (view instanceof ChatContactCell) {
          ((ChatContactCell) view)
              .setContactDelegate(
                  new ChatContactCell.ChatContactCellDelegate() {
                    @Override
                    public void didClickAddButton(ChatContactCell cell, TLRPC.User user) {
                      if (actionBar.isActionModeShowed()) {
                        processRowSelect(cell);
                        return;
                      }
                      MessageObject messageObject = cell.getMessageObject();
                      Bundle args = new Bundle();
                      args.putInt("user_id", messageObject.messageOwner.media.user_id);
                      args.putString("phone", messageObject.messageOwner.media.phone_number);
                      args.putBoolean("addContact", true);
                      presentFragment(new ContactAddActivity(args));
                    }

                    @Override
                    public void didClickPhone(ChatContactCell cell) {
                      if (actionBar.isActionModeShowed()) {
                        processRowSelect(cell);
                        return;
                      }
                      final MessageObject messageObject = cell.getMessageObject();
                      if (getParentActivity() == null
                          || messageObject.messageOwner.media.phone_number == null
                          || messageObject.messageOwner.media.phone_number.length() == 0) {
                        return;
                      }
                      AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                      builder.setItems(
                          new CharSequence[] {
                            LocaleController.getString("Copy", R.string.Copy),
                            LocaleController.getString("Call", R.string.Call)
                          },
                          new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                              if (i == 1) {
                                try {
                                  Intent intent =
                                      new Intent(
                                          Intent.ACTION_DIAL,
                                          Uri.parse(
                                              "tel:"
                                                  + messageObject.messageOwner.media.phone_number));
                                  intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                  getParentActivity().startActivityForResult(intent, 500);
                                } catch (Exception e) {
                                  FileLog.e("tmessages", e);
                                }
                              } else if (i == 0) {
                                try {
                                  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
                                    android.text.ClipboardManager clipboard =
                                        (android.text.ClipboardManager)
                                            ApplicationLoader.applicationContext.getSystemService(
                                                Context.CLIPBOARD_SERVICE);
                                    clipboard.setText(
                                        messageObject.messageOwner.media.phone_number);
                                  } else {
                                    android.content.ClipboardManager clipboard =
                                        (android.content.ClipboardManager)
                                            ApplicationLoader.applicationContext.getSystemService(
                                                Context.CLIPBOARD_SERVICE);
                                    android.content.ClipData clip =
                                        android.content.ClipData.newPlainText(
                                            "label", messageObject.messageOwner.media.phone_number);
                                    clipboard.setPrimaryClip(clip);
                                  }
                                } catch (Exception e) {
                                  FileLog.e("tmessages", e);
                                }
                              }
                            }
                          });
                      showDialog(builder.create());
                    }
                  });
        } else if (view instanceof ChatMusicCell) {
          ((ChatMusicCell) view)
              .setMusicDelegate(
                  new ChatMusicCell.ChatMusicCellDelegate() {
                    @Override
                    public boolean needPlayMusic(MessageObject messageObject) {
                      return MediaController.getInstance().setPlaylist(messages, messageObject);
                    }
                  });
        }
      } else if (view instanceof ChatActionCell) {
        ((ChatActionCell) view)
            .setDelegate(
                new ChatActionCell.ChatActionCellDelegate() {
                  @Override
                  public void didClickedImage(ChatActionCell cell) {
                    MessageObject message = cell.getMessageObject();
                    PhotoViewer.getInstance().setParentActivity(getParentActivity());
                    PhotoViewer.getInstance().openPhoto(message, ChatActivity.this);
                  }

                  @Override
                  public void didLongPressed(ChatActionCell cell) {
                    createMenu(cell, false);
                  }

                  @Override
                  public void needOpenUserProfile(int uid) {
                    if (uid != UserConfig.getClientUserId()) {
                      Bundle args = new Bundle();
                      args.putInt("user_id", uid);
                      presentFragment(new ProfileActivity(args));
                    }
                  }
                });
      }

      return new Holder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
      if (position == botInfoRow) {
        BotHelpCell helpView = (BotHelpCell) holder.itemView;
        helpView.setText(!botInfo.isEmpty() ? botInfo.get(currentUser.id).description : null);
      } else if (position == loadingDownRow || position == loadingUpRow) {
        ChatLoadingCell loadingCell = (ChatLoadingCell) holder.itemView;
        loadingCell.setProgressVisible(loadsCount > 1);
      } else if (position >= messagesStartRow && position < messagesEndRow) {
        MessageObject message = messages.get(messages.size() - (position - messagesStartRow) - 1);
        View view = holder.itemView;

        boolean selected = false;
        boolean disableSelection = false;
        if (actionBar.isActionModeShowed()) {
          if (selectedMessagesIds.containsKey(message.getId())) {
            view.setBackgroundColor(0x6633b5e5);
            selected = true;
          } else {
            view.setBackgroundColor(0);
          }
          disableSelection = true;
        } else {
          view.setBackgroundColor(0);
        }

        if (view instanceof ChatBaseCell) {
          ChatBaseCell baseCell = (ChatBaseCell) view;
          baseCell.isChat = currentChat != null;
          baseCell.setMessageObject(message);
          baseCell.setCheckPressed(!disableSelection, disableSelection && selected);
          if (view instanceof ChatAudioCell
              && MediaController.getInstance()
                  .canDownloadMedia(MediaController.AUTODOWNLOAD_MASK_AUDIO)) {
            ((ChatAudioCell) view).downloadAudioIfNeed();
          }
          baseCell.setHighlighted(
              highlightMessageId != Integer.MAX_VALUE && message.getId() == highlightMessageId);
        } else if (view instanceof ChatActionCell) {
          ChatActionCell actionCell = (ChatActionCell) view;
          actionCell.setMessageObject(message);
        } else if (view instanceof ChatUnreadCell) {
          ChatUnreadCell unreadCell = (ChatUnreadCell) view;
          unreadCell.setText(LocaleController.formatPluralString("NewMessages", unread_to_load));
        }
      }
    }

    @Override
    public int getItemViewType(int position) {
      if (position == loadingUpRow || position == loadingDownRow) {
        return 5;
      } else if (position == botInfoRow) {
        return 7;
      } else if (position >= messagesStartRow && position < messagesEndRow) {
        return messages.get(messages.size() - (position - messagesStartRow) - 1).contentType;
      }
      return 5;
    }

    @Override
    public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
      if (holder.itemView instanceof ChatBaseCell) {
        ChatBaseCell baseCell = (ChatBaseCell) holder.itemView;
        baseCell.setHighlighted(
            highlightMessageId != Integer.MAX_VALUE
                && baseCell.getMessageObject().getId() == highlightMessageId);
      }
      if (holder.itemView instanceof ChatMessageCell) {
        final ChatMessageCell messageCell = (ChatMessageCell) holder.itemView;
        messageCell
            .getViewTreeObserver()
            .addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
                  @Override
                  public boolean onPreDraw() {
                    messageCell.getViewTreeObserver().removeOnPreDrawListener(this);
                    messageCell.getLocalVisibleRect(scrollRect);
                    messageCell.setVisiblePart(scrollRect.top, scrollRect.bottom - scrollRect.top);
                    return true;
                  }
                });
      }
    }

    public void updateRowWithMessageObject(MessageObject messageObject) {
      int index = messages.indexOf(messageObject);
      if (index == -1) {
        return;
      }
      notifyItemChanged(messagesStartRow + messages.size() - index - 1);
    }

    public void removeMessageObject(MessageObject messageObject) {
      int index = messages.indexOf(messageObject);
      if (index == -1) {
        return;
      }
      messages.remove(index);
      notifyItemRemoved(messagesStartRow + messages.size() - index - 1);
    }

    @Override
    public void notifyDataSetChanged() {
      updateRows();
      super.notifyDataSetChanged();
    }

    @Override
    public void notifyItemChanged(int position) {
      updateRows();
      super.notifyItemChanged(position);
    }

    @Override
    public void notifyItemRangeChanged(int positionStart, int itemCount) {
      updateRows();
      super.notifyItemRangeChanged(positionStart, itemCount);
    }

    @Override
    public void notifyItemInserted(int position) {
      updateRows();
      super.notifyItemInserted(position);
    }

    @Override
    public void notifyItemMoved(int fromPosition, int toPosition) {
      updateRows();
      super.notifyItemMoved(fromPosition, toPosition);
    }

    @Override
    public void notifyItemRangeInserted(int positionStart, int itemCount) {
      updateRows();
      super.notifyItemRangeInserted(positionStart, itemCount);
    }

    @Override
    public void notifyItemRemoved(int position) {
      updateRows();
      super.notifyItemRemoved(position);
    }

    @Override
    public void notifyItemRangeRemoved(int positionStart, int itemCount) {
      updateRows();
      super.notifyItemRangeRemoved(positionStart, itemCount);
    }
  }
}
