package com.massimodz8.collaborativegrouporder;

import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;

import com.google.protobuf.nano.MessageNano;
import com.massimodz8.collaborativegrouporder.master.PcAssignmentHelper;

import java.io.File;
import java.io.IOException;

/**
 * Created by Massimo on 10/05/2016.
 * Write a file possibly overwriting existing data but use renaming so we can be mildly safer we
 * won't destroy existing data.
 */
public abstract class AsyncRenamingStore<Container extends MessageNano> extends AsyncTask<Void, Void, Exception> {
    final String target;
    final File dir;
    final Container container;

    public AsyncRenamingStore(@NonNull File filesDir, @NonNull String fileName, @NonNull Container container) {
        target = fileName;
        dir = filesDir;
        this.container = container;
        super.execute();
    }

    @Override
    protected Exception doInBackground(Void... params) {
        File previously = new File(dir, target);
        File store;
        try {
            store = File.createTempFile(target, ".new", dir);
        } catch (IOException e) {
            return e;
        }
        new PersistentDataUtils(PcAssignmentHelper.DOORMAT_BYTES) {
            @Override
            protected String getString(int resource) {
                return AsyncRenamingStore.this.getString(resource);
            }
        }.storeValidGroupData(store, container);
        if(previously.exists() && !previously.delete()) {
            if(!store.delete()) store.deleteOnExit();
            return new Exception(getString(R.string.persistentStorage_failedOldDelete));
        }
        if(!store.renameTo(previously)) return new Exception(String.format(getString(R.string.persistentStorage_failedNewRenameOldGone), store.getName()));
        return null;
    }

    protected abstract String getString(@StringRes int res);
}