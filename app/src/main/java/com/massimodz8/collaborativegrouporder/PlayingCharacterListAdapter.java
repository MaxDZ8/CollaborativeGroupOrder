package com.massimodz8.collaborativegrouporder;

import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

/**
 * Created by Massimo on 25/01/2016.
 * Displaying a list of characters is a bit more involved because I want to share code between client
 * and server. Depending on mode, I select slightly different layouts.
 * The server will enable/disable/show/hide various buttons.
 */
class PlayingCharacterListAdapter extends RecyclerView.Adapter<PlayingCharacterListAdapter.PCViewHolder> {
    interface DataPuller {
        int getVisibleCount();
        /** The values you get there enumerate the action to perform, which is basically the button
         * hit to trigger this callbacks. The values depend on what you have instantiated this for.
         * MODE_CLIENT_INPUT will send only SEND.
         * MODE_SERVER_ACCEPTANCE sends only ACCEPT and REJECT.
         */
        void action(BuildingPlayingCharacter who, int what);

        AlertDialog.Builder makeDialog();
        String getString(int r);

        /// Forwarding to a LayoutInflater
        View inflate(int resource, ViewGroup root, boolean attachToRoot);

        BuildingPlayingCharacter get(int position);
        long getStableId(int position);
    }
    static final int MODE_CLIENT_INPUT = 1;
    static final int MODE_SERVER_ACCEPTANCE = 2;

    static final int SEND = 0;
    static final int ACCEPT = 1;
    static final int REJECT = 2;

    public PlayingCharacterListAdapter(DataPuller puller, int mode) {
        this.puller = puller;
        setHasStableIds(true);
        this.mode = mode;
    }

    private final int mode;
    private final DataPuller puller;

    @Override
    public PCViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final int resid = mode == MODE_CLIENT_INPUT? R.layout.card_joining_character_client_input : R.layout.card_joining_character_server_output;
        return new PCViewHolder(puller.inflate(resid, parent, false));
    }

    private static void visibility(View target, BuildingPlayingCharacter state, int match) {
        if(null == target) return;
        target.setVisibility(match == state.status? View.VISIBLE : View.GONE);
    }

    @Override
    public void onBindViewHolder(PCViewHolder holder, int position) {
        holder.who = puller.get(position);
        // Ok so, stuff is here or maybe it's not. Since those can be cycled I need to assume
        // everything must be enabled/disabled/shown/hidden.
        visibility(holder.check, holder.who, BuildingPlayingCharacter.STATUS_ACCEPTED);
        visibility(holder.send, holder.who, BuildingPlayingCharacter.STATUS_BUILDING);
        visibility(holder.check, holder.who, BuildingPlayingCharacter.STATUS_ACCEPTED);
        visibility(holder.sentFeedback, holder.who, BuildingPlayingCharacter.STATUS_SENT);
        visibility(holder.accept, holder.who, BuildingPlayingCharacter.STATUS_BUILDING);
        visibility(holder.refuse, holder.who, BuildingPlayingCharacter.STATUS_BUILDING);
        holder.initiative.setText(valueof(holder.who.initiativeBonus));
        holder.health.setText(valueof(holder.who.fullHealth));
        holder.experience.setText(valueof(holder.who.experience));
        holder.name.setText(holder.who.name);
    }

    static String valueof(int x) {
        if(x == Integer.MIN_VALUE) return "";
        return String.valueOf(x);
    }

    @Override
    public int getItemCount() {
        return puller.getVisibleCount();
    }

    @Override
    public long getItemId(int position) {
        return puller.getStableId(position);
    }


    // View holder pattern <-> keep handles to internal Views so I don't need to look em up.
    protected class PCViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        TextView name, health, initiative, experience;
        Button send; // client
        Button refuse, accept; // server
        CheckBox check; // client
        TextView sentFeedback;

        BuildingPlayingCharacter who;

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
            sentFeedback = (TextView) container.findViewById(R.id.card_joiningCharacter_sentFeedback);
            if(null != send) send.setOnClickListener(this);
            if(null != refuse) refuse.setOnClickListener(this);
            if(null != accept) accept.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            boolean changeStatus = false, newStatus = false;
            if(v == send) {
                if(validate()) {
                    puller.action(who, SEND);
                    changeStatus = true;
                    newStatus = false;
                }
            }
            else if(v == refuse) {
                puller.action(who, REJECT);
                changeStatus = true;
                newStatus = true;
            }
            else if(v == accept) {
                puller.action(who, ACCEPT);
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
                who.experience = xp;
                who.fullHealth = hp;
                who.initiativeBonus = init;
                who.name = name.getText().toString();
            }
            return errors.isEmpty();
        }
    }
}
