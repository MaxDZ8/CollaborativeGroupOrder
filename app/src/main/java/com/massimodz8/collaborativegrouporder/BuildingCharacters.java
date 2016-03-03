package com.massimodz8.collaborativegrouporder;

import android.os.AsyncTask;

import com.massimodz8.collaborativegrouporder.networkio.Events;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

import java.io.IOException;
import java.util.Date;
import java.util.Vector;

/**
 * Created by Massimo on 08/02/2016.
 * NewPartyDeviceSelectionActivity and NewCharactersApprovalActivity use this to share some common
 * code regarding building the list of devices / characters. At the end, this is a group
 * being formed which will later become a PersistentStorage structure.
 */
public class BuildingCharacters {
    String name;

    public Vector<DeviceStatus> clients;


    DeviceStatus get(MessageChannel c) {
        for (DeviceStatus d : clients) {
            if (d.source == c) return d;
        }
        return null; // impossible most of the time
    }

    void definePlayingCharacter(final Events.CharacterDefinition ev) {
        DeviceStatus owner = get(ev.origin);
        if(null == owner) return; // impossible
        if(!owner.groupMember) {
            new AsyncTask<Void, Void, Exception>() {
                @Override
                protected Exception doInBackground(Void... params) {
                    Network.GroupFormed negative = new Network.GroupFormed();
                    negative.accepted = false;
                    negative.peerKey = ev.character.peerKey;
                    try {
                        ev.origin.writeSync(ProtoBufferEnum.GROUP_FORMED, negative);
                    } catch (IOException e) {
                        return e;
                    }
                    return null;
                }

                //@Override
                //protected void onPostExecute(Exception e) {
                //    if(null != e) {
                //        // This is usually because the connection goes down so do nothing and wait till disconnect is signaled.
                //    }
                //}
            }.execute();
            return;
        }
        for(int rebind = 0; rebind < owner.chars.size(); rebind++) {
            BuildingPlayingCharacter pc = owner.chars.elementAt(rebind);
            if(pc.peerKey == ev.character.peerKey) {
                if(pc.status == BuildingPlayingCharacter.STATUS_ACCEPTED) return; // ignore the thing, it's client's problem, not ours.
                owner.chars.remove(rebind);
            }
        }
        owner.chars.add(new BuildingPlayingCharacter(ev.character));
    }

    void setMessage(Events.PeerMessage ev, int interval_ms) {
        DeviceStatus owner = get(ev.which);
        if(null == owner) return;
        if(ev.msg.charSpecific != 0) return; // ignore, this is not supported at this stage you mofo.
        if(ev.msg.text.length() >= owner.charBudget) return; // messages exceeding can be dropped
        if(null != owner.nextMessage && owner.nextMessage.after(new Date())) return; // also ignore messaging too fast
        owner.charBudget -= ev.msg.text.length();
        owner.nextMessage = new Date(new Date().getTime() + interval_ms);
        owner.lastMessage = ev.msg.text;
    }
}
