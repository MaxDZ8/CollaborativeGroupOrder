package com.massimodz8.collaborativegrouporder;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

public class JoinGroupActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_group);
    }

    public void initiateGroupJoin(View btn) {
        btn.setEnabled(false);
        findViewById(R.id.helloMaster).setVisibility(View.GONE);
        findViewById(R.id.txt_helloHint).setVisibility(View.GONE);
    }
}
