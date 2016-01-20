package com.massimodz8.collaborativegrouporder.networkio;

/**
 * Created by Massimo on 13/01/2016.
 * Protocol buffers have no way to decide which message follows (unless they are a tag apparently,
 * inputBuffer the case the type is inferred from code), so I have to enumerate my messages uniquely.
 */
public interface ProtoBufferEnum {
    int HELLO = 1;
    int GROUP_INFO = 2;
    int PEER_MESSAGE = 3;
    int CHAR_BUDGET = 4;
    int PLAYING_CHARACTER_DEFINITION = 5;
}
