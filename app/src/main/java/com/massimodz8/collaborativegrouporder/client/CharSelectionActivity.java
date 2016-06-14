package com.massimodz8.collaborativegrouporder.client;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.massimodz8.collaborativegrouporder.PreSeparatorDecorator;
import com.massimodz8.collaborativegrouporder.R;
import com.massimodz8.collaborativegrouporder.RunningServiceHandles;

/**
 * This Activity 'does nothing' but presenting its state. It is assumed state exists when this
 * runs. This complete separation simplifies management considerably.
 */
public class CharSelectionActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_char_selection);

        RecyclerView list = (RecyclerView) findViewById(R.id.csa_pcList);
        final RecyclerView.Adapter adapter = new MyLister();
        list.setAdapter(adapter);
        list.addItemDecoration(new PreSeparatorDecorator(list, this) {
            @Override
            protected boolean isEligible(int position) {
                // see adapter's onBindViewHolder
                final PcAssignmentState state = RunningServiceHandles.getInstance().bindChars;
                int here = state.count(PcAssignmentState.TransactingCharacter.PLAYED_HERE);
                int avail = state.count(PcAssignmentState.TransactingCharacter.AVAILABLE);
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
    }

    int disconnect, detach, charDefined, partyReady, ownershipChanged;

    @Override
    protected void onResume() {
        super.onResume();
        final PcAssignmentState state = RunningServiceHandles.getInstance().bindChars;
        final Runnable checkReady = new Runnable() {
            @Override
            public void run() {
                if(state.playChars == null) return; // we didn't get the definitive assignment yet.
                if(state.playChars.length == 0) {
                    new AlertDialog.Builder(CharSelectionActivity.this, R.style.AppDialogStyle)
                            .setMessage(getString(R.string.csa_noDefinitiveCharactersHere))
                            .setCancelable(false)
                            .setPositiveButton(getString(R.string.csa_noDefinitiveCharactersHereDlgDone), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    finish();
                                }
                            })
                            .show();
                }
                // otherwise, wait for detach.
            }
        };
        partyReady = state.onPartyReady.put(checkReady);
        checkReady.run();

        final Runnable checkDetach = new Runnable() {
            @Override
            public void run() {
                if (!state.completed) return; // I need this to filter, not really required
                setResult(RESULT_OK);
                finish();
            }
        };
        detach = state.onDetached.put(checkDetach);
        checkDetach.run();

        final Runnable checkDisconnect = new Runnable() {
            @Override
            public void run() {
                // TODO
            }
        };
        disconnect = state.onDisconnected.put(checkDisconnect);
        // TODO: do I call this?

        final Runnable checkDefs = new Runnable() {
            @Override
            public void run() {
                RecyclerView view = (RecyclerView) findViewById(R.id.csa_pcList);
                view.getAdapter().notifyDataSetChanged();
            }
        };
        charDefined = state.onCharacterDefined.put(checkDefs);
        checkDefs.run();

        // Ownerships are more complicated.
        final PcAssignmentState.OwnershipListener checkOwn = new PcAssignmentState.OwnershipListener() {
            @Override
            public void onRefused(String name) {
                ViewGroup root = (ViewGroup) findViewById(R.id.csa_guiRoot);
                String text = String.format(getString(R.string.csa_charOwnershipChangeRefused), name);
                Snackbar.make(root, text, Snackbar.LENGTH_SHORT).show();
            }

            @Override
            public void onGiven(String name) {
                ViewGroup root = (ViewGroup) findViewById(R.id.csa_guiRoot);
                String text = String.format(getString(R.string.csa_charOwnershipToSomeoneUnsolicited), name);
                Snackbar.make(root, text, Snackbar.LENGTH_SHORT).show();
            }

            @Override
            public void onTakenAway(String name) {
                ViewGroup root = (ViewGroup) findViewById(R.id.csa_guiRoot);
                String text = String.format(getString(R.string.csa_charOwnershipRequestToSomeoneElse), name);
                Snackbar.make(root, text, Snackbar.LENGTH_SHORT).show();
            }

            @Override
            public void onRefresh() {
                final RecyclerView list = (RecyclerView) findViewById(R.id.csa_pcList);
                list.getAdapter().notifyDataSetChanged();
            }
        };
        ownershipChanged = state.onOwnershipChanged.put(checkOwn);
        checkOwn.onRefresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        final PcAssignmentState state = RunningServiceHandles.getInstance().bindChars;
        state.onPartyReady.remove(partyReady);
        state.onDisconnected.remove(disconnect);
        state.onCharacterDefined.remove(charDefined);
        state.onDetached.remove(detach);
        state.onOwnershipChanged.remove(ownershipChanged);
    }

    private static abstract class VariedHolder extends RecyclerView.ViewHolder {
        public VariedHolder(View itemView) {
            super(itemView);
        }

        public abstract void bind(PcAssignmentState.TransactingCharacter o);
    }

    private static class PlayedHereSeparator extends VariedHolder {
        static final int TYPE = 0;
        public PlayedHereSeparator(View itemView) {
            super(itemView);
        }

        @Override
        public void bind(PcAssignmentState.TransactingCharacter o) {
            // nothing to do with this
        }
    }

    private static class AvailableSeparator extends VariedHolder {
        static final int TYPE = 1;
        public AvailableSeparator(View itemView) {
            super(itemView);
        }

        @Override
        public void bind(PcAssignmentState.TransactingCharacter o) {
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
        public void bind(PcAssignmentState.TransactingCharacter o) {
            int count = RunningServiceHandles.getInstance().bindChars.count(PcAssignmentState.TransactingCharacter.PLAYED_SOMEWHERE);
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
        public void bind(PcAssignmentState.TransactingCharacter o) {
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
        public void bind(PcAssignmentState.TransactingCharacter o) {
            charKey = o.pc.peerKey;
            name.setText(o.pc.name);
            level.setText(String.format(getString(R.string.csa_level), o.pc.level));
            hpmax.setText(String.format(getString(R.string.csa_hpMax), o.pc.healthPoints));
            xp.setText(String.format(getString(R.string.csa_xp), o.pc.experience));
            requested.setVisibility(o.pending != PcAssignmentState.TransactingCharacter.NO_REQUEST? View.VISIBLE : View.GONE);
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
            final PcAssignmentState state = RunningServiceHandles.getInstance().bindChars;
            int here = state.count(PcAssignmentState.TransactingCharacter.PLAYED_HERE);
            int avail = state.count(PcAssignmentState.TransactingCharacter.AVAILABLE);
            //int somewhere = count(TransactingCharacter.PLAYED_SOMEWHERE);
            if(here != 0) {
                if(position == 0) return PLAYED_HERE_SEPARATOR_ID;
                position--;
                if(position < here) {
                    final PcAssignmentState.TransactingCharacter got = state.getFilteredCharacter(position, PcAssignmentState.TransactingCharacter.PLAYED_HERE);
                    return got == null? RecyclerView.NO_ID : got.pc.peerKey;
                }
                position -= here;
            }
            if(avail != 0) {
                if(position == 0) return AVAILABLE_SEPARATOR_ID;
                position--;
                if(position < avail) {
                    final PcAssignmentState.TransactingCharacter got = state.getFilteredCharacter(position, PcAssignmentState.TransactingCharacter.AVAILABLE);
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
            final PcAssignmentState state = RunningServiceHandles.getInstance().bindChars;
            int here = state.count(PcAssignmentState.TransactingCharacter.PLAYED_HERE);
            int avail = state.count(PcAssignmentState.TransactingCharacter.AVAILABLE);
            int somewhere = state.count(PcAssignmentState.TransactingCharacter.PLAYED_SOMEWHERE);
            int separators = 0;
            if(here != 0) separators++;
            if(avail != 0) separators++;
            if(somewhere != 0) separators++;
            return here + avail + separators; // characters assigned elsewhere are hidden.
        }

        @Override
        public int getItemViewType(int position) {
            final PcAssignmentState state = RunningServiceHandles.getInstance().bindChars;
            int here = state.count(PcAssignmentState.TransactingCharacter.PLAYED_HERE);
            int avail = state.count(PcAssignmentState.TransactingCharacter.AVAILABLE);
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
            final PcAssignmentState state = RunningServiceHandles.getInstance().bindChars;
            int here = state.count(PcAssignmentState.TransactingCharacter.PLAYED_HERE);
            int avail = state.count(PcAssignmentState.TransactingCharacter.AVAILABLE);
            //int somewhere = count(TransactingCharacter.PLAYED_SOMEWHERE);
            if(here != 0) {
                if(position == 0) {
                    holder.bind(null);
                    return;
                }
                position--;
                if(position < here) {
                    holder.bind(state.getFilteredCharacter(position, PcAssignmentState.TransactingCharacter.PLAYED_HERE));
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
                    holder.bind(state.getFilteredCharacter(position, PcAssignmentState.TransactingCharacter.AVAILABLE));
                    return;
                }
            }
            // must be SomewhereSeparator
            holder.bind(null);
        }
    }

    /// Either ask a char to be assigned to me or ask it to be given away.
    private void characterRequest(int charKey) {
        if(RunningServiceHandles.getInstance().bindChars.characterRequest(charKey)) {
            ((RecyclerView)findViewById(R.id.csa_pcList)).getAdapter().notifyDataSetChanged();
        }
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
