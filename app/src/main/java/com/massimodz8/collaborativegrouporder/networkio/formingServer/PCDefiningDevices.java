package com.massimodz8.collaborativegrouporder.networkio.formingServer;

import android.os.Handler;

import com.massimodz8.collaborativegrouporder.PlayingCharacter;
import com.massimodz8.collaborativegrouporder.networkio.Client;
import com.massimodz8.collaborativegrouporder.networkio.Events;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

import java.io.IOException;

/**
 * Created by Massimo on 14/01/2016.
 * So, the group has been formed and some message channels have been pushed to us.
 * Super cool! Now let's silent up everybody and just have playing characters being defined.
 * Remember: this just collects the character definition messages and pushes them to the GUI
 * thread for sequential processing.
 */
public class PCDefiningDevices extends Pumper<Client> {
    final int definedCharacter;

    public PCDefiningDevices(Handler handler, int disconnectMessageCode, int definedCharacter_) {
        super(handler, disconnectMessageCode);
        definedCharacter = definedCharacter_;
        add(ProtoBufferEnum.PLAYING_CHARACTER_DEFINITION, new Callbacks<Network.PlayingCharacterDefinition>() {
            @Override
            public Network.PlayingCharacterDefinition make() {
                return new Network.PlayingCharacterDefinition();
            }

            @Override
            public void mangle(MessageChannel from, Network.PlayingCharacterDefinition msg) throws IOException {
                message(definedCharacter, new Events.CharacterDefinition(from, msg));
            }
        });
    }

    @Override
    protected Client allocate(MessageChannel c) {
        return new Client(c);
    }
}
