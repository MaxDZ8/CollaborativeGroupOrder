package com.massimodz8.collaborativegrouporder;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;

import com.google.protobuf.nano.MessageNano;
import com.massimodz8.collaborativegrouporder.master.PcAssignmentHelper;

import java.util.ArrayList;

/**
 * Created by Massimo on 31/01/2016.
 * Cut it short on the common points with ids. Meh.
 */
public abstract class AsyncActivityLoadUpdateTask<Container extends MessageNano> extends AsyncLoadUpdateTask<Container> {
    public static abstract class ActivityCallbacks implements Callbacks {
        final Activity owner;

        public ActivityCallbacks(Activity owner) {
            this.owner = owner;
        }

        @Override
        public void onFailedExistingLoad(@NonNull ArrayList<String> errors) {
            StringBuilder concat = new StringBuilder();
            for (String err : errors) concat.append(err).append('\n');
            new AlertDialog.Builder(owner)
                    .setMessage(String.format(getError(owner, ERROR_BAD_INFO_FROM_STORAGE), concat.toString()))
                    .show();
        }

        @Override
        public void onFailedSave(@NonNull Exception e) {
            new AlertDialog.Builder(owner)
                    .setMessage(String.format(getError(owner, ERROR_COULD_NOT_STORE_NEW_DATA), e.getLocalizedMessage()))
                    .show();
        }
    }
    public AsyncActivityLoadUpdateTask(String subdir, String fileName, String targetFilePrefix, final Activity source, Callbacks callbacks) {
        super(source.getFilesDir(), subdir, fileName, new PersistentDataUtils(PcAssignmentHelper.DOORMAT_BYTES) {
            @Override
            protected String getString(int resource) {
                return source.getString(resource);
            }
        }, targetFilePrefix, callbacks);
        this.source = source;
    }


    @Override
    protected String getError(int code) {
        return getError(source, code);
    }


    protected static String getError(Activity owner, int code) {
        switch(code) {
            case ERROR_BAD_INFO_FROM_STORAGE: return owner.getString(R.string.dataLoadUpdate_badGroupInfoFromStorage);
            case ERROR_COULD_NOT_STORE_NEW_DATA: return owner.getString(R.string.dataLoadUpdate_couldNotStoreNewData);
            case ERROR_FAILED_PREVIOUS_DATA_DELETE: return owner.getString(R.string.dataLoadUpdate_failedPreviousDataDelete);
            case ERROR_FAILED_NEW_RENAME_PREVIOUS_DESTROYED: return owner.getString(R.string.dataLoadUpdate_failedNewDataRenamePreviousDestroyed);
        }
        return "<unknown>"; // perhaps I should throw and die here
    }


    final Activity source;

}
