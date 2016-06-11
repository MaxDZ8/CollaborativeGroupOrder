package com.massimodz8.collaborativegrouporder;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.NotificationCompat;

import com.google.protobuf.nano.CodedInputByteBufferNano;
import com.massimodz8.collaborativegrouporder.master.PcAssignmentHelper;
import com.massimodz8.collaborativegrouporder.protocol.nano.MonsterData;
import com.massimodz8.collaborativegrouporder.protocol.nano.PreparedEncounters;
import com.massimodz8.collaborativegrouporder.protocol.nano.StartData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Attempt to unificate the various services, instead of running many we run only one containing
 * everything I need. This is born because the MainMenuActivity also needs persistent state!
 */
public class InternalStateService extends Service {

    public InternalStateService() {
    }

    @Override
    public IBinder onBind(Intent intent) { return new LocalBinder(); }

    public class LocalBinder extends Binder {
        InternalStateService getConcreteService() { return InternalStateService.this; }
    }

    /** What is this? o_o
     * One would like to tie service lifetime to Activity lifetimes somehow but due to the way
     * activities get destroyed when 'destroy activity' option exist we have to have a way to let
     * the main activity know if a service has to be stopped or not. Blah! So, I enforce a lifetime
     * by tracking the foreground notification.
     */
    public Notification notification;
    public static final int INTERNAL_STATE_NOTIFICATION_ID = 123;

    public static final int DATA_EMPTY = 0;
    public static final int DATA_LOADING = 1;
    public static final int DATA_DONE = 2; // might be OK or fail, check error list length.

    /** Not a very expressive name. Loading data is the first thing we care about. Load everything
     * I need from previous app runs. */
    public class Data {
        public int status = DATA_EMPTY;
        public ArrayList<StartData.PartyOwnerData.Group> groupDefs;
        public ArrayList<StartData.PartyClientData.Group> groupKeys;

        public MonsterData.MonsterBook monsters, customMonsters;
        public PreparedEncounters.Collection customBattles;

        public ArrayList<String> error; // if null or empty, DATA_DONE loaded successful otherwise error.

        public final PseudoStack<Runnable> onStatusChanged = new PseudoStack<>();
        void loadAll() {
            status = DATA_LOADING;

            new AsyncTask<Void, Void, Boolean>() {
                ArrayList<String> pending = new ArrayList<>();

                @Override
                protected Boolean doInBackground(Void... params) {
                    String[] need = {
                            PersistentDataUtils.MAIN_DATA_SUBDIR,
                            PersistentDataUtils.SESSION_DATA_SUBDIR,
                            PersistentDataUtils.USER_CUSTOM_DATA_SUBDIR
                    };
                    for (String sub : need) {
                        File dir = new File(getFilesDir(), sub);
                        if(dir.exists()) {
                            if(dir.isDirectory()) continue; // :-)
                            pending.add(String.format(getString(R.string.iss_existsButNoDir), sub));
                            continue;
                        }
                        if(!dir.mkdir()) {
                            pending.add(String.format(getString(R.string.iss_cannotCreateDir), sub));
                        }
                    }
                    return pending.isEmpty();
                }

                @Override
                protected void onPostExecute(Boolean success) {
                    if(!success) {
                        error = pending;
                        Runnable runnable = onStatusChanged.get();
                        if(runnable != null) runnable.run();
                        return;
                    }
                    new AsyncLoadAll().execute();
                }
            }.execute();
        }
    }

    public Notification buildNotification(@NonNull String title, @Nullable String message) {
        final android.support.v4.app.NotificationCompat.Builder help = new NotificationCompat.Builder(this)
                .setOngoing(true)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setContentTitle(title)
                .setSmallIcon(R.drawable.ic_notify_icon)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher));
        if(message != null) help.setContentText(message);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            help.setCategory(Notification.CATEGORY_SERVICE);
        }
        return help.build();
    }

    public void baseNotification() {
        InternalStateService state = RunningServiceHandles.getInstance().state;
        Notification build = state.buildNotification(getString(R.string.app_name), null);
        NotificationManager serv = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if(serv != null) serv.notify(InternalStateService.INTERNAL_STATE_NOTIFICATION_ID, build);
        state.notification = build;
    }

    //----------------------------------------------------------------------------------------------
    //
    // First part: data loading
    //
    //----------------------------------------------------------------------------------------------
    public final Data data = new Data();

    private class AsyncLoadAll extends AsyncTask<Void, Void, Exception> {
        StartData.PartyOwnerData owned;
        StartData.PartyClientData joined;
        MonsterData.MonsterBook monsterBook;
        MonsterData.MonsterBook customMobs;
        PreparedEncounters.Collection custBattles;

        final PersistentDataUtils loader = new PersistentDataUtils(PcAssignmentHelper.DOORMAT_BYTES) {
            @Override
            protected String getString(int resource) {
                return InternalStateService.this.getString(resource);
            }
        };

        @Override
        protected Exception doInBackground(Void... params) {
            StartData.PartyOwnerData pullo = new StartData.PartyOwnerData();
            final File mainDataDir = new File(getFilesDir(), PersistentDataUtils.MAIN_DATA_SUBDIR);
            File srco = new File(mainDataDir, PersistentDataUtils.DEFAULT_GROUP_DATA_FILE_NAME);
            if(srco.exists()) loader.mergeExistingGroupData(pullo, srco);
            else pullo.version = PersistentDataUtils.OWNER_DATA_VERSION;

            StartData.PartyClientData pullk = new StartData.PartyClientData();
            File srck = new File(mainDataDir, PersistentDataUtils.DEFAULT_KEY_FILE_NAME);
            if(srck.exists()) loader.mergeExistingGroupData(pullk, srck);
            else pullk.version = PersistentDataUtils.CLIENT_DATA_WRITE_VERSION;

            MonsterData.MonsterBook pullMon = new MonsterData.MonsterBook();
            final byte[] loadBuff = new byte[128 * 1024];
            try {
                final InputStream srcMon = getAssets().open("monsterData.bin");
                final MaxUtils.TotalLoader loaded = new MaxUtils.TotalLoader(srcMon, loadBuff);
                srcMon.close();
                pullMon.mergeFrom(CodedInputByteBufferNano.newInstance(loaded.fullData, 0, loaded.validBytes));
            } catch (IOException e) {
                // Nightly impossible!
                return e;
            }

            final File customDataDir = new File(getFilesDir(), PersistentDataUtils.USER_CUSTOM_DATA_SUBDIR);
            File cmobs = new File(customDataDir, PersistentDataUtils.CUSTOM_MOBS_FILE_NAME);
            MonsterData.MonsterBook custBook = new MonsterData.MonsterBook();
            try {
                final FileInputStream fis = new FileInputStream(cmobs);
                final MaxUtils.TotalLoader loaded = new MaxUtils.TotalLoader(fis, loadBuff);
                fis.close();
                custBook.mergeFrom(CodedInputByteBufferNano.newInstance(loaded.fullData, 0, loaded.validBytes));
            } catch (FileNotFoundException e) {
                // No problem really. Go ahead.
            } catch (IOException e) {
                return e;
            }

            File cbattles = new File(customDataDir, PersistentDataUtils.CUSTOM_ENCOUNTERS_FILE_NAME);
            PreparedEncounters.Collection allBattles = new PreparedEncounters.Collection();
            try {
                final FileInputStream fis = new FileInputStream(cbattles);
                final MaxUtils.TotalLoader loaded = new MaxUtils.TotalLoader(fis, loadBuff);
                fis.close();
                allBattles.mergeFrom(CodedInputByteBufferNano.newInstance(loaded.fullData, 0, loaded.validBytes));
            } catch (FileNotFoundException e) {
                // No problem really. Go ahead.
            } catch (IOException e) {
                return e;
            }

            owned = pullo;
            joined = pullk;
            monsterBook = pullMon;
            customMobs = custBook;
            custBattles = allBattles;

            return null;
        }

        @Override
        protected void onPostExecute(Exception e) {
            data.status = DATA_DONE;
            if(null != e) {
                data.error = new ArrayList<>();
                data.error.add(e.getLocalizedMessage());
                Runnable runnable = data.onStatusChanged.get();
                if(runnable != null) runnable.run();
                return;
            }
            if(PersistentDataUtils.OWNER_DATA_VERSION != owned.version) upgrade(owned);
            if(PersistentDataUtils.CLIENT_DATA_WRITE_VERSION != joined.version) upgrade(joined);
            final ArrayList<String> errors = loader.validateLoadedDefinitions(owned);
            if(null != errors) {
                if(data.error == null) data.error = new ArrayList<>();
                data.error.addAll(errors);
                return;
            }
            data.monsters = monsterBook;
            data.customMonsters = customMobs;
            data.customBattles = custBattles;
            data.groupDefs = new ArrayList<>();
            data.groupKeys = new ArrayList<>();
            Collections.addAll(data.groupDefs, owned.everything);
            Collections.addAll(data.groupKeys, joined.everything);
            Runnable runnable = data.onStatusChanged.get();
            if(runnable != null) runnable.run();
        }
    }

    /// Called when party owner data loaded version != from current.
    private void upgrade(StartData.PartyOwnerData loaded) {
        if(data.error == null) data.error = new ArrayList<>();
        data.error.add(String.format(getString(R.string.mma_noOwnerDataUpgradeAvailable), loaded.version, PersistentDataUtils.OWNER_DATA_VERSION));
    }

    private void upgrade(StartData.PartyClientData loaded) {
        if(data.error == null) data.error = new ArrayList<>();
        data.error.add(String.format(getString(R.string.mma_noClientDataUpgradeAvailable), loaded.version, PersistentDataUtils.OWNER_DATA_VERSION));
    }
}
