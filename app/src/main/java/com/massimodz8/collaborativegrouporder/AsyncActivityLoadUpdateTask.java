package com.massimodz8.collaborativegrouporder;

import android.app.Activity;
import android.support.v7.app.AlertDialog;

import com.google.protobuf.nano.MessageNano;
import com.google.protobuf.nano.MessageNanoPrinter;

import java.io.File;

/**
 * Created by Massimo on 31/01/2016.
 * Cut it short on the common points with ids. Meh.
 */
public abstract class AsyncActivityLoadUpdateTask<Container extends MessageNano> extends AsyncLoadUpdateTask<Container> {
    public AsyncActivityLoadUpdateTask(String fileName, String targetFilePrefix, final Activity source) {
        super(source.getFilesDir(), fileName, new PersistentDataUtils() {
            @Override
            protected String getString(int resource) {
                return source.getString(resource);
            }
        }, targetFilePrefix);
        this.source = source;
    }


    @Override
    protected String getError(int code) {
        switch(code) {
            case ERROR_BAD_INFO_FROM_STORAGE: return source.getString(R.string.dataLoadUpdate_badGroupInfoFromStorage);
            case ERROR_COULD_NOT_STORE_NEW_DATA: return source.getString(R.string.dataLoadUpdate_couldNotStoreNewData);
            case ERROR_FAILED_PREVIOUS_DATA_DELETE: return source.getString(R.string.dataLoadUpdate_failedPreviousDataDelete);
            case ERROR_FAILED_NEW_RENAME_PREVIOUS_DESTROYED: return source.getString(R.string.dataLoadUpdate_failedNewDataRenamePreviousDestroyed);
        }
        return "<unknown>"; // perhaps I should throw and die here
    }



    @Override
    protected AlertDialog.Builder newAlertDialogBuilder() {
        return new AlertDialog.Builder(source);
    }


    final Activity source;

}
