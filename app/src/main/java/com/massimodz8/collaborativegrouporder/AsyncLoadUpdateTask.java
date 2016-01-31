package com.massimodz8.collaborativegrouporder;

import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;

import com.google.protobuf.nano.MessageNano;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by Massimo on 30/01/2016.
 * Used by CreatePartyActivity and JoinGroupActivity to load and add the new data.
 */
public abstract class AsyncLoadUpdateTask<Container extends MessageNano> extends AsyncTask<Void, Void, ArrayList<String>> {
    protected AsyncLoadUpdateTask(File filesDir, String fileName, PersistentDataUtils helper, String targetFilePrefix) {
        this.dir = filesDir;
        this.fileName = fileName;
        this.helper = helper;
        this.targetFilePrefix = targetFilePrefix;
    }

    final File dir;
    final String fileName;
    final PersistentDataUtils helper;
    final String targetFilePrefix;

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

            String loadError = helper.mergeExistingGroupData(result, dir);
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
            StringBuilder concat = new StringBuilder();
            for (String err : lotsa) concat.append(err).append('\n');
            newAlertDialogBuilder()
                    .setMessage(String.format(getError(ERROR_BAD_INFO_FROM_STORAGE), concat.toString()))
                    .show();
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
                newAlertDialogBuilder()
                        .setMessage(String.format(getError(ERROR_COULD_NOT_STORE_NEW_DATA), e.getLocalizedMessage()))
                        .show();
                return;
            }
            onCompletedSuccessfully();
        }
    }

    protected abstract void onCompletedSuccessfully();
    protected abstract String getError(int code);
    protected abstract void appendNewEntry(Container loaded);
    protected abstract AlertDialog.Builder newAlertDialogBuilder();
    protected abstract void setVersion(Container result);
    protected abstract void upgrade(PersistentDataUtils helper, Container result);
    protected abstract ArrayList<String> validateLoadedDefinitions(PersistentDataUtils helper, Container result);
    protected abstract Container allocate();
}
