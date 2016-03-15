package com.massimodz8.collaborativegrouporder.master;


import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;
import android.support.v7.widget.Toolbar;

import com.massimodz8.collaborativegrouporder.HealthBar;
import com.massimodz8.collaborativegrouporder.R;

public class FreeRoamingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_free_roaming);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new AlertDialog.Builder(FreeRoamingActivity.this).setMessage("TODO").show();
            }
        });
        final ActionBar sab = getSupportActionBar();
        if(null != sab) sab.setDisplayHomeAsUpEnabled(true);

        silly(R.id.hb1, R.id.hb1val,   0, 33);
    }

    void silly(@IdRes int hb, @IdRes int label, int current, int max) {
        HealthBar bar = (HealthBar)findViewById(hb);
        bar.currentHp = current;
        bar.maxHp = max;
        bar.invalidate();
        TextView text = (TextView)findViewById(label);
        text.setText(String.format("%1$d / %2$d", current, max));
    }
}
