package com.animbus.music.ui.activity.settings;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.afollestad.appthemeengine.ATE;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.color.ColorChooserDialog;
import com.android.vending.billing.IInAppBillingService;
import com.animbus.music.R;
import com.animbus.music.ui.activity.settings.chooseIcon.ChooseIcon;
import com.animbus.music.ui.custom.activity.ThemeActivity;
import com.animbus.music.util.Options;

import org.json.JSONException;
import org.json.JSONObject;

//TODO: Switch to more supported settings fragments
public class Settings extends ThemeActivity implements ColorChooserDialog.ColorCallback {

    @Override
    protected void init() {
        setContentView(R.layout.activity_settings);
    }

    @Override
    protected void setVariables() {
        //Everything is done either in ThemeActivity or ATE

        //Temp
        pageNamesSwitch = (SwitchCompat) findViewById(R.id.settings_old_page_names_switch);
        paletteSwitch = (SwitchCompat) findViewById(R.id.settings_old_palette_switch);
        tabsSwitch = (SwitchCompat) findViewById(R.id.settings_old_tabs_switch);
        scrollableTabsSwitch = (SwitchCompat) findViewById(R.id.settings_old_tab_scrollable_switch);
        tabsIconsSwitch = (SwitchCompat) findViewById(R.id.settings_old_tabs_icons);
    }

    @Override
    protected void setUp() {
        //Everything is done either in ThemeActivity or ATE

        //Temp
        loadSettings();
    }

    @Override
    protected int getOptionsMenu() {
        return R.menu.menu_settings;
    }

    @Override
    protected boolean processMenuItem(int id) {
        switch (id) {
            case R.id.action_donate:
                showDonation();
                return true;
            case R.id.action_reset:
                Options.resetPrefs();
                return true;
        }
        return super.processMenuItem(id);
    }

    @Override
    protected void onDestroy() {
        if (mService != null) unbindService(mPlayConnection);
        super.onDestroy();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Settings
    ///////////////////////////////////////////////////////////////////////////

    SwitchCompat
            pageNamesSwitch,
            paletteSwitch,
            tabsSwitch,
            scrollableTabsSwitch,
            tabsIconsSwitch;

    private void saveSettings() {
        Options.setUseCategoryNames(pageNamesSwitch.isChecked());
        Options.setUsePalette(paletteSwitch.isChecked());
        Options.setUseTabs(tabsSwitch.isChecked());
        Options.setUseScrollableTabs(scrollableTabsSwitch.isChecked());
        Options.setUseIconTabs(tabsIconsSwitch.isChecked());
    }

    private void loadSettings() {
        pageNamesSwitch.setChecked(Options.usingCategoryNames());
        paletteSwitch.setChecked(Options.usingPalette());
        tabsSwitch.setChecked(Options.usingTabs());
        scrollableTabsSwitch.setChecked(Options.usingScrollableTabs());
        tabsIconsSwitch.setChecked(Options.usingIconTabs());
        settingChanged(null);
    }

    public void settingChanged(View v) {
        //This is where you add dependancies
        Options.switchDependency(tabsSwitch, true, pageNamesSwitch, false);
        Options.switchDependency(tabsSwitch, false, tabsIconsSwitch, false);
        Options.doubleSwitchDependency(tabsSwitch, tabsIconsSwitch, scrollableTabsSwitch, false, true, false);

        //Saves the settings
        if (v != null) saveSettings();
    }

    public void openAbout(View v) {
        startActivity(new Intent(this, About.class));
    }

    public void openIconSelector(View v) {
        startActivity(new Intent(this, ChooseIcon.class));
    }

    public void showThemePicker(View v) {
        new MaterialDialog.Builder(this).title(R.string.settings_theme_title_choose)
                .items(R.array.settings_theme_items_choose).itemsCallback(new MaterialDialog.ListCallback() {
            @Override
            public void onSelection(MaterialDialog materialDialog, View view, int i, CharSequence charSequence) {
                ATE.config(Settings.this, getATEKey()).activityTheme(getStyleFromPos(i)).apply(Settings.this);
                Options.setLightTheme(i == 2);
            }
        })
                /*.theme(!Options.isLightTheme() ? Theme.DARK : Theme.LIGHT)*/
                .dismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        Settings.this.recreate();
                    }
                }).show();
    }

    private int getStyleFromPos(int pos) {
        switch (pos) {
            case 0:
                return R.style.AppTheme;
            case 1:
                return R.style.AppTheme_Faithful;
            case 2:
                return R.style.AppTheme_Light;
        }
        return 0;
    }

    public void showPrimaryColorDialog(View v) {
        new ColorChooserDialog.Builder(this, R.string.title_color_chooser_primary)
                .accentMode(false)
                .preselect(getPrimaryColor())
                .allowUserColorInputAlpha(false)
                .show();
    }

    public void showAccentColorDialog(View v) {
        new ColorChooserDialog.Builder(this, R.string.title_color_chooser_accent)
                .accentMode(true)
                .preselect(getAccentColor())
                .allowUserColorInputAlpha(false)
                .show();
    }

    public void resetPrimaryColor(View v) {
        ATE.config(this, getATEKey()).accentColor(resolveColorAttr(android.R.attr.colorBackground)).apply(this);
        invalidate();
    }

    @Override
    public void onColorSelection(@NonNull ColorChooserDialog colorChooserDialog, @ColorInt int i) {
        if (!colorChooserDialog.isAccentMode()) {
            ATE.config(this, getATEKey()).primaryColor(i).apply(this);
        } else {
            ATE.config(this, getATEKey()).accentColor(i).apply(this);
        }
        invalidate();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Donations
    ///////////////////////////////////////////////////////////////////////////

    IInAppBillingService mService;
    ServiceConnection mPlayConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = IInAppBillingService.Stub.asInterface(service);
            Log.d("Donations", "CONNECTED");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            Log.d("Donations", "DISCONNECTED");
        }
    };

    public void showDonation() {
        new AlertDialog.Builder(this).setTitle(getResources().getString(R.string.settings_donate_disambiguation_title))
                .setItems(new String[]{getResources().getString(R.string.settings_donate_disambiguation_play),
                        getResources().getString(R.string.settings_donate_disambiguation_paypal)}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND").setPackage("com.android.vending");
                                Boolean isConnected = bindService(serviceIntent, mPlayConnection, Context.BIND_AUTO_CREATE);
                                Log.d("Donations", String.valueOf(isConnected));
                                induceDonatePrices(true);
                                break;
                            case 1:
                                donate(0, false);
                                break;
                        }
                    }
                }).create().show();
    }

    private void induceDonatePrices(final Boolean useGooglePlay) {
        DialogInterface.OnClickListener listener;
        listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                int amount = 0;
                switch (which) {
                    case 0:
                        amount = 1;
                        break;
                    case 1:
                        amount = 5;
                        break;
                    case 2:
                        amount = 10;
                        break;
                    case 3:
                        amount = 25;
                        break;
                    case 4:
                        amount = 50;
                        break;
                }
                donate(amount, useGooglePlay);
            }
        };
        new AlertDialog.Builder(this).setTitle(getResources().getString(R.string.settings_donate_price_title))
                .setItems(new String[]{
                        getResources().getString(R.string.settings_donate_price_1),
                        getResources().getString(R.string.settings_donate_price_5),
                        getResources().getString(R.string.settings_donate_price_10),
                        getResources().getString(R.string.settings_donate_price_25),
                        getResources().getString(R.string.settings_donate_price_50)
                }, listener).create().show();
    }

    public void donate(int amount, boolean useGooglePlay) {
        if (useGooglePlay) {
            sendPlayBroadcast(amount);
        } else {
            new AlertDialog.Builder(Settings.this).setTitle(R.string.settings_donate_terms_popup_title)
                    .setMessage(R.string.settings_donate_terms_popup_message)
                    .setPositiveButton(R.string.settings_donate_terms_popup_pos, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse("https://plus.google.com/+AdrianVovkDev/posts/PUiDmRFzPLw")));
                        }
                    }).setNegativeButton(R.string.settings_donate_terms_popup_neg, null).create().show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 3672) {
            String purchaseData = data.getStringExtra("INAPP_PURCHASE_DATA");
            if (resultCode == AppCompatActivity.RESULT_OK) {
                try {
                    JSONObject jo = new JSONObject(purchaseData);
                    String sku = jo.getString("productId");
                    String token = jo.getString("purchaseToken");
                    Toast.makeText(this, sku, Toast.LENGTH_SHORT).show();
                    try {
                        mService.consumePurchase(3, "com.animbus.music", token);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void sendPlayBroadcast(int amount) {
        Bundle buyIntentBundle = null;
        try {
            buyIntentBundle = mService.getBuyIntent(3, "com.animbus.music", "donate_" + amount, "inapp", "bGoa+V7g/yqDXvKRqq+JTFn4uQZbPiQJo4pf9RzJ");
        } catch (RemoteException e) {
            e.printStackTrace();
            Toast.makeText(this, "ERROR REMOTE", Toast.LENGTH_SHORT).show();
        }
        PendingIntent pendingIntent = buyIntentBundle.getParcelable("BUY_INTENT");
        try {
            Settings.this.startIntentSenderForResult(pendingIntent.getIntentSender(),
                    3672, new Intent(), 0, 0, 0);
        } catch (IntentSender.SendIntentException e) {
            e.printStackTrace();
            Toast.makeText(this, "ERROR INTENT", Toast.LENGTH_SHORT).show();
        }
    }
}