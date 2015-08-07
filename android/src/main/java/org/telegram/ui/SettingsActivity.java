/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.android.MediaController;
import org.telegram.android.MessageObject;
import org.telegram.android.MessagesController;
import org.telegram.android.MessagesStorage;
import org.telegram.android.NotificationCenter;
import org.telegram.android.UserObject;
import org.telegram.android.animationcompat.AnimatorListenerAdapterProxy;
import org.telegram.android.animationcompat.AnimatorSetProxy;
import org.telegram.android.animationcompat.ObjectAnimatorProxy;
import org.telegram.android.animationcompat.ViewProxy;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.SerializedData;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.phoneformat.PhoneFormat;
import org.telegram.ui.actionbar.ActionBar;
import org.telegram.ui.actionbar.ActionBarMenu;
import org.telegram.ui.actionbar.ActionBarMenuItem;
import org.telegram.ui.actionbar.BaseFragment;
import org.telegram.ui.adapters.BaseFragmentAdapter;
import org.telegram.ui.cells.EmptyCell;
import org.telegram.ui.cells.HeaderCell;
import org.telegram.ui.cells.ShadowSectionCell;
import org.telegram.ui.cells.TextCheckCell;
import org.telegram.ui.cells.TextDetailSettingsCell;
import org.telegram.ui.cells.TextInfoCell;
import org.telegram.ui.cells.TextSettingsCell;
import org.telegram.ui.components.AvatarDrawable;
import org.telegram.ui.components.AvatarUpdater;
import org.telegram.ui.components.BackupImageView;
import org.telegram.ui.components.LayoutHelper;
import org.telegram.ui.components.NumberPicker;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

public class SettingsActivity extends BaseFragment
    implements NotificationCenter.NotificationCenterDelegate, PhotoViewer.PhotoViewerProvider {

  private ListView listView;
  private ListAdapter listAdapter;
  private BackupImageView avatarImage;
  private TextView nameTextView;
  private TextView onlineTextView;
  private ImageView writeButton;
  private AnimatorSetProxy writeButtonAnimation;
  private AvatarUpdater avatarUpdater = new AvatarUpdater();

  private int overscrollRow;
  private int emptyRow;
  private int numberSectionRow;
  private int numberRow;
  private int usernameRow;
  private int settingsSectionRow;
  private int settingsSectionRow2;
  private int enableAnimationsRow;
  private int notificationRow;
  private int backgroundRow;
  private int languageRow;
  private int privacyRow;
  private int mediaDownloadSection;
  private int mediaDownloadSection2;
  private int mobileDownloadRow;
  private int wifiDownloadRow;
  private int roamingDownloadRow;
  private int saveToGalleryRow;
  private int messagesSectionRow;
  private int messagesSectionRow2;
  private int textSizeRow;
  private int stickersRow;
  private int sendByEnterRow;
  private int supportSectionRow;
  private int supportSectionRow2;
  private int askQuestionRow;
  private int telegramFaqRow;
  private int sendLogsRow;
  private int clearLogsRow;
  private int switchBackendButtonRow;
  private int versionRow;
  private int contactsSectionRow;
  private int contactsReimportRow;
  private int contactsSortRow;
  private int rowCount;

  private static final int edit_name = 1;
  private static final int logout = 2;

  private static class LinkMovementMethodMy extends LinkMovementMethod {
    @Override
    public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
      try {
        return super.onTouchEvent(widget, buffer, event);
      } catch (Exception e) {
        FileLog.e("tmessages", e);
      }
      return false;
    }
  }

  @Override
  public boolean onFragmentCreate() {
    super.onFragmentCreate();
    avatarUpdater.parentFragment = this;
    avatarUpdater.delegate =
        new AvatarUpdater.AvatarUpdaterDelegate() {
          @Override
          public void didUploadedPhoto(
              TLRPC.InputFile file, TLRPC.PhotoSize small, TLRPC.PhotoSize big) {
            TLRPC.TL_photos_uploadProfilePhoto req = new TLRPC.TL_photos_uploadProfilePhoto();
            req.caption = "";
            req.crop = new TLRPC.TL_inputPhotoCropAuto();
            req.file = file;
            req.geo_point = new TLRPC.TL_inputGeoPointEmpty();
            ConnectionsManager.getInstance()
                .performRpc(
                    req,
                    new RPCRequest.RPCRequestDelegate() {
                      @Override
                      public void run(TLObject response, TLRPC.TL_error error) {
                        if (error == null) {
                          TLRPC.User user =
                              MessagesController.getInstance()
                                  .getUser(UserConfig.getClientUserId());
                          if (user == null) {
                            user = UserConfig.getCurrentUser();
                            if (user == null) {
                              return;
                            }
                            MessagesController.getInstance().putUser(user, false);
                          } else {
                            UserConfig.setCurrentUser(user);
                          }
                          if (user == null) {
                            return;
                          }
                          TLRPC.TL_photos_photo photo = (TLRPC.TL_photos_photo) response;
                          ArrayList<TLRPC.PhotoSize> sizes = photo.photo.sizes;
                          TLRPC.PhotoSize smallSize =
                              FileLoader.getClosestPhotoSizeWithSize(sizes, 100);
                          TLRPC.PhotoSize bigSize =
                              FileLoader.getClosestPhotoSizeWithSize(sizes, 1000);
                          user.photo = new TLRPC.TL_userProfilePhoto();
                          user.photo.photo_id = photo.photo.id;
                          if (smallSize != null) {
                            user.photo.photo_small = smallSize.location;
                          }
                          if (bigSize != null) {
                            user.photo.photo_big = bigSize.location;
                          } else if (smallSize != null) {
                            user.photo.photo_small = smallSize.location;
                          }
                          MessagesStorage.getInstance().clearUserPhotos(user.id);
                          ArrayList<TLRPC.User> users = new ArrayList<>();
                          users.add(user);
                          MessagesStorage.getInstance().putUsersAndChats(users, null, false, true);
                          AndroidUtilities.runOnUIThread(
                              new Runnable() {
                                @Override
                                public void run() {
                                  NotificationCenter.getInstance()
                                      .postNotificationName(
                                          NotificationCenter.updateInterfaces,
                                          MessagesController.UPDATE_MASK_ALL);
                                  NotificationCenter.getInstance()
                                      .postNotificationName(NotificationCenter.mainUserInfoChanged);
                                  UserConfig.saveConfig(true);
                                }
                              });
                        }
                      }
                    });
          }
        };
    NotificationCenter.getInstance().addObserver(this, NotificationCenter.updateInterfaces);

    rowCount = 0;
    overscrollRow = rowCount++;
    emptyRow = rowCount++;
    numberSectionRow = rowCount++;
    numberRow = rowCount++;
    usernameRow = rowCount++;
    settingsSectionRow = rowCount++;
    settingsSectionRow2 = rowCount++;
    notificationRow = rowCount++;
    privacyRow = rowCount++;
    backgroundRow = rowCount++;
    languageRow = rowCount++;
    enableAnimationsRow = rowCount++;
    mediaDownloadSection = rowCount++;
    mediaDownloadSection2 = rowCount++;
    mobileDownloadRow = rowCount++;
    wifiDownloadRow = rowCount++;
    roamingDownloadRow = rowCount++;
    saveToGalleryRow = rowCount++;
    messagesSectionRow = rowCount++;
    messagesSectionRow2 = rowCount++;
    textSizeRow = rowCount++;
    stickersRow = rowCount++;
    sendByEnterRow = rowCount++;
    supportSectionRow = rowCount++;
    supportSectionRow2 = rowCount++;
    askQuestionRow = rowCount++;
    telegramFaqRow = rowCount++;
    if (BuildVars.DEBUG_VERSION) {
      sendLogsRow = rowCount++;
      clearLogsRow = rowCount++;
      switchBackendButtonRow = rowCount++;
    }
    versionRow = rowCount++;
    //contactsSectionRow = rowCount++;
    //contactsReimportRow = rowCount++;
    //contactsSortRow = rowCount++;

    MessagesController.getInstance().loadFullUser(UserConfig.getCurrentUser(), classGuid);

    return true;
  }

  @Override
  public void onFragmentDestroy() {
    super.onFragmentDestroy();
    if (avatarImage != null) {
      avatarImage.setImageDrawable(null);
    }
    MessagesController.getInstance().cancelLoadFullUser(UserConfig.getClientUserId());
    NotificationCenter.getInstance().removeObserver(this, NotificationCenter.updateInterfaces);
    avatarUpdater.clear();
  }

  @Override
  public boolean needAddActionBar() {
    return false;
  }

  @Override
  public View createView(Context context) {
    actionBar.setBackgroundColor(AvatarDrawable.getProfileBackColorForId(5));
    actionBar.setItemsBackground(AvatarDrawable.getButtonColorForId(5));
    actionBar.setBackButtonImage(R.drawable.ic_ab_back);
    actionBar.setExtraHeight(AndroidUtilities.dp(88), false);
    if (AndroidUtilities.isTablet()) {
      actionBar.setOccupyStatusBar(false);
    }
    actionBar.setActionBarMenuOnItemClick(
        new ActionBar.ActionBarMenuOnItemClick() {
          @Override
          public void onItemClick(int id) {
            if (id == -1) {
              finishFragment();
            } else if (id == edit_name) {
              presentFragment(new ChangeNameActivity());
            } else if (id == logout) {
              if (getParentActivity() == null) {
                return;
              }
              AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
              builder.setMessage(
                  LocaleController.getString("AreYouSureLogout", R.string.AreYouSureLogout));
              builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
              builder.setPositiveButton(
                  LocaleController.getString("OK", R.string.OK),
                  new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                      MessagesController.getInstance().performLogout(true);
                    }
                  });
              builder.setNegativeButton(
                  LocaleController.getString("Cancel", R.string.Cancel), null);
              showDialog(builder.create());
            }
          }
        });
    ActionBarMenu menu = actionBar.createMenu();
    ActionBarMenuItem item = menu.addItem(0, R.drawable.ic_ab_other);
    item.addSubItem(edit_name, LocaleController.getString("EditName", R.string.EditName), 0);
    item.addSubItem(logout, LocaleController.getString("LogOut", R.string.LogOut), 0);

    listAdapter = new ListAdapter(context);

    fragmentView =
        new FrameLayout(context) {
          @Override
          protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            if (child == listView) {
              boolean result = super.drawChild(canvas, child, drawingTime);
              if (parentLayout != null) {
                int actionBarHeight = 0;
                int childCount = getChildCount();
                for (int a = 0; a < childCount; a++) {
                  View view = getChildAt(a);
                  if (view == child) {
                    continue;
                  }
                  if (view instanceof ActionBar && view.getVisibility() == VISIBLE) {
                    if (((ActionBar) view).getCastShadows()) {
                      actionBarHeight = view.getMeasuredHeight();
                    }
                    break;
                  }
                }
                parentLayout.drawHeaderShadow(canvas, actionBarHeight);
              }
              return result;
            } else {
              return super.drawChild(canvas, child, drawingTime);
            }
          }
        };
    FrameLayout frameLayout = (FrameLayout) fragmentView;

    avatarImage = new BackupImageView(context);
    avatarImage.setRoundRadius(AndroidUtilities.dp(30));
    actionBar.addView(
        avatarImage,
        LayoutHelper.createFrame(
            60,
            60,
            (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM,
            LocaleController.isRTL ? 0 : 17,
            0,
            LocaleController.isRTL ? 17 : 0,
            22));
    avatarImage.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            TLRPC.User user =
                MessagesController.getInstance().getUser(UserConfig.getClientUserId());
            if (user.photo != null && user.photo.photo_big != null) {
              PhotoViewer.getInstance().setParentActivity(getParentActivity());
              PhotoViewer.getInstance().openPhoto(user.photo.photo_big, SettingsActivity.this);
            }
          }
        });

    nameTextView = new TextView(context);
    nameTextView.setTextColor(0xffffffff);
    nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
    nameTextView.setLines(1);
    nameTextView.setMaxLines(1);
    nameTextView.setSingleLine(true);
    nameTextView.setEllipsize(TextUtils.TruncateAt.END);
    nameTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
    nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
    actionBar.addView(
        nameTextView,
        LayoutHelper.createFrame(
            LayoutHelper.WRAP_CONTENT,
            LayoutHelper.WRAP_CONTENT,
            (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM,
            LocaleController.isRTL ? 16 : 97,
            0,
            LocaleController.isRTL ? 97 : 16,
            51));

    onlineTextView = new TextView(context);
    onlineTextView.setTextColor(AvatarDrawable.getProfileTextColorForId(5));
    onlineTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
    onlineTextView.setLines(1);
    onlineTextView.setMaxLines(1);
    onlineTextView.setSingleLine(true);
    onlineTextView.setEllipsize(TextUtils.TruncateAt.END);
    onlineTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
    actionBar.addView(
        onlineTextView,
        LayoutHelper.createFrame(
            LayoutHelper.WRAP_CONTENT,
            LayoutHelper.WRAP_CONTENT,
            (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM,
            LocaleController.isRTL ? 16 : 97,
            0,
            LocaleController.isRTL ? 97 : 16,
            30));

    listView = new ListView(context);
    listView.setDivider(null);
    listView.setDividerHeight(0);
    listView.setVerticalScrollBarEnabled(false);
    AndroidUtilities.setListViewEdgeEffectColor(
        listView, AvatarDrawable.getProfileBackColorForId(5));
    frameLayout.addView(
        listView,
        LayoutHelper.createFrame(
            LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
    listView.setAdapter(listAdapter);
    listView.setOnItemClickListener(
        new AdapterView.OnItemClickListener() {
          @Override
          public void onItemClick(AdapterView<?> adapterView, View view, final int i, long l) {
            if (i == textSizeRow) {
              if (getParentActivity() == null) {
                return;
              }
              AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
              builder.setTitle(LocaleController.getString("TextSize", R.string.TextSize));
              final NumberPicker numberPicker = new NumberPicker(getParentActivity());
              numberPicker.setMinValue(12);
              numberPicker.setMaxValue(30);
              numberPicker.setValue(MessagesController.getInstance().fontSize);
              builder.setView(numberPicker);
              builder.setNegativeButton(
                  LocaleController.getString("Done", R.string.Done),
                  new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                      SharedPreferences preferences =
                          ApplicationLoader.applicationContext.getSharedPreferences(
                              "mainconfig", Activity.MODE_PRIVATE);
                      SharedPreferences.Editor editor = preferences.edit();
                      editor.putInt("fons_size", numberPicker.getValue());
                      MessagesController.getInstance().fontSize = numberPicker.getValue();
                      editor.commit();
                      if (listView != null) {
                        listView.invalidateViews();
                      }
                    }
                  });
              showDialog(builder.create());
            } else if (i == enableAnimationsRow) {
              SharedPreferences preferences =
                  ApplicationLoader.applicationContext.getSharedPreferences(
                      "mainconfig", Activity.MODE_PRIVATE);
              boolean animations = preferences.getBoolean("view_animations", true);
              SharedPreferences.Editor editor = preferences.edit();
              editor.putBoolean("view_animations", !animations);
              editor.commit();
              if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(!animations);
              }
            } else if (i == notificationRow) {
              presentFragment(new NotificationsSettingsActivity());
            } else if (i == backgroundRow) {
              presentFragment(new WallpapersActivity());
            } else if (i == askQuestionRow) {
              if (getParentActivity() == null) {
                return;
              }
              final TextView message = new TextView(getParentActivity());
              message.setText(
                  Html.fromHtml(
                      LocaleController.getString("AskAQuestionInfo", R.string.AskAQuestionInfo)));
              message.setTextSize(18);
              message.setPadding(
                  AndroidUtilities.dp(8),
                  AndroidUtilities.dp(5),
                  AndroidUtilities.dp(8),
                  AndroidUtilities.dp(6));
              message.setMovementMethod(new LinkMovementMethodMy());

              AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
              builder.setView(message);
              builder.setPositiveButton(
                  LocaleController.getString("AskButton", R.string.AskButton),
                  new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                      performAskAQuestion();
                    }
                  });
              builder.setNegativeButton(
                  LocaleController.getString("Cancel", R.string.Cancel), null);
              showDialog(builder.create());
            } else if (i == sendLogsRow) {
              sendLogs();
            } else if (i == clearLogsRow) {
              FileLog.cleanupLogs();
            } else if (i == sendByEnterRow) {
              SharedPreferences preferences =
                  ApplicationLoader.applicationContext.getSharedPreferences(
                      "mainconfig", Activity.MODE_PRIVATE);
              boolean send = preferences.getBoolean("send_by_enter", false);
              SharedPreferences.Editor editor = preferences.edit();
              editor.putBoolean("send_by_enter", !send);
              editor.commit();
              if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(!send);
              }
            } else if (i == saveToGalleryRow) {
              MediaController.getInstance().toggleSaveToGallery();
              if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MediaController.getInstance().canSaveToGallery());
              }
            } else if (i == privacyRow) {
              presentFragment(new PrivacySettingsActivity());
            } else if (i == languageRow) {
              presentFragment(new LanguageSelectActivity());
            } else if (i == switchBackendButtonRow) {
              if (getParentActivity() == null) {
                return;
              }
              AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
              builder.setMessage(LocaleController.getString("AreYouSure", R.string.AreYouSure));
              builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
              builder.setPositiveButton(
                  LocaleController.getString("OK", R.string.OK),
                  new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                      ConnectionsManager.getInstance().switchBackend();
                    }
                  });
              builder.setNegativeButton(
                  LocaleController.getString("Cancel", R.string.Cancel), null);
              showDialog(builder.create());
            } else if (i == telegramFaqRow) {
              try {
                Intent pickIntent =
                    new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(
                            LocaleController.getString("TelegramFaqUrl", R.string.TelegramFaqUrl)));
                getParentActivity().startActivityForResult(pickIntent, 500);
              } catch (Exception e) {
                FileLog.e("tmessages", e);
              }
            } else if (i == contactsReimportRow) {

            } else if (i == contactsSortRow) {
              if (getParentActivity() == null) {
                return;
              }
              AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
              builder.setTitle(LocaleController.getString("SortBy", R.string.SortBy));
              builder.setItems(
                  new CharSequence[] {
                    LocaleController.getString("Default", R.string.Default),
                    LocaleController.getString("SortFirstName", R.string.SortFirstName),
                    LocaleController.getString("SortLastName", R.string.SortLastName)
                  },
                  new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                      SharedPreferences preferences =
                          ApplicationLoader.applicationContext.getSharedPreferences(
                              "mainconfig", Activity.MODE_PRIVATE);
                      SharedPreferences.Editor editor = preferences.edit();
                      editor.putInt("sortContactsBy", which);
                      editor.commit();
                      if (listView != null) {
                        listView.invalidateViews();
                      }
                    }
                  });
              builder.setNegativeButton(
                  LocaleController.getString("Cancel", R.string.Cancel), null);
              showDialog(builder.create());
            } else if (i == wifiDownloadRow || i == mobileDownloadRow || i == roamingDownloadRow) {
              if (getParentActivity() == null) {
                return;
              }
              AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());

              int mask = 0;
              if (i == mobileDownloadRow) {
                builder.setTitle(
                    LocaleController.getString(
                        "WhenUsingMobileData", R.string.WhenUsingMobileData));
                mask = MediaController.getInstance().mobileDataDownloadMask;
              } else if (i == wifiDownloadRow) {
                builder.setTitle(
                    LocaleController.getString(
                        "WhenConnectedOnWiFi", R.string.WhenConnectedOnWiFi));
                mask = MediaController.getInstance().wifiDownloadMask;
              } else if (i == roamingDownloadRow) {
                builder.setTitle(LocaleController.getString("WhenRoaming", R.string.WhenRoaming));
                mask = MediaController.getInstance().roamingDownloadMask;
              }
              builder.setMultiChoiceItems(
                  new CharSequence[] {
                    LocaleController.getString("AttachPhoto", R.string.AttachPhoto),
                    LocaleController.getString("AttachAudio", R.string.AttachAudio),
                    LocaleController.getString("AttachVideo", R.string.AttachVideo),
                    LocaleController.getString("AttachDocument", R.string.AttachDocument)
                  },
                  new boolean[] {
                    (mask & MediaController.AUTODOWNLOAD_MASK_PHOTO) != 0,
                    (mask & MediaController.AUTODOWNLOAD_MASK_AUDIO) != 0,
                    (mask & MediaController.AUTODOWNLOAD_MASK_VIDEO) != 0,
                    (mask & MediaController.AUTODOWNLOAD_MASK_DOCUMENT) != 0
                  },
                  new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                      int mask = 0;
                      SharedPreferences preferences =
                          ApplicationLoader.applicationContext.getSharedPreferences(
                              "mainconfig", Activity.MODE_PRIVATE);
                      SharedPreferences.Editor editor = preferences.edit();
                      if (i == mobileDownloadRow) {
                        mask = MediaController.getInstance().mobileDataDownloadMask;
                      } else if (i == wifiDownloadRow) {
                        mask = MediaController.getInstance().wifiDownloadMask;
                      } else if (i == roamingDownloadRow) {
                        mask = MediaController.getInstance().roamingDownloadMask;
                      }

                      int maskDiff = 0;
                      if (which == 0) {
                        maskDiff = MediaController.AUTODOWNLOAD_MASK_PHOTO;
                      } else if (which == 1) {
                        maskDiff = MediaController.AUTODOWNLOAD_MASK_AUDIO;
                      } else if (which == 2) {
                        maskDiff = MediaController.AUTODOWNLOAD_MASK_VIDEO;
                      } else if (which == 3) {
                        maskDiff = MediaController.AUTODOWNLOAD_MASK_DOCUMENT;
                      }

                      if (isChecked) {
                        mask |= maskDiff;
                      } else {
                        mask &= ~maskDiff;
                      }

                      if (i == mobileDownloadRow) {
                        editor.putInt("mobileDataDownloadMask", mask);
                        MediaController.getInstance().mobileDataDownloadMask = mask;
                      } else if (i == wifiDownloadRow) {
                        editor.putInt("wifiDownloadMask", mask);
                        MediaController.getInstance().wifiDownloadMask = mask;
                      } else if (i == roamingDownloadRow) {
                        editor.putInt("roamingDownloadMask", mask);
                        MediaController.getInstance().roamingDownloadMask = mask;
                      }
                      editor.commit();
                      if (listView != null) {
                        listView.invalidateViews();
                      }
                    }
                  });
              builder.setNegativeButton(LocaleController.getString("OK", R.string.OK), null);
              showDialog(builder.create());
            } else if (i == usernameRow) {
              presentFragment(new ChangeUsernameActivity());
            } else if (i == numberRow) {
              presentFragment(new ChangePhoneHelpActivity());
            } else if (i == stickersRow) {
              presentFragment(new StickersActivity());
            }
          }
        });

    frameLayout.addView(actionBar);

    writeButton = new ImageView(context);
    writeButton.setBackgroundResource(R.drawable.floating_user_states);
    writeButton.setImageResource(R.drawable.floating_camera);
    writeButton.setScaleType(ImageView.ScaleType.CENTER);
    if (Build.VERSION.SDK_INT >= 21) {
      StateListAnimator animator = new StateListAnimator();
      animator.addState(
          new int[] {android.R.attr.state_pressed},
          ObjectAnimator.ofFloat(
                  writeButton, "translationZ", AndroidUtilities.dp(2), AndroidUtilities.dp(4))
              .setDuration(200));
      animator.addState(
          new int[] {},
          ObjectAnimator.ofFloat(
                  writeButton, "translationZ", AndroidUtilities.dp(4), AndroidUtilities.dp(2))
              .setDuration(200));
      writeButton.setStateListAnimator(animator);
      writeButton.setOutlineProvider(
          new ViewOutlineProvider() {
            @SuppressLint("NewApi")
            @Override
            public void getOutline(View view, Outline outline) {
              outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
            }
          });
    }
    frameLayout.addView(
        writeButton,
        LayoutHelper.createFrame(
            LayoutHelper.WRAP_CONTENT,
            LayoutHelper.WRAP_CONTENT,
            (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP,
            LocaleController.isRTL ? 16 : 0,
            0,
            LocaleController.isRTL ? 0 : 16,
            0));
    writeButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            if (getParentActivity() == null) {
              return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());

            CharSequence[] items;

            TLRPC.User user =
                MessagesController.getInstance().getUser(UserConfig.getClientUserId());
            if (user == null) {
              user = UserConfig.getCurrentUser();
            }
            if (user == null) {
              return;
            }
            boolean fullMenu = false;
            if (user.photo != null
                && user.photo.photo_big != null
                && !(user.photo instanceof TLRPC.TL_userProfilePhotoEmpty)) {
              items =
                  new CharSequence[] {
                    LocaleController.getString("FromCamera", R.string.FromCamera),
                    LocaleController.getString("FromGalley", R.string.FromGalley),
                    LocaleController.getString("DeletePhoto", R.string.DeletePhoto)
                  };
              fullMenu = true;
            } else {
              items =
                  new CharSequence[] {
                    LocaleController.getString("FromCamera", R.string.FromCamera),
                    LocaleController.getString("FromGalley", R.string.FromGalley)
                  };
            }

            final boolean full = fullMenu;
            builder.setItems(
                items,
                new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface dialogInterface, int i) {
                    if (i == 0) {
                      avatarUpdater.openCamera();
                    } else if (i == 1) {
                      avatarUpdater.openGallery();
                    } else if (i == 2) {
                      MessagesController.getInstance().deleteUserPhoto(null);
                    }
                  }
                });
            showDialog(builder.create());
          }
        });

    listView.setOnScrollListener(
        new AbsListView.OnScrollListener() {
          @Override
          public void onScrollStateChanged(AbsListView view, int scrollState) {}

          @Override
          public void onScroll(
              AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            if (totalItemCount == 0) {
              return;
            }
            int height = 0;
            View child = view.getChildAt(0);
            if (child != null) {
              if (firstVisibleItem == 0) {
                height = AndroidUtilities.dp(88) + (child.getTop() < 0 ? child.getTop() : 0);
              }
              if (actionBar.getExtraHeight() != height) {
                actionBar.setExtraHeight(height, true);
                needLayout();
              }
            }
          }
        });

    return fragmentView;
  }

  @Override
  protected void onDialogDismiss() {
    MediaController.getInstance().checkAutodownloadSettings();
  }

  @Override
  public void updatePhotoAtIndex(int index) {}

  @Override
  public PhotoViewer.PlaceProviderObject getPlaceForPhoto(
      MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
    if (fileLocation == null) {
      return null;
    }
    TLRPC.User user = MessagesController.getInstance().getUser(UserConfig.getClientUserId());
    if (user != null && user.photo != null && user.photo.photo_big != null) {
      TLRPC.FileLocation photoBig = user.photo.photo_big;
      if (photoBig.local_id == fileLocation.local_id
          && photoBig.volume_id == fileLocation.volume_id
          && photoBig.dc_id == fileLocation.dc_id) {
        int coords[] = new int[2];
        avatarImage.getLocationInWindow(coords);
        PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
        object.viewX = coords[0];
        object.viewY = coords[1] - AndroidUtilities.statusBarHeight;
        object.parentView = avatarImage;
        object.imageReceiver = avatarImage.getImageReceiver();
        object.user_id = UserConfig.getClientUserId();
        object.thumb = object.imageReceiver.getBitmap();
        object.size = -1;
        object.radius = avatarImage.getImageReceiver().getRoundRadius();
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
  public void willHidePhotoViewer() {
    avatarImage.getImageReceiver().setVisible(true, true);
  }

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

  public void performAskAQuestion() {
    final SharedPreferences preferences =
        ApplicationLoader.applicationContext.getSharedPreferences(
            "mainconfig", Activity.MODE_PRIVATE);
    int uid = preferences.getInt("support_id", 0);
    TLRPC.User supportUser = null;
    if (uid != 0) {
      supportUser = MessagesController.getInstance().getUser(uid);
      if (supportUser == null) {
        String userString = preferences.getString("support_user", null);
        if (userString != null) {
          try {
            byte[] datacentersBytes = Base64.decode(userString, Base64.DEFAULT);
            if (datacentersBytes != null) {
              SerializedData data = new SerializedData(datacentersBytes);
              supportUser = TLRPC.User.TLdeserialize(data, data.readInt32(false), false);
              if (supportUser != null && supportUser.id == 333000) {
                supportUser = null;
              }
              data.cleanup();
            }
          } catch (Exception e) {
            FileLog.e("tmessages", e);
            supportUser = null;
          }
        }
      }
    }
    if (supportUser == null) {
      final ProgressDialog progressDialog = new ProgressDialog(getParentActivity());
      progressDialog.setMessage(LocaleController.getString("Loading", R.string.Loading));
      progressDialog.setCanceledOnTouchOutside(false);
      progressDialog.setCancelable(false);
      progressDialog.show();
      TLRPC.TL_help_getSupport req = new TLRPC.TL_help_getSupport();
      ConnectionsManager.getInstance()
          .performRpc(
              req,
              new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                  if (error == null) {

                    final TLRPC.TL_help_support res = (TLRPC.TL_help_support) response;
                    AndroidUtilities.runOnUIThread(
                        new Runnable() {
                          @Override
                          public void run() {
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putInt("support_id", res.user.id);
                            SerializedData data = new SerializedData();
                            res.user.serializeToStream(data);
                            editor.putString(
                                "support_user",
                                Base64.encodeToString(data.toByteArray(), Base64.DEFAULT));
                            editor.commit();
                            data.cleanup();
                            try {
                              progressDialog.dismiss();
                            } catch (Exception e) {
                              FileLog.e("tmessages", e);
                            }
                            ArrayList<TLRPC.User> users = new ArrayList<>();
                            users.add(res.user);
                            MessagesStorage.getInstance().putUsersAndChats(users, null, true, true);
                            MessagesController.getInstance().putUser(res.user, false);
                            Bundle args = new Bundle();
                            args.putInt("user_id", res.user.id);
                            presentFragment(new ChatActivity(args));
                          }
                        });
                  } else {
                    AndroidUtilities.runOnUIThread(
                        new Runnable() {
                          @Override
                          public void run() {
                            try {
                              progressDialog.dismiss();
                            } catch (Exception e) {
                              FileLog.e("tmessages", e);
                            }
                          }
                        });
                  }
                }
              });
    } else {
      MessagesController.getInstance().putUser(supportUser, true);
      Bundle args = new Bundle();
      args.putInt("user_id", supportUser.id);
      presentFragment(new ChatActivity(args));
    }
  }

  @Override
  public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
    avatarUpdater.onActivityResult(requestCode, resultCode, data);
  }

  @Override
  public void saveSelfArgs(Bundle args) {
    if (avatarUpdater != null && avatarUpdater.currentPicturePath != null) {
      args.putString("path", avatarUpdater.currentPicturePath);
    }
  }

  @Override
  public void restoreSelfArgs(Bundle args) {
    if (avatarUpdater != null) {
      avatarUpdater.currentPicturePath = args.getString("path");
    }
  }

  @Override
  public void didReceivedNotification(int id, Object... args) {
    if (id == NotificationCenter.updateInterfaces) {
      int mask = (Integer) args[0];
      if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0
          || (mask & MessagesController.UPDATE_MASK_NAME) != 0) {
        updateUserData();
      }
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    if (listAdapter != null) {
      listAdapter.notifyDataSetChanged();
    }
    updateUserData();
    fixLayout();
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    fixLayout();
  }

  private void needLayout() {
    FrameLayout.LayoutParams layoutParams;
    if (listView != null) {
      layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
      layoutParams.topMargin =
          (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0)
              + ActionBar.getCurrentActionBarHeight();
      listView.setLayoutParams(layoutParams);
    }

    if (avatarImage != null) {
      float diff = actionBar.getExtraHeight() / (float) AndroidUtilities.dp(88);
      float diffm = 1.0f - diff;

      int avatarSize = 42 + (int) (18 * diff);
      int avatarX = 17 + (int) (47 * diffm);
      int avatarY =
          AndroidUtilities.dp(22)
              - (int)
                  ((AndroidUtilities.dp(22)
                          - (ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(42)) / 2)
                      * (1.0f - diff));
      int nameX = 97 + (int) (21 * diffm);
      int nameEndX = 16 + (int) (32 * diffm);
      int nameY = avatarY + AndroidUtilities.dp(29 - 13 * diffm);
      int statusY = avatarY + AndroidUtilities.dp(8 - 7 * diffm);
      float scale = 1.0f - 0.12f * diffm;

      layoutParams = (FrameLayout.LayoutParams) writeButton.getLayoutParams();
      layoutParams.topMargin =
          (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0)
              + ActionBar.getCurrentActionBarHeight()
              + actionBar.getExtraHeight()
              - AndroidUtilities.dp(29.5f);
      writeButton.setLayoutParams(layoutParams);

      //ViewProxy.setScaleX(writeButton, diff > 0.2f ? 1.0f : diff / 0.2f);
      //ViewProxy.setScaleY(writeButton, diff > 0.2f ? 1.0f : diff / 0.2f);
      //ViewProxy.setAlpha(writeButton, diff > 0.2f ? 1.0f : diff / 0.2f);
      final boolean setVisible = diff > 0.2f;
      boolean currentVisible = writeButton.getTag() == null;
      if (setVisible != currentVisible) {
        if (setVisible) {
          writeButton.setTag(null);
          writeButton.setVisibility(View.VISIBLE);
        } else {
          writeButton.setTag(0);
        }
        if (writeButtonAnimation != null) {
          AnimatorSetProxy old = writeButtonAnimation;
          writeButtonAnimation = null;
          old.cancel();
        }
        writeButtonAnimation = new AnimatorSetProxy();
        if (setVisible) {
          writeButtonAnimation.setInterpolator(new DecelerateInterpolator());
          writeButtonAnimation.playTogether(
              ObjectAnimatorProxy.ofFloat(writeButton, "scaleX", 1.0f),
              ObjectAnimatorProxy.ofFloat(writeButton, "scaleY", 1.0f),
              ObjectAnimatorProxy.ofFloat(writeButton, "alpha", 1.0f));
        } else {
          writeButtonAnimation.setInterpolator(new AccelerateInterpolator());
          writeButtonAnimation.playTogether(
              ObjectAnimatorProxy.ofFloat(writeButton, "scaleX", 0.2f),
              ObjectAnimatorProxy.ofFloat(writeButton, "scaleY", 0.2f),
              ObjectAnimatorProxy.ofFloat(writeButton, "alpha", 0.0f));
        }
        writeButtonAnimation.setDuration(150);
        writeButtonAnimation.addListener(
            new AnimatorListenerAdapterProxy() {
              @Override
              public void onAnimationEnd(Object animation) {
                if (writeButtonAnimation != null && writeButtonAnimation.equals(animation)) {
                  writeButton.clearAnimation();
                  writeButton.setVisibility(setVisible ? View.VISIBLE : View.GONE);
                  writeButtonAnimation = null;
                }
              }
            });
        writeButtonAnimation.start();
      }

      avatarImage.setRoundRadius(AndroidUtilities.dp(avatarSize / 2));
      layoutParams = (FrameLayout.LayoutParams) avatarImage.getLayoutParams();
      layoutParams.width = AndroidUtilities.dp(avatarSize);
      layoutParams.height = AndroidUtilities.dp(avatarSize);
      layoutParams.leftMargin = LocaleController.isRTL ? 0 : AndroidUtilities.dp(avatarX);
      layoutParams.rightMargin = LocaleController.isRTL ? AndroidUtilities.dp(avatarX) : 0;
      layoutParams.bottomMargin = avatarY;
      avatarImage.setLayoutParams(layoutParams);

      ViewProxy.setPivotX(nameTextView, 0);
      ViewProxy.setPivotY(nameTextView, 0);
      ViewProxy.setScaleX(nameTextView, scale);
      ViewProxy.setScaleY(nameTextView, scale);
      layoutParams = (FrameLayout.LayoutParams) nameTextView.getLayoutParams();
      layoutParams.leftMargin = AndroidUtilities.dp(LocaleController.isRTL ? nameEndX : nameX);
      layoutParams.rightMargin = AndroidUtilities.dp(LocaleController.isRTL ? nameX : nameEndX);
      layoutParams.bottomMargin = nameY;
      nameTextView.setLayoutParams(layoutParams);

      layoutParams = (FrameLayout.LayoutParams) onlineTextView.getLayoutParams();
      layoutParams.leftMargin = AndroidUtilities.dp(LocaleController.isRTL ? nameEndX : nameX);
      layoutParams.rightMargin = AndroidUtilities.dp(LocaleController.isRTL ? nameX : nameEndX);
      layoutParams.bottomMargin = statusY;
      onlineTextView.setLayoutParams(layoutParams);
    }
  }

  private void fixLayout() {
    if (fragmentView == null) {
      return;
    }
    fragmentView
        .getViewTreeObserver()
        .addOnPreDrawListener(
            new ViewTreeObserver.OnPreDrawListener() {
              @Override
              public boolean onPreDraw() {
                if (fragmentView != null) {
                  needLayout();
                  fragmentView.getViewTreeObserver().removeOnPreDrawListener(this);
                }
                return true;
              }
            });
  }

  private void updateUserData() {
    TLRPC.User user = MessagesController.getInstance().getUser(UserConfig.getClientUserId());
    TLRPC.FileLocation photo = null;
    TLRPC.FileLocation photoBig = null;
    if (user.photo != null) {
      photo = user.photo.photo_small;
      photoBig = user.photo.photo_big;
    }
    AvatarDrawable avatarDrawable = new AvatarDrawable(user, true);
    avatarDrawable.setColor(0xff5c98cd);
    if (avatarImage != null) {
      avatarImage.setImage(photo, "50_50", avatarDrawable);
      avatarImage
          .getImageReceiver()
          .setVisible(!PhotoViewer.getInstance().isShowingImage(photoBig), false);

      nameTextView.setText(UserObject.getUserName(user));
      onlineTextView.setText(LocaleController.getString("Online", R.string.Online));

      avatarImage
          .getImageReceiver()
          .setVisible(!PhotoViewer.getInstance().isShowingImage(photoBig), false);
    }
  }

  private void sendLogs() {
    try {
      ArrayList<Uri> uris = new ArrayList<>();
      File sdCard = ApplicationLoader.applicationContext.getExternalFilesDir(null);
      File dir = new File(sdCard.getAbsolutePath() + "/logs");
      File[] files = dir.listFiles();
      for (File file : files) {
        uris.add(Uri.fromFile(file));
      }

      if (uris.isEmpty()) {
        return;
      }
      Intent i = new Intent(Intent.ACTION_SEND_MULTIPLE);
      i.setType("message/rfc822");
      i.putExtra(Intent.EXTRA_EMAIL, new String[] {BuildVars.SEND_LOGS_EMAIL});
      i.putExtra(Intent.EXTRA_SUBJECT, "last logs");
      i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
      getParentActivity()
          .startActivityForResult(Intent.createChooser(i, "Select email application."), 500);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private class ListAdapter extends BaseFragmentAdapter {
    private Context mContext;

    public ListAdapter(Context context) {
      mContext = context;
    }

    @Override
    public boolean areAllItemsEnabled() {
      return false;
    }

    @Override
    public boolean isEnabled(int i) {
      return i == textSizeRow
          || i == enableAnimationsRow
          || i == notificationRow
          || i == backgroundRow
          || i == numberRow
          || i == askQuestionRow
          || i == sendLogsRow
          || i == sendByEnterRow
          || i == privacyRow
          || i == wifiDownloadRow
          || i == mobileDownloadRow
          || i == clearLogsRow
          || i == roamingDownloadRow
          || i == languageRow
          || i == usernameRow
          || i == switchBackendButtonRow
          || i == telegramFaqRow
          || i == contactsSortRow
          || i == contactsReimportRow
          || i == saveToGalleryRow
          || i == stickersRow;
    }

    @Override
    public int getCount() {
      return rowCount;
    }

    @Override
    public Object getItem(int i) {
      return null;
    }

    @Override
    public long getItemId(int i) {
      return i;
    }

    @Override
    public boolean hasStableIds() {
      return false;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
      int type = getItemViewType(i);
      if (type == 0) {
        if (view == null) {
          view = new EmptyCell(mContext);
        }
        if (i == overscrollRow) {
          ((EmptyCell) view).setHeight(AndroidUtilities.dp(88));
        } else {
          ((EmptyCell) view).setHeight(AndroidUtilities.dp(16));
        }
      } else if (type == 1) {
        if (view == null) {
          view = new ShadowSectionCell(mContext);
        }
      } else if (type == 2) {
        if (view == null) {
          view = new TextSettingsCell(mContext);
        }
        TextSettingsCell textCell = (TextSettingsCell) view;
        if (i == textSizeRow) {
          SharedPreferences preferences =
              ApplicationLoader.applicationContext.getSharedPreferences(
                  "mainconfig", Activity.MODE_PRIVATE);
          int size = preferences.getInt("fons_size", AndroidUtilities.isTablet() ? 18 : 16);
          textCell.setTextAndValue(
              LocaleController.getString("TextSize", R.string.TextSize),
              String.format("%d", size),
              true);
        } else if (i == languageRow) {
          textCell.setTextAndValue(
              LocaleController.getString("Language", R.string.Language),
              LocaleController.getCurrentLanguageName(),
              true);
        } else if (i == contactsSortRow) {
          String value;
          SharedPreferences preferences =
              ApplicationLoader.applicationContext.getSharedPreferences(
                  "mainconfig", Activity.MODE_PRIVATE);
          int sort = preferences.getInt("sortContactsBy", 0);
          if (sort == 0) {
            value = LocaleController.getString("Default", R.string.Default);
          } else if (sort == 1) {
            value = LocaleController.getString("FirstName", R.string.SortFirstName);
          } else {
            value = LocaleController.getString("LastName", R.string.SortLastName);
          }
          textCell.setTextAndValue(
              LocaleController.getString("SortBy", R.string.SortBy), value, true);
        } else if (i == notificationRow) {
          textCell.setText(
              LocaleController.getString("NotificationsAndSounds", R.string.NotificationsAndSounds),
              true);
        } else if (i == backgroundRow) {
          textCell.setText(
              LocaleController.getString("ChatBackground", R.string.ChatBackground), true);
        } else if (i == sendLogsRow) {
          textCell.setText("Send Logs", true);
        } else if (i == clearLogsRow) {
          textCell.setText("Clear Logs", true);
        } else if (i == askQuestionRow) {
          textCell.setText(LocaleController.getString("AskAQuestion", R.string.AskAQuestion), true);
        } else if (i == privacyRow) {
          textCell.setText(
              LocaleController.getString("PrivacySettings", R.string.PrivacySettings), true);
        } else if (i == switchBackendButtonRow) {
          textCell.setText("Switch Backend", true);
        } else if (i == telegramFaqRow) {
          textCell.setText(LocaleController.getString("TelegramFAQ", R.string.TelegramFaq), true);
        } else if (i == contactsReimportRow) {
          textCell.setText(
              LocaleController.getString("ImportContacts", R.string.ImportContacts), true);
        } else if (i == stickersRow) {
          textCell.setText(LocaleController.getString("Stickers", R.string.Stickers), true);
        }
      } else if (type == 3) {
        if (view == null) {
          view = new TextCheckCell(mContext);
        }
        TextCheckCell textCell = (TextCheckCell) view;

        SharedPreferences preferences =
            ApplicationLoader.applicationContext.getSharedPreferences(
                "mainconfig", Activity.MODE_PRIVATE);
        if (i == enableAnimationsRow) {
          textCell.setTextAndCheck(
              LocaleController.getString("EnableAnimations", R.string.EnableAnimations),
              preferences.getBoolean("view_animations", true),
              false);
        } else if (i == sendByEnterRow) {
          textCell.setTextAndCheck(
              LocaleController.getString("SendByEnter", R.string.SendByEnter),
              preferences.getBoolean("send_by_enter", false),
              false);
        } else if (i == saveToGalleryRow) {
          textCell.setTextAndCheck(
              LocaleController.getString("SaveToGallerySettings", R.string.SaveToGallerySettings),
              MediaController.getInstance().canSaveToGallery(),
              false);
        }
      } else if (type == 4) {
        if (view == null) {
          view = new HeaderCell(mContext);
        }
        if (i == settingsSectionRow2) {
          ((HeaderCell) view).setText(LocaleController.getString("SETTINGS", R.string.SETTINGS));
        } else if (i == supportSectionRow2) {
          ((HeaderCell) view).setText(LocaleController.getString("Support", R.string.Support));
        } else if (i == messagesSectionRow2) {
          ((HeaderCell) view)
              .setText(LocaleController.getString("MessagesSettings", R.string.MessagesSettings));
        } else if (i == mediaDownloadSection2) {
          ((HeaderCell) view)
              .setText(
                  LocaleController.getString(
                      "AutomaticMediaDownload", R.string.AutomaticMediaDownload));
        } else if (i == numberSectionRow) {
          ((HeaderCell) view).setText(LocaleController.getString("Info", R.string.Info));
        }
      } else if (type == 5) {
        if (view == null) {
          view = new TextInfoCell(mContext);
          try {
            PackageInfo pInfo =
                ApplicationLoader.applicationContext
                    .getPackageManager()
                    .getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            ((TextInfoCell) view)
                .setText(
                    String.format(
                        Locale.US,
                        "Telegram for Android v%s (%d)",
                        pInfo.versionName,
                        pInfo.versionCode));
          } catch (Exception e) {
            FileLog.e("tmessages", e);
          }
        }
      } else if (type == 6) {
        if (view == null) {
          view = new TextDetailSettingsCell(mContext);
        }
        TextDetailSettingsCell textCell = (TextDetailSettingsCell) view;

        if (i == mobileDownloadRow || i == wifiDownloadRow || i == roamingDownloadRow) {
          int mask;
          String value;
          SharedPreferences preferences =
              ApplicationLoader.applicationContext.getSharedPreferences(
                  "mainconfig", Activity.MODE_PRIVATE);
          if (i == mobileDownloadRow) {
            value = LocaleController.getString("WhenUsingMobileData", R.string.WhenUsingMobileData);
            mask = MediaController.getInstance().mobileDataDownloadMask;
          } else if (i == wifiDownloadRow) {
            value = LocaleController.getString("WhenConnectedOnWiFi", R.string.WhenConnectedOnWiFi);
            mask = MediaController.getInstance().wifiDownloadMask;
          } else {
            value = LocaleController.getString("WhenRoaming", R.string.WhenRoaming);
            mask = MediaController.getInstance().roamingDownloadMask;
          }
          String text = "";
          if ((mask & MediaController.AUTODOWNLOAD_MASK_PHOTO) != 0) {
            text += LocaleController.getString("AttachPhoto", R.string.AttachPhoto);
          }
          if ((mask & MediaController.AUTODOWNLOAD_MASK_AUDIO) != 0) {
            if (text.length() != 0) {
              text += ", ";
            }
            text += LocaleController.getString("AttachAudio", R.string.AttachAudio);
          }
          if ((mask & MediaController.AUTODOWNLOAD_MASK_VIDEO) != 0) {
            if (text.length() != 0) {
              text += ", ";
            }
            text += LocaleController.getString("AttachVideo", R.string.AttachVideo);
          }
          if ((mask & MediaController.AUTODOWNLOAD_MASK_DOCUMENT) != 0) {
            if (text.length() != 0) {
              text += ", ";
            }
            text += LocaleController.getString("AttachDocument", R.string.AttachDocument);
          }
          if (text.length() == 0) {
            text = LocaleController.getString("NoMediaAutoDownload", R.string.NoMediaAutoDownload);
          }
          textCell.setTextAndValue(value, text, true);
        } else if (i == numberRow) {
          TLRPC.User user = UserConfig.getCurrentUser();
          String value;
          if (user != null && user.phone != null && user.phone.length() != 0) {
            value = PhoneFormat.getInstance().format("+" + user.phone);
          } else {
            value = LocaleController.getString("NumberUnknown", R.string.NumberUnknown);
          }
          textCell.setTextAndValue(
              value, LocaleController.getString("Phone", R.string.Phone), true);
        } else if (i == usernameRow) {
          TLRPC.User user = UserConfig.getCurrentUser();
          String value;
          if (user != null && user.username != null && user.username.length() != 0) {
            value = "@" + user.username;
          } else {
            value = LocaleController.getString("UsernameEmpty", R.string.UsernameEmpty);
          }
          textCell.setTextAndValue(
              value, LocaleController.getString("Username", R.string.Username), false);
        }
      }
      return view;
    }

    @Override
    public int getItemViewType(int i) {
      if (i == emptyRow || i == overscrollRow) {
        return 0;
      }
      if (i == settingsSectionRow
          || i == supportSectionRow
          || i == messagesSectionRow
          || i == mediaDownloadSection
          || i == contactsSectionRow) {
        return 1;
      } else if (i == enableAnimationsRow || i == sendByEnterRow || i == saveToGalleryRow) {
        return 3;
      } else if (i == notificationRow
          || i == backgroundRow
          || i == askQuestionRow
          || i == sendLogsRow
          || i == privacyRow
          || i == clearLogsRow
          || i == switchBackendButtonRow
          || i == telegramFaqRow
          || i == contactsReimportRow
          || i == textSizeRow
          || i == languageRow
          || i == contactsSortRow
          || i == stickersRow) {
        return 2;
      } else if (i == versionRow) {
        return 5;
      } else if (i == wifiDownloadRow
          || i == mobileDownloadRow
          || i == roamingDownloadRow
          || i == numberRow
          || i == usernameRow) {
        return 6;
      } else if (i == settingsSectionRow2
          || i == messagesSectionRow2
          || i == supportSectionRow2
          || i == numberSectionRow
          || i == mediaDownloadSection2) {
        return 4;
      } else {
        return 2;
      }
    }

    @Override
    public int getViewTypeCount() {
      return 7;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }
  }
}
