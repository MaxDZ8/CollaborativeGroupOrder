package com.massimodz8.collaborativegrouporder;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage;

/** The server is 'gathering' player devices so they can join a new session.
 * This is important and we must be able to navigate back there every time needed in case
 * players get disconnected.
 */
public class GatheringActivity extends AppCompatActivity {
    public static class State {
        final PersistentStorage.PartyOwnerData.Group party;

        public State(PersistentStorage.PartyOwnerData.Group party) {
            this.party = party;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gathering);

        final CrossActivityShare appState = (CrossActivityShare) getApplicationContext();
    }
}
