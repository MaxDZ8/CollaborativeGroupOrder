package com.massimodz8.collaborativegrouporder.master;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.ViewGroup;

import com.massimodz8.collaborativegrouporder.BuildingPlayingCharacter;
import com.massimodz8.collaborativegrouporder.MainMenuActivity;
import com.massimodz8.collaborativegrouporder.networkio.Events;
import com.massimodz8.collaborativegrouporder.networkio.MessageChannel;
import com.massimodz8.collaborativegrouporder.networkio.ProtoBufferEnum;
import com.massimodz8.collaborativegrouporder.networkio.PumpTarget;
import com.massimodz8.collaborativegrouporder.networkio.Pumper;
import com.massimodz8.collaborativegrouporder.protocol.nano.Network;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;

/**
 * Created by Massimo on 08/02/2016.
 * A group being formed which will later become a PersistentStorage structure.
 * This requires some more care as it also tracks ownership from devices.
 */
public abstract class PartyDefinitionHelper {
    public static final int PEER_MESSAGE_INTERVAL_MS = 2000;
    public static final int INITIAL_MESSAGE_CHAR_BUDGET = 50;
    final public String name;
    public ArrayList<DeviceStatus> clients = new ArrayList<>();
    public Pumper netPump;

    public PartyDefinitionHelper(String name) {
        this.name = name;
        final Handler handler = new MyHandler(this);
        netPump = new Pumper(handler, MSG_SOCKET_LOST, MSG_PUMPER_DETACHED)
                .add(ProtoBufferEnum.HELLO, new PumpTarget.Callbacks<Network.Hello>() {
                @Override
                public Network.Hello make() { return new Network.Hello(); }

                @Override
                public boolean mangle(MessageChannel from, Network.Hello msg) throws IOException {
                    handler.sendMessage(handler.obtainMessage(MSG_HELLO, new Events.Hello(from, msg)));
                    return false;
                }
            }).add(ProtoBufferEnum.PEER_MESSAGE, new PumpTarget.Callbacks<Network.PeerMessage>() {
                @Override
                public Network.PeerMessage make() {
                    return new Network.PeerMessage();
                }

                @Override
                public boolean mangle(MessageChannel from, Network.PeerMessage msg) throws IOException {
                    handler.sendMessage(handler.obtainMessage(MSG_PEER_MESSAGE, new Events.PeerMessage(from, msg)));
                    return false;
                }
            }).add(ProtoBufferEnum.PLAYING_CHARACTER_DEFINITION, new PumpTarget.Callbacks<Network.PlayingCharacterDefinition>() {
                @Override
                public Network.PlayingCharacterDefinition make() { return new Network.PlayingCharacterDefinition(); }

                @Override
                public boolean mangle(MessageChannel from, Network.PlayingCharacterDefinition msg) throws IOException {
                    handler.sendMessage(handler.obtainMessage(MSG_CHARACTER_DEFINITION, new Events.CharacterDefinition(from, msg)));
                    return false;
                }
            });
    }

    protected abstract void onMessageChanged(DeviceStatus owner);
    protected abstract void onGone(MessageChannel which, Exception reason);
    protected abstract void onDetached(MessageChannel which);


    DeviceStatus get(MessageChannel c) {
        for (DeviceStatus d : clients) {
            if (d.source == c) return d;
        }
        return null; // impossible most of the time
    }

    void definePlayingCharacter(final Events.CharacterDefinition ev) {
        DeviceStatus owner = get(ev.origin);
        if(null == owner) return; // impossible
        if(owner.kicked) return; // more likely...
        if(!owner.groupMember) {
            new AsyncTask<Void, Void, Exception>() {
                @Override
                protected Exception doInBackground(Void... params) {
                    Network.GroupFormed negative = new Network.GroupFormed();
                    negative.accepted = false;
                    negative.peerKey = ev.character.peerKey;
                    try {
                        ev.origin.writeSync(ProtoBufferEnum.GROUP_FORMED, negative);
                    } catch (IOException e) {
                        return e;
                    }
                    return null;
                }

                //@Override
                //protected void onPostExecute(Exception e) {
                //    if(null != e) {
                //        // This is usually because the connection goes down so do nothing and wait till disconnect is signaled.
                //    }
                //}
            }.execute();
            return;
        }
        for(int rebind = 0; rebind < owner.chars.size(); rebind++) {
            BuildingPlayingCharacter pc = owner.chars.get(rebind);
            if(pc.peerKey == ev.character.peerKey) {
                if(pc.status == BuildingPlayingCharacter.STATUS_ACCEPTED) return; // ignore the thing, it's client's problem, not ours.
                owner.chars.remove(rebind);
            }
        }
        final BuildingPlayingCharacter pc = new BuildingPlayingCharacter(ev.character);
        owner.chars.add(pc);
        if(charsApprovalAdapter != null) charsApprovalAdapter.notifyDataSetChanged();
    }

    void setMessage(Events.PeerMessage ev) {
        DeviceStatus owner = get(ev.which);
        if(null == owner) return;
        if(owner.kicked) return;
        if(ev.msg.charSpecific != 0) return; // ignore, this is not supported at this stage you mofo.
        if(ev.msg.text.length() >= owner.charBudget) return; // messages exceeding can be dropped
        if(null != owner.nextMessage && owner.nextMessage.after(new Date())) return; // also ignore messaging too fast
        owner.charBudget -= ev.msg.text.length();
        owner.nextMessage = new Date(new Date().getTime() + PEER_MESSAGE_INTERVAL_MS);
        owner.lastMessage = ev.msg.text;
        onMessageChanged(owner);
    }

    void kick(MessageChannel pipe, boolean soft) {
        for (PartyDefinitionHelper.DeviceStatus match : clients) {
            if(match.source == pipe) {
                match.kicked = true;
                if(!soft) clients.remove(match);
                break;
            }
        }
        if(soft) return;
        final Pumper.MessagePumpingThread goner = netPump.move(pipe);
        if(null == goner) return; // unlikely by construction
        goner.interrupt();
        new Thread() {
            @Override
            public void run() {
                try {
                    goner.getSource().socket.close();
                } catch (IOException e) {
                    // suppress
                }
            }
        }.start();
    }

    public void kickNonMembers() {
        final ArrayList<MessageChannel> kick = new ArrayList<>();
        for(int loop = 0; loop < clients.size(); loop++) {
            PartyDefinitionHelper.DeviceStatus dev = clients.get(loop);
            if(dev.kicked || !dev.groupMember) {
                kick.add(dev.source);
                clients.remove(loop--);
            }
        }
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                for (MessageChannel pipe : kick) {
                    Pumper.MessagePumpingThread worker = netPump.move(pipe);
                    if(worker == null) continue; // very unlikely
                    worker.interrupt();
                    try {
                        worker.getSource().socket.close();
                    } catch (IOException e) {
                        // silence
                    }
                }

                return null;
            }
        }.execute();
    }

    public interface CharsApprovalHolderFactoryBinder<VH extends RecyclerView.ViewHolder> {
        VH createUnbound(ViewGroup parent, int viewType);
        void bind(@NonNull VH target, @NonNull BuildingPlayingCharacter proposal);
    }

    public <VH extends RecyclerView.ViewHolder> CharsApprovalAdapter<VH> setNewCharsApprovalAdapter(CharsApprovalHolderFactoryBinder<VH> factory) {
        CharsApprovalAdapter<VH> gen = null;
        if(factory != null) gen = new CharsApprovalAdapter<>(factory);
        charsApprovalAdapter = gen;
        return gen;
    }

    public static class DeviceStatus {
        public final MessageChannel source;
        public String lastMessage; // if null still not talking
        public int charBudget;
        public boolean groupMember;
        public boolean kicked;
        public ArrayList<BuildingPlayingCharacter> chars = new ArrayList<>(); // if contains something we have been promoted
        public Date nextMessage;
        public byte[] salt;

        public DeviceStatus(MessageChannel source) {
            this.source = source;
        }
    }

    static private class MyHandler extends Handler {
        final WeakReference<PartyDefinitionHelper> self;

        public MyHandler(PartyDefinitionHelper self) {
            this.self = new WeakReference<>(self);
        }

        @Override
        public void handleMessage(Message msg) {
            final PartyDefinitionHelper self = this.self.get();
            switch (msg.what) {
                case MSG_CHARACTER_DEFINITION: self.definePlayingCharacter((Events.CharacterDefinition) msg.obj); break;
                case MSG_PEER_MESSAGE: self.setMessage((Events.PeerMessage) msg.obj); break;
                case MSG_SOCKET_LOST: {
                    final Events.SocketDisconnected real = (Events.SocketDisconnected) msg.obj;
                    self.gone(real.which, real.reason);
                } break;
                case MSG_PUMPER_DETACHED: self.onDetached((MessageChannel)msg.obj); break;
                case MSG_HELLO: {
                    final Events.Hello real = (Events.Hello)msg.obj;
                    self.hello(real.origin, real.payload);
                    break;
            }
        }
    }
    }

    private void hello(final MessageChannel from, Network.Hello payload) {
        final Network.GroupInfo info = new Network.GroupInfo();
        info.forming = true;
        info.name = PartyDefinitionHelper.this.name;
        info.version = MainMenuActivity.NETWORK_VERSION;
        final Network.CharBudget bud = helloBudget(from);
        new Thread(new Runnable() {
            @Override
            public void run() {
                // What if it fails? Perhaps I should be careful with this as well.
                // For the time being, I've already spent too much time on this.
                try {
                    from.writeSync(ProtoBufferEnum.GROUP_INFO, info);
                    if (bud != null) from.writeSync(ProtoBufferEnum.CHAR_BUDGET, bud);
                } catch (IOException e) {
                    // TODO signal those... perhaps transition to fully sequential sending.
                }

            }
        }).start();
    }

    private Network.CharBudget helloBudget(MessageChannel from) {
        final DeviceStatus dev = get(from);
        if(null == dev) return null; // impossible
        if(dev.lastMessage != null) return null;
        dev.charBudget = INITIAL_MESSAGE_CHAR_BUDGET;
        Network.CharBudget bud = new Network.CharBudget();
        bud.total = INITIAL_MESSAGE_CHAR_BUDGET;
        bud.period = PEER_MESSAGE_INTERVAL_MS;
        return bud;
    }

    private void gone(MessageChannel which, Exception reason) {
        final Pumper.MessagePumpingThread away = netPump.move(which);
        away.interrupt();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    away.getSource().socket.close();
                } catch (IOException e) {
                    // silence
                }
            }
        }).start();
        onGone(which, reason);
    }

    private static final int MSG_CHARACTER_DEFINITION = 1;
    private static final int MSG_PEER_MESSAGE = 2;
    private static final int MSG_SOCKET_LOST = 3;
    private static final int MSG_PUMPER_DETACHED = 4;
    private static final int MSG_HELLO = 5;

    public class CharsApprovalAdapter<VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {
        private final CharsApprovalHolderFactoryBinder<VH> factory;

        public CharsApprovalAdapter(@NonNull CharsApprovalHolderFactoryBinder<VH> factory) {
            this.factory = factory;
            setHasStableIds(true);
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            return factory.createUnbound(parent, viewType);
        }

        @Override
        public long getItemId(int position) {
            BuildingPlayingCharacter pc = get(position);
            return pc != null? pc.unique : RecyclerView.NO_ID;
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            BuildingPlayingCharacter pc = get(position);
            if(pc != null) factory.bind(holder, pc);
        }

        private BuildingPlayingCharacter get(int position) {
            for (DeviceStatus dev : clients) {
                if(dev.kicked || !dev.groupMember) continue;
                for (BuildingPlayingCharacter pc : dev.chars) {
                    if(pc.status == BuildingPlayingCharacter.STATUS_REJECTED) continue;
                    if(position == 0) return pc;
                    position--;
                }
            }
            return null;
        }

        @Override
        public int getItemCount() {
            int count = 0;
            for (DeviceStatus dev : clients) {
                if(dev.kicked || !dev.groupMember) continue;
                for (BuildingPlayingCharacter pc : dev.chars) {
                    if(pc.status != BuildingPlayingCharacter.STATUS_REJECTED) count++;
                }
            }
            return count;
        }
    }
    private CharsApprovalAdapter charsApprovalAdapter;

    public boolean approve(int unique) {
        for (DeviceStatus dev : clients) {
            if(dev.kicked || !dev.groupMember) continue;
            for (BuildingPlayingCharacter pc : dev.chars) {
                if(pc.unique != unique) continue;
                boolean signal = pc.status != BuildingPlayingCharacter.STATUS_ACCEPTED;
                pc.status = BuildingPlayingCharacter.STATUS_ACCEPTED;
                if(signal && charsApprovalAdapter != null) charsApprovalAdapter.notifyDataSetChanged();
            }
        }
        return false;
    }

    public boolean reject(int unique) {
        for (DeviceStatus dev : clients) {
            if(dev.kicked || !dev.groupMember) continue;
            for (BuildingPlayingCharacter pc : dev.chars) {
                if(pc.unique != unique) continue;
                boolean signal = pc.status != BuildingPlayingCharacter.STATUS_REJECTED;
                pc.status = BuildingPlayingCharacter.STATUS_REJECTED;
                if(signal && charsApprovalAdapter != null) charsApprovalAdapter.notifyDataSetChanged();
            }
        }
        return false;
    }

    public boolean isApproved(int unique) {
        for (DeviceStatus dev : clients) {
            if(dev.kicked || !dev.groupMember) continue;
            for (BuildingPlayingCharacter pc : dev.chars) {
                if(pc.unique != unique) continue;
                return pc.status == BuildingPlayingCharacter.STATUS_ACCEPTED;
            }
        }
        return false;
    }
}
