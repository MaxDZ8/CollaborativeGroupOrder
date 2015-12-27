package com.massimodz8.collaborativegrouporder;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Xml;
import android.widget.Button;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class MainMenuActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);

        File parties = new File("parties.xml");
        try {
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(parties));
            //Xml parsers = new Xml(bis);
        } catch (FileNotFoundException e) {
            // In theory I would just check parties.exists() but since FileInputStream(parties) checks and throws anyway I go with exception.
            // This is not an error at first run and we do nothing in that case.
        }
    }
}
