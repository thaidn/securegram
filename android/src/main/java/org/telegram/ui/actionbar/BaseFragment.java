/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.actionbar;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.android.NotificationCenter;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import xyz.securegram.R;

public class BaseFragment {

  private boolean isFinished = false;
  protected Dialog visibleDialog = null;

  protected View fragmentView;
  protected ActionBarLayout parentLayout;
  protected ActionBar actionBar;
  protected int classGuid = 0;
  protected Bundle arguments;
  protected boolean swipeBackEnabled = true;
  protected boolean hasOwnBackground = false;

  protected int notificationEvents[];

  public BaseFragment() {
    classGuid = ConnectionsManager.getInstance().generateClassGuid();
  }

  public BaseFragment(Bundle args) {
    arguments = args;
    classGuid = ConnectionsManager.getInstance().generateClassGuid();
  }

  public View createView(Context context) {
    return null;
  }

  public Bundle getArguments() {
    return arguments;
  }

  protected void clearViews() {
    if (fragmentView != null) {
      ViewGroup parent = (ViewGroup) fragmentView.getParent();
      if (parent != null) {
        try {
          parent.removeView(fragmentView);
        } catch (Exception e) {
          FileLog.e("tmessages", e);
        }
      }
      fragmentView = null;
    }
    if (actionBar != null) {
      ViewGroup parent = (ViewGroup) actionBar.getParent();
      if (parent != null) {
        try {
          parent.removeView(actionBar);
        } catch (Exception e) {
          FileLog.e("tmessages", e);
        }
      }
      actionBar = null;
    }
    parentLayout = null;
  }

  protected void setParentLayout(ActionBarLayout layout) {
    if (parentLayout != layout) {
      parentLayout = layout;
      if (fragmentView != null) {
        ViewGroup parent = (ViewGroup) fragmentView.getParent();
        if (parent != null) {
          try {
            parent.removeView(fragmentView);
          } catch (Exception e) {
            FileLog.e("tmessages", e);
          }
        }
        if (parentLayout != null && parentLayout.getContext() != fragmentView.getContext()) {
          fragmentView = null;
        }
      }
      if (actionBar != null) {
        ViewGroup parent = (ViewGroup) actionBar.getParent();
        if (parent != null) {
          try {
            parent.removeView(actionBar);
          } catch (Exception e) {
            FileLog.e("tmessages", e);
          }
        }
        if (parentLayout != null && parentLayout.getContext() != actionBar.getContext()) {
          actionBar = null;
        }
      }
      if (parentLayout != null && actionBar == null) {
        actionBar = new ActionBar(parentLayout.getContext());
        actionBar.parentFragment = this;
        actionBar.setBackgroundColor(0xff54759e);
        actionBar.setItemsBackground(R.drawable.bar_selector);
      }
    }
  }

  public void finishFragment() {
    finishFragment(true);
  }

  public void finishFragment(boolean animated) {
    if (isFinished || parentLayout == null) {
      return;
    }
    parentLayout.closeLastFragment(animated);
  }

  public void removeSelfFromStack() {
    if (isFinished || parentLayout == null) {
      return;
    }
    parentLayout.removeFragmentFromStack(this);
  }

  public boolean onFragmentCreate() {
    if (notificationEvents != null) {
      for (int event: notificationEvents) {
        NotificationCenter.getInstance().addObserver(this, event);
      }
    }

    return true;
  }

  public void onFragmentDestroy() {
    ConnectionsManager.getInstance().cancelRpcsForClassGuid(classGuid);
    if (notificationEvents != null) {
      for (int event: notificationEvents) {
        NotificationCenter.getInstance().removeObserver(this, event);
      }
    }

    if (actionBar != null) {
      actionBar.setEnabled(false);
    }
    isFinished = true;
  }

  public void onResume() {}

  public void onPause() {
    if (actionBar != null) {
      actionBar.onPause();
    }
    try {
      if (visibleDialog != null && visibleDialog.isShowing()) {
        visibleDialog.dismiss();
        visibleDialog = null;
      }
    } catch (Exception e) {
      FileLog.e("tmessages", e);
    }
  }

  public void onConfigurationChanged(android.content.res.Configuration newConfig) {}

  public boolean onBackPressed() {
    return true;
  }

  public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {}

  public void saveSelfArgs(Bundle args) {}

  public void restoreSelfArgs(Bundle args) {}

  public boolean presentFragment(BaseFragment fragment) {
    return parentLayout != null && parentLayout.presentFragment(fragment);
  }

  public boolean presentFragment(BaseFragment fragment, boolean removeLast) {
    return parentLayout != null && parentLayout.presentFragment(fragment, removeLast);
  }

  public boolean presentFragment(
      BaseFragment fragment, boolean removeLast, boolean forceWithoutAnimation) {
    return parentLayout != null
        && parentLayout.presentFragment(fragment, removeLast, forceWithoutAnimation, true);
  }

  public Activity getParentActivity() {
    if (parentLayout != null) {
      return parentLayout.parentActivity;
    }
    return null;
  }

  public void startActivityForResult(final Intent intent, final int requestCode) {
    if (parentLayout != null) {
      parentLayout.startActivityForResult(intent, requestCode);
    }
  }

  public void onBeginSlide() {
    try {
      if (visibleDialog != null && visibleDialog.isShowing()) {
        visibleDialog.dismiss();
        visibleDialog = null;
      }
    } catch (Exception e) {
      FileLog.e("tmessages", e);
    }
    if (actionBar != null) {
      actionBar.onPause();
    }
  }

  protected void onOpenAnimationEnd() {}

  protected void onOpenAnimationStart() {}

  protected void onBecomeFullyVisible() {}

  public void onLowMemory() {}

  public boolean needAddActionBar() {
    return true;
  }

  public Dialog showDialog(Dialog dialog) {
    if (dialog == null
        || parentLayout == null
        || parentLayout.animationInProgress
        || parentLayout.startedTracking
        || parentLayout.checkTransitionAnimation()) {
      return null;
    }
    try {
      if (visibleDialog != null) {
        visibleDialog.dismiss();
        visibleDialog = null;
      }
    } catch (Exception e) {
      FileLog.e("tmessages", e);
    }
    try {
      visibleDialog = dialog;
      visibleDialog.setCanceledOnTouchOutside(true);
      visibleDialog.setOnDismissListener(
          new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
              visibleDialog = null;
              onDialogDismiss();
            }
          });
      visibleDialog.show();
      return visibleDialog;
    } catch (Exception e) {
      FileLog.e("tmessages", e);
    }
    return null;
  }

  protected void onDialogDismiss() {}

  public Dialog getVisibleDialog() {
    return visibleDialog;
  }

  public void setVisibleDialog(Dialog dialog) {
    visibleDialog = dialog;
  }
}
