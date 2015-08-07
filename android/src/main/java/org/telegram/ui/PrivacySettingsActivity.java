/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ListView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.ContactsController;
import org.telegram.android.LocaleController;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.actionbar.ActionBar;
import org.telegram.ui.actionbar.BaseFragment;
import org.telegram.ui.adapters.BaseFragmentAdapter;
import org.telegram.ui.cells.HeaderCell;
import org.telegram.ui.cells.TextInfoPrivacyCell;
import org.telegram.ui.cells.TextSettingsCell;
import org.telegram.ui.components.LayoutHelper;

import java.util.ArrayList;

public class PrivacySettingsActivity extends BaseFragment
    implements NotificationCenter.NotificationCenterDelegate {

  private ListAdapter listAdapter;

  private int privacySectionRow;
  private int blockedRow;
  private int lastSeenRow;
  private int lastSeenDetailRow;
  private int securitySectionRow;
  private int sessionsRow;
  private int passwordRow;
  private int passcodeRow;
  private int sessionsDetailRow;
  private int deleteAccountSectionRow;
  private int deleteAccountRow;
  private int deleteAccountDetailRow;
  private int rowCount;

  @Override
  public boolean onFragmentCreate() {
    super.onFragmentCreate();

    ContactsController.getInstance().loadPrivacySettings();

    rowCount = 0;
    privacySectionRow = rowCount++;
    blockedRow = rowCount++;
    lastSeenRow = rowCount++;
    lastSeenDetailRow = rowCount++;
    securitySectionRow = rowCount++;
    passcodeRow = rowCount++;
    passwordRow = rowCount++;
    sessionsRow = rowCount++;
    sessionsDetailRow = rowCount++;
    deleteAccountSectionRow = rowCount++;
    deleteAccountRow = rowCount++;
    deleteAccountDetailRow = rowCount++;

    NotificationCenter.getInstance().addObserver(this, NotificationCenter.privacyRulesUpdated);

    return true;
  }

  @Override
  public void onFragmentDestroy() {
    super.onFragmentDestroy();
    NotificationCenter.getInstance().removeObserver(this, NotificationCenter.privacyRulesUpdated);
  }

  @Override
  public View createView(Context context) {
    actionBar.setBackButtonImage(R.drawable.ic_ab_back);
    actionBar.setAllowOverlayTitle(true);
    actionBar.setTitle(LocaleController.getString("PrivacySettings", R.string.PrivacySettings));
    actionBar.setActionBarMenuOnItemClick(
        new ActionBar.ActionBarMenuOnItemClick() {
          @Override
          public void onItemClick(int id) {
            if (id == -1) {
              finishFragment();
            }
          }
        });

    listAdapter = new ListAdapter(context);

    fragmentView = new FrameLayout(context);
    FrameLayout frameLayout = (FrameLayout) fragmentView;
    frameLayout.setBackgroundColor(0xfff0f0f0);

    ListView listView = new ListView(context);
    listView.setDivider(null);
    listView.setDividerHeight(0);
    listView.setVerticalScrollBarEnabled(false);
    listView.setDrawSelectorOnTop(true);
    frameLayout.addView(
        listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    listView.setAdapter(listAdapter);
    listView.setOnItemClickListener(
        new AdapterView.OnItemClickListener() {
          @Override
          public void onItemClick(AdapterView<?> adapterView, View view, final int i, long l) {
            if (i == blockedRow) {
              presentFragment(new BlockedUsersActivity());
            } else if (i == sessionsRow) {
              presentFragment(new SessionsActivity());
            } else if (i == deleteAccountRow) {
              if (getParentActivity() == null) {
                return;
              }
              AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
              builder.setTitle(
                  LocaleController.getString("DeleteAccountTitle", R.string.DeleteAccountTitle));
              builder.setItems(
                  new CharSequence[] {
                    LocaleController.formatPluralString("Months", 1),
                    LocaleController.formatPluralString("Months", 3),
                    LocaleController.formatPluralString("Months", 6),
                    LocaleController.formatPluralString("Years", 1)
                  },
                  new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                      int value = 0;
                      if (which == 0) {
                        value = 30;
                      } else if (which == 1) {
                        value = 90;
                      } else if (which == 2) {
                        value = 182;
                      } else if (which == 3) {
                        value = 365;
                      }
                      final ProgressDialog progressDialog = new ProgressDialog(getParentActivity());
                      progressDialog.setMessage(
                          LocaleController.getString("Loading", R.string.Loading));
                      progressDialog.setCanceledOnTouchOutside(false);
                      progressDialog.setCancelable(false);
                      progressDialog.show();

                      final TLRPC.TL_account_setAccountTTL req =
                          new TLRPC.TL_account_setAccountTTL();
                      req.ttl = new TLRPC.TL_accountDaysTTL();
                      req.ttl.days = value;
                      ConnectionsManager.getInstance()
                          .performRpc(
                              req,
                              new RPCRequest.RPCRequestDelegate() {
                                @Override
                                public void run(
                                    final TLObject response, final TLRPC.TL_error error) {
                                  AndroidUtilities.runOnUIThread(
                                      new Runnable() {
                                        @Override
                                        public void run() {
                                          try {
                                            progressDialog.dismiss();
                                          } catch (Exception e) {
                                            FileLog.e("tmessages", e);
                                          }
                                          if (response instanceof TLRPC.TL_boolTrue) {
                                            ContactsController.getInstance()
                                                .setDeleteAccountTTL(req.ttl.days);
                                            listAdapter.notifyDataSetChanged();
                                          }
                                        }
                                      });
                                }
                              });
                    }
                  });
              builder.setNegativeButton(
                  LocaleController.getString("Cancel", R.string.Cancel), null);
              showDialog(builder.create());
            } else if (i == lastSeenRow) {
              presentFragment(new LastSeenActivity());
            } else if (i == passwordRow) {
              presentFragment(new TwoStepVerificationActivity(0));
            } else if (i == passcodeRow) {
              if (UserConfig.passcodeHash.length() > 0) {
                presentFragment(new PasscodeActivity(2));
              } else {
                presentFragment(new PasscodeActivity(0));
              }
            }
          }
        });

    return fragmentView;
  }

  @Override
  public void didReceivedNotification(int id, Object... args) {
    if (id == NotificationCenter.privacyRulesUpdated) {
      if (listAdapter != null) {
        listAdapter.notifyDataSetChanged();
      }
    }
  }

  private String formatRulesString() {
    ArrayList<TLRPC.PrivacyRule> privacyRules = ContactsController.getInstance().getPrivacyRules();
    if (privacyRules.size() == 0) {
      return LocaleController.getString("LastSeenNobody", R.string.LastSeenNobody);
    }
    int type = -1;
    int plus = 0;
    int minus = 0;
    for (TLRPC.PrivacyRule rule : privacyRules) {
      if (rule instanceof TLRPC.TL_privacyValueAllowUsers) {
        plus += rule.users.size();
      } else if (rule instanceof TLRPC.TL_privacyValueDisallowUsers) {
        minus += rule.users.size();
      } else if (rule instanceof TLRPC.TL_privacyValueAllowAll) {
        type = 0;
      } else if (rule instanceof TLRPC.TL_privacyValueDisallowAll) {
        type = 1;
      } else {
        type = 2;
      }
    }
    if (type == 0 || type == -1 && minus > 0) {
      if (minus == 0) {
        return LocaleController.getString("LastSeenEverybody", R.string.LastSeenEverybody);
      } else {
        return LocaleController.formatString(
            "LastSeenEverybodyMinus", R.string.LastSeenEverybodyMinus, minus);
      }
    } else if (type == 2 || type == -1 && minus > 0 && plus > 0) {
      if (plus == 0 && minus == 0) {
        return LocaleController.getString("LastSeenContacts", R.string.LastSeenContacts);
      } else {
        if (plus != 0 && minus != 0) {
          return LocaleController.formatString(
              "LastSeenContactsMinusPlus", R.string.LastSeenContactsMinusPlus, minus, plus);
        } else if (minus != 0) {
          return LocaleController.formatString(
              "LastSeenContactsMinus", R.string.LastSeenContactsMinus, minus);
        } else if (plus != 0) {
          return LocaleController.formatString(
              "LastSeenContactsPlus", R.string.LastSeenContactsPlus, plus);
        }
      }
    } else if (type == 1 || type == -1 && plus > 0) {
      if (plus == 0) {
        return LocaleController.getString("LastSeenNobody", R.string.LastSeenNobody);
      } else {
        return LocaleController.formatString(
            "LastSeenNobodyPlus", R.string.LastSeenNobodyPlus, plus);
      }
    }
    return "unknown";
  }

  @Override
  public void onResume() {
    super.onResume();
    if (listAdapter != null) {
      listAdapter.notifyDataSetChanged();
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
      return i == passcodeRow
          || i == passwordRow
          || i == blockedRow
          || i == sessionsRow
          || i == lastSeenRow && !ContactsController.getInstance().getLoadingLastSeenInfo()
          || i == deleteAccountRow && !ContactsController.getInstance().getLoadingDeleteInfo();
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
          view = new TextSettingsCell(mContext);
          view.setBackgroundColor(0xffffffff);
        }
        TextSettingsCell textCell = (TextSettingsCell) view;
        if (i == blockedRow) {
          textCell.setText(LocaleController.getString("BlockedUsers", R.string.BlockedUsers), true);
        } else if (i == sessionsRow) {
          textCell.setText(
              LocaleController.getString("SessionsTitle", R.string.SessionsTitle), false);
        } else if (i == passwordRow) {
          textCell.setText(
              LocaleController.getString("TwoStepVerification", R.string.TwoStepVerification),
              true);
        } else if (i == passcodeRow) {
          textCell.setText(LocaleController.getString("Passcode", R.string.Passcode), true);
        } else if (i == lastSeenRow) {
          String value;
          if (ContactsController.getInstance().getLoadingLastSeenInfo()) {
            value = LocaleController.getString("Loading", R.string.Loading);
          } else {
            value = formatRulesString();
          }
          textCell.setTextAndValue(
              LocaleController.getString("PrivacyLastSeen", R.string.PrivacyLastSeen),
              value,
              false);
        } else if (i == deleteAccountRow) {
          String value;
          if (ContactsController.getInstance().getLoadingDeleteInfo()) {
            value = LocaleController.getString("Loading", R.string.Loading);
          } else {
            int ttl = ContactsController.getInstance().getDeleteAccountTTL();
            if (ttl <= 182) {
              value = LocaleController.formatPluralString("Months", ttl / 30);
            } else if (ttl == 365) {
              value = LocaleController.formatPluralString("Years", ttl / 365);
            } else {
              value = LocaleController.formatPluralString("Days", ttl);
            }
          }
          textCell.setTextAndValue(
              LocaleController.getString("DeleteAccountIfAwayFor", R.string.DeleteAccountIfAwayFor),
              value,
              false);
        }
      } else if (type == 1) {
        if (view == null) {
          view = new TextInfoPrivacyCell(mContext);
        }
        if (i == deleteAccountDetailRow) {
          ((TextInfoPrivacyCell) view)
              .setText(LocaleController.getString("DeleteAccountHelp", R.string.DeleteAccountHelp));
          view.setBackgroundResource(R.drawable.greydivider_bottom);
        } else if (i == lastSeenDetailRow) {
          ((TextInfoPrivacyCell) view)
              .setText(LocaleController.getString("LastSeenHelp", R.string.LastSeenHelp));
          view.setBackgroundResource(R.drawable.greydivider);
        } else if (i == sessionsDetailRow) {
          ((TextInfoPrivacyCell) view)
              .setText(LocaleController.getString("SessionsInfo", R.string.SessionsInfo));
          view.setBackgroundResource(R.drawable.greydivider);
        }
      } else if (type == 2) {
        if (view == null) {
          view = new HeaderCell(mContext);
          view.setBackgroundColor(0xffffffff);
        }
        if (i == privacySectionRow) {
          ((HeaderCell) view)
              .setText(LocaleController.getString("PrivacyTitle", R.string.PrivacyTitle));
        } else if (i == securitySectionRow) {
          ((HeaderCell) view)
              .setText(LocaleController.getString("SecurityTitle", R.string.SecurityTitle));
        } else if (i == deleteAccountSectionRow) {
          ((HeaderCell) view)
              .setText(
                  LocaleController.getString("DeleteAccountTitle", R.string.DeleteAccountTitle));
        }
      }
      return view;
    }

    @Override
    public int getItemViewType(int i) {
      if (i == lastSeenRow
          || i == blockedRow
          || i == deleteAccountRow
          || i == sessionsRow
          || i == passwordRow
          || i == passcodeRow) {
        return 0;
      } else if (i == deleteAccountDetailRow || i == lastSeenDetailRow || i == sessionsDetailRow) {
        return 1;
      } else if (i == securitySectionRow
          || i == deleteAccountSectionRow
          || i == privacySectionRow) {
        return 2;
      }
      return 0;
    }

    @Override
    public int getViewTypeCount() {
      return 3;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }
  }
}
