package com.massimodz8.collaborativegrouporder.client;

import android.content.DialogInterface;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.PreSeparatorDecorator;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.networkio.PumpTarget;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class CharSelectionActivity extends AppCompatActivity {
    // Before starting this activity, make sure to populate its connection parameters and friends.
    // Those will be cleared as soon as the activity goes onCreate and then never reused again.
    // onCreate assumes those non-null. Just call prepare(...)
    private static Pumper.MessagePumpingThread serverPipe; // inout
    private static StartData.PartyClientData.Group connectedParty; // inout
    private static Network.PlayingCharacterDefinition character; // In only
    private static int[] playChars; // inout, server ids of actors to manage here

    // to move on myState.
    private StartData.PartyClientData.Group party;
    private MessageChannel pipe;
    private int ticket;

    public static void prepare(Pumper.MessagePumpingThread pipe, StartData.PartyClientData.Group info, Network.PlayingCharacterDefinition first) {
        serverPipe = pipe;
        connectedParty = info;
        character = first;
    }

    public static StartData.PartyClientData.Group movePlayingParty() {
        final StartData.PartyClientData.Group res = connectedParty;
        connectedParty = null;
        return res;
    }
    public static Pumper.MessagePumpingThread moveServerWorker() {
        final Pumper.MessagePumpingThread res = serverPipe;
        serverPipe = null;
        return res;
    }
    public static int[] movePlayChars() {
        final int[] res = playChars;
        playChars = null;
        return res;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_char_selection);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler = new MyHandler(this);
        netPump = new Pumper(handler, MSG_DISCONNECT, MSG_DETACH)
                .add(ProtoBufferEnum.PLAYING_CHARACTER_DEFINITION, new PumpTarget.Callbacks<Network.PlayingCharacterDefinition>() {
                    @Override
                    public Network.PlayingCharacterDefinition make() { return new Network.PlayingCharacterDefinition(); }

                    @Override
                    public boolean mangle(MessageChannel from, Network.PlayingCharacterDefinition msg) throws IOException {
                        handler.sendMessage(handler.obtainMessage(MSG_CHARACTER_DEFINITION, msg));
                        return false;
                    }
                }).add(ProtoBufferEnum.CHARACTER_OWNERSHIP, new PumpTarget.Callbacks<Network.CharacterOwnership>() {
                    @Override
                    public Network.CharacterOwnership make() { return new Network.CharacterOwnership(); }

                    @Override
                    public boolean mangle(MessageChannel from, Network.CharacterOwnership msg) throws IOException {
                        handler.sendMessage(handler.obtainMessage(MSG_CHARACTER_OWNERSHIP, msg));
                        return false;
                    }
                }).add(ProtoBufferEnum.PHASE_CONTROL, new PumpTarget.Callbacks<Network.PhaseControl>() {
                    @Override
                    public Network.PhaseControl make() { return new Network.PhaseControl(); }

                    @Override
                    public boolean mangle(MessageChannel from, Network.PhaseControl msg) throws IOException {
                        if(msg.type != Network.PhaseControl.T_DEFINITIVE_CHAR_ASSIGNMENT) return false; // Error?
                        handler.sendMessage(handler.obtainMessage(MSG_PARTY_READY, msg));
                        return true;
                    }
                });

        RecyclerView list = (RecyclerView) findViewById(R.id.csa_pcList);
        final RecyclerView.Adapter adapter = new MyLister();
        list.setAdapter(adapter);
        list.addItemDecoration(new PreSeparatorDecorator(list, this) {
            @Override
            protected boolean isEligible(int position) {
                // see adapter's onBindViewHolder
                int here = count(TransactingCharacter.PLAYED_HERE);
                int avail = count(TransactingCharacter.AVAILABLE);
                //int somewhere = count(TransactingCharacter.PLAYED_SOMEWHERE);
                if (here != 0) {
                    if (position == 0) return false;
                    position--;
                    if (position < here) return position != 0;
                    position -= here;
                }
                if (avail != 0) {
                    if (position == 0) return false;
                    position--;
                    if (position < avail) return position != 0;
                }
                return false;
            }
        });
        final ItemTouchHelper swiper = new ItemTouchHelper(new MyItemTouchCallback());
        list.addItemDecoration(swiper);
        swiper.attachToRecyclerView(list);

        party = connectedParty;
        connectedParty = null;

        if(null != character) addChar(character);
        character = null;

        pipe = serverPipe.getSource();
        netPump.pump(serverPipe);
        serverPipe = null;
    }


    @Override
    protected void onPause() {
        super.onPause();
        if(null != netPump) {
            final Pumper.MessagePumpingThread[] threads = netPump.move();
            for (Pumper.MessagePumpingThread worker : threads) worker.interrupt();
            new Thread() {
                @Override
                public void run() {
                    for (Pumper.MessagePumpingThread worker : threads) {
                        try {
                            worker.getSource().socket.close();
                        } catch (IOException e) {
                            // it's a goner anyway
                        }
                    }
                }
            }.start();
        }
    }

    private Pumper netPump;
    private Handler handler;

    private static final int MSG_DISCONNECT = 1;
    private static final int MSG_DETACH = 2;
    private static final int MSG_CHARACTER_DEFINITION = 3;
    private static final int MSG_CHARACTER_OWNERSHIP = 4;
    private static final int MSG_PARTY_READY = 5;

    private static class MyHandler extends Handler {
        final WeakReference<CharSelectionActivity> self;

        private MyHandler(CharSelectionActivity self) {
            this.self = new WeakReference<>(self);
        }

        @Override
        public void handleMessage(Message msg) {
            final CharSelectionActivity self = this.self.get();
            switch(msg.what) {
                case MSG_DISCONNECT: {
                } break;
                case MSG_DETACH: {
                    serverPipe = self.netPump.move()[0];
                    connectedParty = self.party;
                    self.setResult(RESULT_OK);
                    self.finish();
                } break;
                case MSG_CHARACTER_DEFINITION: {
                    Network.PlayingCharacterDefinition real = (Network.PlayingCharacterDefinition) msg.obj;
                    self.addChar(real);
                } break;
                case MSG_CHARACTER_OWNERSHIP: {
                    Network.CharacterOwnership real = (Network.CharacterOwnership)msg.obj;
                    self.ownership(real);
                } break;
                case MSG_PARTY_READY: { // detach will follow soon, just reset all my characters
                    Network.PhaseControl real = (Network.PhaseControl)msg.obj;
                    if(real.yourChars.length == 0) {
                        new AlertDialog.Builder(self, R.style.AppDialogStyle)
                                .setMessage(self.getString(R.string.csa_noDefinitiveCharactersHere))
                                .setCancelable(false)
                                .setPositiveButton(self.getString(R.string.csa_noDefinitiveCharactersHereDlgDone), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        self.finish();
                                    }
                                })
                                .show();
                        return;
                    }
                    playChars = real.yourChars;
                } break;
            }
        }
    }

    private static class TransactingCharacter {
        static final int PLAYED_HERE = 0;
        static final int PLAYED_SOMEWHERE = 1;
        static final int AVAILABLE = 2;
        static final int NO_REQUEST = -1;
        int type = AVAILABLE;
        final Network.PlayingCharacterDefinition pc;

        public TransactingCharacter(Network.PlayingCharacterDefinition pc) {
            this.pc = pc;
        }

        int pending = NO_REQUEST; // check type to understand if being given away or being requested

        public void sendRequest(final MessageChannel server) {
            final Network.CharacterOwnership request = new Network.CharacterOwnership();
            request.ticket = pending;
            request.type = Network.CharacterOwnership.REQUEST;
            request.character = pc.peerKey;
            new Thread(){
                @Override
                public void run() {
                    try {
                        server.writeSync(ProtoBufferEnum.CHARACTER_OWNERSHIP, request);
                    } catch (IOException e) {
                        // suppress this. This is huge, but I need something better designed to deal with errors,
                        // perhaps I will have my AppCompatActivityWithPendingAsyncOperations class.
                        // TODO: transition me to long-running task mode.
                    }
                }
            }.start();
        }

        public void toggleOwnership() {
            if(type == PLAYED_HERE) type = AVAILABLE;
            else if(type == AVAILABLE) type = PLAYED_HERE;
            // not called on PLAYED_SOMEWHERE, would be bad bad bad
        }
    }

    ArrayList<TransactingCharacter> chars = new ArrayList<>();

    private void addChar(Network.PlayingCharacterDefinition newDef) {
        final TransactingCharacter add = new TransactingCharacter(newDef);
        final TransactingCharacter character = getCharacterByKey(newDef.peerKey);
        if(null != character) { // redefined. Not really supposed to happen.
            chars.set(chars.indexOf(character), add);
        }
        else chars.add(add);
        RecyclerView view = (RecyclerView) findViewById(R.id.csa_pcList);
        view.getAdapter().notifyDataSetChanged();
    }

    void ownership(final Network.CharacterOwnership msg) {
        ticket = msg.ticket;
        final TransactingCharacter character = getCharacterByKey(msg.character);
        if(null == character) return; // super odd. Not sure of what I should be doing in this case. In theory the server prevents this from happening but it currently has a quirk
        switch(msg.type) {
            case Network.CharacterOwnership.REQUEST: return; // the server does not use this currently... perhaps to update ticket?
            case Network.CharacterOwnership.OBSOLETE: { // just try again with the updated ticket.
                character.sendRequest(pipe);
                return;
            }
            case Network.CharacterOwnership.ACCEPTED: {
                if(character.pending == TransactingCharacter.NO_REQUEST) break; // happens if the server assigned something to us before our request reached it
                character.pending = TransactingCharacter.NO_REQUEST;
                character.toggleOwnership();
                break;
            }
            case Network.CharacterOwnership.REJECTED: {
                if(character.pending == TransactingCharacter.NO_REQUEST) break; // happens if the server assigned something to us before our request reached it
                character.pending = TransactingCharacter.NO_REQUEST;
                ViewGroup root = (ViewGroup) findViewById(R.id.csa_guiRoot);
                String text = String.format(getString(R.string.csa_charOwnershipChangeRefused), character.pc.name);
                Snackbar.make(root, text, Snackbar.LENGTH_SHORT).show();
                break;
            }
            case Network.CharacterOwnership.YOURS:
            case Network.CharacterOwnership.AVAIL: {
                int matched = msg.type == Network.CharacterOwnership.YOURS? TransactingCharacter.PLAYED_HERE : TransactingCharacter.AVAILABLE;
                if(character.type == matched && character.pending != TransactingCharacter.NO_REQUEST) {
                    msg.type = Network.CharacterOwnership.REJECTED;
                    ownership(msg);
                    return;
                }
                character.pending = TransactingCharacter.NO_REQUEST;
                character.type = matched;
                break;
            }
            case Network.CharacterOwnership.BOUND: { // This is almost like AVAILABLE but not quite.
                switch(character.type) {
                    case TransactingCharacter.PLAYED_SOMEWHERE: return;
                    case TransactingCharacter.PLAYED_HERE:
                        if(character.pending == TransactingCharacter.NO_REQUEST) {
                            ViewGroup root = (ViewGroup) findViewById(R.id.csa_guiRoot);
                            String text = String.format(getString(R.string.csa_charOwnershipToSomeoneUnsolicited), character.pc.name);
                            Snackbar.make(root, text, Snackbar.LENGTH_SHORT).show();
                        }
                        break;
                    case TransactingCharacter.AVAILABLE:
                        if(character.pending != TransactingCharacter.NO_REQUEST) {
                            ViewGroup root = (ViewGroup) findViewById(R.id.csa_guiRoot);
                            String text = String.format(getString(R.string.csa_charOwnershipRequestToSomeoneElse), character.pc.name);
                            Snackbar.make(root, text, Snackbar.LENGTH_SHORT).show();
                        }
                        character.pending = TransactingCharacter.NO_REQUEST;
                        character.type = TransactingCharacter.PLAYED_SOMEWHERE;
                        break;
                }
                break;
            }
        }
        final RecyclerView list = (RecyclerView) findViewById(R.id.csa_pcList);
        list.getAdapter().notifyDataSetChanged();
    }


    private static abstract class VariedHolder extends RecyclerView.ViewHolder {
        public VariedHolder(View itemView) {
            super(itemView);
        }

        public abstract void bind(TransactingCharacter o);
    }

    private static class PlayedHereSeparator extends VariedHolder {
        static final int TYPE = 0;
        public PlayedHereSeparator(View itemView) {
            super(itemView);
        }

        @Override
        public void bind(TransactingCharacter o) {
            // nothing to do with this
        }
    }

    private static class AvailableSeparator extends VariedHolder {
        static final int TYPE = 1;
        public AvailableSeparator(View itemView) {
            super(itemView);
        }

        @Override
        public void bind(TransactingCharacter o) {
            // nop for the time being
        }
    }

    private class SomewhereSeparator extends VariedHolder { // not really, just a textview, but made layout for simplicity
        static final int TYPE = 2;
        TextView label;
        public SomewhereSeparator(View itemView) {
            super(itemView);
            label = (TextView) itemView.findViewById(R.id.vh_cs_somewhereCount);
        }

        @Override
        public void bind(TransactingCharacter o) {
            int count = count(TransactingCharacter.PLAYED_SOMEWHERE);
            String msg;
            if(count == 1) msg = getString(R.string.csa_oneCharSomewhere);
            else msg = String.format(getString(R.string.csa_pluralCharsSomewhere), count);
            label.setText(msg);
        }
    }

    private static class PlayedHereHolder extends VariedHolder { // not really, just a textview, but made layout for simplicity
        static final int TYPE = 3;
        ImageView avatar;
        TextView name;
        public int charKey;

        public PlayedHereHolder(View itemView) {
            super(itemView);
            avatar = (ImageView) itemView.findViewById(R.id.vhPHC_image);
            name = (TextView) itemView.findViewById(R.id.vhPHC_name);
        }

        @Override
        public void bind(TransactingCharacter o) {
            charKey = o.pc.peerKey;
            name.setText(o.pc.name);
        }
    }

    private class AvailableHolder extends VariedHolder implements View.OnClickListener { // not really, just a textview, but made layout for simplicity
        static final int TYPE = 4;
        ImageView avatar;
        TextView name, level, hpmax, xp, requested;
        int charKey;

        public AvailableHolder(View itemView) {
            super(itemView);
            avatar = (ImageView) itemView.findViewById(R.id.vhAC_image);
            name = (TextView) itemView.findViewById(R.id.vhAA_name);
            level = (TextView) itemView.findViewById(R.id.vhAC_level);
            hpmax = (TextView) itemView.findViewById(R.id.vhAC_hpMax);
            xp = (TextView) itemView.findViewById(R.id.vhAC_xp);
            requested = (TextView) itemView.findViewById(R.id.vhAC_requested);
            itemView.setOnClickListener(this);
        }

        @Override
        public void bind(TransactingCharacter o) {
            charKey = o.pc.peerKey;
            name.setText(o.pc.name);
            level.setText(String.format(getString(R.string.csa_level), o.pc.level));
            hpmax.setText(String.format(getString(R.string.csa_hpMax), o.pc.healthPoints));
            xp.setText(String.format(getString(R.string.csa_xp), o.pc.experience));
            requested.setVisibility(o.pending != TransactingCharacter.NO_REQUEST? View.VISIBLE : View.GONE);
        }

        @Override
        public void onClick(View v) {
            characterRequest(charKey);
        }
    }

    private class MyLister extends RecyclerView.Adapter<VariedHolder> {
        public MyLister() {
            super();
            setHasStableIds(true);
        }

        @Override
        public long getItemId(int position) {
            // If you look at the protocol, it turns out peer keys are uint!
            // Java is shit and does not have uints, but it's very convenient.
            // To be kept in sync with onBindViewHolder
            int here = count(TransactingCharacter.PLAYED_HERE);
            int avail = count(TransactingCharacter.AVAILABLE);
            //int somewhere = count(TransactingCharacter.PLAYED_SOMEWHERE);
            if(here != 0) {
                if(position == 0) return PLAYED_HERE_SEPARATOR_ID;
                position--;
                if(position < here) {
                    final TransactingCharacter got = getFilteredCharacter(position, TransactingCharacter.PLAYED_HERE);
                    return got == null? RecyclerView.NO_ID : got.pc.peerKey;
                }
                position -= here;
            }
            if(avail != 0) {
                if(position == 0) return AVAILABLE_SEPARATOR_ID;
                position--;
                if(position < avail) {
                    final TransactingCharacter got = getFilteredCharacter(position, TransactingCharacter.AVAILABLE);
                    return got == null? RecyclerView.NO_ID : got.pc.peerKey;
                }
            }
            // must be SomewhereSeparator
            return PLAYED_SOMEWHERE_SEPARATOR_ID;
        }
        private static final int PLAYED_HERE_SEPARATOR_ID = -1;
        private static final int AVAILABLE_SEPARATOR_ID = -2;
        private static final int PLAYED_SOMEWHERE_SEPARATOR_ID = -3;

        @Override
        public int getItemCount() {
            int here = count(TransactingCharacter.PLAYED_HERE);
            int avail = count(TransactingCharacter.AVAILABLE);
            int somewhere = count(TransactingCharacter.PLAYED_SOMEWHERE);
            int separators = 0;
            if(here != 0) separators++;
            if(avail != 0) separators++;
            if(somewhere != 0) separators++;
            return here + avail + separators; // characters assigned elsewhere are hidden.
        }

        @Override
        public int getItemViewType(int position) {
            int here = count(TransactingCharacter.PLAYED_HERE);
            int avail = count(TransactingCharacter.AVAILABLE);
            //int somewhere = count(TransactingCharacter.PLAYED_SOMEWHERE);
            if(here != 0) {
                if(position == 0) return PlayedHereSeparator.TYPE;
                position--;
                if(position < here) return PlayedHereHolder.TYPE;
                position -= here;
            }
            if(avail != 0) {
                if(position == 0) return AvailableSeparator.TYPE;
                position--;
                if(position < avail) return AvailableHolder.TYPE;
                //position -= here;
            }
            return SomewhereSeparator.TYPE;
        }

        @Override
        public VariedHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inf = getLayoutInflater();
            switch(viewType) {
                case AvailableSeparator.TYPE:
                    return new AvailableSeparator(inf.inflate(R.layout.vh_character_selection_available_separator, parent, false));
                case SomewhereSeparator.TYPE:
                    return new SomewhereSeparator(inf.inflate(R.layout.vh_character_selection_somewhere_separator, parent, false));
                case PlayedHereSeparator.TYPE:
                    return new PlayedHereSeparator(inf.inflate(R.layout.vh_character_selection_here_separator, parent, false));
                case AvailableHolder.TYPE:
                    return new AvailableHolder(inf.inflate(R.layout.vh_character_selection_available_character, parent, false));
                case PlayedHereHolder.TYPE:
                    return new PlayedHereHolder(inf.inflate(R.layout.vh_character_selection_played_here_character, parent, false));
            }
            return null;
        }

        @Override
        public void onBindViewHolder(VariedHolder holder, int position) {
            int here = count(TransactingCharacter.PLAYED_HERE);
            int avail = count(TransactingCharacter.AVAILABLE);
            //int somewhere = count(TransactingCharacter.PLAYED_SOMEWHERE);
            if(here != 0) {
                if(position == 0) {
                    holder.bind(null);
                    return;
                }
                position--;
                if(position < here) {
                    holder.bind(getFilteredCharacter(position, TransactingCharacter.PLAYED_HERE));
                    return;
                }
                position -= here;
            }
            if(avail != 0) {
                if(position == 0) {
                    holder.bind(null);
                    return;
                }
                position--;
                if(position < avail) {
                    holder.bind(getFilteredCharacter(position, TransactingCharacter.AVAILABLE));
                    return;
                }
            }
            // must be SomewhereSeparator
            holder.bind(null);
        }
    }

    private int count(int type) {
        int match = 0;
        for(TransactingCharacter el : chars) {
            if(type == el.type) match++;
        }
        return match;
    }

    private TransactingCharacter getFilteredCharacter(int position, int filter) {
        for(TransactingCharacter pc : chars) {
            if(pc.type != filter) continue;
            if(position == 0) return pc;
            position--;
        }
        return null;
    }

    private TransactingCharacter getCharacterByKey(int key) {
        for(TransactingCharacter pc : chars) {
            if(pc.pc.peerKey == key) return pc;
        }
        return null;
    }

    /// Either ask a char to be assigned to me or ask it to be given away.
    private void characterRequest(int charKey) {
        TransactingCharacter character = getCharacterByKey(charKey);
        if(null == character) return; // impossible
        // It might be AVAILABLE or PLAYED_HERE, it doesn't matter to me, get request and notify.
        if(character.pending != TransactingCharacter.NO_REQUEST) return; // don't mess with us.
        character.pending = ticket++;
        ((RecyclerView)findViewById(R.id.csa_pcList)).getAdapter().notifyDataSetChanged();
        character.sendRequest(pipe);
    }

    class MyItemTouchCallback extends ItemTouchHelper.SimpleCallback {
        static final int DRAG_FORBIDDEN = 0;
        static final int SWIPE_HORIZONTAL = ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;

        MyItemTouchCallback() {
            super(DRAG_FORBIDDEN, SWIPE_HORIZONTAL);
        }

        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
            return false;
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
            if(!(viewHolder instanceof PlayedHereHolder)) return;
            PlayedHereHolder real = (PlayedHereHolder)viewHolder;
            characterRequest(real.charKey);
        }

        @Override
        public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            if(viewHolder instanceof PlayedHereHolder) return makeMovementFlags(DRAG_FORBIDDEN, SWIPE_HORIZONTAL);
            return 0;
        }
    }
}
