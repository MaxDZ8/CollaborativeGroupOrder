package com.massimodz8.collaborativegrouporder.client;

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
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class CharSelectionActivity extends AppCompatActivity {
    // Before starting this activity, make sure to populate its connection parameters and friends.
    // Those will be cleared as soon as the activity goes onCreate and then never reused again.
    // onCreate assumes those non-null. Just call prepare(...)
    private static Pumper.MessagePumpingThread serverPipe;
    private static PersistentStorage.PartyClientData.Group connectedParty;
    private static Network.PlayingCharacterList pcList; // this can be of 4 different types... and can actually be null, even though it doesn't make sense.

    private PersistentStorage.PartyClientData.Group party;
    private MessageChannel pipe;

    public static void prepare(Pumper.MessagePumpingThread pipe, PersistentStorage.PartyClientData.Group info, Network.PlayingCharacterList last) {
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
                        handler.sendMessage(handler.obtainMessage(MSG_LIST_RECEIVED, msg));
                        return msg.set == Network.PlayingCharacterList.YOURS_DEFINITIVE;
                    }
                });
        handler = new MyHandler(this);

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

        if(null != pcList) dispatch(pcList);
        pcList = null;

        pipe = serverPipe.getSource();
        netPump.pump(serverPipe);
        serverPipe = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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
    private static final int MSG_LIST_RECEIVED = 3;

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
        int set(int type) {
            int res = type != this.type || pending != NO_REQUEST? 1 : 0;
            this.type = type;
            pending = NO_REQUEST;
            return res;
        }

        private int pending = NO_REQUEST; // check type to understand if being given away or being requested
        static int requestCount = 0;
        int newRequest() {
            return pending = requestCount++;
        }
        boolean relevant(int requestCount) {
            return pending != NO_REQUEST && pending <= requestCount;
        }
    }

    ArrayList<TransactingCharacter> chars = new ArrayList<>();

    private void dispatch(Network.PlayingCharacterList list) {
        int takenAway = 0, refused = 0, changes = 0;
        switch (list.set) {
            case Network.PlayingCharacterList.READY:   // READY is characters bound to devices not you.
            case Network.PlayingCharacterList.AVAIL: { // AVAIL is not bound to you nor anyone else
                // For our purposes what matters here is that we either dont' get owneship or we lose it, they're the same.
                for (Network.PlayingCharacterDefinition pc : list.payload) {
                    TransactingCharacter known = getCharacterByKey(pc.peerKey);
                    if(null == known) {
                        TransactingCharacter add = new TransactingCharacter(pc);
                        chars.add(add);
                        if(list.set == Network.PlayingCharacterList.READY) add.type = TransactingCharacter.PLAYED_SOMEWHERE;
                        changes++;
                        continue;
                    }
                    // Assigned to someone else. Two conditions here worth signaling.
                    if(known.type == TransactingCharacter.PLAYED_HERE && known.pending == TransactingCharacter.NO_REQUEST) takenAway++;
                    else if(known.type == TransactingCharacter.AVAILABLE && known.relevant(list.requestCount)) refused++;
                    int status = list.set == Network.PlayingCharacterList.READY? TransactingCharacter.PLAYED_SOMEWHERE : TransactingCharacter.AVAILABLE;
                    changes += known.set(status);
                }
            } break;
            case Network.PlayingCharacterList.YOURS:
            case Network.PlayingCharacterList.YOURS_DEFINITIVE: { // definitive is the same as further processing happens on pump detach
                for (Network.PlayingCharacterDefinition pc : list.payload) {
                    TransactingCharacter known = getCharacterByKey(pc.peerKey);
                    if(null == known) {
                        TransactingCharacter add = new TransactingCharacter(pc);
                        add.type = TransactingCharacter.PLAYED_HERE;
                        chars.add(add);
                        changes++;
                        continue;
                    }
                    // Assigned to me. Signal if this is a refusal of taking my char back, otherwise it's fine.
                    if(known.type == TransactingCharacter.PLAYED_HERE && known.relevant(list.requestCount)) refused++;
                    changes += known.set(TransactingCharacter.PLAYED_HERE);
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
        if(refused != 0 || takenAway != 0 || changes != 0) {
            RecyclerView view = (RecyclerView) findViewById(R.id.csa_pcList);
            view.getAdapter().notifyDataSetChanged();
        }
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
            name = (TextView) itemView.findViewById(R.id.vhAC_name);
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
            level.setText(String.format(getString(R.string.vhAC_level), o.pc.level));
            hpmax.setText(String.format(getString(R.string.vhAC_hpMax), o.pc.healthPoints));
            xp.setText(String.format(getString(R.string.vhAC_xp), o.pc.experience));
            requested.setVisibility(o.pending != TransactingCharacter.NO_REQUEST? View.VISIBLE : View.GONE);
        }

        @Override
        public void onClick(View v) {
            characterRequest(charKey);
        }
    }

    private class MyLister extends RecyclerView.Adapter<VariedHolder> {
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
        character.newRequest();
        ((RecyclerView)findViewById(R.id.csa_pcList)).getAdapter().notifyDataSetChanged();

        final Network.PlayingCharacterMoveRequest payload = new Network.PlayingCharacterMoveRequest();
        payload.character = charKey;
        payload.take = character.type == TransactingCharacter.AVAILABLE;
        new Thread() {
            public void run() {
                try {
                    pipe.writeSync(ProtoBufferEnum.CHARACTER_MOVE_REQUEST, payload);
                } catch (IOException e) {
                    // suppress this. This is huge, but I need something better designed to deal with errors,
                    // perhaps I will have my AppCompatActivityWithPendingAsyncOperations class.
                }
            }
        }.start();
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
