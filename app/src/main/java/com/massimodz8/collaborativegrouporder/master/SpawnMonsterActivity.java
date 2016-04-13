package com.massimodz8.collaborativegrouporder.master;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;

import com.massimodz8.collaborativegrouporder.R;

public class SpawnMonsterActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spawn_monster);

        // Get the intent, verify the action and get the query
        Intent intent = getIntent();
        if(!Intent.ACTION_SEARCH.equals(intent.getAction())) return;
        String query = intent.getStringExtra(SearchManager.QUERY);

        // Doing a search.
        new AlertDialog.Builder(this).setMessage("search:" + query).show();
    }
}
