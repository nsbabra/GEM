package com.animbus.music.ui.activity.settings;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.afollestad.appthemeengine.ATE;
import com.afollestad.appthemeengine.Config;
import com.afollestad.appthemeengine.prefs.ATEColorPreference;
import com.afollestad.materialdialogs.color.ColorChooserDialog;
import com.android.vending.billing.IInAppBillingService;
import com.animbus.music.R;
import com.animbus.music.ui.custom.activity.ThemeActivity;
import com.animbus.music.util.Options;

import org.json.JSONException;
import org.json.JSONObject;

public class Settings extends ThemeActivity implements ColorChooserDialog.ColorCallback {

    @Override
    protected void init() {
        setContentView(R.layout.activity_settings);
    }

    @Override
    protected void setVariables() {
        //Everything is done either in ThemeActivity or ATE
    }

    @Override
    protected void setUp() {
        getFragmentManager().beginTransaction().replace(R.id.prefs, new PrefsFragment()).commit();
    }

    @Override
    protected int getOptionsMenu() {
        return R.menu.menu_settings;
    }

    @Override
    protected boolean processMenuItem(int id) {
        switch (id) {
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

    @Override
    public void onColorSelection(@NonNull ColorChooserDialog colorChooserDialog, @ColorInt int i) {
        if (!colorChooserDialog.isAccentMode())
            ATE.config(this, getATEKey()).primaryColor(i).apply(this);
        else
            ATE.config(this, getATEKey()).accentColor(i).apply(this);
        ((PrefsFragment) getFragmentManager().findFragmentById(R.id.prefs)).configure();
        recreate();
    }

    public static class PrefsFragment extends PreferenceFragment {

        public PrefsFragment() {

        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            configure();
        }

        public void configure() {
            findPreference("base_theme").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    ATE.config(getActivity(), ((Settings) getActivity()).getATEKey())
                            .activityTheme(getStyleFromPos(Integer.parseInt((String) newValue))).commit();
                    Options.setLightTheme(Integer.parseInt((String) newValue) == 2);
                    getActivity().recreate();
                    return true;
                }
            });

            final ATEColorPreference primaryColor = (ATEColorPreference) findPreference("primary");
            final int primary = Config.primaryColor(getActivity(), ((Settings) getActivity()).getATEKey());
            primaryColor.setColor(primary, Color.BLACK);
            primaryColor.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    new ColorChooserDialog.Builder((Settings) getActivity(), R.string.settings_primary)
                            .accentMode(false)
                            .preselect(primary)
                            .allowUserColorInputAlpha(false)
                            .show();
                    return true;
                }
            });

            ATEColorPreference accentColor = (ATEColorPreference) findPreference("accent");
            final int accent = Config.accentColor(getActivity(), ((Settings) getActivity()).getATEKey());
            accentColor.setColor(accent, Color.BLACK);
            accentColor.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    new ColorChooserDialog.Builder((Settings) getActivity(), R.string.settings_accent)
                            .accentMode(true)
                            .preselect(accent)
                            .allowUserColorInputAlpha(false)
                            .show();
                    return true;
                }
            });

            findPreference("reset_primary").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    ATE.config(getActivity(), ((Settings) getActivity()).getATEKey())
                            .primaryColor(((Settings) getActivity()).resolveColorAttr(android.R.attr.colorBackground))
                            .commit();
                    getActivity().recreate();
                    return true;
                }
            });

            findPreference("donate").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    ((Settings) getActivity()).showDonation();
                    return true;
                }
            });
        }

        private int getStyleFromPos(int pos) {
            switch (pos) {
                case 0:
                    return R.style.AppTheme_Dark;
                case 1:
                    return R.style.AppTheme_Faithful;
                case 2:
                    return R.style.AppTheme_Light;
            }
            return 0;
        }
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