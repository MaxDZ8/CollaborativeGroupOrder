package com.massimodz8.collaborativegrouporder;

import android.support.annotation.StringRes;
import android.support.annotation.WorkerThread;

import com.google.protobuf.nano.CodedInputByteBufferNano;
import com.google.protobuf.nano.CodedOutputByteBufferNano;
import com.google.protobuf.nano.MessageNano;
import com.massimodz8.collaborativegrouporder.protocol.nano.LevelAdvancement;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;
import com.massimodz8.collaborativegrouporder.protocol.nano.Session;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


/**
 * Created by Massimo on 29/01/2016.
 * Stuff dealing with important, persistent data. Might involve future utilities to upgrade data
 * structures.
 */
public abstract class PersistentDataUtils {
    private final int minimumSaltBytes;

    protected PersistentDataUtils(int minimumSaltBytes) {
        this.minimumSaltBytes = minimumSaltBytes;
    }

    public static final String MAIN_DATA_SUBDIR = "main";
    public static final String DEFAULT_GROUP_DATA_FILE_NAME = "groupDefs";
    public static final String DEFAULT_KEY_FILE_NAME = "keys";

    public static final String SESSION_DATA_SUBDIR = "session";

    public static final String USER_CUSTOM_DATA_SUBDIR = "custom"; // monsters, prepared encounters etc
    public static final String CUSTOM_MOBS_FILE_NAME = "userMonsters";
    public static final String CUSTOM_ENCOUNTERS_FILE_NAME = "userEncounters";


    /**
     * Ok, we want to save a party, whatever it is owned or joined we need to create a "session file" which will be initially empty.
     * This function takes care of creating that empty file for you and returns its name.
     * @return File name to be used.
     */
    @WorkerThread
    public static String makeInitialSession(Date when, File filesDir, String name) {
        // First strategy is the most convenient for the user. It's party name. Likely to fail.
        // Then creation date + party name, only creation date. If this fails we're busted.
        File session = createSessionFile(filesDir, name);
        if(session != null) return name;
        Calendar local = Calendar.getInstance();
        local.setTime(when);
        String timestamp = String.format(Locale.ENGLISH, "%1$tY%1$tm%1$td_%1$tH%1$tM%1$tS", local);
        session = createSessionFile(filesDir, timestamp + name);
        if(session != null) return timestamp + name;
        session = createSessionFile(filesDir, timestamp);
        if(session != null) return timestamp;
        return null;
    }

    public static StartData.PartyOwnerData makePartyOwnerData(ArrayList<StartData.PartyOwnerData.Group> defs) {
        StartData.PartyOwnerData all = new StartData.PartyOwnerData();
        all.version = OWNER_DATA_VERSION;
        all.everything = new StartData.PartyOwnerData.Group[defs.size()];
        for(int cp = 0; cp < defs.size(); cp++) all.everything[cp] = defs.get(cp);
        return all;
    }

    public static StartData.PartyClientData makePartyClientData(ArrayList<StartData.PartyClientData.Group> defs) {
        StartData.PartyClientData all = new StartData.PartyClientData();
        all.version = CLIENT_DATA_WRITE_VERSION;
        all.everything = new StartData.PartyClientData.Group[defs.size()];
        for(int cp = 0; cp < defs.size(); cp++) all.everything[cp] = defs.get(cp);
        return all;
    }

    protected abstract String getString(@StringRes int resource);

    public static final int OWNER_DATA_VERSION = 1;
    public static final int CLIENT_DATA_WRITE_VERSION = 1;
    public static final int MAX_GROUP_DATA_BYTES = 1024 * 1024 * 4;
    public static final int MAX_SESSION_DATA_BYTES = 1024 * 1024 * 4;

    public <Container extends MessageNano> String mergeExistingGroupData(Container dst, File from) {
        FileInputStream source;
        try {
            source = new FileInputStream(from);
        } catch (FileNotFoundException e) {
            return null;
        }
        if(from.length() > MAX_GROUP_DATA_BYTES) return getString(R.string.persistentStorage_archiveTooBig);
        byte[] everything = new byte[(int)from.length()];
        try {
            final int count = source.read(everything);
            if(count != everything.length) return getString(R.string.persistentStorage_readSizeMismatch);
            source.close();
        } catch (IOException e) {
            return getString(R.string.persistentStorage_failedRead);
        }
        CodedInputByteBufferNano input = CodedInputByteBufferNano.newInstance(everything);
        try {
            dst.mergeFrom(input);
        } catch (IOException e) {
            return getString(R.string.persistentStorage_failedRead);
        }
        return null;
    }

    public ArrayList<String> validateLoadedDefinitions(StartData.PartyOwnerData loaded) {
        ArrayList<String> arr = new ArrayList<>();
        if(loaded.version == 0) {
            arr.add(getString(R.string.persistentStorage_groupDataVersionZero));
            return arr;
        }
        if(loaded.version >= 1) {
            for(int check = 0; check < loaded.everything.length; check++) {
                final StartData.PartyOwnerData.Group ref = loaded.everything[check];
                if(invalid(arr, ref, check)) continue;
                for(int cmp = check + 1; cmp < loaded.everything.length; cmp++) {
                    if(ref.name.equals(loaded.everything[cmp].name)) {
                        String err = getString(R.string.persistentStorage_groupNameClash);
                        arr.add(String.format(err, check, cmp, ref.name));
                    }
                }
            }
        }
        return arr.isEmpty()? null : arr;
    }

    public ArrayList<String> validateLoadedDefinitions(StartData.PartyClientData loaded) {
        ArrayList<String> arr = new ArrayList<>();
        if(loaded.version == 0) {
            arr.add(getString(R.string.persistentStorage_groupDataVersionZero));
            return arr;
        }
        if(loaded.version >= 1) {
            for(int check = 0; check < loaded.everything.length; check++) {
                final StartData.PartyClientData.Group ref = loaded.everything[check];
                if(invalid(arr, ref, check)) continue;
                for(int cmp = check + 1; cmp < loaded.everything.length; cmp++) {
                    if(ref.name.equals(loaded.everything[cmp].name) &&
                            Arrays.equals(ref.key, loaded.everything[cmp].key)) {
                        String err = getString(R.string.persistentStorage_groupKeyNameClash);
                        arr.add(String.format(err, check, cmp, ref.name));
                    }
                }
            }
        }
        return arr.isEmpty()? null : arr;
    }

    private boolean invalid(ArrayList<String> errors, StartData.PartyOwnerData.Group group, int index) {
        final int start = errors.size();
        final String premise = String.format(getString(R.string.persistentStorage_errorReport_premise), index, group.name.isEmpty() ? "" : String.format("(%1$s)", group.name));
        if(group.name.isEmpty()) errors.add(premise + getString(R.string.persistentStorage_missingName));
        if(group.created == null || group.created.nanos != 0 || group.created.seconds == 0) errors.add(premise + getString(R.string.persistentStorage_badCreationTimestamp));
        if(group.sessionFile == null || group.sessionFile.isEmpty()) errors.add(premise + getString(R.string.persistentStorage_badSessionFile));
        if(group.advancementPace == LevelAdvancement.LA_UNSPECIFIED) errors.add(premise + getString(R.string.persistentStorage_badLevelAdvancementPace));

        new ActorValidator(true, errors, String.format("%1$s->%2$s", premise, getString(R.string.persistentStorage_partyDefValidationPremise)))
                .check(getString(R.string.persistentStorage_playingCharacters), group.party)
                .check(getString(R.string.persistentStorage_NPC), group.npcs);

        for(int loop = 0; loop < group.devices.length; loop++) {
            StartData.PartyOwnerData.DeviceInfo dev = group.devices[loop];
            if(dev.salt.length < minimumSaltBytes) errors.add(premise + String.format(getString(R.string.persistentStorage_deviceBadSalt), loop));
        }
        return start != errors.size();
    }

    private boolean invalid(ArrayList<String> errors, StartData.PartyClientData.Group group, int index) {
        final int start = errors.size();
        final String premise = String.format(getString(R.string.persistentStorage_errorReport_premise), index, group.name.isEmpty() ? "" : String.format("(%1$s)", group.name));
        if(group.name.isEmpty()) errors.add(premise + getString(R.string.persistentStorage_missingName));
        if(group.key.length < 1) errors.add(premise + getString(R.string.persistentStorage_missingKey));
        if(group.received == null || group.received.nanos != 0 || group.received.seconds == 0) errors.add(premise + getString(R.string.persistentStorage_badCreationTimestamp));
        if(group.sessionFile == null || group.sessionFile.isEmpty()) errors.add(premise + getString(R.string.persistentStorage_badSessionFile));
        return start != errors.size();
    }

    public <Container extends MessageNano> String storeValidGroupData(File store, Container valid) {
        byte[] buff = new byte[valid.getSerializedSize()];
        CodedOutputByteBufferNano output = CodedOutputByteBufferNano.newInstance(buff);
        try {
            valid.writeTo(output);
        } catch (IOException e) {
            return getString(R.string.persistentStorage_failedSerialization);
        }
        FileOutputStream out;
        try {
            out = new FileOutputStream(store);
            out.write(buff);
            out.close();
        } catch (FileNotFoundException e) {
            return getString(R.string.persistentStorage_failedOutputStreamCreation);
        } catch (IOException e) {
            return getString(R.string.persistentStorage_failedWrite);
        }
        return null;
    }

    class ActorValidator {
        final boolean preparedActionForbidden;
        final ArrayList<String> errors;
        final String base;

        ActorValidator(boolean preparedActionForbidden, ArrayList<String> errors, String premiseBase) {
            this.preparedActionForbidden = preparedActionForbidden;
            this.errors = errors;
            base = premiseBase;
        }

        ActorValidator check(String mod, StartData.ActorDefinition[] list) {
            final String premise = base + mod;
            for(int i = 0; i < list.length; i++) {
                final StartData.ActorDefinition actor = list[i];
                String head = String.format(Locale.getDefault(), "%1$s[%2$d]", premise, i);
                if(actor.name.isEmpty()) errors.add(head + getString(R.string.persistentStorage_actorMissingName));
                head = String.format("%1$s(%2$s)", head, actor.name);
                check(head + getString(R.string.persistentStorage_actorStatsPremise), actor.stats);
            }
            return this;
        }

        void check(String premise, StartData.ActorStatistics[] list) {
            if(list.length == 0) {
                errors.add(premise + getString(R.string.persistentStorage_actorStatsEmpty));
            }
            // For the time being, statistics are always more or less valid.
        }
    }

    public void upgrade(StartData.PartyOwnerData result) {
        if(result.version == 0) result.version = 1; // free upgrade :P
    }

    public void upgrade(StartData.PartyClientData result) {
        if(result.version == 0) result.version = 1; // free upgrade :P
    }

    private static File createSessionFile(File filesDir, String name) {
        File session = new File(new File(filesDir, SESSION_DATA_SUBDIR), name);
        try {
            if(!session.createNewFile()) session = null;
            else if(!session.canWrite()) {
                session.deleteOnExit();
                session = null;
            }
        } catch (IOException e) {
            session = null; // most likely malformed file
        }
        return session;
    }

    public String load(int byDef, Session.Suspended fetch, FileInputStream source, int size) {
        byte[] everything = new byte[size];
        try {
            final int count = source.read(everything);
            if(count != everything.length) return getString(R.string.persistentStorage_readSizeMismatch);
        } catch (IOException e) {
            return getString(R.string.persistentStorage_failedRead);
        }
        CodedInputByteBufferNano input = CodedInputByteBufferNano.newInstance(everything);
        try {
            fetch.mergeFrom(input);
        } catch (IOException e) {
            return getString(R.string.persistentStorage_failedRead);
        }
        // Coherence check: actor ids must be unique.
        for(int loop = 0; loop < fetch.live.length; loop++) {
            int use = fetch.live[loop].peerKey;
            for(int search = loop + 1; search < fetch.live.length; search++) {
                if(fetch.live[search].peerKey == use) {
                    return getString(R.string.persistentStorage_duplicatedActorKey);
                }
            }
        }
        // the .notFighting flag is not a problem, unknowns are just ignored.
        if(fetch.fighting == null) return null; // we're in free roaming mode and we're fine with it.
        // Battle order validation requires some more care, especially now as known byDef actors *can* be omitted.
        HashMap<Integer, Boolean> actors = new HashMap<>();
        for(int loop = 0; loop < byDef; loop++) actors.put(loop, true);
        for (Network.ActorState actor : fetch.live) actors.put(actor.peerKey, true);
        for (int idle : fetch.notFighting) actors.put(idle, false);
        int numFighters = 0;
        for (Map.Entry<Integer, Boolean> el : actors.entrySet()) {
            if(el.getValue()) numFighters++;
        }
        if(fetch.fighting.enabled.length != numFighters) {
            String bad = getString(R.string.persistentStorage_initiativeMismatch);
            return String.format(Locale.getDefault(), bad, numFighters, fetch.fighting.enabled.length);
        }
        if(fetch.fighting.id.length != numFighters || fetch.fighting.initiative.length != numFighters * 3) {
            return getString(R.string.persistentStorage_incoherentInitiativeLists);
        }
        // And now, since initiatives have their own id we get to check'em again bruh
        for(int loop = 0; loop < fetch.fighting.id.length; loop++) {
            int good = fetch.fighting.id[loop];
            for(int search = loop + 1; search < fetch.fighting.id.length; search++) {
                if(good == fetch.fighting.id[search]) {
                    return getString(R.string.persistentStorage_duplicatedActorInitiative);
                }
            }
            // And while we're at it, each entry here must be known and 'selected to fight' !
            final Boolean willFight = actors.get(good);
            if(willFight == null) return getString(R.string.persistentStorage_unknownFighter);
            if(!willFight) return getString(R.string.persistentStorage_actorShouldNotBeFighting);
        }
        if(fetch.fighting.round != 0) { // then actorID must be one in list
            boolean found = false;
            for (int id : fetch.fighting.id) {
                if(id == fetch.fighting.currentActor) {
                    found = true;
                    break;

                }
            }
            if(!found) return getString(R.string.persistentStorage_invalidCurrentActor);
        }
        return null;
    }
}
