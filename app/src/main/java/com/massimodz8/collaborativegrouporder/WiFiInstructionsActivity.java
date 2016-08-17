package com.massimodz8.collaborativegrouporder;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import java.util.List;

public class WiFiInstructionsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_instructions);
    }

    public void goWifiOptions_callback(View item) {
        Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PackageManager pm = getPackageManager();
        List cand = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if(cand.size() > 0) {
            startActivity(intent);
            MainMenuActivity.networkTroubleshootShown = false; // check it out again when something happens
            finish();
            return;
        }
        new AlertDialog.Builder(this, R.style.AppDialogStyle)
                .setIcon(R.drawable.ic_error_white_24dp)
                .setMessage(getString(R.string.wfia_noWifiOptions))
                .show();
    }

    public void goTetheringOptions_callback(View item) {
        Intent intent = new Intent(Intent.ACTION_MAIN, null)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(new ComponentName("com.android.settings", "com.android.settings.TetherSettings"));
        PackageManager pm = getPackageManager();
        List cand = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if(cand.size() > 0) {
            startActivity(intent);
            MainMenuActivity.networkTroubleshootShown = false; // check it out again when something happens
            finish();
            return;
        }
        new AlertDialog.Builder(this, R.style.AppDialogStyle)
                .setIcon(R.drawable.ic_error_white_24dp)
                .setMessage(getString(R.string.wfia_noTetheringOptions))
                .show();
    }
}
