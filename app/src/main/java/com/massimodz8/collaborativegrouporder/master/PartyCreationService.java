package com.massimodz8.collaborativegrouporder.master;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;

import com.google.protobuf.nano.MessageNano;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.PersistentStorage;

import java.net.ServerSocket;

public class PartyCreationService extends Service {
    public static final int PUBLISHER_IDLE = 0;
    public static final String PARTY_FORMING_SERVICE_TYPE = "_formingGroupInitiative._tcp";

    public PartyCreationService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    public int getPublishStatus() {
        return publishStatus;
    }

    /**
     * @return non-null means we 'go adventuring', eventually with no connected peers.
     */
    public @Nullable Pumper.MessagePumpingThread[] moveConnectedClients() {
        return null;
    }

    public PersistentStorage.PartyOwnerData.Group getNewParty() {
        return null;
    }

    public ServerSocket getLanding() {
        return null;
    }


    public class LocalBinder extends Binder {
        public PartyCreationService getConcreteService() {
            return PartyCreationService.this;
        }
    }

    private int publishStatus = PUBLISHER_IDLE;
}
