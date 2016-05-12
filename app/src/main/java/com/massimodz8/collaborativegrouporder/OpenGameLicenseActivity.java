package com.massimodz8.collaborativegrouporder;

import android.app.ActionBar;
import android.os.Bundle;
import android.app.Activity;

public class OpenGameLicenseActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_open_game_license);
        ActionBar ab = getActionBar();
        if(ab != null) ab.setDisplayHomeAsUpEnabled(true);
    }
}
