package com.massimodz8.collaborativegrouporder;

import android.app.Activity;
import android.os.Bundle;

public class RestoreFromNotificationActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Thank you http://stackoverflow.com/questions/6575730/notification-to-restore-a-task-rather-than-a-specific-activity
        finish();
    }
}
