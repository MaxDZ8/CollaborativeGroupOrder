package com.massimodz8.collaborativegrouporder;

import android.os.AsyncTask;
import android.support.annotation.NonNull;

import com.google.protobuf.nano.MessageNano;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by Massimo on 30/01/2016.
 * Used by CreatePartyActivity and JoinGroupActivity to load and add the new data.
 */
public abstract class AsyncLoadUpdateTask<Container extends MessageNano> extends AsyncTask<Void, Void, ArrayList<String>> {
    public interface Callbacks {
        /**
         * Called if something goes wrong attempting to load the data already existing. A non-existing
         * file does not require to load so it does not generate errors. Will count at least 1.
         */
        void onFailedExistingLoad(@NonNull ArrayList<String> errors);
        void onFailedSave(@NonNull Exception wrong);
        void onCompletedSuccessfully();
    }
    protected AsyncLoadUpdateTask(File filesDir, String fileName, PersistentDataUtils helper, String targetFilePrefix, @NonNull Callbacks callbacks) {
        this.dir = filesDir;
        this.fileName = fileName;
        this.helper = helper;
        this.targetFilePrefix = targetFilePrefix;
        this.callbacks = callbacks;
    }

    final File dir;
    final String fileName;
    final PersistentDataUtils helper;
    final String targetFilePrefix;
    final Callbacks callbacks;

    public static final int ERROR_BAD_INFO_FROM_STORAGE = 1;
    public static final int ERROR_COULD_NOT_STORE_NEW_DATA = 2;
    public static final int ERROR_FAILED_PREVIOUS_DATA_DELETE = 3;
    public static final int ERROR_FAILED_NEW_RENAME_PREVIOUS_DESTROYED = 4;

    volatile Container loaded;
    volatile File previously;

    @Override
    protected ArrayList<String> doInBackground(Void... params) {
        Container result = allocate();
        previously = new File(dir, fileName);
        if(previously.exists()) {
            ArrayList<String> error = new ArrayList<>();
            if(!previously.canRead()) {
                error.add(helper.getString(R.string.persistentStorage_cannotReadGroupList));
                return error;
            }

            String loadError = helper.mergeExistingGroupData(result, previously);
            if(loadError != null) {
                error.add(loadError);
                return error;
            }
            error = validateLoadedDefinitions(helper, result);
            if(error != null) return error;
            upgrade(helper, result);
        }
        else setVersion(result);
        loaded = result;
        return null;
    }

    protected void onPostExecute(ArrayList<String> lotsa) {
        if (lotsa != null) {
            callbacks.onFailedExistingLoad(lotsa);
            return;
        }
        appendNewEntry(loaded);
        new AsyncStore().execute();
    }

    private class AsyncStore extends AsyncTask<Void, Void, Exception> {
        @Override
        protected Exception doInBackground(Void... params) {
            File store;
            try {
                store = File.createTempFile(targetFilePrefix, ".new", dir);
            } catch (IOException e) {
                return e;
            }
            helper.storeValidGroupData(store, loaded);
            if(previously.exists() && !previously.delete()) return new Exception(getError(ERROR_FAILED_PREVIOUS_DATA_DELETE));
            if(!store.renameTo(previously)) return new Exception(getError(ERROR_FAILED_NEW_RENAME_PREVIOUS_DESTROYED));
            return null;
        }

        @Override
        protected void onPostExecute(Exception e) {
            if(e != null) {
                callbacks.onFailedSave(e);
                return;
            }
            callbacks.onCompletedSuccessfully();
        }
    }

    protected abstract String getError(int code);
    protected abstract void appendNewEntry(Container loaded);
    protected abstract void setVersion(Container result);
    protected abstract void upgrade(PersistentDataUtils helper, Container result);
    protected abstract ArrayList<String> validateLoadedDefinitions(PersistentDataUtils helper, Container result);
    protected abstract Container allocate();
}
