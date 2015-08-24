/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.android.MessagesController;
import org.telegram.messenger.ApplicationLoader;
import xyz.securegram.R;
import org.telegram.messenger.TLRPC;
import org.telegram.ui.actionbar.ActionBar;
import org.telegram.ui.actionbar.BaseFragment;
import org.telegram.ui.components.IdenticonDrawable;

public class IdenticonActivity extends BaseFragment {
  private int chat_id;

  public IdenticonActivity(Bundle args) {
    super(args);
  }

  @Override
  public boolean onFragmentCreate() {
    chat_id = getArguments().getInt("chat_id");
    return super.onFragmentCreate();
  }

  @Override
  public View createView(Context context) {
    actionBar.setBackButtonImage(R.drawable.ic_ab_back);
    actionBar.setAllowOverlayTitle(true);
    actionBar.setTitle(LocaleController.getString("EncryptionKey", R.string.EncryptionKey));

    actionBar.setActionBarMenuOnItemClick(
        new ActionBar.ActionBarMenuOnItemClick() {
          @Override
          public void onItemClick(int id) {
            if (id == -1) {
              finishFragment();
            }
          }
        });

    fragmentView =
        getParentActivity().getLayoutInflater().inflate(R.layout.identicon_layout, null, false);
    ImageView identiconView = (ImageView) fragmentView.findViewById(R.id.identicon_view);
    TextView textView = (TextView) fragmentView.findViewById(R.id.identicon_text);
    TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance().getEncryptedChat(chat_id);
    if (encryptedChat != null) {
      IdenticonDrawable drawable = new IdenticonDrawable();
      identiconView.setImageDrawable(drawable);
      drawable.setEncryptedChat(encryptedChat);
      TLRPC.User user = MessagesController.getInstance().getUser(encryptedChat.user_id);
      textView.setText(
          AndroidUtilities.replaceTags(
              LocaleController.formatString(
                  "EncryptionKeyDescription",
                  R.string.EncryptionKeyDescription,
                  user.first_name,
                  user.first_name)));
    }

    fragmentView.setOnTouchListener(
        new View.OnTouchListener() {
          @Override
          public boolean onTouch(View v, MotionEvent event) {
            return true;
          }
        });

    return fragmentView;
  }

  @Override
  public void onConfigurationChanged(android.content.res.Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    fixLayout();
  }

  @Override
  public void onResume() {
    super.onResume();
    fixLayout();
  }

  private void fixLayout() {
    ViewTreeObserver obs = fragmentView.getViewTreeObserver();
    obs.addOnPreDrawListener(
        new ViewTreeObserver.OnPreDrawListener() {
          @Override
          public boolean onPreDraw() {
            if (fragmentView == null) {
              return true;
            }
            fragmentView.getViewTreeObserver().removeOnPreDrawListener(this);
            LinearLayout layout = (LinearLayout) fragmentView;
            WindowManager manager =
                (WindowManager)
                    ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE);
            int rotation = manager.getDefaultDisplay().getRotation();

            if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
              layout.setOrientation(LinearLayout.HORIZONTAL);
            } else {
              layout.setOrientation(LinearLayout.VERTICAL);
            }

            fragmentView.setPadding(
                fragmentView.getPaddingLeft(),
                0,
                fragmentView.getPaddingRight(),
                fragmentView.getPaddingBottom());
            return true;
          }
        });
  }
}
