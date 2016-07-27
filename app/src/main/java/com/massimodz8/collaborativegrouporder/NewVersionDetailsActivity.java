package com.massimodz8.collaborativegrouporder;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

public class NewVersionDetailsActivity extends AppCompatActivity {
    Intent rateIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_version_details);

        final Uri uri = Uri.parse("market://details?id=" + getApplicationContext().getPackageName());
        rateIntent = new Intent(Intent.ACTION_VIEW, uri);
        if (getPackageManager().queryIntentActivities(rateIntent, 0).size() < 1) findViewById(R.id.nvda_rateBtn).setEnabled(false);

        final PackageManager pman = getPackageManager();
        final PackageInfo pack;
        try {
            pack = pman.getPackageInfo(getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            return; // impossible!
        }
        final TextView ver = (TextView) findViewById(R.id.nvda_versionString);
        ver.setText(pack.versionName);
    }

    public void rateAndReview_callback(View view) {
        startActivity(rateIntent);
    }
}
