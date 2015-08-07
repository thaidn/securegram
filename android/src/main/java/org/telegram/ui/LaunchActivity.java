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
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.Toast;

import org.telegram.android.AndroidUtilities;
import org.telegram.phoneformat.PhoneFormat;
import org.telegram.android.ContactsController;
import org.telegram.android.SendMessagesHelper;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.android.LocaleController;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.adapters.DrawerLayoutAdapter;
import org.telegram.ui.actionbar.ActionBarLayout;
import org.telegram.ui.actionbar.BaseFragment;
import org.telegram.ui.actionbar.DrawerLayoutContainer;
import org.telegram.ui.components.DrawerPlayerView;
import org.telegram.ui.components.LayoutHelper;
import org.telegram.ui.components.PasscodeView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class LaunchActivity extends Activity implements ActionBarLayout.ActionBarLayoutDelegate,
    NotificationCenter.NotificationCenterDelegate, DialogsActivity.MessagesActivityDelegate {
    private boolean finished;
    private String videoPath;
    private String sendingText;
    private ArrayList<Uri> photoPathsArray;
    private ArrayList<String> documentsPathsArray;
    private ArrayList<Uri> documentsUrisArray;
    private String documentsMimeType;
    private ArrayList<String> documentsOriginalPathsArray;
    private ArrayList<TLRPC.User> contactsToSend;
    private int currentConnectionState;
    private static ArrayList<BaseFragment> mainFragmentsStack = new ArrayList<>();

    private ActionBarLayout actionBarLayout;
    protected DrawerLayoutContainer drawerLayoutContainer;
    private DrawerLayoutAdapter drawerLayoutAdapter;
    private PasscodeView passcodeView;
    private AlertDialog visibleDialog;

    private Intent passcodeSaveIntent;
    private boolean passcodeSaveIntentIsNew;
    private boolean passcodeSaveIntentIsRestored;

    private Runnable lockRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ApplicationLoader.postInitApplication();

        Intent intent = getIntent();
        if (!UserConfig.isClientActivated()) {
            if (intent != null) {
                String act = intent.getAction();
                if (Intent.ACTION_SEND.equals(act) || Intent.ACTION_SEND_MULTIPLE.equals(act)) {
                    super.onCreate(savedInstanceState);
                    finish();
                    return;
                }
            }
        }

        if (UserConfig.passcodeHash.length() != 0 && UserConfig.appLocked) {
            UserConfig.lastPauseTime = ConnectionsManager.getInstance().getCurrentTime();
        }

        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            AndroidUtilities.statusBarHeight = getResources().getDimensionPixelSize(resourceId);
        }

        buildLayout(savedInstanceState);
        handleIntent(intent, false /* isNew */, savedInstanceState != null /* isRestored */,
            false /* isFromPassword */);
    }

    private void buildLayout(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setTheme(R.style.Theme_TMessages);
        getWindow().setBackgroundDrawableResource(R.drawable.transparent);

        super.onCreate(savedInstanceState);

        drawerLayoutContainer = new DrawerLayoutContainer(this);
        actionBarLayout = new ActionBarLayout(this);
        drawerLayoutContainer.addView(actionBarLayout,
            new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(drawerLayoutContainer,
            new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        addMenuAndAudioPlayerViews();

        drawerLayoutContainer.setParentActionBarLayout(actionBarLayout);
        actionBarLayout.setDrawerLayoutContainer(drawerLayoutContainer);
        actionBarLayout.init(mainFragmentsStack);
        actionBarLayout.setDelegate(this);

        ApplicationLoader.loadWallpaper();

        addPasscodeView();

        NotificationCenter.getInstance().postNotificationName(
            NotificationCenter.closeOtherAppActivities, this);
        currentConnectionState = ConnectionsManager.getInstance().getConnectionState();

        NotificationCenter.getInstance().addObserver(this, NotificationCenter.appDidLogout);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.mainUserInfoChanged);
        NotificationCenter.getInstance().addObserver(this,
            NotificationCenter.closeOtherAppActivities);
        NotificationCenter.getInstance().addObserver(this,
            NotificationCenter.didUpdatedConnectionState);
        if (Build.VERSION.SDK_INT < 14) {
            NotificationCenter.getInstance().addObserver(this,
                NotificationCenter.screenStateChanged);
        }

        maybeAddFragments(savedInstanceState);
    }

    private void addMenuAndAudioPlayerViews() {
        FrameLayout listViewContainer = new FrameLayout(this);
        listViewContainer.setBackgroundColor(0xffffffff);
        drawerLayoutContainer.setDrawerLayout(listViewContainer);
        FrameLayout.LayoutParams layoutParams =
            (FrameLayout.LayoutParams) listViewContainer.getLayoutParams();
        Point screenSize = AndroidUtilities.getRealScreenSize();
        layoutParams.width = Math.min(screenSize.x, screenSize.y) - AndroidUtilities.dp(56);
        layoutParams.height = LayoutHelper.MATCH_PARENT;
        listViewContainer.setLayoutParams(layoutParams);

        ListView listView = new ListView(this);
        drawerLayoutAdapter = new DrawerLayoutAdapter(this);
        listView.setAdapter(drawerLayoutAdapter);
        listView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setVerticalScrollBarEnabled(false);
        listViewContainer.addView(listView,
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Bundle args = new Bundle();
                switch (position) {
                    case 2:
                        // Group Chat
                        presentFragment(new GroupCreateActivity());
                        drawerLayoutContainer.closeDrawer(false);
                        break;
                    case 3:
                        // Secret Chat
                        args.putBoolean("onlyUsers", true);
                        args.putBoolean("destroyAfterSelect", true);
                        args.putBoolean("createSecretChat", true);
                        presentFragment(new ContactsActivity(args));
                        drawerLayoutContainer.closeDrawer(false);
                        break;
                    case 4:
                        // Broadcast
                        args.putBoolean("broadcast", true);
                        presentFragment(new GroupCreateActivity(args));
                        drawerLayoutContainer.closeDrawer(false);
                        break;
                    case 6:
                        // Contacts
                        presentFragment(new ContactsActivity(null));
                        drawerLayoutContainer.closeDrawer(false);
                        break;
                    case 7:
                        // Invite Friends
                        try {
                            Intent intent = new Intent(Intent.ACTION_SEND);
                            intent.setType("text/plain");
                            intent.putExtra(Intent.EXTRA_TEXT,
                                ContactsController.getInstance().getInviteText());
                            startActivityForResult(Intent.createChooser(intent,
                                LocaleController.getString("InviteFriends",
                                    R.string.InviteFriends)), 500);
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                        drawerLayoutContainer.closeDrawer(false);
                        break;
                    case 8:
                        // Settings
                        presentFragment(new SettingsActivity());
                        drawerLayoutContainer.closeDrawer(false);
                        break;
                    case 9:
                        // FAQ
                        try {
                            Intent pickIntent = new Intent(Intent.ACTION_VIEW,
                                Uri.parse(LocaleController.getString("TelegramFaqUrl",
                                    R.string.TelegramFaqUrl)));
                            startActivityForResult(pickIntent, 500);
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                        drawerLayoutContainer.closeDrawer(false);
                        break;
                }
            }
        });

        DrawerPlayerView drawerPlayerView = new DrawerPlayerView(this, listView);
        listViewContainer.addView(drawerPlayerView,
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 51, Gravity.LEFT | Gravity.BOTTOM));
        drawerPlayerView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actionBarLayout.presentFragment(new AudioPlayerActivity());
                drawerLayoutContainer.closeDrawer(false);
            }
        });
    }

    private void addPasscodeView() {
        passcodeView = new PasscodeView(this);
        drawerLayoutContainer.addView(passcodeView);
        FrameLayout.LayoutParams layoutParams1 =
            (FrameLayout.LayoutParams) passcodeView.getLayoutParams();
        layoutParams1.width = LayoutHelper.MATCH_PARENT;
        layoutParams1.height = LayoutHelper.MATCH_PARENT;
        passcodeView.setLayoutParams(layoutParams1);
    }

    private void maybeAddFragments(Bundle savedInstanceState) {
        if (!actionBarLayout.fragmentsStack.isEmpty()) {
            boolean allowOpen = true;
            if (actionBarLayout.fragmentsStack.size() == 1 &&
                actionBarLayout.fragmentsStack.get(0) instanceof LoginActivity) {
                allowOpen = false;
            }
            drawerLayoutContainer.setAllowOpenDrawer(allowOpen, false /* animated */);
            return;
        }

        if (!UserConfig.isClientActivated()) {
            actionBarLayout.addFragmentToStack(new LoginActivity());
            drawerLayoutContainer.setAllowOpenDrawer(false /* allowOpen */, false /* animated */);
        } else {
            actionBarLayout.addFragmentToStack(new DialogsActivity(null));
            drawerLayoutContainer.setAllowOpenDrawer(true, false);
        }

        restoreSavedFragment(savedInstanceState);
    }

    private void restoreSavedFragment(Bundle savedInstanceState) {
        try {
            if (savedInstanceState != null) {
                String fragmentName = savedInstanceState.getString("fragment");
                if (fragmentName != null) {
                    Bundle args = savedInstanceState.getBundle("args");
                    switch (fragmentName) {
                        case "chat":
                            if (args != null) {
                                ChatActivity chat = new ChatActivity(args);
                                if (actionBarLayout.addFragmentToStack(chat)) {
                                    chat.restoreSelfArgs(savedInstanceState);
                                }
                            }
                            break;
                        case "settings": {
                            SettingsActivity settings = new SettingsActivity();
                            actionBarLayout.addFragmentToStack(settings);
                            settings.restoreSelfArgs(savedInstanceState);
                            break;
                        }
                        case "group":
                            if (args != null) {
                                GroupCreateFinalActivity group = new GroupCreateFinalActivity(args);
                                if (actionBarLayout.addFragmentToStack(group)) {
                                    group.restoreSelfArgs(savedInstanceState);
                                }
                            }
                            break;
                        case "chat_profile":
                            if (args != null) {
                                ProfileActivity profile = new ProfileActivity(args);
                                if (actionBarLayout.addFragmentToStack(profile)) {
                                    profile.restoreSelfArgs(savedInstanceState);
                                }
                            }
                            break;
                        case "wallpapers": {
                            WallpapersActivity settings = new WallpapersActivity();
                            actionBarLayout.addFragmentToStack(settings);
                            settings.restoreSelfArgs(savedInstanceState);
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    private void showPasscodeActivity() {
        if (passcodeView == null) {
            return;
        }
        UserConfig.appLocked = true;
        if (PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().closePhoto(false /* animated */, true /* fromEditMode */);
        }
        passcodeView.onShow();
        UserConfig.isWaitingForPasscodeEnter = true;
        drawerLayoutContainer.setAllowOpenDrawer(false /* allowOpen */, false /* animated */);
        passcodeView.setDelegate(new PasscodeView.PasscodeViewDelegate() {
            @Override
            public void didAcceptedPassword() {
                UserConfig.isWaitingForPasscodeEnter = false;
                if (passcodeSaveIntent != null) {
                    handleIntent(passcodeSaveIntent, passcodeSaveIntentIsNew,
                        passcodeSaveIntentIsRestored, true /* isFromPassword */);
                    passcodeSaveIntent = null;
                }
                drawerLayoutContainer.setAllowOpenDrawer(true, false);
                actionBarLayout.showLastFragment();
            }
        });
    }

    private void handleSendIntent(Intent intent) {
        boolean error = false;
        String type = intent.getType();
        switch (type) {
            case ContactsContract.Contacts.CONTENT_VCARD_TYPE:
                Uri vCardUri = (Uri) intent.getExtras().get(Intent.EXTRA_STREAM);
                contactsToSend = AndroidUtilities.getContactsToSend(getContentResolver(), vCardUri);
                if (contactsToSend == null) {
                    error = true;
                }
                break;

            case "text/plain":
            case "message/rfc822":
                String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (text == null) {
                    text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT).toString();
                }
                String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
                if (text != null && text.length() != 0) {
                    if ((text.startsWith("http://") || text.startsWith("https://")) &&
                        subject != null && subject.length() != 0) {
                        text = subject + "\n" + text;
                    }
                    sendingText = text;
                } else {
                    error = true;
                }
                break;
        }

        Parcelable parcelable = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (parcelable != null) {
            String path;
            if (!(parcelable instanceof Uri)) {
                parcelable = Uri.parse(parcelable.toString());
            }
            Uri uri = (Uri) parcelable;
            if (uri != null && (type != null && type.startsWith("image/") ||
                uri.toString().toLowerCase().endsWith(".jpg"))) {
                photoPathsArray = new ArrayList<>();
                photoPathsArray.add(uri);
            } else {
                path = AndroidUtilities.getPath(uri);
                if (path != null) {
                    if (path.startsWith("file:")) {
                        path = path.replace("file://", "");
                    }
                    if (type != null && type.startsWith("video/")) {
                        videoPath = path;
                    } else {
                        documentsPathsArray = new ArrayList<>();
                        documentsOriginalPathsArray = new ArrayList<>();
                        documentsPathsArray.add(path);
                        documentsOriginalPathsArray.add(uri.toString());
                    }
                } else {
                    documentsUrisArray = new ArrayList<>();
                    documentsUrisArray.add(uri);
                    documentsMimeType = type;
                }
            }
        } else if (sendingText == null) {
            error = true;
        }

        if (error) {
            Toast.makeText(this, "Unsupported content", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleSendMultipleIntent(Intent intent) {
        boolean error = false;
        try {
            ArrayList<Parcelable> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            String type = intent.getType();
            if (uris != null) {
                if (type != null && type.startsWith("image/")) {
                    for (Parcelable parcelable : uris) {
                        if (!(parcelable instanceof Uri)) {
                            parcelable = Uri.parse(parcelable.toString());
                        }
                        Uri uri = (Uri) parcelable;
                        if (photoPathsArray == null) {
                            photoPathsArray = new ArrayList<>();
                        }
                        photoPathsArray.add(uri);
                    }
                } else {
                    for (Parcelable parcelable : uris) {
                        if (!(parcelable instanceof Uri)) {
                            parcelable = Uri.parse(parcelable.toString());
                        }
                        String path = AndroidUtilities.getPath((Uri) parcelable);
                        String originalPath = parcelable.toString();
                        if (originalPath == null) {
                            originalPath = path;
                        }
                        if (path != null) {
                            if (path.startsWith("file:")) {
                                path = path.replace("file://", "");
                            }
                            if (documentsPathsArray == null) {
                                documentsPathsArray = new ArrayList<>();
                                documentsOriginalPathsArray = new ArrayList<>();
                            }
                            documentsPathsArray.add(path);
                            documentsOriginalPathsArray.add(originalPath);
                        }
                    }
                }
            } else {
                error = true;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            error = true;
        }
        if (error) {
            Toast.makeText(this, "Unsupported content", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleIntent(Intent intent, boolean isNew, boolean isRestored,
                              boolean fromPassword) {
        if (!fromPassword &&
            (AndroidUtilities.needShowPasscode(true) || UserConfig.isWaitingForPasscodeEnter)) {
            showPasscodeActivity();
            passcodeSaveIntent = intent;
            passcodeSaveIntentIsNew = isNew;
            passcodeSaveIntentIsRestored = isRestored;
            UserConfig.saveConfig(false /* withFile */);
            return;
        }

        boolean pushOpened = false;
        int flags = intent.getFlags();
        if (UserConfig.isClientActivated() &&
            (flags & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) {
            String action = intent.getAction();
            if (intent != null && action != null && !isRestored) {
                switch (action) {
                    case Intent.ACTION_SEND:
                        handleSendIntent(intent);
                        break;
                    case Intent.ACTION_SEND_MULTIPLE:
                        handleSendMultipleIntent(intent);
                        break;
                    case "org.telegram.messenger.OPEN_ACCOUNT":
                        actionBarLayout.presentFragment(new SettingsActivity(), false, true, true);
                        drawerLayoutContainer.setAllowOpenDrawer(true, false);
                        pushOpened = true;
                        break;
                    case "com.tmessages.openchat":
                        int chatId = intent.getIntExtra("chatId", 0);
                        int userId = intent.getIntExtra("userId", 0);
                        int encId = intent.getIntExtra("encId", 0);
                        if (chatId != 0 || userId != 0 || encId != 0) {
                            Bundle args = new Bundle();
                            args.putInt("user_id", userId);
                            args.putInt("chat_id", chatId);
                            args.putInt("enc_id", encId);
                            ChatActivity fragment = new ChatActivity(args);
                            pushOpened = actionBarLayout.presentFragment(fragment,
                                false, true, true);
                            NotificationCenter.getInstance().postNotificationName(
                                NotificationCenter.closeChats);
                        } else {
                            actionBarLayout.removeAllFragments();
                            pushOpened = false;
                            isNew = false;
                        }
                        break;
                    case "com.tmessages.openplayer":
                        for (int a = 0; a < actionBarLayout.fragmentsStack.size(); a++) {
                            BaseFragment fragment = actionBarLayout.fragmentsStack.get(a);
                            if (fragment instanceof AudioPlayerActivity) {
                                actionBarLayout.removeFragmentFromStack(fragment);
                                break;
                            }
                        }
                        drawerLayoutContainer.setAllowOpenDrawer(true, false);
                        actionBarLayout.presentFragment(new AudioPlayerActivity(), false, true,
                            true);
                        pushOpened = true;
                        break;
                }
            }
        }

        if (videoPath != null || photoPathsArray != null || sendingText != null ||
            documentsPathsArray != null || contactsToSend != null || documentsUrisArray != null) {
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
            Bundle args = new Bundle();
            args.putBoolean("onlySelect", true);
            args.putString("selectAlertString",
                LocaleController.getString("SendMessagesTo", R.string.SendMessagesTo));
            args.putString("selectAlertStringGroup",
                LocaleController.getString("SendMessagesToGroup", R.string.SendMessagesToGroup));
            DialogsActivity fragment = new DialogsActivity(args);
            fragment.setDelegate(this);
            boolean removeLast = actionBarLayout.fragmentsStack.size() > 1 &&
                actionBarLayout.fragmentsStack.get(actionBarLayout.fragmentsStack.size() - 1)
                    instanceof DialogsActivity;
            actionBarLayout.presentFragment(fragment, removeLast, true, true);
            pushOpened = true;
            if (PhotoViewer.getInstance().isVisible()) {
                PhotoViewer.getInstance().closePhoto(false, true);
            }
            drawerLayoutContainer.setAllowOpenDrawer(true, false);
        }

        if (!pushOpened && !isNew) {
            if (actionBarLayout.fragmentsStack.isEmpty()) {
                if (!UserConfig.isClientActivated()) {
                    actionBarLayout.addFragmentToStack(new LoginActivity());
                    drawerLayoutContainer.setAllowOpenDrawer(false, false);
                } else {
                    actionBarLayout.addFragmentToStack(new DialogsActivity(null));
                    drawerLayoutContainer.setAllowOpenDrawer(true, false);
                }
            }
            actionBarLayout.showLastFragment();
        }
        intent.setAction(null);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent, true /* isNew */, false /* isRestored */, false /* isFromPassword */);
    }

    @Override
    public void didSelectDialog(DialogsActivity messageFragment, long dialog_id, boolean param) {
        if (dialog_id != 0) {
            int lower_part = (int) dialog_id;
            int high_id = (int) (dialog_id >> 32);

            Bundle args = new Bundle();
            args.putBoolean("scrollToTopOnResume", true);
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
            if (lower_part != 0) {
                if (high_id == 1) {
                    args.putInt("chat_id", lower_part);
                } else {
                    if (lower_part > 0) {
                        args.putInt("user_id", lower_part);
                    } else if (lower_part < 0) {
                        args.putInt("chat_id", -lower_part);
                    }
                }
            } else {
                args.putInt("enc_id", high_id);
            }
            ChatActivity fragment = new ChatActivity(args);

            if (videoPath != null) {
                if (android.os.Build.VERSION.SDK_INT >= 16) {
                    actionBarLayout.addFragmentToStack(fragment,
                        actionBarLayout.fragmentsStack.size() - 1);

                    if (!fragment.openVideoEditor(videoPath, true, false)) {
                        messageFragment.finishFragment(true);
                    }
                } else {
                    actionBarLayout.presentFragment(fragment, true);
                    SendMessagesHelper.prepareSendingVideo(videoPath,
                        0, 0, 0, 0, null, dialog_id, null);
                }
            } else {
                actionBarLayout.presentFragment(fragment, true);

                if (sendingText != null) {
                    SendMessagesHelper.prepareSendingText(sendingText, dialog_id);
                }

                if (photoPathsArray != null) {
                    SendMessagesHelper.prepareSendingPhotos(
                        null, photoPathsArray, dialog_id, null, null);
                }
                if (documentsPathsArray != null || documentsUrisArray != null) {
                    SendMessagesHelper.prepareSendingDocuments(documentsPathsArray,
                        documentsOriginalPathsArray, documentsUrisArray, documentsMimeType,
                        dialog_id, null);
                }
                if (contactsToSend != null && !contactsToSend.isEmpty()) {
                    for (TLRPC.User user : contactsToSend) {
                        SendMessagesHelper.getInstance().sendMessage(user, dialog_id, null);
                    }
                }
            }

            photoPathsArray = null;
            videoPath = null;
            sendingText = null;
            documentsPathsArray = null;
            documentsOriginalPathsArray = null;
            contactsToSend = null;
        }
    }

    private void onFinish() {
        if (finished) {
            return;
        }
        finished = true;
        if (lockRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(lockRunnable);
            lockRunnable = null;
        }
        NotificationCenter.getInstance().removeObserver(this,
            NotificationCenter.appDidLogout);
        NotificationCenter.getInstance().removeObserver(this,
            NotificationCenter.mainUserInfoChanged);
        NotificationCenter.getInstance().removeObserver(this,
            NotificationCenter.closeOtherAppActivities);
        NotificationCenter.getInstance().removeObserver(this,
            NotificationCenter.didUpdatedConnectionState);
        if (Build.VERSION.SDK_INT < 14) {
            NotificationCenter.getInstance().removeObserver(this,
                NotificationCenter.screenStateChanged);
        }
    }

    public void presentFragment(BaseFragment fragment) {
        actionBarLayout.presentFragment(fragment);
    }

    public boolean presentFragment(final BaseFragment fragment, final boolean removeLast,
                                   boolean forceWithoutAnimation) {
        return actionBarLayout.presentFragment(fragment, removeLast, forceWithoutAnimation, true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (UserConfig.passcodeHash.length() != 0 && UserConfig.lastPauseTime != 0) {
            UserConfig.lastPauseTime = 0;
            UserConfig.saveConfig(false);
        }
        super.onActivityResult(requestCode, resultCode, data);
        if (actionBarLayout.fragmentsStack.size() != 0) {
            BaseFragment fragment = actionBarLayout.fragmentsStack.get(
                actionBarLayout.fragmentsStack.size() - 1);
            fragment.onActivityResultFragment(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        onPasscodePause();
        actionBarLayout.onPause();
        ApplicationLoader.mainInterfacePaused = true;
        ConnectionsManager.getInstance().setAppPaused(true, false);
    }

    @Override
    protected void onDestroy() {
        PhotoViewer.getInstance().destroyPhotoViewer();
        SecretPhotoViewer.getInstance().destroyPhotoViewer();
        try {
            if (visibleDialog != null) {
                visibleDialog.dismiss();
                visibleDialog = null;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        super.onDestroy();
        onFinish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        onPasscodeResume();
        if (passcodeView.getVisibility() != View.VISIBLE) {
            actionBarLayout.onResume();
        } else {
            passcodeView.onResume();
        }
        ApplicationLoader.mainInterfacePaused = false;
        ConnectionsManager.getInstance().setAppPaused(false, false);
        updateCurrentConnectionState();
        if (PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().onResume();
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        AndroidUtilities.checkDisplaySize();
        super.onConfigurationChanged(newConfig);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.appDidLogout) {
            if (drawerLayoutAdapter != null) {
                drawerLayoutAdapter.notifyDataSetChanged();
            }
            for (BaseFragment fragment : actionBarLayout.fragmentsStack) {
                fragment.onFragmentDestroy();
            }
            actionBarLayout.fragmentsStack.clear();
            onFinish();
            finish();
        } else if (id == NotificationCenter.closeOtherAppActivities) {
            if (args[0] != this) {
                onFinish();
                finish();
            }
        } else if (id == NotificationCenter.didUpdatedConnectionState) {
            int state = (Integer) args[0];
            if (currentConnectionState != state) {
                FileLog.e("tmessages", "switch to state " + state);
                currentConnectionState = state;
                updateCurrentConnectionState();
            }
        } else if (id == NotificationCenter.mainUserInfoChanged) {
            drawerLayoutAdapter.notifyDataSetChanged();
        } else if (id == NotificationCenter.screenStateChanged) {
            if (!ApplicationLoader.mainInterfacePaused) {
                if (!ApplicationLoader.isScreenOn) {
                    onPasscodePause();
                } else {
                    onPasscodeResume();
                }
            }
        }
    }

    private void onPasscodePause() {
        if (lockRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(lockRunnable);
            lockRunnable = null;
        }
        if (UserConfig.passcodeHash.length() != 0) {
            UserConfig.lastPauseTime = ConnectionsManager.getInstance().getCurrentTime();
            lockRunnable = new Runnable() {
                @Override
                public void run() {
                    if (lockRunnable == this) {
                        if (AndroidUtilities.needShowPasscode(true)) {
                            FileLog.e("tmessages", "lock app");
                            showPasscodeActivity();
                        } else {
                            FileLog.e("tmessages", "didn't pass lock check");
                        }
                        lockRunnable = null;
                    }
                }
            };
            if (UserConfig.appLocked) {
                AndroidUtilities.runOnUIThread(lockRunnable, 1000);
            } else if (UserConfig.autoLockIn != 0) {
                AndroidUtilities.runOnUIThread(lockRunnable,
                    (long) UserConfig.autoLockIn * 1000 + 1000);
            }
        } else {
            UserConfig.lastPauseTime = 0;
        }
        UserConfig.saveConfig(false);
    }

    private void onPasscodeResume() {
        if (lockRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(lockRunnable);
            lockRunnable = null;
        }
        if (AndroidUtilities.needShowPasscode(true)) {
            showPasscodeActivity();
        }
        if (UserConfig.lastPauseTime != 0) {
            UserConfig.lastPauseTime = 0;
            UserConfig.saveConfig(false);
        }
    }

    private void updateCurrentConnectionState() {
        String text = null;
        if (currentConnectionState == 1) {
            text = LocaleController.getString("WaitingForNetwork", R.string.WaitingForNetwork);
        } else if (currentConnectionState == 2) {
            text = LocaleController.getString("Connecting", R.string.Connecting);
        } else if (currentConnectionState == 3) {
            text = LocaleController.getString("Updating", R.string.Updating);
        }
        actionBarLayout.setTitleOverlayText(text);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        try {
            super.onSaveInstanceState(outState);
            BaseFragment lastFragment = null;
            if (!actionBarLayout.fragmentsStack.isEmpty()) {
                lastFragment = actionBarLayout.fragmentsStack.get(
                    actionBarLayout.fragmentsStack.size() - 1);
            }

            if (lastFragment != null) {
                Bundle args = lastFragment.getArguments();
                if (lastFragment instanceof ChatActivity && args != null) {
                    outState.putBundle("args", args);
                    outState.putString("fragment", "chat");
                } else if (lastFragment instanceof SettingsActivity) {
                    outState.putString("fragment", "settings");
                } else if (lastFragment instanceof GroupCreateFinalActivity && args != null) {
                    outState.putBundle("args", args);
                    outState.putString("fragment", "group");
                } else if (lastFragment instanceof WallpapersActivity) {
                    outState.putString("fragment", "wallpapers");
                } else if (lastFragment instanceof ProfileActivity &&
                    ((ProfileActivity) lastFragment).isChat() && args != null) {
                    outState.putBundle("args", args);
                    outState.putString("fragment", "chat_profile");
                }
                lastFragment.saveSelfArgs(outState);
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    @Override
    public void onBackPressed() {
        if (passcodeView.getVisibility() == View.VISIBLE) {
            finish();
            return;
        }
        if (PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().closePhoto(true, false);
        } else if (drawerLayoutContainer.isDrawerOpened()) {
            drawerLayoutContainer.closeDrawer(false);
        } else {
            actionBarLayout.onBackPressed();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        actionBarLayout.onLowMemory();
    }

    @Override
    public void onActionModeStarted(ActionMode mode) {
        super.onActionModeStarted(mode);
        actionBarLayout.onActionModeStarted(mode);
    }

    @Override
    public void onActionModeFinished(ActionMode mode) {
        super.onActionModeFinished(mode);
        actionBarLayout.onActionModeFinished(mode);
    }

    @Override
    public boolean onPreIme() {
        if (PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().closePhoto(true, false);
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (actionBarLayout.fragmentsStack.size() == 1) {
                if (!drawerLayoutContainer.isDrawerOpened()) {
                    if (getCurrentFocus() != null) {
                        AndroidUtilities.hideKeyboard(getCurrentFocus());
                    }
                    drawerLayoutContainer.openDrawer(false);
                } else {
                    drawerLayoutContainer.closeDrawer(false);
                }
            } else {
                actionBarLayout.onKeyUp(keyCode, event);
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean needPresentFragment(BaseFragment fragment, boolean removeLast,
                                       boolean forceWithoutAnimation, ActionBarLayout layout) {
        drawerLayoutContainer.setAllowOpenDrawer(
            !(fragment instanceof LoginActivity || fragment instanceof CountrySelectActivity),
            false);
        return true;
    }

    @Override
    public boolean needAddFragmentToStack(BaseFragment fragment, ActionBarLayout layout) {
        drawerLayoutContainer.setAllowOpenDrawer(
            !(fragment instanceof LoginActivity || fragment instanceof CountrySelectActivity),
            false);
        return true;
    }

    @Override
    public boolean needCloseLastFragment(ActionBarLayout layout) {
        if (layout.fragmentsStack.size() <= 1) {
            onFinish();
            finish();
            return false;
        }
        return true;
    }

    @Override
    public void onRebuildAllFragments(ActionBarLayout layout) {
        drawerLayoutAdapter.notifyDataSetChanged();
    }
}
