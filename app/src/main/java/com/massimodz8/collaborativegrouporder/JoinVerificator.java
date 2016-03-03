package com.massimodz8.collaborativegrouporder;

import android.support.annotation.NonNull;

import com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Created by Massimo on 23/02/2016.
 * Party keys and the doormat. They are used to validate join requests. Devices must produce
 * magic blobs and send them to master. Master must be able to produce the same bytes.
 * So, it makes sense to have this code around. It's very simple.
 */
public class JoinVerificator {
    private final MessageDigest hasher;
    private final byte[][] keys;

    public JoinVerificator(@NonNull PersistentStorage.PartyOwnerData.DeviceInfo[] known, @NonNull MessageDigest hasher) {
        this.hasher = hasher;
        keys = new byte[known.length][];
        for(int loop = 0; loop < known.length; loop++) keys[loop] = known[loop].salt;
    }

    public JoinVerificator(@NonNull PersistentStorage.PartyClientData.Group me, @NonNull MessageDigest hasher) {
        this.hasher = hasher;
        keys = new byte[1][];
        keys[0] = me.key;
    }
    public Integer match(@NonNull byte[] sent, @NonNull byte[] received) {
        for(int loop = 0; loop < keys.length; loop++) {
            byte[] salt = keys[loop];
            byte[] input = new byte[sent.length + salt.length];
            System.arraycopy(sent, 0, input, 0, sent.length);
            System.arraycopy(salt, 0, input, sent.length, salt.length);
            hasher.reset();
            final byte[] blob = hasher.digest(input);
            if(Arrays.equals(blob, received)) return loop;
        }
        return null;
    }
    public byte[] mangle(@NonNull byte[] received) {
        byte[] salt = keys[0];
        byte[] input = new byte[received.length + salt.length];
        System.arraycopy(received, 0, input, 0, received.length);
        System.arraycopy(salt, 0, input, received.length, salt.length);
        hasher.reset();
        return hasher.digest(input);
    }
}
