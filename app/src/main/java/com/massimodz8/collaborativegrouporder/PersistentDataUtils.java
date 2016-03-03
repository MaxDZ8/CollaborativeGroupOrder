package com.massimodz8.collaborativegrouporder;

import com.google.protobuf.nano.CodedInputByteBufferNano;
import com.google.protobuf.nano.CodedOutputByteBufferNano;
import com.google.protobuf.nano.MessageNano;
import com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;


/**
 * Created by Massimo on 29/01/2016.
 * Stuff dealing with important, persistent data. Might involve future utilities to upgrade data
 * structures.
 */
public abstract class PersistentDataUtils {
    protected abstract String getString(int resource);

    public static final int OWNER_DATA_VERSION = 1;
    public static final int CLIENT_DATA_WRITE_VERSION = 1;
    public static final int MAX_GROUP_DATA_BYTES = 1024 * 1024 * 4;

    public <Container extends MessageNano> String mergeExistingGroupData(Container dst, File from) {
        FileInputStream source;
        try {
            source = new FileInputStream(from);
        } catch (FileNotFoundException e) {
            return null;
        }
        if(from.length() > MAX_GROUP_DATA_BYTES) return getString(R.string.persistentStorage_groupDataTooBig);
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
                final PersistentStorage.PartyOwnerData.Group ref = loaded.everything[check];
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

    public ArrayList<String> validateLoadedDefinitions(PersistentStorage.PartyClientData loaded) {
        ArrayList<String> arr = new ArrayList<>();
        if(loaded.version == 0) {
            arr.add(getString(R.string.persistentStorage_groupDataVersionZero));
            return arr;
        }
        if(loaded.version >= 1) {
            for(int check = 0; check < loaded.everything.length; check++) {
                final PersistentStorage.PartyClientData.Group ref = loaded.everything[check];
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

    private boolean invalid(ArrayList<String> errors, PersistentStorage.PartyOwnerData.Group group, int index) {
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

    private boolean invalid(ArrayList<String> errors, PersistentStorage.PartyClientData.Group group, int index) {
        final int start = errors.size();
        final String premise = String.format(getString(R.string.persistentStorage_errorReport_premise), index, group.name.isEmpty() ? "" : String.format("(%1$s)", group.name));
        if(group.name.isEmpty()) errors.add(premise + getString(R.string.persistentStorage_missingName));
        if(group.key.length < 1) errors.add(premise + getString(R.string.persistentStorage_missingKey));
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

        ActorValidator check(String mod, PersistentStorage.ActorDefinition[] list, boolean required) {
            final String premise = base + mod;
            if(list.length == 0) {
                if(required) errors.add(premise + getString(R.string.persistentStorage_groupWithNoActors));
                return this;
            }
            for(int i = 0; i < list.length; i++) {
                final PersistentStorage.ActorDefinition actor = list[i];
                String head = String.format("%1$s[%2$d]", premise, i);
                if(actor.name.isEmpty()) errors.add(head + getString(R.string.persistentStorage_actorMissingName));
                head = String.format("%1$s(%2$s)", head, actor.name);
                if(actor.level == 0) errors.add(head + getString(R.string.persistentStorage_badLevel));
                check(head + getString(R.string.persistentStorage_actorStatsPremise), actor.stats);
            }
            return this;
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

    public void upgrade(PersistentStorage.PartyClientData result) {
        if(result.version == 0) result.version = 1; // free upgrade :P
    }

    public static final String DEFAULT_GROUP_DATA_FILE_NAME = "groupDefs.bin";
    public static final String DEFAULT_KEY_FILE_NAME = "keys.bin";
}
