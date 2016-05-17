package com.massimodz8.collaborativegrouporder;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import java.util.Date;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        final PackageManager pman = getPackageManager();
        final ApplicationInfo app;
        final PackageInfo pack;
        try {
            app = pman.getApplicationInfo(getPackageName(), 0);
            pack = pman.getPackageInfo(getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            return; // impossible!
        }


        TextView label = (TextView)findViewById(R.id.aa_versionString);
        label.setText(String.format(getString(R.string.aa_versionFormat), pack.versionName));
        label = (TextView) findViewById(R.id.aa_releaseCount);
        label.setText(String.format(getString(R.string.aa_commitFormat), String.valueOf(pack.versionCode)));
        label = (TextView) findViewById(R.id.aa_dataDir);
        label.setText(String.format(getString(R.string.aa_dataDir), app.dataDir));

        Date lastUpdate = new Date(pack.lastUpdateTime);
        java.text.DateFormat bruh = java.text.DateFormat.getDateInstance();
        label.setText(String.format(getString(R.string.aa_lastUpdated), bruh.format(lastUpdate)));
    }
}
