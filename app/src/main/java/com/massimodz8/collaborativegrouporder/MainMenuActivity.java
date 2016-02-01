package com.massimodz8.collaborativegrouporder;

import android.content.Intent;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class MainMenuActivity extends AppCompatActivity {
    public static final int REALLY_BAD_EXIT_REASON_INCOHERENT_CODE = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);

        File parties = new File("parties.xml");
        try {
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(parties));
            //Xml parsers = new Xml(bis);
        } catch (FileNotFoundException e) {
            /*
            In theory I would just check parties.exists() but since FileInputStream(parties) checks and throws anyway I go with exception.
            This is not an error at first run and we do nothing in that case.
            */
        }
    }

    public void startCreateParty_callback(View btn) {
        Intent go = new Intent(this, CreatePartyActivity.class);
        startActivityForResult(go, GROUP_CREATED);
    }

    public void startJoinGroupActivity_callback(View btn) {
        Intent go = new Intent(this, JoinGroupActivity.class);
        startActivityForResult(go, GROUP_JOINED);
    }
    public void startGoAdventuringActivity_callback(View btn) { startGoAdventuringActivity(null, null); }


    private static final int GROUP_CREATED = 1;
    private static final int GROUP_JOINED = 2;


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case GROUP_CREATED: {
                if (resultCode != RESULT_OK) return; // RESULT_CANCELLED
                if(!data.getBooleanExtra(CreatePartyActivity.RESULT_EXTRA_START_SESSION, false)) return;
                final String name = data.getStringExtra(CreatePartyActivity.RESULT_EXTRA_CREATED_PARTY_NAME);
                startNewSessionActivity(name, data.getByteArrayExtra(CreatePartyActivity.RESULT_EXTRA_CREATED_PARTY_KEY));
                break;
            }
            case GROUP_JOINED: {
                if(resultCode != RESULT_OK) return;
                if(!data.getBooleanExtra(JoinGroupActivity.RESULT_EXTRA_GO_ADVENTURING, false)) return;
                final String name = data.getStringExtra(JoinGroupActivity.RESULT_EXTRA_JOINED_PARTY_NAME);
                final byte[] key = data.getByteArrayExtra(JoinGroupActivity.RESULT_EXTRA_JOINED_PARTY_KEY);
                startGoAdventuringActivity(name, key);
                break;
            }
        }
    }

    private void startNewSessionActivity(String name, byte[] groupKey) {
        new AlertDialog.Builder(this)
                .setTitle("Not implemented!")
                .setMessage("new session!")
                .show();
    }

    void startGoAdventuringActivity(String autojoin, byte[] key) {
        new AlertDialog.Builder(this)
                .setTitle("Not implemented!")
                .setMessage("going adventuring!")
                .show();
    }
}
