package org.emoncms.myapps;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.vision.barcode.Barcode;

import org.emoncms.myapps.barcodescanner.BarcodeCaptureActivity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener
{
    private static final int RC_BARCODE_CAPTURE = 9001;
//    static final String TAG = "SETTINGSFRAGMENT";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.main_preferences);
    }

    @Override
    public void onResume() {
        super.onResume();

        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        /*
        Preference button = findPreference("scanqrcode");
        button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // launch barcode activity.
                Intent intent = new Intent(getActivity(), BarcodeCaptureActivity.class);
                startActivityForResult(intent, RC_BARCODE_CAPTURE);
                return true;
            }
        });
        */
    }

    @Override
    public void onPause() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onActivityCreated(Bundle savesInstanceState) {
        super.onActivityCreated(savesInstanceState);

        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) actionBar.setTitle(R.string.settings);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
    {
        if (key.equals(getString(R.string.setting_keepscreenon)))
            ((MainActivity) getActivity()).setKeepScreenOn(sharedPreferences.getBoolean(getString(R.string.setting_keepscreenon), false));
        else if (key.equals(getString(R.string.setting_language)))
        {
            getActivity().finish();
            Intent intent = new Intent(getActivity(), MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {

        if (requestCode == RC_BARCODE_CAPTURE)
        {
            if (resultCode == CommonStatusCodes.SUCCESS)
            {
                if (data != null)
                {
                    Barcode barcode = data.getParcelableExtra(BarcodeCaptureActivity.BarcodeObject);

                    Pattern pattern = Pattern.compile("^(http[s]?)://([^:/\\s]+.*)/app\\?[readkey=]+=([^&]+)#myelectric");
                    Matcher matcher = pattern.matcher(barcode.displayValue);

                    if (matcher.matches() && matcher.groupCount() == 3)
                    {
                        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity().getBaseContext());
                        SharedPreferences.Editor se = sp.edit();
                        se.putString(getString(R.string.setting_url), matcher.group(2));
                        se.putString(getString(R.string.setting_apikey), matcher.group(3));
                        se.putBoolean(getString(R.string.setting_usessl), matcher.group(1).equalsIgnoreCase("https"));
                        se.apply();
                        setPreferenceScreen(null);
                        addPreferencesFromResource(R.xml.main_preferences);

                        Snackbar sn = Snackbar.make(getView(), R.string.qr_code_success,
                                Snackbar.LENGTH_LONG);
                        sn.getView().setBackgroundColor(Color.GRAY);
                        TextView tv = (TextView) sn.getView().findViewById(android.support.design.R.id.snackbar_text);
                        tv.setTypeface(null, Typeface.BOLD);
                        sn.show();
                    } else {
                        Snackbar sn = Snackbar.make(getView(), R.string.qr_code_fail,
                                Snackbar.LENGTH_LONG);
                        sn.getView().setBackgroundColor(Color.GRAY);
                        TextView tv = (TextView) sn.getView().findViewById(android.support.design.R.id.snackbar_text);
                        tv.setTypeface(null, Typeface.BOLD);
                        sn.show();
                    }
                }
            }
            else
            {
                super.onActivityResult(requestCode, resultCode, data);
            }
        }
    }
}