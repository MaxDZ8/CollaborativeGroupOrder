package com.massimodz8.collaborativegrouporder;

import android.content.Context;
import android.content.DialogInterface;
import android.net.nsd.NsdManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.networkio.Events;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.networkio.formingServer.GroupForming;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.Objects;

/* This activity is started by the MainMenuActivity when the user wants to assemble a new party.
We publish a service and listen to network to find users joining.
 */
/** todo: this is an excellent moment to provide some ads: after the GM started scanning
 * he has to wait for users to join and I can push to him whatever I want.  */
public class CreatePartyActivity extends AppCompatActivity {
    private GroupForming gathering;
    private RecyclerView.Adapter characterListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_party);

        final CreatePartyActivity self = this;
        gathering = new GroupForming(new MyHandler(this)) {
            @Override
            public AlertDialog.Builder makeDialog() {
                return new AlertDialog.Builder(self);
            }

            @Override
            public String getString(int resource) {
                return self.getString(resource);
            }

            @Override
            public View inflate(int resource, ViewGroup root, boolean attachToRoot) {
                return getLayoutInflater().inflate(resource, root, attachToRoot);
            }

            @Override
            public void onFailedAccept() {
                AlertDialog.Builder build = new AlertDialog.Builder(self);
                build.setMessage(R.string.cannotAccept);
                build.show();
            }

            @Override
            public void refreshSilentCount(int currently) {
                handler.sendMessage(handler.obtainMessage(MSG_SILENT_DEVICE_COUNT, currently));
            }

            @Override
            protected void refreshCharacterData() {
                characterListAdapter.notifyDataSetChanged();
                final boolean enable = countAcceptedCharacters() > 0;
                findViewById(R.id.createPartyActivity_makeGroupButton).setEnabled(enable);
            }

            @Override
            protected void onGroupMemberChange(int currentCount) {
                findViewById(R.id.createPartyActivity_makeDeviceGroup).setEnabled(currentCount > 0);
            }
        };

        characterListAdapter = new PlayingCharacterListAdapter(gathering, PlayingCharacterListAdapter.MODE_SERVER_ACCEPTANCE);
        RecyclerView target = (RecyclerView) findViewById(R.id.pcList);
        target.setLayoutManager(new LinearLayoutManager(this));
        target.setAdapter(characterListAdapter);
    }
    protected void onDestroy() {
        if(gathering != null) try {
            gathering.shutdown();
        } catch (IOException e) {
            /// ehrm... hope n pray the OS will just kill us
        }
        super.onDestroy();
    }

    @Override
    public void onResume() {
        // regen service if I decide to tear it down.
        super.onResume();
    }

    @Override
    public void onPause() {
        // nsdService.unregisterService(this);
        // I don't do this. I really want the service to stay on.
        super.onPause();
    }

    public void initiatePartyHandshake(View view) {
        final EditText groupNameView = (EditText)findViewById(R.id.in_partyName);
        final String gname = groupNameView.getText().toString();
        if(gname.isEmpty()) {
            AlertDialog.Builder build = new AlertDialog.Builder(this);
            build.setTitle(R.string.groupNameIsEmpty_title)
                    .setMessage(R.string.groupNameIsEmpty_msg)
                    .setPositiveButton(R.string.groupName_retry, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            groupNameView.requestFocus();
                        }
                    });
            build.show();
            return;
        }
        view.setVisibility(View.GONE);
        findViewById(R.id.txt_privacyWarning).setVisibility(View.GONE);

        NsdManager nsd = (NsdManager)getSystemService(Context.NSD_SERVICE);
        if(nsd == null) {
            AlertDialog.Builder build = new AlertDialog.Builder(this);
            build.setTitle(R.string.nullNSDService_title)
                    .setMessage(R.string.nullNSDService_msg);
            build.show();
            return;
        }
        if(!gathering.publish(gname, nsd, MSG_SERVICE_REGISTRATION_COMPLETE)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.serverSocketFailed_title)
                    .setMessage(R.string.serverSocketFailed_msg)
                    .show();
        }
    }

    void onNSRegistrationComplete(GroupForming.ServiceRegistrationResult res) {
        if(res == null) res = new GroupForming.ServiceRegistrationResult(-1); // impossible by construction... for now.
        if(!res.successful) {
            AlertDialog.Builder build = new AlertDialog.Builder(this);
            String readable = nsdErrorString(res.error);
            build.setTitle(R.string.serviceRegFailed_title)
                    .setMessage(String.format(getString(R.string.serviceRegFailed_msg), readable));
            build.show();
            return;
        }
        TextView port = (TextView)findViewById(R.id.txt_FYI_port);
        String hostInfo = getString(R.string.FYI_explicitConnectInfo);
        hostInfo += String.format(getString(R.string.explicit_portInfo), gathering.getLocalPort());

        RecyclerView groupList = (RecyclerView) findViewById(R.id.groupList);
        groupList.setLayoutManager(new LinearLayoutManager(this));
        groupList.setAdapter(gathering);

        final EditText groupNameView = (EditText)findViewById(R.id.in_partyName);
        groupNameView.setEnabled(false);
        groupNameView.setVisibility(View.GONE);
        findViewById(R.id.txt_getGroupNameDesc).setVisibility(View.GONE);
        final ActionBar actionBar = getSupportActionBar();
        if(actionBar != null) actionBar.setTitle(String.format(getString(R.string.networkListening_groupNameTitle), gathering.getUserName()));

        Enumeration<NetworkInterface> nics;
        try {
            nics = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            AlertDialog.Builder build = new AlertDialog.Builder(this);
            build.setMessage(R.string.cannotEnumerateNICs);
            build.show();
            return;
        }
        if(nics != null) {
            while(nics.hasMoreElements()) {
                NetworkInterface n = nics.nextElement();
                Enumeration<InetAddress> addrs = n.getInetAddresses();
                Inet4Address ipFour = null;
                Inet6Address ipSix = null;
                while(addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if(a.isAnyLocalAddress()) continue; // ~0.0.0.0 or ::, sure not useful
                    if(a.isLoopbackAddress()) continue; // ~127.0.0.1 or ::1, not useful
                    if(ipFour == null && a instanceof Inet4Address) ipFour = (Inet4Address)a;
                    if(ipSix == null && a instanceof Inet6Address) ipSix = (Inet6Address)a;
                }
                if(ipFour != null) hostInfo += String.format(getString(R.string.explicit_address), stripUselessChars(ipFour.toString()));
                if(ipSix != null) hostInfo += String.format(getString(R.string.explicit_address), stripUselessChars(ipSix.toString()));
            }
        }

        port.setText(hostInfo);
        port.setVisibility(View.VISIBLE);

        findViewById(R.id.txt_scanning).setVisibility(View.VISIBLE);
        findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
        findViewById(R.id.groupList).setVisibility(View.VISIBLE);
        View closeGroupBtn = findViewById(R.id.createPartyActivity_makeDeviceGroup);
        closeGroupBtn.setVisibility(View.VISIBLE);
        closeGroupBtn.setEnabled(false);

        try {
            gathering.begin(MSG_SOCKET_DEAD, MSG_PEER_MESSAGE_UPDATED);
        } catch (IOException e) {
            AlertDialog.Builder build = new AlertDialog.Builder(this);
            build.setMessage(R.string.cannotStartSilentForming);
            build.show();
        }
    }

    static class MyHandler extends Handler {
        final WeakReference<CreatePartyActivity> target;

        public MyHandler(CreatePartyActivity target) {
            this.target = new WeakReference<>(target);
        }

        @Override
        public void handleMessage(Message msg) {
            final CreatePartyActivity target = this.target.get();
            switch(msg.what) {
                case MSG_SERVICE_REGISTRATION_COMPLETE: target.onNSRegistrationComplete((GroupForming.ServiceRegistrationResult) msg.obj); break;
                case MSG_SILENT_DEVICE_COUNT: target.onSilentCountChanged(); break;
                case MSG_SOCKET_DEAD: target.onSocketDead((Events.SocketDisconnected) msg.obj); break;
                case MSG_PEER_MESSAGE_UPDATED: {
                    Events.PeerMessage real = (Events.PeerMessage)msg.obj;
                    target.gathering.updateDeviceMessage(real.which, real.msg);
                    target.onTalkingCountChanged();
                    break;
                }
                case MSG_CHARACTER_DEFINITION: target.characterUpdate((Events.CharacterDefinition)msg.obj); break;
            }
        }
    }

    private void characterUpdate(final Events.CharacterDefinition obj) {
        final String badThing = good(obj.character);
        if(badThing != null) {
            final CreatePartyActivity self = this;
            new AsyncTask<Void, Void, Exception>() {
                @Override
                protected Exception doInBackground(Void... params) {
                    Network.GroupFormed reject = new Network.GroupFormed();
                    reject.peerKey = obj.character.peerKey;
                    try {
                        obj.origin.writeSync(ProtoBufferEnum.GROUP_FORMED, reject);
                    } catch (IOException e) {
                        return e;
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Exception e) {
                    if(e == null) return;
                    new AlertDialog.Builder(self)
                            .setMessage(e.getLocalizedMessage())
                            .show();
                }
            }.execute();
            return;
        }
        gathering.update(obj.origin, obj.character);
    }

    private String good(Network.PlayingCharacterDefinition def) {
        if(def.experience < 0) return getString(R.string.createPartyActivity_PCDefDiscarded_badExperience);
        if(def.healthPoints < 0) return getString(R.string.createPartyActivity_PCDefDiscarded_badHealth);
        if(def.name.isEmpty()) return getString(R.string.createPartyActivity_PCDefDiscarded_badName);
        return null;
    }


    private void onSocketDead(Events.SocketDisconnected obj) {
        String lastMessage = gathering.getMessage(obj.which);
        String names = gathering.getCharNames(obj.which);
        gathering.kick(obj.which);

        if(lastMessage == null) { // Not much of a real deal, give up.
            onSilentCountChanged();
        }
        else if(names.equals("")) {
            new AlertDialog.Builder(this)
                    .setMessage(String.format(getString(R.string.ohNo_talkingIsGone), lastMessage))
                    .show();
        }
        else {
            new AlertDialog.Builder(this)
                    .setMessage(String.format(getString(R.string.ohNo_pgsAreGone), names))
                    .show();
        }
    }

    private void onTalkingCountChanged() {
        updateCount(R.id.txt_talkingDeviceCounts, R.string.talkingDeviceStats, gathering.getTalkingCount());
    }

    private void onSilentCountChanged() {
        updateCount(R.id.txt_connectedDeviceCounts, R.string.anonDeviceStats, gathering.getSilentCount());
    }

    private void updateCount(int targetID, int stringRes, int count) {
        TextView target = (TextView)findViewById(targetID);
        if(gathering == null) return; // impossible
        if(count == 0) target.setVisibility(View.INVISIBLE);
        else {
            String show = getString(count < 2? R.string.word_device_singular : R.string.word_device_plural);
            target.setText(String.format(getString(stringRes), count, show));
            target.setVisibility(View.VISIBLE);
        }
    }

    private static String stripUselessChars(String s) {
        for(int i = 0; i < s.length(); i++) {
            if(s.charAt(i) == '%') {
                s = s.substring(0, i);
                break;
            }
        }
        return s.charAt(0) == '/'? s.substring(1) : s;
    }

    private static String nsdErrorString(int error) {
        switch(error) {
            case NsdManager.FAILURE_ALREADY_ACTIVE: return "FAILURE_ALREADY_ACTIVE";
            case NsdManager.FAILURE_INTERNAL_ERROR: return "FAILURE_INTERNAL_ERROR";
            case NsdManager.FAILURE_MAX_LIMIT: return "FAILURE_MAX_LIMIT";
        }
        return String.format("%1$d", error);
    }


    // NsdManager.RegistrationListener() is async AND on a different thread so I cannot just
    // modify the various controls from there. Instead, wait for success/fail and then
    // pass a notification to the UI thread.
    public static final int MSG_SERVICE_REGISTRATION_COMPLETE = 1;
    public static final int MSG_SILENT_DEVICE_COUNT = 2;
    public static final int MSG_SOCKET_DEAD = 3;
    public static final int MSG_PEER_MESSAGE_UPDATED = 4;
    public static final int MSG_CHARACTER_DEFINITION = 5;


    public void createDeviceGroup_callback(View button) {
        final byte[] salt;
        try {
            salt = gathering.buildKey();
        } catch (NoSuchAlgorithmException e) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.veryBadError)
                    .setMessage(R.string.noSuchAlgorithmExceptionAlertDialogMessage)
                    .show();
            return;
        }
        gathering.makeDeviceGroup(MSG_CHARACTER_DEFINITION, salt);

        // Now boring stuff, all the UI so far must go.
        int[] gone = new int[] {
                R.id.progressBar, R.id.txt_connectedDeviceCounts, R.id.txt_talkingDeviceCounts,
                R.id.groupList, R.id.createPartyActivity_makeDeviceGroup, R.id.txt_FYI_port, R.id.txt_scanning
        };
        for(int id : gone) findViewById(id).setVisibility(View.GONE);

        final CreatePartyActivity self = this;
        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                Network.GroupFormed forming = new Network.GroupFormed();
                forming.salt = salt;
                int failures = gathering.broadcast(ProtoBufferEnum.GROUP_FORMED, forming);
                if(failures != 0) return new Exception(getString(R.string.atLeastOneFailedWriteSync));
                return null;
            }

            @Override
            protected void onPostExecute(Exception e) {
                if(e != null) {
                    new AlertDialog.Builder(self)
                            .setMessage(String.format(getString(R.string.createPartyActivity_failedPCDefinitionSwitch), e.toString()))
                            .show();
                    return;
                }
                findViewById(R.id.pcList).setVisibility(View.VISIBLE);
                findViewById(R.id.createPartyActivity_definingCharactersFeedback).setVisibility(View.VISIBLE);
                final String localized = getString(R.string.phaseDefiningCharacters);
                final ActionBar actionBar = self.getSupportActionBar();
                if(actionBar != null) actionBar.setTitle(String.format("%1$s - %2$s", gathering.getUserName(), localized));
                self.findViewById(R.id.createPartyActivity_makeGroupButton).setVisibility(View.VISIBLE);
            }
        }.execute();
    }

    public void createPlayingCharacterGroup_callback(View btn) {
        new AlertDialog.Builder(this)
                .setMessage("todo")
                .show();

    }
}
