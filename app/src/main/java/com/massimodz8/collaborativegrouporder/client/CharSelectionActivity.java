package com.massimodz8.collaborativegrouporder.client;

import android.os.Handler;
import android.os.Message;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.ViewGroup;

import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.networkio.Events;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.networkio.PumpTarget;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

public class CharSelectionActivity extends AppCompatActivity {
    // Before starting this activity, make sure to populate its connection parameters and friends.
    // Those will be cleared as soon as the activity goes onCreate and then never reused again.
    // onCreate assumes those non-null. Just call prepare(...)
    private static Pumper.MessagePumpingThread serverPipe;
    private static PersistentStorage.PartyClientData.Group connectedParty;
    private static Network.PlayingCharacterList pcList; // this can be of 4 different types... and can actually be null, even though it doesn't make sense.

    private PersistentStorage.PartyClientData.Group party;

    private static void prepare(Pumper.MessagePumpingThread pipe, PersistentStorage.PartyClientData.Group info, Network.PlayingCharacterList last) {
        serverPipe = pipe;
        connectedParty = info;
        pcList = last;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_char_selection);
        netPump = new Pumper(handler, MSG_DISCONNECT, MSG_DETACH)
                .add(ProtoBufferEnum.CHARACTER_LIST, new PumpTarget.Callbacks<Network.PlayingCharacterList>() {
                    @Override
                    public Network.PlayingCharacterList make() { return new Network.PlayingCharacterList(); }

                    @Override
                    public boolean mangle(MessageChannel from, Network.PlayingCharacterList msg) throws IOException {
                        handler.sendMessage(handler.obtainMessage(MSG_LIST_RECEIVED, new Events.CharList(from, msg)));
                        return msg.set == Network.PlayingCharacterList.YOURS_DEFINITIVE;
                    }
                });
        handler = new MyHandler(this);

        if(null != pcList) dispatch(pcList);
        pcList = null;

        netPump.pump(serverPipe);
        serverPipe = null;

        party = connectedParty;
        connectedParty = null;
    }

    private Pumper netPump;
    private Handler handler;

    private static final int MSG_DISCONNECT = 1;
    private static final int MSG_DETACH = 2;
    private static final int MSG_LIST_RECEIVED = 3;

    private static class MyHandler extends Handler {
        final WeakReference<CharSelectionActivity> self;

        private MyHandler(CharSelectionActivity self) {
            this.self = new WeakReference<CharSelectionActivity>(self);
        }

        @Override
        public void handleMessage(Message msg) {
            final CharSelectionActivity self = this.self.get();
            switch(msg.what) {
                case MSG_DISCONNECT: {
                } break;
                case MSG_DETACH: {
                    self.gotoTheRealDeal();
                } break;
                case MSG_LIST_RECEIVED: {
                    Network.PlayingCharacterList real = (Network.PlayingCharacterList) msg.obj;
                    self.dispatch(real);
                } break;
            }
        }
    }

    private void gotoTheRealDeal() {
        new AlertDialog.Builder(this).setMessage("TODO: take currently assigned characters and prepare to exit").show();
    }

    ArrayList<Network.PlayingCharacterDefinition> playedHere = new ArrayList<>();
    ArrayList<Network.PlayingCharacterDefinition> elsewhere = new ArrayList<>();
    ArrayList<Network.PlayingCharacterDefinition> unassigned = new ArrayList<>();
    ArrayList<Network.PlayingCharacterDefinition> requested = new ArrayList<>();
    Map<Network.PlayingCharacterDefinition, Integer> requestIndex = new IdentityHashMap<>();

    private static Network.PlayingCharacterDefinition matchKey(ArrayList<Network.PlayingCharacterDefinition> container, int groupId) {
        for (Network.PlayingCharacterDefinition check : container) {
            if(check.peerKey == groupId) return check;
        }
        return null;
    }

    private static boolean moveByKey(int peerKey, ArrayList<Network.PlayingCharacterDefinition> src, ArrayList<Network.PlayingCharacterDefinition> dst) {
        for(int loop = 0; loop < src.size(); loop++) {
            Network.PlayingCharacterDefinition el = src.get(loop);
            if(el.peerKey == peerKey) {
                src.remove(loop);
                dst.add(el);
                return true;
            }
        }
        return false;
    }

    /// This is very similar to moveByKey but moves only if the requestCount is at least the same as the number returned from requestIndex.
    /// If not, it means my request is not processed yet so I cannot really make anything about it.
    private boolean moveIfProcessed(int requestCount, int peerKey, ArrayList<Network.PlayingCharacterDefinition> src, ArrayList<Network.PlayingCharacterDefinition> dst) {
        for(int loop = 0; loop < requested.size(); loop++) {
            Network.PlayingCharacterDefinition el = src.get(loop);
            Integer when = requestIndex.get(el);
            if(null == when) continue; // impossible by construction
            if(el.peerKey == peerKey && requestCount >= when) {
                src.remove(loop);
                dst.add(el);
                return true;
            }
        }
        return false;
    }


    private void dispatch(Network.PlayingCharacterList list) {
        int takenAway = 0, refused = 0;
        switch (list.set) {
            case Network.PlayingCharacterList.READY: { // READY is characters bound to devices not you.
                for (Network.PlayingCharacterDefinition pc : list.payload) {
                    if(matchKey(elsewhere, pc.peerKey) != null) continue; // nothing to do, already ok, lists are not incremental
                    if(moveByKey(pc.peerKey, unassigned, elsewhere)) continue; // typical, not worth emphatizing
                    if(moveByKey(pc.peerKey, playedHere, elsewhere)) takenAway++;
                    else if(moveByKey(pc.peerKey, requested, elsewhere)) refused++;
                    else elsewhere.add(pc); // new peerkey
                }
            } break;
            case Network.PlayingCharacterList.AVAIL: {
                for (Network.PlayingCharacterDefinition pc : list.payload) {
                    if(matchKey(unassigned, pc.peerKey) != null) continue; // changes nothing
                    if(moveByKey(pc.peerKey, elsewhere, unassigned)) continue; // I don't really care
                    if(moveByKey(pc.peerKey, playedHere, unassigned)) takenAway++;
                    else if(moveIfProcessed(list.requestCount, pc.peerKey, requested, unassigned)) refused++;
                    else unassigned.add(pc);
                }
            } break;
            case Network.PlayingCharacterList.YOURS:
                case Network.PlayingCharacterList.YOURS_DEFINITIVE: { // definitive is the same as further processing happens on pump detach
                for (Network.PlayingCharacterDefinition pc : list.payload) {
                    if(moveByKey(pc.peerKey, unassigned, playedHere)) continue; // that's fine. I guess.
                    if(moveByKey(pc.peerKey, elsewhere, playedHere)) continue; // I don't think it's worth signaling.
                    if(moveIfProcessed(list.requestCount, pc.peerKey, requested, playedHere)) { // my request has been satisfied
                        requestIndex.remove(pc);
                        continue;
                    }
                    // Small complication here, might negate a 'I want to give up this character' request.
                    // As a start, let's check if that's an id I know about.
                    Network.PlayingCharacterDefinition match = matchKey(playedHere, pc.peerKey);
                    if(match == null) {
                        playedHere.add(pc);
                        continue; // I got a new one and it's already mine.
                    }
                    // Otherwise, if I have requested to give up I might have to signal this and clear, otherwise it's nop.
                    Integer when = requestIndex.get(match);
                    if(when == null) continue;
                    if(when < list.requestCount) {
                        requestIndex.remove(match);
                        refused++;
                    }
                }
            } break;
            default:
                new AlertDialog.Builder(this)
                        .setMessage(this.getString(R.string.csa_incoherentCharList))
                        .show();
        }
        ViewGroup root = null;
        if(refused != 0) {
            root = (ViewGroup) findViewById(R.id.csa_guiRoot);
            String text = getString(refused == 1? R.string.csa_refusedSingular : R.string.csa_refusedPlural);
            if(refused != 1) text = String.format(text, refused);
            Snackbar.make(root, text, Snackbar.LENGTH_SHORT).show();
        }
        if(takenAway != 0) {
            if(root == null) root = (ViewGroup) findViewById(R.id.csa_guiRoot);
            String text = getString(refused == 1? R.string.csa_takenAwaySingular : R.string.csa_takenAwayPlural);
            if(refused != 1) text = String.format(text, refused);
            Snackbar.make(root, text, Snackbar.LENGTH_SHORT).show();
        }
    }
}
