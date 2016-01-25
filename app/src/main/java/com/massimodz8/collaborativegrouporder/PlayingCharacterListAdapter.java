package com.massimodz8.collaborativegrouporder;

import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Created by Massimo on 25/01/2016.
 * Displaying a list of characters is a bit more involved because I want to share code between client
 * and server.
 * Both use card_joining_character layout, which is by default in client mode.
 * The server will enable/disable/show/hide various buttons.
 */
class PlayingCharacterListAdapter extends RecyclerView.Adapter<PlayingCharacterListAdapter.PCViewHolder> {
    interface PlayingCharacterPuller {
        int getVisibleCount();
        /** The values you get there enumerate the action to perform, which is basically the button
         * hit to trigger this callbacks. The values depend on what you have instantiated this for.
         * MODE_CLIENT_INPUT will send only SEND.
         * MODE_SERVER_ACCEPTANCE sends only ACCEPT and REJECT.
         */
        void action(PlayingCharacter who, String peerKey, int what);

        AlertDialog.Builder makeDialog();
        String getString(int r);

        /// Forwarding to a LayoutInflater
        View inflate(int resource, ViewGroup root, boolean attachToRoot);

        JoinGroupActivity.PlayingCharacter get(int position);
    }
    static final int MODE_CLIENT_INPUT = 1;
    static final int MODE_SERVER_ACCEPTANCE = 2;

    static final int SEND = 0;
    static final int ACCEPT = 1;
    static final int REJECT = 2;

    public PlayingCharacterListAdapter(PlayingCharacterPuller puller, int mode) {
        this.puller = puller;
        setHasStableIds(true);
        this.mode = mode;
    }

    private final int mode;
    private final PlayingCharacterPuller puller;

    @Override
    public PCViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View layout = puller.inflate(R.layout.card_joinable_group, parent, false);
        return new PCViewHolder(layout);
    }

    @Override
    public void onBindViewHolder(PCViewHolder holder, int position) {
        holder.who = puller.get(position);
        // Ok so, stuff is here or maybe it's not. Since those can be cycled I need to assume
        // everything must be enabled/disabled/shown/hidden.
        holder.check.setVisibility(JoinGroupActivity.PlayingCharacter.STATUS_ACCEPTED == holder.who.status ? View.VISIBLE : View.GONE);
        if(mode == MODE_CLIENT_INPUT) {
            holder.send.setVisibility(JoinGroupActivity.PlayingCharacter.STATUS_BUILDING == holder.who.status ? View.VISIBLE : View.GONE);
        }
        else { // MODE_SERVER_ACCEPTANCE
            holder.accept.setVisibility(JoinGroupActivity.PlayingCharacter.STATUS_BUILDING == holder.who.status? View.VISIBLE : View.GONE);
            holder.refuse.setVisibility(JoinGroupActivity.PlayingCharacter.STATUS_BUILDING == holder.who.status? View.VISIBLE : View.GONE);
        }
        holder.initiative.setText(String.valueOf(holder.who.payload.initiativeBonus));
        holder.health.setText(String.valueOf(holder.who.payload.fullHealth));
        holder.experience.setText(String.valueOf(holder.who.payload.experience));
        holder.name.setText(String.valueOf(holder.who.payload.name));
    }

    @Override
    public int getItemCount() {
        return puller.getVisibleCount();
    }

    @Override
    public long getItemId(int position) {
        return puller.get(position).unique;
    }


    // View holder pattern <-> keep handles to internal Views so I don't need to look em up.
    protected class PCViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        TextView name, health, initiative, experience;
        Button send; // client
        Button refuse, accept; // server
        CheckBox check; // client

        JoinGroupActivity.PlayingCharacter who;

        public PCViewHolder(View container) {
            super(container);
            name = (TextView) container.findViewById(R.id.card_joiningCharacter_name);
            health = (TextView) container.findViewById(R.id.card_joiningCharacter_hp);
            initiative = (TextView) container.findViewById(R.id.card_joiningCharacter_initBonus);
            experience = (TextView) container.findViewById(R.id.card_joiningCharacter_xp);
            send = (Button) container.findViewById(R.id.card_joiningCharacter_sendButton);
            refuse = (Button) container.findViewById(R.id.card_joiningCharacter_refuseButton);
            accept = (Button) container.findViewById(R.id.card_joiningCharacter_acceptButton);
            check = (CheckBox) container.findViewById(R.id.card_joiningCharacter_accepted);
            send.setOnClickListener(this);
            refuse.setOnClickListener(this);
            accept.setOnClickListener(this);

            switch(mode) {
                case MODE_CLIENT_INPUT:
                    refuse.setVisibility(View.GONE);
                    accept.setVisibility(View.GONE);
                    break;
                case MODE_SERVER_ACCEPTANCE:
                    name.setInputType(InputType.TYPE_NULL);
                    experience.setInputType(InputType.TYPE_NULL);
                    health.setInputType(InputType.TYPE_NULL);
                    initiative.setInputType(InputType.TYPE_NULL);
                    send.setVisibility(View.GONE);
                    break;
            }
        }

        @Override
        public void onClick(View v) {
            boolean changeStatus = false, newStatus = false;
            if(v == send) {
                if(validate()) {
                    puller.action(who.payload, who.peerKey, SEND);
                    changeStatus = true;
                    newStatus = false;
                }
            }
            else if(v == refuse) {
                puller.action(who.payload, who.peerKey, REJECT);
                changeStatus = true;
                newStatus = true;
            }
            else if(v == accept) {
                puller.action(who.payload, who.peerKey, ACCEPT);
                refuse.setVisibility(View.GONE);
                accept.setVisibility(View.GONE);
                check.setVisibility(View.VISIBLE);
            }

            if(changeStatus) {
                name.setEnabled(newStatus);
                health.setEnabled(newStatus);
                initiative.setEnabled(newStatus);
                experience.setEnabled(newStatus);
            }
        }

        private boolean validate() {
            String errors = "";
            if(name.getText().length() < 1) errors += puller.getString(R.string.charInputValidation_badName);
            int hp = 0, init = 0, xp = 0;
            try {
                hp = Integer.parseInt(health.getText().toString());
                if(hp <= 0) errors += puller.getString(R.string.charInputValidation_hpMustBePositive);
            } catch(NumberFormatException e) {
                errors += puller.getString(R.string.charInputValidation_hpMustBeInteger);
            }
            try {
                init = Integer.parseInt(initiative.getText().toString());
            } catch(NumberFormatException e) {
                errors += puller.getString(R.string.charInputValidation_initMustBeNumber);
            }
            try {
                xp = Integer.parseInt(experience.getText().toString());
                if(xp <= 0) errors += puller.getString(R.string.charInputValidation_xpMustBePositive);
            } catch(NumberFormatException e) {
                errors += puller.getString(R.string.charInputValidation_xpMustBeNumeric);
            }
            if(!errors.isEmpty()) {
                puller.makeDialog().setTitle(puller.getString(R.string.charInputValidation_badCharacter_title))
                        .setMessage(errors + puller.getString(R.string.charInputValidation_badCharacter_msgHint))
                        .show();
            }
            else {
                who.payload.experience = xp;
                who.payload.fullHealth = hp;
                who.payload.initiativeBonus = init;
                who.payload.name = name.getText().toString();
            }
            return errors.isEmpty();
        }
    }
}
