package com.massimodz8.collaborativegrouporder;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.StringRes;

import com.google.protobuf.nano.MessageNano;
import com.massimodz8.collaborativegrouporder.master.PcAssignmentHelper;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;

/**
 * Support PartyPickActivity by providing an internal, semi-persistent state.
 */
public class PartyPickingService extends Service {
    public MessageNano sessionParty; // when party picking goes adventuring the party to use is put there.
    public IdentityHashMap<MessageNano, PersistentDataUtils.SessionStructs> sessionData;
    public IdentityHashMap<MessageNano, String> sessionErrors;
    public Runnable onSessionDataLoaded;

    public PartyPickingService() {
    }


    PartyPickingService setKnownParties(final ArrayList<StartData.PartyOwnerData.Group> defs,
                                        final ArrayList<StartData.PartyClientData.Group> keys) {
        this.defs = new ArrayList<>();
        this.keys = new ArrayList<>();
        this.defs.addAll(defs);
        this.keys.addAll(keys);
        hideDefKey = new boolean[defs.size() + keys.size()];
        return this;
    }

    AsyncTask<Void, Void, Integer> startLoadingSessions() {
        if(sessionData != null) {
            if(onSessionDataLoaded != null) onSessionDataLoaded.run();
            return null;
        }
        final PersistentDataUtils loader = new PersistentDataUtils(PcAssignmentHelper.DOORMAT_BYTES) {
            @Override
            protected String getString(@StringRes int resource) {
                return PartyPickingService.this.getString(resource);
            }
        };
        final ArrayList<StartData.PartyOwnerData.Group> threadDefs = new ArrayList<>();
        final ArrayList<StartData.PartyClientData.Group> threadKeys = new ArrayList<>();
        final IdentityHashMap<MessageNano, PersistentDataUtils.SessionStructs> result = new IdentityHashMap<>();
        final IdentityHashMap<MessageNano, String> errors = new IdentityHashMap<>();
        threadDefs.addAll(defs);
        threadKeys.addAll(keys);
        return new AsyncTask<Void, Void, Integer>() {
            @Override
            protected Integer doInBackground(Void... params) {
                int errors = 0;
                for (StartData.PartyOwnerData.Group party : threadDefs) errors += loadSession(party, party.sessionFile);
                for (StartData.PartyClientData.Group party : threadKeys) errors += loadSession(party, party.sessionFile);
                return errors;
            }

            @Override
            protected void onPostExecute(Integer errorCount) {
                sessionData = result;
                sessionErrors = errors;
                if(onSessionDataLoaded != null) onSessionDataLoaded.run();
            }

            private int loadSession(MessageNano party, String sessionFile) {
                File data = new File(getFilesDir(), sessionFile);
                if(!data.exists()) {
                    errors.put(party, getString(R.string.pes_sessionNotFound));
                    return 1;
                }
                PersistentDataUtils.SessionStructs loaded = new PersistentDataUtils.SessionStructs();
                if(data.length() == 0) {
                    result.put(party, loaded);
                    return 1;
                }
                if(data.length() > PersistentDataUtils.MAX_SESSION_DATA_BYTES) {
                    errors.put(party, String.format(getString(R.string.pes_sessionTooBig), data.length(), PersistentDataUtils.MAX_SESSION_DATA_BYTES));
                    return 1;
                }
                FileInputStream load;
                try {
                    load = new FileInputStream(data);
                } catch (FileNotFoundException e) {
                    errors.put(party, getString(R.string.ppa_noInputStream));
                    return 1;
                }
                String error = loader.load(loaded, load, (int)data.length());
                if(error != null) {
                    errors.put(party, error);
                    try {
                        load.close();
                    } catch (IOException e) {
                        // suppress
                    }
                    return 1;
                }
                result.put(party, loaded);
                return 0;
            }
        };
    }

    public boolean setDeletionFlag(MessageNano party, boolean delete) {
        int index = 0;
        while(defs.get(index) != party) index++;
        if(index != defs.size()) {
            while(keys.get(index - defs.size()) != party) index++;
        }
        boolean was = hideDefKey[index];
        hideDefKey[index] = delete;
        return delete != was;
    }
    public StartData.PartyOwnerData.Group getOwned(int sparse) {
        return defs.get(sparse);
    }
    public StartData.PartyClientData.Group getJoined(int sparse) {
        return keys.get(sparse);
    }

    public void getDense(ArrayList<StartData.PartyOwnerData.Group> owned, ArrayList<StartData.PartyClientData.Group> joined, boolean copyIfDeletionFlag) {
        int index = 0;
        for (StartData.PartyOwnerData.Group party : defs) {
            if(hideDefKey[index++] == copyIfDeletionFlag) owned.add(party);
        }
        for (StartData.PartyClientData.Group party : keys) {
            if(hideDefKey[index++] == copyIfDeletionFlag) joined.add(party);
        }
    }

    public int indexOf(MessageNano party) {
        int index = 0;
        for (StartData.PartyOwnerData.Group test : defs) {
            if(party == test) return index;
            index++;
        }
        for (StartData.PartyClientData.Group test : keys) {
            if(party == test) return index;
            index++;
        }
        return -1;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    class LocalBinder extends Binder {
        PartyPickingService getConcreteService() { return PartyPickingService.this; }
    }

    private ArrayList<StartData.PartyOwnerData.Group> defs;
    private ArrayList<StartData.PartyClientData.Group> keys;
    private boolean[] hideDefKey;

    private <X> ArrayList<X> condCopy(ArrayList<X> coll, int offset, boolean discard) {
        ArrayList<X> res = new ArrayList<>();
        for(int loop = 0; loop < coll.size(); loop++) {
            if(hideDefKey[offset + loop] == discard) continue;
            res.add(coll.get(loop));
        }
        return res;
    }
}
