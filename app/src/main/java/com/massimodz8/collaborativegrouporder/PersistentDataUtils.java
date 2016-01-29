package com.massimodz8.collaborativegrouporder;

import com.google.protobuf.nano.CodedInputByteBufferNano;
import com.google.protobuf.nano.CodedOutputByteBufferNano;
import com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;


/**
 * Created by Massimo on 29/01/2016.
 * Stuff dealing with important, persistent data. Might involve future utilities to upgrade data
 * structures.
 */
public abstract class PersistentDataUtils {
    protected abstract String getString(int resource);

    public static final int DEFAULT_WRITE_VERSION = 1;
    public static final int MAX_GROUP_DATA_BYTES = 1024 * 1024 * 4;

    public String mergeExistingGroupData(PersistentStorage.PartyOwnerData dst, File from) {
        FileInputStream source;
        try {
            source = new FileInputStream(from);
        } catch (FileNotFoundException e) {
            return getString(R.string.persistentStorage_cannotReadGroupList);
        }
        if(from.length() > MAX_GROUP_DATA_BYTES) return getString(R.string.persistentStorage_groupDataTooBig);
        byte[] everything = new byte[(int)from.length()];
        try {
            final int count = source.read(everything);
            if(count != everything.length) return getString(R.string.persistentStorage_readSizeMismatch);
        } catch (IOException e) {
            return getString(R.string.persistentStorage_failedRead);
        }
        CodedInputByteBufferNano input = CodedInputByteBufferNano.newInstance(everything);
        try {
            dst.mergeFrom(input);
        } catch (IOException e) {
            return getString(R.string.persistentStorage_failedReadGroupList);
        }
        return null;
    }

    public ArrayList<String> validateLoadedDefinitions(PersistentStorage.PartyOwnerData loaded) {
        ArrayList<String> arr = new ArrayList<>();
        if(loaded.version == 0) {
            arr.add(getString(R.string.persistentStorage_groupDataVersionZero));
            return arr;
        }
        if(loaded.version >= 1) {
            for(int check = 0; check < loaded.everything.length; check++) {
                if(invalid(arr, loaded.everything[check], check)) continue;
                for(int cmp = check; cmp < loaded.everything.length; cmp++) {
                    if(loaded.everything[check].name.equals(loaded.everything[cmp].name)) {
                        String err = getString(R.string.persistentStorage_groupNameClash);
                        arr.add(String.format(err, check, cmp, loaded.everything[check].name));
                    }
                }
            }
        }
        return arr;
    }

    private boolean invalid(ArrayList<String> errors, PersistentStorage.Group group, int index) {
        final int start = errors.size();
        final String premise = String.format(getString(R.string.persistentStorage_errorReport_premise), index, group.name.isEmpty() ? "" : String.format("(%1$s)", group.name));
        if(group.name.isEmpty()) errors.add(premise + getString(R.string.persistentStorage_missingName));
        if(group.salt.length < 1) errors.add(premise + getString(R.string.persistentStorage_missingKey));
        if(group.usually == null) errors.add(premise + getString(R.string.persistentStorage_missingPartyDefinition));
        else {
            ActorValidator usual = new ActorValidator(true, errors, String.format("%1$s->%2$s", premise, getString(R.string.persistentStorage_partyDefValidationPremise)));
            usual.check(getString(R.string.persistentStorage_playingCharacters), group.usually.party, true);
            usual.check(getString(R.string.persistentStorage_NPC), group.usually.npcs, false);

        }
        return start != errors.size();
    }

    public String storeValidGroupData(File store, PersistentStorage.PartyOwnerData valid) {
        byte[] buff = new byte[CodedOutputByteBufferNano.computeMessageSizeNoTag(valid)];
        CodedOutputByteBufferNano output = CodedOutputByteBufferNano.newInstance(buff);
        try {
            output.writeMessageNoTag(valid);
        } catch (IOException e) {
            return getString(R.string.persistentStorage_failedSerialization);
        }
        FileOutputStream out;
        try {
            out = new FileOutputStream(store);
            out.write(buff);
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

        void check(String mod, PersistentStorage.Actor[] list, boolean required) {
            final String premise = base + mod;
            if(list.length == 0) {
                if(required) errors.add(premise + getString(R.string.persistentStorage_groupWithNoActors));
                return;
            }
            for(int i = 0; i < list.length; i++) {
                final PersistentStorage.Actor actor = list[i];
                String head = String.format("%1$s[%2$d]", premise, i);
                if(actor.name.isEmpty()) errors.add(head + getString(R.string.persistentStorage_actorMissingName));
                head = String.format("%1$s(%2$s)", head, actor.name);
                if(actor.level == 0) errors.add(head + getString(R.string.persistentStorage_badLevel));
                if(!actor.preparedAction.isEmpty() && preparedActionForbidden) errors.add(head + getString(R.string.persistentStorage_preparedActionsForbidden));
                check(head + getString(R.string.persistentStorage_actorStatsPremise), actor.stats);
            }
        }

        void check(String premise, PersistentStorage.ActorStatistics[] list) {
            if(list.length == 0) {
                errors.add(premise + getString(R.string.persistentStorage_actorStatsEmpty));
            }
            // For the time being, statistics are always more or less valid.
        }
    }

    public void upgrade(PersistentStorage.PartyOwnerData result) {
        if(result.version == 0) result.version = 1; // free upgrade :P
    }

    public static final String DEFAULT_GROUP_DATA_FILE_NAME = "groups.bin";
}
