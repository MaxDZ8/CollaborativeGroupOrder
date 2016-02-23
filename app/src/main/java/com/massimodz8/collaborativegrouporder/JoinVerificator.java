package com.massimodz8.collaborativegrouporder;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by Massimo on 23/02/2016.
 * Party keys and the doormat. They are used to validate join requests. Devices must produce
 * magic blobs and send them to master. Master must be able to produce the same bytes.
 * So, it makes sense to have this code around. It's very simple.
 */
public class JoinVerificator {
    private final MessageDigest hasher;
    private byte[] blob;

    public JoinVerificator() throws NoSuchAlgorithmException {
        hasher = MessageDigest.getInstance("SHA-256");
    }
    public byte[] mangle(byte[] doormat, byte[] partyKey) {
        if(null == blob) {
            byte[] input = new byte[doormat.length + partyKey.length];
            System.arraycopy(doormat, 0, input, 0, doormat.length);
            System.arraycopy(partyKey, 0, input, doormat.length, partyKey.length);

            blob = hasher.digest(input);
        }
        return blob;
    }
}
