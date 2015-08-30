/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.ContactsController;
import org.telegram.android.LocaleController;
import org.telegram.android.MessagesController;
import org.telegram.android.MessagesStorage;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import xyz.securegram.R;
import xyz.securegram.axolotl.AxolotlController;

import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.phoneformat.PhoneFormat;
import org.telegram.ui.actionbar.ActionBar;
import org.telegram.ui.actionbar.ActionBarMenu;
import org.telegram.ui.actionbar.BaseFragment;
import org.telegram.ui.components.LayoutHelper;
import org.telegram.ui.components.SlideView;
import org.telegram.ui.components.TypefaceSpan;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class LoginActivity extends BaseFragment {

  private int currentViewNum = 0;
  private SlideView[] views = new SlideView[5];
  private ProgressDialog progressDialog;

  private static final int done_button = 1;

  @Override
  public void onFragmentDestroy() {
    super.onFragmentDestroy();
    for (SlideView v : views) {
      if (v != null) {
        v.onDestroyActivity();
      }
    }
    if (progressDialog != null) {
      try {
        progressDialog.dismiss();
      } catch (Exception e) {
        FileLog.e("tmessages", e);
      }
      progressDialog = null;
    }
  }

  @Override
  public View createView(Context context) {
    actionBar.setTitle(LocaleController.getString("AppName", R.string.AppName));

    actionBar.setActionBarMenuOnItemClick(
        new ActionBar.ActionBarMenuOnItemClick() {
          @Override
          public void onItemClick(int id) {
            if (id == done_button) {
              views[currentViewNum].onNextPressed();
            } else if (id == -1) {
              onBackPressed();
            }
          }
        });

    ActionBarMenu menu = actionBar.createMenu();
    menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));

    fragmentView = new ScrollView(context);
    ScrollView scrollView = (ScrollView) fragmentView;
    scrollView.setFillViewport(true);

    FrameLayout frameLayout = new FrameLayout(context);
    scrollView.addView(frameLayout);
    ScrollView.LayoutParams layoutParams = (ScrollView.LayoutParams) frameLayout.getLayoutParams();
    layoutParams.width = ScrollView.LayoutParams.MATCH_PARENT;
    layoutParams.height = ScrollView.LayoutParams.WRAP_CONTENT;
    layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
    frameLayout.setLayoutParams(layoutParams);

    views[0] = new PhoneView(context);
    views[1] = new LoginActivitySmsView(context);
    views[2] = new LoginActivityRegisterView(context);
    views[3] = new LoginActivityPasswordView(context);
    views[4] = new LoginActivityRecoverView(context);

    for (int a = 0; a < 5; a++) {
      views[a].setVisibility(a == 0 ? View.VISIBLE : View.GONE);
      frameLayout.addView(views[a]);
      FrameLayout.LayoutParams layoutParams1 =
          (FrameLayout.LayoutParams) views[a].getLayoutParams();
      layoutParams1.width = LayoutHelper.MATCH_PARENT;
      layoutParams1.height = a == 0 ? LayoutHelper.WRAP_CONTENT : LayoutHelper.MATCH_PARENT;
      layoutParams1.leftMargin = AndroidUtilities.dp(AndroidUtilities.isTablet() ? 26 : 18);
      layoutParams1.rightMargin = AndroidUtilities.dp(AndroidUtilities.isTablet() ? 26 : 18);
      layoutParams1.topMargin = AndroidUtilities.dp(30);
      layoutParams1.gravity = Gravity.TOP | Gravity.LEFT;
      views[a].setLayoutParams(layoutParams1);
    }

    Bundle savedInstanceState = loadCurrentState();
    if (savedInstanceState != null) {
      currentViewNum = savedInstanceState.getInt("currentViewNum", 0);
    }
    actionBar.setTitle(views[currentViewNum].getHeaderName());
    for (int a = 0; a < views.length; a++) {
      if (savedInstanceState != null) {
        views[a].restoreStateParams(savedInstanceState);
      }
      if (currentViewNum == a) {
        actionBar.setBackButtonImage(views[a].needBackButton() ? R.drawable.ic_ab_back : 0);
        views[a].setVisibility(View.VISIBLE);
        views[a].onShow();
      } else {
        views[a].setVisibility(View.GONE);
      }
    }

    return fragmentView;
  }

  @Override
  public void onPause() {
    super.onPause();
    if (!AndroidUtilities.isTablet()) {
      getParentActivity()
          .getWindow()
          .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
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
  }

  private Bundle loadCurrentState() {
    try {
      Bundle bundle = new Bundle();
      SharedPreferences preferences =
          ApplicationLoader.applicationContext.getSharedPreferences(
              "logininfo", Context.MODE_PRIVATE);
      Map<String, ?> params = preferences.getAll();
      for (Map.Entry<String, ?> entry : params.entrySet()) {
        String key = entry.getKey();
        Object value = entry.getValue();
        String[] args = key.split("_\\|_");
        if (args.length == 1) {
          if (value instanceof String) {
            bundle.putString(key, (String) value);
          } else if (value instanceof Integer) {
            bundle.putInt(key, (Integer) value);
          }
        } else if (args.length == 2) {
          Bundle inner = bundle.getBundle(args[0]);
          if (inner == null) {
            inner = new Bundle();
            bundle.putBundle(args[0], inner);
          }
          if (value instanceof String) {
            inner.putString(args[1], (String) value);
          } else if (value instanceof Integer) {
            inner.putInt(args[1], (Integer) value);
          }
        }
      }
      return bundle;
    } catch (Exception e) {
      FileLog.e("tmessages", e);
    }
    return null;
  }

  private void clearCurrentState() {
    SharedPreferences preferences =
        ApplicationLoader.applicationContext.getSharedPreferences(
            "logininfo", Context.MODE_PRIVATE);
    SharedPreferences.Editor editor = preferences.edit();
    editor.clear();
    editor.commit();
  }

  private void putBundleToEditor(Bundle bundle, SharedPreferences.Editor editor, String prefix) {
    Set<String> keys = bundle.keySet();
    for (String key : keys) {
      Object obj = bundle.get(key);
      if (obj instanceof String) {
        if (prefix != null) {
          editor.putString(prefix + "_|_" + key, (String) obj);
        } else {
          editor.putString(key, (String) obj);
        }
      } else if (obj instanceof Integer) {
        if (prefix != null) {
          editor.putInt(prefix + "_|_" + key, (Integer) obj);
        } else {
          editor.putInt(key, (Integer) obj);
        }
      } else if (obj instanceof Bundle) {
        putBundleToEditor((Bundle) obj, editor, key);
      }
    }
  }

  @Override
  public boolean onBackPressed() {
    if (currentViewNum == 0) {
      for (SlideView v : views) {
        if (v != null) {
          v.onDestroyActivity();
        }
      }
      clearCurrentState();
      return true;
    } else if (currentViewNum == 3) {
      views[currentViewNum].onBackPressed();
      setPage(0, true, null, true);
    } else if (currentViewNum == 4) {
      views[currentViewNum].onBackPressed();
      setPage(3, true, null, true);
    }
    return false;
  }

  public void needShowAlert(String title, String text) {
    if (text == null || getParentActivity() == null) {
      return;
    }
    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
    builder.setTitle(title);
    builder.setMessage(text);
    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
    showDialog(builder.create());
  }

  public void needShowProgress() {
    if (getParentActivity() == null
        || getParentActivity().isFinishing()
        || progressDialog != null) {
      return;
    }
    progressDialog = new ProgressDialog(getParentActivity());
    progressDialog.setMessage(LocaleController.getString("Loading", R.string.Loading));
    progressDialog.setCanceledOnTouchOutside(false);
    progressDialog.setCancelable(false);
    progressDialog.show();
  }

  public void needHideProgress() {
    if (progressDialog == null) {
      return;
    }
    try {
      progressDialog.dismiss();
    } catch (Exception e) {
      FileLog.e("tmessages", e);
    }
    progressDialog = null;
  }

  public void setPage(int page, boolean animated, Bundle params, boolean back) {
    if (android.os.Build.VERSION.SDK_INT > 13) {
      final SlideView outView = views[currentViewNum];
      final SlideView newView = views[page];
      currentViewNum = page;
      actionBar.setBackButtonImage(newView.needBackButton() ? R.drawable.ic_ab_back : 0);

      newView.setParams(params);
      actionBar.setTitle(newView.getHeaderName());
      newView.onShow();
      newView.setX(back ? -AndroidUtilities.displaySize.x : AndroidUtilities.displaySize.x);
      outView
          .animate()
          .setInterpolator(new AccelerateDecelerateInterpolator())
          .setListener(
              new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {}

                @SuppressLint("NewApi")
                @Override
                public void onAnimationEnd(Animator animator) {
                  outView.setVisibility(View.GONE);
                  outView.setX(0);
                }

                @Override
                public void onAnimationCancel(Animator animator) {}

                @Override
                public void onAnimationRepeat(Animator animator) {}
              })
          .setDuration(300)
          .translationX(back ? AndroidUtilities.displaySize.x : -AndroidUtilities.displaySize.x)
          .start();
      newView
          .animate()
          .setInterpolator(new AccelerateDecelerateInterpolator())
          .setListener(
              new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {
                  newView.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationEnd(Animator animator) {}

                @Override
                public void onAnimationCancel(Animator animator) {}

                @Override
                public void onAnimationRepeat(Animator animator) {}
              })
          .setDuration(300)
          .translationX(0)
          .start();
    } else {
      actionBar.setBackButtonImage(views[page].needBackButton() ? R.drawable.ic_ab_back : 0);
      views[currentViewNum].setVisibility(View.GONE);
      currentViewNum = page;
      views[page].setParams(params);
      views[page].setVisibility(View.VISIBLE);
      actionBar.setTitle(views[page].getHeaderName());
      views[page].onShow();
    }
  }

  @Override
  public void saveSelfArgs(Bundle outState) {
    try {
      Bundle bundle = new Bundle();
      bundle.putInt("currentViewNum", currentViewNum);
      for (int a = 0; a <= currentViewNum; a++) {
        SlideView v = views[a];
        if (v != null) {
          v.saveStateParams(bundle);
        }
      }
      SharedPreferences preferences =
          ApplicationLoader.applicationContext.getSharedPreferences(
              "logininfo", Context.MODE_PRIVATE);
      SharedPreferences.Editor editor = preferences.edit();
      editor.clear();
      putBundleToEditor(bundle, editor, null);
      editor.commit();
    } catch (Exception e) {
      FileLog.e("tmessages", e);
    }
  }

  public void needFinishActivity() {
    clearCurrentState();
    presentFragment(new DialogsActivity(null), true);
    NotificationCenter.getInstance().postNotificationName(NotificationCenter.mainUserInfoChanged);
  }

  public class PhoneView extends SlideView implements AdapterView.OnItemSelectedListener {

    private EditText codeField;
    private EditText phoneField;
    private TextView countryButton;

    private int countryState = 0;

    private ArrayList<String> countriesArray = new ArrayList<>();
    private HashMap<String, String> countriesMap = new HashMap<>();
    private HashMap<String, String> codesMap = new HashMap<>();

    private boolean ignoreSelection = false;
    private boolean ignoreOnTextChange = false;
    private boolean ignoreOnPhoneChange = false;
    private boolean nextPressed = false;

    public PhoneView(Context context) {
      super(context);

      setOrientation(VERTICAL);

      countryButton = new TextView(context);
      countryButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
      countryButton.setPadding(
          AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), 0);
      countryButton.setTextColor(0xff212121);
      countryButton.setMaxLines(1);
      countryButton.setSingleLine(true);
      countryButton.setEllipsize(TextUtils.TruncateAt.END);
      countryButton.setGravity(
          (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_HORIZONTAL);
      countryButton.setBackgroundResource(R.drawable.spinner_states);
      addView(countryButton);
      LayoutParams layoutParams = (LayoutParams) countryButton.getLayoutParams();
      layoutParams.width = LayoutHelper.MATCH_PARENT;
      layoutParams.height = AndroidUtilities.dp(36);
      layoutParams.bottomMargin = AndroidUtilities.dp(14);
      countryButton.setLayoutParams(layoutParams);
      countryButton.setOnClickListener(
          new OnClickListener() {
            @Override
            public void onClick(View view) {
              CountrySelectActivity fragment = new CountrySelectActivity();
              fragment.setCountrySelectActivityDelegate(
                  new CountrySelectActivity.CountrySelectActivityDelegate() {
                    @Override
                    public void didSelectCountry(String name) {
                      selectCountry(name);
                      phoneField.requestFocus();
                    }
                  });
              presentFragment(fragment);
            }
          });

      View view = new View(context);
      view.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
      view.setBackgroundColor(0xffdbdbdb);
      addView(view);
      layoutParams = (LayoutParams) view.getLayoutParams();
      layoutParams.width = LayoutHelper.MATCH_PARENT;
      layoutParams.height = 1;
      layoutParams.topMargin = AndroidUtilities.dp(-17.5f);
      layoutParams.leftMargin = AndroidUtilities.dp(4);
      layoutParams.rightMargin = AndroidUtilities.dp(4);
      view.setLayoutParams(layoutParams);

      LinearLayout linearLayout = new LinearLayout(context);
      linearLayout.setOrientation(HORIZONTAL);
      addView(linearLayout);
      layoutParams = (LayoutParams) linearLayout.getLayoutParams();
      layoutParams.width = LayoutHelper.MATCH_PARENT;
      layoutParams.height = LayoutHelper.WRAP_CONTENT;
      layoutParams.topMargin = AndroidUtilities.dp(20);
      linearLayout.setLayoutParams(layoutParams);

      TextView textView = new TextView(context);
      textView.setText("+");
      textView.setTextColor(0xff212121);
      textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
      linearLayout.addView(textView);
      layoutParams = (LayoutParams) textView.getLayoutParams();
      layoutParams.width = LayoutHelper.WRAP_CONTENT;
      layoutParams.height = LayoutHelper.WRAP_CONTENT;
      textView.setLayoutParams(layoutParams);

      codeField = new EditText(context);
      codeField.setInputType(InputType.TYPE_CLASS_PHONE);
      codeField.setTextColor(0xff212121);
      AndroidUtilities.clearCursorDrawable(codeField);
      codeField.setPadding(AndroidUtilities.dp(10), 0, 0, 0);
      codeField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
      codeField.setMaxLines(1);
      codeField.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
      codeField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
      InputFilter[] inputFilters = new InputFilter[1];
      inputFilters[0] = new InputFilter.LengthFilter(4);
      codeField.setFilters(inputFilters);
      linearLayout.addView(codeField);
      layoutParams = (LayoutParams) codeField.getLayoutParams();
      layoutParams.width = AndroidUtilities.dp(55);
      layoutParams.height = AndroidUtilities.dp(36);
      layoutParams.rightMargin = AndroidUtilities.dp(16);
      layoutParams.leftMargin = AndroidUtilities.dp(-9);
      codeField.setLayoutParams(layoutParams);
      codeField.addTextChangedListener(
          new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {}

            @Override
            public void afterTextChanged(Editable editable) {
              if (ignoreOnTextChange) {
                ignoreOnTextChange = false;
                return;
              }
              ignoreOnTextChange = true;
              String text = PhoneFormat.stripExceptNumbers(codeField.getText().toString());
              codeField.setText(text);
              if (text.length() == 0) {
                countryButton.setText(
                    LocaleController.getString("ChooseCountry", R.string.ChooseCountry));
                countryState = 1;
              } else {
                String country = codesMap.get(text);
                if (country != null) {
                  int index = countriesArray.indexOf(country);
                  if (index != -1) {
                    ignoreSelection = true;
                    countryButton.setText(countriesArray.get(index));

                    updatePhoneField();
                    countryState = 0;
                  } else {
                    countryButton.setText(
                        LocaleController.getString("WrongCountry", R.string.WrongCountry));
                    countryState = 2;
                  }
                } else {
                  countryButton.setText(
                      LocaleController.getString("WrongCountry", R.string.WrongCountry));
                  countryState = 2;
                }
                codeField.setSelection(codeField.getText().length());
              }
            }
          });
      codeField.setOnEditorActionListener(
          new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
              if (i == EditorInfo.IME_ACTION_NEXT) {
                phoneField.requestFocus();
                return true;
              }
              return false;
            }
          });

      phoneField = new EditText(context);
      phoneField.setInputType(InputType.TYPE_CLASS_PHONE);
      phoneField.setTextColor(0xff212121);
      phoneField.setHintTextColor(0xff979797);
      phoneField.setPadding(0, 0, 0, 0);
      AndroidUtilities.clearCursorDrawable(phoneField);
      phoneField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
      phoneField.setMaxLines(1);
      phoneField.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
      phoneField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
      linearLayout.addView(phoneField);
      layoutParams = (LayoutParams) phoneField.getLayoutParams();
      layoutParams.width = LayoutHelper.MATCH_PARENT;
      layoutParams.height = AndroidUtilities.dp(36);
      phoneField.setLayoutParams(layoutParams);
      phoneField.addTextChangedListener(
          new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
              if (ignoreOnPhoneChange) {
                return;
              }
              if (count == 1 && after == 0 && s.length() > 1) {
                String phoneChars = "0123456789";
                String str = s.toString();
                String substr = str.substring(start, start + 1);
                if (!phoneChars.contains(substr)) {
                  ignoreOnPhoneChange = true;
                  StringBuilder builder = new StringBuilder(str);
                  int toDelete = 0;
                  for (int a = start; a >= 0; a--) {
                    substr = str.substring(a, a + 1);
                    if (phoneChars.contains(substr)) {
                      break;
                    }
                    toDelete++;
                  }
                  builder.delete(Math.max(0, start - toDelete), start + 1);
                  str = builder.toString();
                  if (PhoneFormat.strip(str).length() == 0) {
                    phoneField.setText("");
                  } else {
                    phoneField.setText(str);
                    updatePhoneField();
                  }
                  ignoreOnPhoneChange = false;
                }
              }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
              if (ignoreOnPhoneChange) {
                return;
              }
              updatePhoneField();
            }
          });
      phoneField.setOnEditorActionListener(
          new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
              if (i == EditorInfo.IME_ACTION_NEXT) {
                onNextPressed();
                return true;
              }
              return false;
            }
          });

      textView = new TextView(context);
      textView.setText(LocaleController.getString("StartText", R.string.StartText));
      textView.setTextColor(0xff757575);
      textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
      textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
      textView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
      addView(textView);
      layoutParams = (LayoutParams) textView.getLayoutParams();
      layoutParams.width = LayoutHelper.WRAP_CONTENT;
      layoutParams.height = LayoutHelper.WRAP_CONTENT;
      layoutParams.topMargin = AndroidUtilities.dp(28);
      layoutParams.bottomMargin = AndroidUtilities.dp(10);
      layoutParams.gravity = LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT;
      textView.setLayoutParams(layoutParams);

      HashMap<String, String> languageMap = new HashMap<>();
      try {
        BufferedReader reader =
            new BufferedReader(
                new InputStreamReader(getResources().getAssets().open("countries.txt")));
        String line;
        while ((line = reader.readLine()) != null) {
          String[] args = line.split(";");
          countriesArray.add(0, args[2]);
          countriesMap.put(args[2], args[0]);
          codesMap.put(args[0], args[2]);
          languageMap.put(args[1], args[2]);
        }
        reader.close();
      } catch (Exception e) {
        FileLog.e("tmessages", e);
      }

      Collections.sort(
          countriesArray,
          new Comparator<String>() {
            @Override
            public int compare(String lhs, String rhs) {
              return lhs.compareTo(rhs);
            }
          });

      String country = null;

      try {
        TelephonyManager telephonyManager =
            (TelephonyManager)
                ApplicationLoader.applicationContext.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager != null) {
          country = telephonyManager.getSimCountryIso().toUpperCase();
        }
      } catch (Exception e) {
        FileLog.e("tmessages", e);
      }

      if (country != null) {
        String countryName = languageMap.get(country);
        if (countryName != null) {
          int index = countriesArray.indexOf(countryName);
          if (index != -1) {
            codeField.setText(countriesMap.get(countryName));
            countryState = 0;
          }
        }
      }
      if (codeField.length() == 0) {
        countryButton.setText(LocaleController.getString("ChooseCountry", R.string.ChooseCountry));
        countryState = 1;
      }

      if (codeField.length() != 0) {
        AndroidUtilities.showKeyboard(phoneField);
        phoneField.requestFocus();
      } else {
        AndroidUtilities.showKeyboard(codeField);
        codeField.requestFocus();
      }
    }

    public void selectCountry(String name) {
      int index = countriesArray.indexOf(name);
      if (index != -1) {
        ignoreOnTextChange = true;
        codeField.setText(countriesMap.get(name));
        countryButton.setText(name);
        countryState = 0;
      }
    }

    private void updatePhoneField() {
      ignoreOnPhoneChange = true;
      try {
        String codeText = codeField.getText().toString();
        String phone =
            PhoneFormat.getInstance().format("+" + codeText + phoneField.getText().toString());
        int idx = phone.indexOf(" ");
        if (idx != -1) {
          String resultCode = PhoneFormat.stripExceptNumbers(phone.substring(0, idx));
          if (!codeText.equals(resultCode)) {
            phone = PhoneFormat.getInstance().format(phoneField.getText().toString()).trim();
            phoneField.setText(phone);
            phoneField.setSelection(phoneField.length());
          } else {
            phoneField.setText(phone.substring(idx).trim());
            phoneField.setSelection(phoneField.length());
          }
        } else {
          phoneField.setSelection(phoneField.length());
        }
      } catch (Exception e) {
        FileLog.e("tmessages", e);
      }
      ignoreOnPhoneChange = false;
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
      if (ignoreSelection) {
        ignoreSelection = false;
        return;
      }
      ignoreOnTextChange = true;
      String str = countriesArray.get(i);
      codeField.setText(countriesMap.get(str));
      updatePhoneField();
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {}

    @Override
    public void onNextPressed() {
      if (nextPressed) {
        return;
      }
      if (countryState == 1) {
        needShowAlert(
            LocaleController.getString("AppName", R.string.AppName),
            LocaleController.getString("ChooseCountry", R.string.ChooseCountry));
        return;
      } else if (countryState == 2 && !BuildVars.DEBUG_VERSION) {
        needShowAlert(
            LocaleController.getString("AppName", R.string.AppName),
            LocaleController.getString("WrongCountry", R.string.WrongCountry));
        return;
      }
      if (codeField.length() == 0) {
        needShowAlert(
            LocaleController.getString("AppName", R.string.AppName),
            LocaleController.getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
        return;
      }
      ConnectionsManager.getInstance().cleanUp();
      TLRPC.TL_auth_sendCode req = new TLRPC.TL_auth_sendCode();
      String phone =
          PhoneFormat.stripExceptNumbers("" + codeField.getText() + phoneField.getText());
      ConnectionsManager.getInstance().applyCountryPortNumber(phone);
      req.api_hash = BuildVars.APP_HASH;
      req.api_id = BuildVars.APP_ID;
      req.sms_type = 0;
      req.phone_number = phone;
      req.lang_code =
          LocaleController.getLocaleString(LocaleController.getInstance().getSystemDefaultLocale());
      if (req.lang_code == null || req.lang_code.length() == 0) {
        req.lang_code = "en";
      }

      final Bundle params = new Bundle();
      params.putString("phone", "+" + codeField.getText() + phoneField.getText());
      try {
        params.putString(
            "ephone",
            "+"
                + PhoneFormat.stripExceptNumbers(codeField.getText().toString())
                + " "
                + PhoneFormat.stripExceptNumbers(phoneField.getText().toString()));
      } catch (Exception e) {
        FileLog.e("tmessages", e);
        params.putString("ephone", "+" + phone);
      }
      params.putString("phoneFormated", phone);
      nextPressed = true;
      needShowProgress();
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
                          nextPressed = false;
                          if (error == null) {
                            final TLRPC.TL_auth_sentCode res = (TLRPC.TL_auth_sentCode) response;
                            params.putString("phoneHash", res.phone_code_hash);
                            params.putInt("calltime", res.send_call_timeout * 1000);
                            setPage(1, true, params, false);
                          } else {
                            if (error.text != null) {
                              if (error.text.contains("PHONE_NUMBER_INVALID")) {
                                needShowAlert(
                                    LocaleController.getString("AppName", R.string.AppName),
                                    LocaleController.getString(
                                        "InvalidPhoneNumber", R.string.InvalidPhoneNumber));
                              } else if (error.text.contains("PHONE_CODE_EMPTY")
                                  || error.text.contains("PHONE_CODE_INVALID")) {
                                needShowAlert(
                                    LocaleController.getString("AppName", R.string.AppName),
                                    LocaleController.getString(
                                        "InvalidCode", R.string.InvalidCode));
                              } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                                needShowAlert(
                                    LocaleController.getString("AppName", R.string.AppName),
                                    LocaleController.getString(
                                        "CodeExpired", R.string.CodeExpired));
                              } else if (error.text.startsWith("FLOOD_WAIT")) {
                                needShowAlert(
                                    LocaleController.getString("AppName", R.string.AppName),
                                    LocaleController.getString("FloodWait", R.string.FloodWait));
                              } else if (error.code != -1000) {
                                needShowAlert(
                                    LocaleController.getString("AppName", R.string.AppName),
                                    error.text);
                              }
                            }
                          }
                          needHideProgress();
                        }
                      });
                }
              },
              true,
              RPCRequest.RPCRequestClassGeneric
                  | RPCRequest.RPCRequestClassFailOnServerErrors
                  | RPCRequest.RPCRequestClassWithoutLogin
                  | RPCRequest.RPCRequestClassTryDifferentDc
                  | RPCRequest.RPCRequestClassEnableUnauthorized);
    }

    @Override
    public void onShow() {
      super.onShow();
      if (phoneField != null) {
        phoneField.requestFocus();
        phoneField.setSelection(phoneField.length());
      }
    }

    @Override
    public String getHeaderName() {
      return LocaleController.getString("YourPhone", R.string.YourPhone);
    }

    @Override
    public void saveStateParams(Bundle bundle) {
      String code = codeField.getText().toString();
      if (code != null && code.length() != 0) {
        bundle.putString("phoneview_code", code);
      }
      String phone = phoneField.getText().toString();
      if (phone != null && phone.length() != 0) {
        bundle.putString("phoneview_phone", phone);
      }
    }

    @Override
    public void restoreStateParams(Bundle bundle) {
      String code = bundle.getString("phoneview_code");
      if (code != null) {
        codeField.setText(code);
      }
      String phone = bundle.getString("phoneview_phone");
      if (phone != null) {
        phoneField.setText(phone);
      }
    }
  }

  public class LoginActivitySmsView extends SlideView {

    private String phoneHash;
    private String requestPhone;
    private String emailPhone;
    private EditText codeField;
    private TextView confirmTextView;
    private TextView timeText;
    private TextView problemText;
    private Bundle currentParams;

    private Timer timeTimer;
    private Timer codeTimer;
    private final Object timerSync = new Object();
    private volatile int time = 60000;
    private volatile int codeTime = 15000;
    private double lastCurrentTime;
    private double lastCodeTime;
    private boolean nextPressed = false;
    private String lastError = "";

    public LoginActivitySmsView(Context context) {
      super(context);

      setOrientation(VERTICAL);

      confirmTextView = new TextView(context);
      confirmTextView.setTextColor(0xff757575);
      confirmTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
      confirmTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
      confirmTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
      addView(confirmTextView);
      LayoutParams layoutParams = (LayoutParams) confirmTextView.getLayoutParams();
      layoutParams.width = LayoutHelper.WRAP_CONTENT;
      layoutParams.height = LayoutHelper.WRAP_CONTENT;
      layoutParams.gravity = LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT;
      confirmTextView.setLayoutParams(layoutParams);

      codeField = new EditText(context);
      codeField.setTextColor(0xff212121);
      codeField.setHint(LocaleController.getString("Code", R.string.Code));
      AndroidUtilities.clearCursorDrawable(codeField);
      codeField.setHintTextColor(0xff979797);
      codeField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
      codeField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
      codeField.setInputType(InputType.TYPE_CLASS_PHONE);
      codeField.setMaxLines(1);
      codeField.setPadding(0, 0, 0, 0);
      addView(codeField);
      layoutParams = (LayoutParams) codeField.getLayoutParams();
      layoutParams.width = LayoutHelper.MATCH_PARENT;
      layoutParams.height = AndroidUtilities.dp(36);
      layoutParams.gravity = Gravity.CENTER_HORIZONTAL;
      layoutParams.topMargin = AndroidUtilities.dp(20);
      codeField.setLayoutParams(layoutParams);
      codeField.setOnEditorActionListener(
          new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
              if (i == EditorInfo.IME_ACTION_NEXT) {
                onNextPressed();
                return true;
              }
              return false;
            }
          });

      timeText = new TextView(context);
      timeText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
      timeText.setTextColor(0xff757575);
      timeText.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
      timeText.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
      addView(timeText);
      layoutParams = (LayoutParams) timeText.getLayoutParams();
      layoutParams.width = LayoutHelper.WRAP_CONTENT;
      layoutParams.height = LayoutHelper.WRAP_CONTENT;
      layoutParams.gravity = LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT;
      layoutParams.topMargin = AndroidUtilities.dp(30);
      timeText.setLayoutParams(layoutParams);

      problemText = new TextView(context);
      problemText.setText(
          LocaleController.getString("DidNotGetTheCode", R.string.DidNotGetTheCode));
      problemText.setVisibility(time < 1000 ? VISIBLE : GONE);
      problemText.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
      problemText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
      problemText.setTextColor(0xff4d83b3);
      problemText.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
      problemText.setPadding(0, AndroidUtilities.dp(2), 0, AndroidUtilities.dp(12));
      addView(problemText);
      layoutParams = (LayoutParams) problemText.getLayoutParams();
      layoutParams.width = LayoutHelper.WRAP_CONTENT;
      layoutParams.height = LayoutHelper.WRAP_CONTENT;
      layoutParams.gravity = LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT;
      layoutParams.topMargin = AndroidUtilities.dp(20);
      problemText.setLayoutParams(layoutParams);
      problemText.setOnClickListener(
          new OnClickListener() {
            @Override
            public void onClick(View v) {
              try {
                PackageInfo pInfo =
                    ApplicationLoader.applicationContext
                        .getPackageManager()
                        .getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
                String version =
                    String.format(Locale.US, "%s (%d)", pInfo.versionName, pInfo.versionCode);

                Intent mailer = new Intent(Intent.ACTION_SEND);
                mailer.setType("message/rfc822");
                mailer.putExtra(Intent.EXTRA_EMAIL, new String[] {"sms@stel.com"});
                mailer.putExtra(
                    Intent.EXTRA_SUBJECT,
                    "Android registration/login issue " + version + " " + emailPhone);
                mailer.putExtra(
                    Intent.EXTRA_TEXT,
                    "Phone: "
                        + requestPhone
                        + "\nApp version: "
                        + version
                        + "\nOS version: SDK "
                        + Build.VERSION.SDK_INT
                        + "\nDevice Name: "
                        + Build.MANUFACTURER
                        + Build.MODEL
                        + "\nLocale: "
                        + Locale.getDefault()
                        + "\nError: "
                        + lastError);
                getContext().startActivity(Intent.createChooser(mailer, "Send email..."));
              } catch (Exception e) {
                needShowAlert(
                    LocaleController.getString("AppName", R.string.AppName),
                    LocaleController.getString("NoMailInstalled", R.string.NoMailInstalled));
              }
            }
          });

      LinearLayout linearLayout = new LinearLayout(context);
      linearLayout.setGravity(
          (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
      addView(linearLayout);
      layoutParams = (LayoutParams) linearLayout.getLayoutParams();
      layoutParams.width = LayoutHelper.MATCH_PARENT;
      layoutParams.height = LayoutHelper.MATCH_PARENT;
      layoutParams.gravity = (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
      linearLayout.setLayoutParams(layoutParams);

      TextView wrongNumber = new TextView(context);
      wrongNumber.setGravity(
          (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_HORIZONTAL);
      wrongNumber.setTextColor(0xff4d83b3);
      wrongNumber.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
      wrongNumber.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
      wrongNumber.setPadding(0, AndroidUtilities.dp(24), 0, 0);
      linearLayout.addView(wrongNumber);
      layoutParams = (LayoutParams) wrongNumber.getLayoutParams();
      layoutParams.width = LayoutHelper.WRAP_CONTENT;
      layoutParams.height = LayoutHelper.WRAP_CONTENT;
      layoutParams.gravity =
          Gravity.BOTTOM | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
      layoutParams.bottomMargin = AndroidUtilities.dp(10);
      wrongNumber.setLayoutParams(layoutParams);
      wrongNumber.setText(LocaleController.getString("WrongNumber", R.string.WrongNumber));
      wrongNumber.setOnClickListener(
          new OnClickListener() {
            @Override
            public void onClick(View view) {
              onBackPressed();
              setPage(0, true, null, true);
            }
          });
    }

    @Override
    public String getHeaderName() {
      return LocaleController.getString("YourCode", R.string.YourCode);
    }

    @Override
    public void setParams(Bundle params) {
      if (params == null) {
        return;
      }
      codeField.setText("");
      currentParams = params;
      String phone = params.getString("phone");
      emailPhone = params.getString("ephone");
      requestPhone = params.getString("phoneFormated");
      phoneHash = params.getString("phoneHash");
      time = params.getInt("calltime");

      if (phone == null) {
        return;
      }

      String number = PhoneFormat.getInstance().format(phone);
      String str =
          String.format(
              LocaleController.getString("SentSmsCode", R.string.SentSmsCode) + " %s", number);
      try {
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder(str);
        TypefaceSpan span = new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        int idx = str.indexOf(number);
        stringBuilder.setSpan(span, idx, idx + number.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        confirmTextView.setText(stringBuilder);
      } catch (Exception e) {
        FileLog.e("tmessages", e);
        confirmTextView.setText(str);
      }

      AndroidUtilities.showKeyboard(codeField);
      codeField.requestFocus();

      destroyTimer();
      destroyCodeTimer();
      timeText.setText(LocaleController.formatString("CallText", R.string.CallText, 1, 0));
      lastCurrentTime = System.currentTimeMillis();
      problemText.setVisibility(time < 1000 ? VISIBLE : GONE);

      createTimer();
    }

    private void createCodeTimer() {
      if (codeTimer != null) {
        return;
      }
      codeTime = 15000;
      codeTimer = new Timer();
      lastCodeTime = System.currentTimeMillis();
      codeTimer.schedule(
          new TimerTask() {
            @Override
            public void run() {
              double currentTime = System.currentTimeMillis();
              double diff = currentTime - lastCodeTime;
              codeTime -= diff;
              lastCodeTime = currentTime;
              AndroidUtilities.runOnUIThread(
                  new Runnable() {
                    @Override
                    public void run() {
                      if (codeTime <= 1000) {
                        problemText.setVisibility(VISIBLE);
                        destroyCodeTimer();
                      }
                    }
                  });
            }
          },
          0,
          1000);
    }

    private void destroyCodeTimer() {
      try {
        synchronized (timerSync) {
          if (codeTimer != null) {
            codeTimer.cancel();
            codeTimer = null;
          }
        }
      } catch (Exception e) {
        FileLog.e("tmessages", e);
      }
    }

    private void createTimer() {
      if (timeTimer != null) {
        return;
      }
      timeTimer = new Timer();
      timeTimer.schedule(
          new TimerTask() {
            @Override
            public void run() {
              double currentTime = System.currentTimeMillis();
              double diff = currentTime - lastCurrentTime;
              time -= diff;
              lastCurrentTime = currentTime;
              AndroidUtilities.runOnUIThread(
                  new Runnable() {
                    @Override
                    public void run() {
                      if (time >= 1000) {
                        int minutes = time / 1000 / 60;
                        int seconds = time / 1000 - minutes * 60;
                        timeText.setText(
                            LocaleController.formatString(
                                "CallText", R.string.CallText, minutes, seconds));
                      } else {
                        timeText.setText(LocaleController.getString("Calling", R.string.Calling));
                        destroyTimer();
                        createCodeTimer();
                        TLRPC.TL_auth_sendCall req = new TLRPC.TL_auth_sendCall();
                        req.phone_number = requestPhone;
                        req.phone_code_hash = phoneHash;
                        ConnectionsManager.getInstance()
                            .performRpc(
                                req,
                                new RPCRequest.RPCRequestDelegate() {
                                  @Override
                                  public void run(TLObject response, final TLRPC.TL_error error) {
                                    if (error != null && error.text != null) {
                                      AndroidUtilities.runOnUIThread(
                                          new Runnable() {
                                            @Override
                                            public void run() {
                                              lastError = error.text;
                                            }
                                          });
                                    }
                                  }
                                },
                                true,
                                RPCRequest.RPCRequestClassGeneric
                                    | RPCRequest.RPCRequestClassFailOnServerErrors
                                    | RPCRequest.RPCRequestClassWithoutLogin);
                      }
                    }
                  });
            }
          },
          0,
          1000);
    }

    private void destroyTimer() {
      try {
        synchronized (timerSync) {
          if (timeTimer != null) {
            timeTimer.cancel();
            timeTimer = null;
          }
        }
      } catch (Exception e) {
        FileLog.e("tmessages", e);
      }
    }

    @Override
    public void onNextPressed() {
      if (nextPressed) {
        return;
      }
      nextPressed = true;
      final TLRPC.TL_auth_signIn req = new TLRPC.TL_auth_signIn();
      req.phone_number = requestPhone;
      req.phone_code = codeField.getText().toString();
      req.phone_code_hash = phoneHash;
      destroyTimer();
      needShowProgress();
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
                          nextPressed = false;
                          if (error == null) {
                            needHideProgress();
                            TLRPC.TL_auth_authorization res =
                                (TLRPC.TL_auth_authorization) response;
                            destroyTimer();
                            destroyCodeTimer();
                            UserConfig.clearConfig();
                            MessagesController.getInstance().cleanUp();
                            UserConfig.setCurrentUser(res.user);
                            UserConfig.saveConfig(true);
                            MessagesStorage.getInstance().cleanUp(true);
                            ArrayList<TLRPC.User> users = new ArrayList<>();
                            users.add(res.user);
                            MessagesStorage.getInstance().putUsersAndChats(users, null, true, true);
                            MessagesController.getInstance().putUser(res.user, false);
                            ContactsController.getInstance().checkAppAccount();
                            MessagesController.getInstance().getBlockedUsers(true);
                            needFinishActivity();
                            ConnectionsManager.getInstance().initPushConnection();
                            Utilities.stageQueue.postRunnable(
                                new Runnable() {
                                  @Override
                                  public void run() {
                                    ConnectionsManager.getInstance().updateDcSettings(0);
                                  }
                                });
                          } else {
                            lastError = error.text;

                            if (error.text.contains("PHONE_NUMBER_UNOCCUPIED")) {
                              needHideProgress();
                              Bundle params = new Bundle();
                              params.putString("phoneFormated", requestPhone);
                              params.putString("phoneHash", phoneHash);
                              params.putString("code", req.phone_code);
                              setPage(2, true, params, false);
                              destroyTimer();
                              destroyCodeTimer();
                            } else if (error.text.contains("SESSION_PASSWORD_NEEDED")) {
                              TLRPC.TL_account_getPassword req2 =
                                  new TLRPC.TL_account_getPassword();
                              ConnectionsManager.getInstance()
                                  .performRpc(
                                      req2,
                                      new RPCRequest.RPCRequestDelegate() {
                                        @Override
                                        public void run(
                                            final TLObject response, final TLRPC.TL_error error) {
                                          AndroidUtilities.runOnUIThread(
                                              new Runnable() {
                                                @Override
                                                public void run() {
                                                  needHideProgress();
                                                  if (error == null) {
                                                    TLRPC.TL_account_password password =
                                                        (TLRPC.TL_account_password) response;
                                                    Bundle bundle = new Bundle();
                                                    bundle.putString(
                                                        "current_salt",
                                                        Utilities.bytesToHex(
                                                            password.current_salt));
                                                    bundle.putString("hint", password.hint);
                                                    bundle.putString(
                                                        "email_unconfirmed_pattern",
                                                        password.email_unconfirmed_pattern);
                                                    bundle.putString("phoneFormated", requestPhone);
                                                    bundle.putString("phoneHash", phoneHash);
                                                    bundle.putString("code", req.phone_code);
                                                    bundle.putInt(
                                                        "has_recovery",
                                                        password.has_recovery ? 1 : 0);
                                                    setPage(3, true, bundle, false);
                                                  } else {
                                                    needShowAlert(
                                                        LocaleController.getString(
                                                            "AppName", R.string.AppName),
                                                        error.text);
                                                  }
                                                }
                                              });
                                        }
                                      },
                                      true,
                                      RPCRequest.RPCRequestClassGeneric
                                          | RPCRequest.RPCRequestClassFailOnServerErrors
                                          | RPCRequest.RPCRequestClassWithoutLogin);
                              destroyTimer();
                              destroyCodeTimer();
                            } else {
                              needHideProgress();
                              createTimer();
                              if (error.text.contains("PHONE_NUMBER_INVALID")) {
                                needShowAlert(
                                    LocaleController.getString("AppName", R.string.AppName),
                                    LocaleController.getString(
                                        "InvalidPhoneNumber", R.string.InvalidPhoneNumber));
                              } else if (error.text.contains("PHONE_CODE_EMPTY")
                                  || error.text.contains("PHONE_CODE_INVALID")) {
                                needShowAlert(
                                    LocaleController.getString("AppName", R.string.AppName),
                                    LocaleController.getString(
                                        "InvalidCode", R.string.InvalidCode));
                              } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                                needShowAlert(
                                    LocaleController.getString("AppName", R.string.AppName),
                                    LocaleController.getString(
                                        "CodeExpired", R.string.CodeExpired));
                              } else if (error.text.startsWith("FLOOD_WAIT")) {
                                needShowAlert(
                                    LocaleController.getString("AppName", R.string.AppName),
                                    LocaleController.getString("FloodWait", R.string.FloodWait));
                              } else {
                                needShowAlert(
                                    LocaleController.getString("AppName", R.string.AppName),
                                    error.text);
                              }
                            }
                          }
                        }
                      });
                }
              },
              true,
              RPCRequest.RPCRequestClassGeneric
                  | RPCRequest.RPCRequestClassFailOnServerErrors
                  | RPCRequest.RPCRequestClassWithoutLogin);
    }

    @Override
    public void onBackPressed() {
      destroyTimer();
      destroyCodeTimer();
      currentParams = null;
    }

    @Override
    public void onDestroyActivity() {
      super.onDestroyActivity();
      destroyTimer();
      destroyCodeTimer();
    }

    @Override
    public void onShow() {
      super.onShow();
      if (codeField != null) {
        codeField.requestFocus();
        codeField.setSelection(codeField.length());
      }
    }

    @Override
    public void saveStateParams(Bundle bundle) {
      String code = codeField.getText().toString();
      if (code != null && code.length() != 0) {
        bundle.putString("smsview_code", code);
      }
      if (currentParams != null) {
        bundle.putBundle("smsview_params", currentParams);
      }
      if (time != 0) {
        bundle.putInt("time", time);
      }
    }

    @Override
    public void restoreStateParams(Bundle bundle) {
      currentParams = bundle.getBundle("smsview_params");
      if (currentParams != null) {
        setParams(currentParams);
      }
      String code = bundle.getString("smsview_code");
      if (code != null) {
        codeField.setText(code);
      }
      Integer t = bundle.getInt("time");
      if (t != 0) {
        time = t;
      }
    }
  }

  public class LoginActivityPasswordView extends SlideView {

    private EditText codeField;
    private TextView confirmTextView;
    private TextView resetAccountButton;
    private TextView resetAccountText;

    private Bundle currentParams;
    private boolean nextPressed;
    private byte[] current_salt;
    private String hint;
    private String email_unconfirmed_pattern;
    private boolean has_recovery;
    private String requestPhone;
    private String phoneHash;
    private String phoneCode;

    public LoginActivityPasswordView(Context context) {
      super(context);

      setOrientation(VERTICAL);

      confirmTextView = new TextView(context);
      confirmTextView.setTextColor(0xff757575);
      confirmTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
      confirmTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
      confirmTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
      confirmTextView.setText(
          LocaleController.getString("LoginPasswordText", R.string.LoginPasswordText));
      addView(confirmTextView);
      LayoutParams layoutParams = (LayoutParams) confirmTextView.getLayoutParams();
      layoutParams.width = LayoutHelper.WRAP_CONTENT;
      layoutParams.height = LayoutHelper.WRAP_CONTENT;
      layoutParams.gravity = LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT;
      confirmTextView.setLayoutParams(layoutParams);

      codeField = new EditText(context);
      codeField.setTextColor(0xff212121);
      AndroidUtilities.clearCursorDrawable(codeField);
      codeField.setHintTextColor(0xff979797);
      codeField.setHint(LocaleController.getString("LoginPassword", R.string.LoginPassword));
      codeField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
      codeField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
      codeField.setMaxLines(1);
      codeField.setPadding(0, 0, 0, 0);
      codeField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
      codeField.setTransformationMethod(PasswordTransformationMethod.getInstance());
      codeField.setTypeface(Typeface.DEFAULT);
      codeField.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
      addView(codeField);
      layoutParams = (LayoutParams) codeField.getLayoutParams();
      layoutParams.width = LayoutHelper.MATCH_PARENT;
      layoutParams.height = AndroidUtilities.dp(36);
      layoutParams.gravity = Gravity.CENTER_HORIZONTAL;
      layoutParams.topMargin = AndroidUtilities.dp(20);
      codeField.setLayoutParams(layoutParams);
      codeField.setOnEditorActionListener(
          new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
              if (i == EditorInfo.IME_ACTION_NEXT) {
                onNextPressed();
                return true;
              }
              return false;
            }
          });

      TextView cancelButton = new TextView(context);
      cancelButton.setGravity(
          (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
      cancelButton.setTextColor(0xff4d83b3);
      cancelButton.setText(LocaleController.getString("ForgotPassword", R.string.ForgotPassword));
      cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
      cancelButton.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
      cancelButton.setPadding(0, AndroidUtilities.dp(14), 0, 0);
      addView(cancelButton);
      layoutParams = (LayoutParams) cancelButton.getLayoutParams();
      layoutParams.width = LayoutHelper.WRAP_CONTENT;
      layoutParams.height = LayoutHelper.WRAP_CONTENT;
      layoutParams.gravity = Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
      cancelButton.setLayoutParams(layoutParams);
      cancelButton.setOnClickListener(
          new OnClickListener() {
            @Override
            public void onClick(View view) {
              if (has_recovery) {
                needShowProgress();
                TLRPC.TL_auth_requestPasswordRecovery req =
                    new TLRPC.TL_auth_requestPasswordRecovery();
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
                                    needHideProgress();
                                    if (error == null) {
                                      final TLRPC.TL_auth_passwordRecovery res =
                                          (TLRPC.TL_auth_passwordRecovery) response;
                                      AlertDialog.Builder builder =
                                          new AlertDialog.Builder(getParentActivity());
                                      builder.setMessage(
                                          LocaleController.formatString(
                                              "RestoreEmailSent",
                                              R.string.RestoreEmailSent,
                                              res.email_pattern));
                                      builder.setTitle(
                                          LocaleController.getString("AppName", R.string.AppName));
                                      builder.setPositiveButton(
                                          LocaleController.getString("OK", R.string.OK),
                                          new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(
                                                DialogInterface dialogInterface, int i) {
                                              Bundle bundle = new Bundle();
                                              bundle.putString(
                                                  "email_unconfirmed_pattern", res.email_pattern);
                                              setPage(4, true, bundle, false);
                                            }
                                          });
                                      Dialog dialog = showDialog(builder.create());
                                      if (dialog != null) {
                                        dialog.setCanceledOnTouchOutside(false);
                                        dialog.setCancelable(false);
                                      }
                                    } else {
                                      if (error.text.startsWith("FLOOD_WAIT")) {
                                        int time = Utilities.parseInt(error.text);
                                        String timeString;
                                        if (time < 60) {
                                          timeString =
                                              LocaleController.formatPluralString("Seconds", time);
                                        } else {
                                          timeString =
                                              LocaleController.formatPluralString(
                                                  "Minutes", time / 60);
                                        }
                                        needShowAlert(
                                            LocaleController.getString("AppName", R.string.AppName),
                                            LocaleController.formatString(
                                                "FloodWaitTime",
                                                R.string.FloodWaitTime,
                                                timeString));
                                      } else {
                                        needShowAlert(
                                            LocaleController.getString("AppName", R.string.AppName),
                                            error.text);
                                      }
                                    }
                                  }
                                });
                          }
                        },
                        true,
                        RPCRequest.RPCRequestClassGeneric
                            | RPCRequest.RPCRequestClassFailOnServerErrors
                            | RPCRequest.RPCRequestClassWithoutLogin);
              } else {
                resetAccountText.setVisibility(VISIBLE);
                resetAccountButton.setVisibility(VISIBLE);
                AndroidUtilities.hideKeyboard(codeField);
                needShowAlert(
                    LocaleController.getString(
                        "RestorePasswordNoEmailTitle", R.string.RestorePasswordNoEmailTitle),
                    LocaleController.getString(
                        "RestorePasswordNoEmailText", R.string.RestorePasswordNoEmailText));
              }
            }
          });

      resetAccountButton = new TextView(context);
      resetAccountButton.setGravity(
          (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
      resetAccountButton.setTextColor(0xffff6666);
      resetAccountButton.setVisibility(GONE);
      resetAccountButton.setText(
          LocaleController.getString("ResetMyAccount", R.string.ResetMyAccount));
      resetAccountButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
      resetAccountButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
      resetAccountButton.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
      resetAccountButton.setPadding(0, AndroidUtilities.dp(14), 0, 0);
      addView(resetAccountButton);
      layoutParams = (LayoutParams) resetAccountButton.getLayoutParams();
      layoutParams.width = LayoutHelper.WRAP_CONTENT;
      layoutParams.height = LayoutHelper.WRAP_CONTENT;
      layoutParams.gravity = Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
      layoutParams.topMargin = AndroidUtilities.dp(34);
      resetAccountButton.setLayoutParams(layoutParams);
      resetAccountButton.setOnClickListener(
          new OnClickListener() {
            @Override
            public void onClick(View view) {
              AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
              builder.setMessage(
                  LocaleController.getString(
                      "ResetMyAccountWarningText", R.string.ResetMyAccountWarningText));
              builder.setTitle(
                  LocaleController.getString(
                      "ResetMyAccountWarning", R.string.ResetMyAccountWarning));
              builder.setPositiveButton(
                  LocaleController.getString(
                      "ResetMyAccountWarningReset", R.string.ResetMyAccountWarningReset),
                  new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                      needShowProgress();
                      TLRPC.TL_account_deleteAccount req = new TLRPC.TL_account_deleteAccount();
                      req.reason = "Forgot password";
                      ConnectionsManager.getInstance()
                          .performRpc(
                              req,
                              new RPCRequest.RPCRequestDelegate() {
                                @Override
                                public void run(TLObject response, final TLRPC.TL_error error) {
                                  AndroidUtilities.runOnUIThread(
                                      new Runnable() {
                                        @Override
                                        public void run() {
                                          needHideProgress();
                                          if (error == null) {
                                            Bundle params = new Bundle();
                                            params.putString("phoneFormated", requestPhone);
                                            params.putString("phoneHash", phoneHash);
                                            params.putString("code", phoneCode);
                                            setPage(2, true, params, false);
                                          } else {
                                            needShowAlert(
                                                LocaleController.getString(
                                                    "AppName", R.string.AppName),
                                                error.text);
                                          }
                                        }
                                      });
                                }
                              },
                              true,
                              RPCRequest.RPCRequestClassGeneric
                                  | RPCRequest.RPCRequestClassWithoutLogin
                                  | RPCRequest.RPCRequestClassFailOnServerErrors);
                    }
                  });
              builder.setNegativeButton(
                  LocaleController.getString("Cancel", R.string.Cancel), null);
              showDialog(builder.create());
            }
          });

      resetAccountText = new TextView(context);
      resetAccountText.setGravity(
          (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
      resetAccountText.setVisibility(GONE);
      resetAccountText.setTextColor(0xff757575);
      resetAccountText.setText(
          LocaleController.getString("ResetMyAccountText", R.string.ResetMyAccountText));
      resetAccountText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
      resetAccountText.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
      addView(resetAccountText);
      layoutParams = (LayoutParams) resetAccountText.getLayoutParams();
      layoutParams.width = LayoutHelper.WRAP_CONTENT;
      layoutParams.height = LayoutHelper.WRAP_CONTENT;
      layoutParams.gravity = Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
      layoutParams.bottomMargin = AndroidUtilities.dp(14);
      layoutParams.topMargin = AndroidUtilities.dp(7);
      resetAccountText.setLayoutParams(layoutParams);
    }

    @Override
    public String getHeaderName() {
      return LocaleController.getString("LoginPassword", R.string.LoginPassword);
    }

    @Override
    public void setParams(Bundle params) {
      if (params == null) {
        return;
      }
      if (params.isEmpty()) {
        resetAccountButton.setVisibility(VISIBLE);
        resetAccountText.setVisibility(VISIBLE);
        AndroidUtilities.hideKeyboard(codeField);
        return;
      }
      resetAccountButton.setVisibility(GONE);
      resetAccountText.setVisibility(GONE);
      codeField.setText("");
      currentParams = params;
      current_salt = Utilities.hexToBytes(currentParams.getString("current_salt"));
      hint = currentParams.getString("hint");
      has_recovery = currentParams.getInt("has_recovery") == 1;
      email_unconfirmed_pattern = currentParams.getString("email_unconfirmed_pattern");
      requestPhone = params.getString("phoneFormated");
      phoneHash = params.getString("phoneHash");
      phoneCode = params.getString("code");

      AndroidUtilities.showKeyboard(codeField);
      codeField.requestFocus();

      if (hint != null && hint.length() > 0) {
        codeField.setHint(hint);
      } else {
        codeField.setHint(LocaleController.getString("LoginPassword", R.string.LoginPassword));
      }
    }

    private void onPasscodeError(boolean clear) {
      if (getParentActivity() == null) {
        return;
      }
      Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
      if (v != null) {
        v.vibrate(200);
      }
      if (clear) {
        codeField.setText("");
      }
      AndroidUtilities.shakeTextView(confirmTextView, 2, 0);
    }

    @Override
    public void onNextPressed() {
      if (nextPressed) {
        return;
      }

      String oldPassword = codeField.getText().toString();
      if (oldPassword.length() == 0) {
        onPasscodeError(false);
        return;
      }
      nextPressed = true;
      byte[] oldPasswordBytes = null;
      try {
        oldPasswordBytes = oldPassword.getBytes("UTF-8");
      } catch (Exception e) {
        FileLog.e("tmessages", e);
      }

      needShowProgress();
      byte[] hash = new byte[current_salt.length * 2 + oldPasswordBytes.length];
      System.arraycopy(current_salt, 0, hash, 0, current_salt.length);
      System.arraycopy(oldPasswordBytes, 0, hash, current_salt.length, oldPasswordBytes.length);
      System.arraycopy(
          current_salt, 0, hash, hash.length - current_salt.length, current_salt.length);

      final TLRPC.TL_auth_checkPassword req = new TLRPC.TL_auth_checkPassword();
      req.password_hash = Utilities.computeSHA256(hash, 0, hash.length);
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
                          needHideProgress();
                          nextPressed = false;
                          if (error == null) {
                            TLRPC.TL_auth_authorization res =
                                (TLRPC.TL_auth_authorization) response;
                            UserConfig.clearConfig();
                            MessagesController.getInstance().cleanUp();
                            UserConfig.setCurrentUser(res.user);
                            UserConfig.saveConfig(true);
                            MessagesStorage.getInstance().cleanUp(true);
                            ArrayList<TLRPC.User> users = new ArrayList<>();
                            users.add(res.user);
                            MessagesStorage.getInstance().putUsersAndChats(users, null, true, true);
                            MessagesController.getInstance().putUser(res.user, false);
                            ContactsController.getInstance().checkAppAccount();
                            MessagesController.getInstance().getBlockedUsers(true);
                            needFinishActivity();
                            ConnectionsManager.getInstance().initPushConnection();
                            Utilities.stageQueue.postRunnable(
                                new Runnable() {
                                  @Override
                                  public void run() {
                                    ConnectionsManager.getInstance().updateDcSettings(0);
                                  }
                                });
                          } else {
                            if (error.text.equals("PASSWORD_HASH_INVALID")) {
                              onPasscodeError(true);
                            } else if (error.text.startsWith("FLOOD_WAIT")) {
                              int time = Utilities.parseInt(error.text);
                              String timeString;
                              if (time < 60) {
                                timeString = LocaleController.formatPluralString("Seconds", time);
                              } else {
                                timeString =
                                    LocaleController.formatPluralString("Minutes", time / 60);
                              }
                              needShowAlert(
                                  LocaleController.getString("AppName", R.string.AppName),
                                  LocaleController.formatString(
                                      "FloodWaitTime", R.string.FloodWaitTime, timeString));
                            } else {
                              needShowAlert(
                                  LocaleController.getString("AppName", R.string.AppName),
                                  error.text);
                            }
                          }
                        }
                      });
                }
              },
              true,
              RPCRequest.RPCRequestClassGeneric
                  | RPCRequest.RPCRequestClassFailOnServerErrors
                  | RPCRequest.RPCRequestClassWithoutLogin);
    }

    @Override
    public boolean needBackButton() {
      return true;
    }

    @Override
    public void onBackPressed() {
      currentParams = null;
    }

    @Override
    public void onShow() {
      super.onShow();
      if (codeField != null) {
        codeField.requestFocus();
        codeField.setSelection(codeField.length());
      }
    }

    @Override
    public void saveStateParams(Bundle bundle) {
      String code = codeField.getText().toString();
      if (code != null && code.length() != 0) {
        bundle.putString("passview_code", code);
      }
      if (currentParams != null) {
        bundle.putBundle("passview_params", currentParams);
      }
    }

    @Override
    public void restoreStateParams(Bundle bundle) {
      currentParams = bundle.getBundle("passview_params");
      if (currentParams != null) {
        setParams(currentParams);
      }
      String code = bundle.getString("passview_code");
      if (code != null) {
        codeField.setText(code);
      }
    }
  }

  public class LoginActivityRecoverView extends SlideView {

    private EditText codeField;
    private TextView confirmTextView;
    private TextView cancelButton;

    private Bundle currentParams;
    private boolean nextPressed;
    private String email_unconfirmed_pattern;

    public LoginActivityRecoverView(Context context) {
      super(context);

      setOrientation(VERTICAL);

      confirmTextView = new TextView(context);
      confirmTextView.setTextColor(0xff757575);
      confirmTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
      confirmTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
      confirmTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
      confirmTextView.setText(
          LocaleController.getString("RestoreEmailSentInfo", R.string.RestoreEmailSentInfo));
      addView(confirmTextView);
      LayoutParams layoutParams = (LayoutParams) confirmTextView.getLayoutParams();
      layoutParams.width = LayoutHelper.WRAP_CONTENT;
      layoutParams.height = LayoutHelper.WRAP_CONTENT;
      layoutParams.gravity = (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
      confirmTextView.setLayoutParams(layoutParams);

      codeField = new EditText(context);
      codeField.setTextColor(0xff212121);
      AndroidUtilities.clearCursorDrawable(codeField);
      codeField.setHintTextColor(0xff979797);
      codeField.setHint(LocaleController.getString("PasswordCode", R.string.PasswordCode));
      codeField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
      codeField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
      codeField.setMaxLines(1);
      codeField.setPadding(0, 0, 0, 0);
      codeField.setInputType(InputType.TYPE_CLASS_PHONE);
      codeField.setTransformationMethod(PasswordTransformationMethod.getInstance());
      codeField.setTypeface(Typeface.DEFAULT);
      codeField.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
      addView(codeField);
      layoutParams = (LayoutParams) codeField.getLayoutParams();
      layoutParams.width = LayoutHelper.MATCH_PARENT;
      layoutParams.height = AndroidUtilities.dp(36);
      layoutParams.gravity = Gravity.CENTER_HORIZONTAL;
      layoutParams.topMargin = AndroidUtilities.dp(20);
      codeField.setLayoutParams(layoutParams);
      codeField.setOnEditorActionListener(
          new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
              if (i == EditorInfo.IME_ACTION_NEXT) {
                onNextPressed();
                return true;
              }
              return false;
            }
          });

      cancelButton = new TextView(context);
      cancelButton.setGravity(
          (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM);
      cancelButton.setTextColor(0xff4d83b3);
      cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
      cancelButton.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
      cancelButton.setPadding(0, AndroidUtilities.dp(14), 0, 0);
      addView(cancelButton);
      layoutParams = (LayoutParams) cancelButton.getLayoutParams();
      layoutParams.width = LayoutHelper.WRAP_CONTENT;
      layoutParams.height = LayoutHelper.WRAP_CONTENT;
      layoutParams.gravity =
          Gravity.BOTTOM | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
      layoutParams.bottomMargin = AndroidUtilities.dp(14);
      cancelButton.setLayoutParams(layoutParams);
      cancelButton.setOnClickListener(
          new OnClickListener() {
            @Override
            public void onClick(View view) {
              AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
              builder.setMessage(
                  LocaleController.getString(
                      "RestoreEmailTroubleText", R.string.RestoreEmailTroubleText));
              builder.setTitle(
                  LocaleController.getString(
                      "RestorePasswordNoEmailTitle", R.string.RestorePasswordNoEmailTitle));
              builder.setPositiveButton(
                  LocaleController.getString("OK", R.string.OK),
                  new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                      setPage(3, true, new Bundle(), true);
                    }
                  });
              Dialog dialog = showDialog(builder.create());
              if (dialog != null) {
                dialog.setCanceledOnTouchOutside(false);
                dialog.setCancelable(false);
              }
            }
          });
    }

    @Override
    public boolean needBackButton() {
      return true;
    }

    @Override
    public String getHeaderName() {
      return LocaleController.getString("LoginPassword", R.string.LoginPassword);
    }

    @Override
    public void setParams(Bundle params) {
      if (params == null) {
        return;
      }
      codeField.setText("");
      currentParams = params;
      email_unconfirmed_pattern = currentParams.getString("email_unconfirmed_pattern");
      cancelButton.setText(
          LocaleController.formatString(
              "RestoreEmailTrouble", R.string.RestoreEmailTrouble, email_unconfirmed_pattern));

      AndroidUtilities.showKeyboard(codeField);
      codeField.requestFocus();
    }

    private void onPasscodeError(boolean clear) {
      if (getParentActivity() == null) {
        return;
      }
      Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
      if (v != null) {
        v.vibrate(200);
      }
      if (clear) {
        codeField.setText("");
      }
      AndroidUtilities.shakeTextView(confirmTextView, 2, 0);
    }

    @Override
    public void onNextPressed() {
      if (nextPressed) {
        return;
      }

      String oldPassword = codeField.getText().toString();
      if (oldPassword.length() == 0) {
        onPasscodeError(false);
        return;
      }
      nextPressed = true;

      String code = codeField.getText().toString();
      if (code.length() == 0) {
        onPasscodeError(false);
        return;
      }
      needShowProgress();
      TLRPC.TL_auth_recoverPassword req = new TLRPC.TL_auth_recoverPassword();
      req.code = code;
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
                          needHideProgress();
                          nextPressed = false;
                          if (error == null) {
                            TLRPC.TL_auth_authorization res =
                                (TLRPC.TL_auth_authorization) response;
                            UserConfig.clearConfig();
                            MessagesController.getInstance().cleanUp();
                            UserConfig.setCurrentUser(res.user);
                            UserConfig.saveConfig(true);
                            MessagesStorage.getInstance().cleanUp(true);
                            ArrayList<TLRPC.User> users = new ArrayList<>();
                            users.add(res.user);
                            MessagesStorage.getInstance().putUsersAndChats(users, null, true, true);
                            MessagesController.getInstance().putUser(res.user, false);
                            ContactsController.getInstance().checkAppAccount();
                            MessagesController.getInstance().getBlockedUsers(true);
                            needFinishActivity();
                            ConnectionsManager.getInstance().initPushConnection();
                            Utilities.stageQueue.postRunnable(
                                new Runnable() {
                                  @Override
                                  public void run() {
                                    ConnectionsManager.getInstance().updateDcSettings(0);
                                  }
                                });
                          } else {
                            if (error.text.startsWith("CODE_INVALID")) {
                              onPasscodeError(true);
                            } else if (error.text.startsWith("FLOOD_WAIT")) {
                              int time = Utilities.parseInt(error.text);
                              String timeString;
                              if (time < 60) {
                                timeString = LocaleController.formatPluralString("Seconds", time);
                              } else {
                                timeString =
                                    LocaleController.formatPluralString("Minutes", time / 60);
                              }
                              needShowAlert(
                                  LocaleController.getString("AppName", R.string.AppName),
                                  LocaleController.formatString(
                                      "FloodWaitTime", R.string.FloodWaitTime, timeString));
                            } else {
                              needShowAlert(
                                  LocaleController.getString("AppName", R.string.AppName),
                                  error.text);
                            }
                          }
                        }
                      });
                }
              },
              true,
              RPCRequest.RPCRequestClassGeneric
                  | RPCRequest.RPCRequestClassFailOnServerErrors
                  | RPCRequest.RPCRequestClassWithoutLogin);
    }

    @Override
    public void onBackPressed() {
      currentParams = null;
    }

    @Override
    public void onShow() {
      super.onShow();
      if (codeField != null) {
        codeField.requestFocus();
        codeField.setSelection(codeField.length());
      }
    }

    @Override
    public void saveStateParams(Bundle bundle) {
      String code = codeField.getText().toString();
      if (code != null && code.length() != 0) {
        bundle.putString("recoveryview_code", code);
      }
      if (currentParams != null) {
        bundle.putBundle("recoveryview_params", currentParams);
      }
    }

    @Override
    public void restoreStateParams(Bundle bundle) {
      currentParams = bundle.getBundle("recoveryview_params");
      if (currentParams != null) {
        setParams(currentParams);
      }
      String code = bundle.getString("recoveryview_code");
      if (code != null) {
        codeField.setText(code);
      }
    }
  }

  public class LoginActivityRegisterView extends SlideView {

    private EditText firstNameField;
    private EditText lastNameField;
    private String requestPhone;
    private String phoneHash;
    private String phoneCode;
    private Bundle currentParams;
    private boolean nextPressed = false;

    public LoginActivityRegisterView(Context context) {
      super(context);

      setOrientation(VERTICAL);

      TextView textView = new TextView(context);
      textView.setText(LocaleController.getString("RegisterText", R.string.RegisterText));
      textView.setTextColor(0xff757575);
      textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
      textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
      addView(textView);
      LayoutParams layoutParams = (LayoutParams) textView.getLayoutParams();
      layoutParams.width = LayoutHelper.WRAP_CONTENT;
      layoutParams.height = LayoutHelper.WRAP_CONTENT;
      layoutParams.topMargin = AndroidUtilities.dp(8);
      layoutParams.gravity = LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT;
      textView.setLayoutParams(layoutParams);

      firstNameField = new EditText(context);
      firstNameField.setHintTextColor(0xff979797);
      firstNameField.setTextColor(0xff212121);
      AndroidUtilities.clearCursorDrawable(firstNameField);
      firstNameField.setHint(LocaleController.getString("FirstName", R.string.FirstName));
      firstNameField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
      firstNameField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
      firstNameField.setMaxLines(1);
      firstNameField.setInputType(InputType.TYPE_TEXT_FLAG_CAP_WORDS);
      addView(firstNameField);
      layoutParams = (LayoutParams) firstNameField.getLayoutParams();
      layoutParams.width = LayoutHelper.MATCH_PARENT;
      layoutParams.height = AndroidUtilities.dp(36);
      layoutParams.topMargin = AndroidUtilities.dp(26);
      firstNameField.setLayoutParams(layoutParams);
      firstNameField.setOnEditorActionListener(
          new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
              if (i == EditorInfo.IME_ACTION_NEXT) {
                lastNameField.requestFocus();
                return true;
              }
              return false;
            }
          });

      lastNameField = new EditText(context);
      lastNameField.setHint(LocaleController.getString("LastName", R.string.LastName));
      lastNameField.setHintTextColor(0xff979797);
      lastNameField.setTextColor(0xff212121);
      AndroidUtilities.clearCursorDrawable(lastNameField);
      lastNameField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
      lastNameField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
      lastNameField.setMaxLines(1);
      lastNameField.setInputType(InputType.TYPE_TEXT_FLAG_CAP_WORDS);
      addView(lastNameField);
      layoutParams = (LayoutParams) lastNameField.getLayoutParams();
      layoutParams.width = LayoutHelper.MATCH_PARENT;
      layoutParams.height = AndroidUtilities.dp(36);
      layoutParams.topMargin = AndroidUtilities.dp(10);
      lastNameField.setLayoutParams(layoutParams);

      LinearLayout linearLayout = new LinearLayout(context);
      linearLayout.setGravity(Gravity.BOTTOM | Gravity.CENTER_VERTICAL);
      addView(linearLayout);
      layoutParams = (LayoutParams) linearLayout.getLayoutParams();
      layoutParams.width = LayoutHelper.MATCH_PARENT;
      layoutParams.height = LayoutHelper.MATCH_PARENT;
      linearLayout.setLayoutParams(layoutParams);

      TextView wrongNumber = new TextView(context);
      wrongNumber.setText(
          LocaleController.getString("CancelRegistration", R.string.CancelRegistration));
      wrongNumber.setGravity(
          (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_HORIZONTAL);
      wrongNumber.setTextColor(0xff4d83b3);
      wrongNumber.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
      wrongNumber.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
      wrongNumber.setPadding(0, AndroidUtilities.dp(24), 0, 0);
      linearLayout.addView(wrongNumber);
      layoutParams = (LayoutParams) wrongNumber.getLayoutParams();
      layoutParams.width = LayoutHelper.WRAP_CONTENT;
      layoutParams.height = LayoutHelper.WRAP_CONTENT;
      layoutParams.gravity =
          Gravity.BOTTOM | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
      layoutParams.bottomMargin = AndroidUtilities.dp(10);
      wrongNumber.setLayoutParams(layoutParams);
      wrongNumber.setOnClickListener(
          new OnClickListener() {
            @Override
            public void onClick(View view) {
              AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
              builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
              builder.setMessage(
                  LocaleController.getString(
                      "AreYouSureRegistration", R.string.AreYouSureRegistration));
              builder.setPositiveButton(
                  LocaleController.getString("OK", R.string.OK),
                  new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                      onBackPressed();
                      setPage(0, true, null, true);
                    }
                  });
              builder.setNegativeButton(
                  LocaleController.getString("Cancel", R.string.Cancel), null);
              showDialog(builder.create());
            }
          });
    }

    @Override
    public void onBackPressed() {
      currentParams = null;
    }

    @Override
    public String getHeaderName() {
      return LocaleController.getString("YourName", R.string.YourName);
    }

    @Override
    public void onShow() {
      super.onShow();
      if (firstNameField != null) {
        firstNameField.requestFocus();
        firstNameField.setSelection(firstNameField.length());
      }
    }

    @Override
    public void setParams(Bundle params) {
      if (params == null) {
        return;
      }
      firstNameField.setText("");
      lastNameField.setText("");
      requestPhone = params.getString("phoneFormated");
      phoneHash = params.getString("phoneHash");
      phoneCode = params.getString("code");
      currentParams = params;
    }

    @Override
    public void onNextPressed() {
      if (nextPressed) {
        return;
      }
      nextPressed = true;
      TLRPC.TL_auth_signUp req = new TLRPC.TL_auth_signUp();
      req.phone_code = phoneCode;
      req.phone_code_hash = phoneHash;
      req.phone_number = requestPhone;
      req.first_name = firstNameField.getText().toString();
      req.last_name = lastNameField.getText().toString();
      needShowProgress();
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
                          nextPressed = false;
                          needHideProgress();
                          if (error == null) {
                            final TLRPC.TL_auth_authorization res =
                                (TLRPC.TL_auth_authorization) response;
                            UserConfig.clearConfig();
                            MessagesController.getInstance().cleanUp();
                            UserConfig.setCurrentUser(res.user);
                            UserConfig.saveConfig(true);
                            MessagesStorage.getInstance().cleanUp(true);
                            ArrayList<TLRPC.User> users = new ArrayList<>();
                            users.add(res.user);
                            MessagesStorage.getInstance().putUsersAndChats(users, null, true, true);
                            MessagesController.getInstance().putUser(res.user, false);
                            ContactsController.getInstance().checkAppAccount();
                            MessagesController.getInstance().getBlockedUsers(true);
                            needFinishActivity();
                            ConnectionsManager.getInstance().initPushConnection();
                            AxolotlController.getInstance().registerAxolotlIdentity();
                            Utilities.stageQueue.postRunnable(
                                new Runnable() {
                                  @Override
                                  public void run() {
                                    ConnectionsManager.getInstance().updateDcSettings(0);
                                  }
                                });
                          } else {
                            if (error.text.contains("PHONE_NUMBER_INVALID")) {
                              needShowAlert(
                                  LocaleController.getString("AppName", R.string.AppName),
                                  LocaleController.getString(
                                      "InvalidPhoneNumber", R.string.InvalidPhoneNumber));
                            } else if (error.text.contains("PHONE_CODE_EMPTY")
                                || error.text.contains("PHONE_CODE_INVALID")) {
                              needShowAlert(
                                  LocaleController.getString("AppName", R.string.AppName),
                                  LocaleController.getString("InvalidCode", R.string.InvalidCode));
                            } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                              needShowAlert(
                                  LocaleController.getString("AppName", R.string.AppName),
                                  LocaleController.getString("CodeExpired", R.string.CodeExpired));
                            } else if (error.text.contains("FIRSTNAME_INVALID")) {
                              needShowAlert(
                                  LocaleController.getString("AppName", R.string.AppName),
                                  LocaleController.getString(
                                      "InvalidFirstName", R.string.InvalidFirstName));
                            } else if (error.text.contains("LASTNAME_INVALID")) {
                              needShowAlert(
                                  LocaleController.getString("AppName", R.string.AppName),
                                  LocaleController.getString(
                                      "InvalidLastName", R.string.InvalidLastName));
                            } else {
                              needShowAlert(
                                  LocaleController.getString("AppName", R.string.AppName),
                                  error.text);
                            }
                          }
                        }
                      });
                }
              },
              true,
              RPCRequest.RPCRequestClassGeneric
                  | RPCRequest.RPCRequestClassWithoutLogin
                  | RPCRequest.RPCRequestClassFailOnServerErrors);
    }

    @Override
    public void saveStateParams(Bundle bundle) {
      String first = firstNameField.getText().toString();
      if (first != null && first.length() != 0) {
        bundle.putString("registerview_first", first);
      }
      String last = lastNameField.getText().toString();
      if (last != null && last.length() != 0) {
        bundle.putString("registerview_last", last);
      }
      if (currentParams != null) {
        bundle.putBundle("registerview_params", currentParams);
      }
    }

    @Override
    public void restoreStateParams(Bundle bundle) {
      currentParams = bundle.getBundle("registerview_params");
      if (currentParams != null) {
        setParams(currentParams);
      }
      String first = bundle.getString("registerview_first");
      if (first != null) {
        firstNameField.setText(first);
      }
      String last = bundle.getString("registerview_last");
      if (last != null) {
        lastNameField.setText(last);
      }
    }
  }
}
