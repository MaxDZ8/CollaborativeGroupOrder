package com.massimodz8.collaborativegrouporder.networkMessage;

import java.io.Serializable;

/**
 * Created by Massimo on 07/01/2016.
 * A message somehow sent from a peer to another so the receiver can display it.
 * They are a bit special. For example, in the initial connection stage those messages are used
 * to display / identify active devices.
 */
public class PeerMessage implements Serializable {
    public String text;
    public PeerMessage(String txt) { text = txt; }
}
