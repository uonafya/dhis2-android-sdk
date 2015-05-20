/*
 *  Copyright (c) 2015, University of Oslo
 *  * All rights reserved.
 *  *
 *  * Redistribution and use in source and binary forms, with or without
 *  * modification, are permitted provided that the following conditions are met:
 *  * Redistributions of source code must retain the above copyright notice, this
 *  * list of conditions and the following disclaimer.
 *  *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *  * this list of conditions and the following disclaimer in the documentation
 *  * and/or other materials provided with the distribution.
 *  * Neither the name of the HISP project nor the names of its contributors may
 *  * be used to endorse or promote products derived from this software without
 *  * specific prior written permission.
 *  *
 *  * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 *  * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 *  * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *  * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 *  * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package org.hisp.dhis2.android.sdk.controllers;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.location.Location;
import android.util.Log;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raizlabs.android.dbflow.runtime.FlowContentObserver;
import com.squareup.otto.Subscribe;

import org.hisp.dhis2.android.sdk.R;
import org.hisp.dhis2.android.sdk.controllers.datavalues.DataValueController;
import org.hisp.dhis2.android.sdk.controllers.datavalues.DataValueLoader;
import org.hisp.dhis2.android.sdk.controllers.metadata.MetaDataController;
import org.hisp.dhis2.android.sdk.controllers.metadata.MetaDataLoader;
import org.hisp.dhis2.android.sdk.controllers.tasks.AuthUserTask;
import org.hisp.dhis2.android.sdk.events.BaseEvent;
import org.hisp.dhis2.android.sdk.events.LoadingEvent;
import org.hisp.dhis2.android.sdk.events.LoadingMessageEvent;
import org.hisp.dhis2.android.sdk.events.MessageEvent;
import org.hisp.dhis2.android.sdk.events.ResponseEvent;
import org.hisp.dhis2.android.sdk.network.http.ApiRequestCallback;
import org.hisp.dhis2.android.sdk.network.http.Response;
import org.hisp.dhis2.android.sdk.network.managers.NetworkManager;
import org.hisp.dhis2.android.sdk.persistence.Dhis2Application;
import org.hisp.dhis2.android.sdk.persistence.models.Constant;
import org.hisp.dhis2.android.sdk.persistence.models.DataElement;
import org.hisp.dhis2.android.sdk.persistence.models.DataValue;
import org.hisp.dhis2.android.sdk.persistence.models.Enrollment;
import org.hisp.dhis2.android.sdk.persistence.models.Event;
import org.hisp.dhis2.android.sdk.persistence.models.Option;
import org.hisp.dhis2.android.sdk.persistence.models.OptionSet;
import org.hisp.dhis2.android.sdk.persistence.models.OrganisationUnit;
import org.hisp.dhis2.android.sdk.persistence.models.OrganisationUnitProgramRelationship;
import org.hisp.dhis2.android.sdk.persistence.models.Program;
import org.hisp.dhis2.android.sdk.persistence.models.ProgramIndicator;
import org.hisp.dhis2.android.sdk.persistence.models.ProgramStage;
import org.hisp.dhis2.android.sdk.persistence.models.ProgramStageDataElement;
import org.hisp.dhis2.android.sdk.persistence.models.ProgramStageSection;
import org.hisp.dhis2.android.sdk.persistence.models.ProgramTrackedEntityAttribute;
import org.hisp.dhis2.android.sdk.persistence.models.TrackedEntity;
import org.hisp.dhis2.android.sdk.persistence.models.TrackedEntityAttribute;
import org.hisp.dhis2.android.sdk.persistence.models.TrackedEntityAttributeValue;
import org.hisp.dhis2.android.sdk.persistence.models.TrackedEntityInstance;
import org.hisp.dhis2.android.sdk.persistence.models.User;
import org.hisp.dhis2.android.sdk.services.PeriodicSynchronizer;
import org.hisp.dhis2.android.sdk.utils.APIException;
import org.hisp.dhis2.android.sdk.utils.CustomDialogFragment;
import org.hisp.dhis2.android.sdk.utils.GpsManager;

import java.io.IOException;

public final class Dhis2 {
    private static final String CLASS_TAG = "Dhis2";

    public final static String LAST_UPDATED_METADATA = "lastupdated_metadata";
    public final static String LAST_UPDATED_DATAVALUES = "lastupdated_datavalues";
    public final static String LOAD = "load";
    public static final String LOADED = "loaded";
    public static final String UPDATED = "updated";

    /* flags used to determine what data to be loaded from the server */
    public static final String LOAD_TRACKER = "load_tracker";
    public static final String LOAD_EVENTCAPTURE = "load_eventcapture";

    public static final String UPDATE_FREQUENCY = "update_frequency";
    public final static String QUEUED = "queued";
    public final static String PREFS_NAME = "DHIS2";
    private final static String USERNAME = "username";
    private final static String PASSWORD = "password";
    private final static String SERVER = "server";
    private final static String CREDENTIALS = "credentials";
    private MetaDataController metaDataController;
    private DataValueController dataValueController;
    private ObjectMapper objectMapper;
    private Context context; //beware when using this as it must be set explicitly
    private Activity activity;

    private boolean loadingInitial = false;
    private boolean loading = false;


    public Dhis2() {
        objectMapper = new ObjectMapper();
    }

    public static Context getContext() {
        return getInstance().context;
    }

    public static Dhis2 getInstance() {
        return Dhis2Application.dhis2;
    }

    public static void activateGps(Context context) {
        GpsManager.init(context);
        GpsManager.getInstance().requestLocationUpdates();
    }

    public static void disableGps() {
        GpsManager.getInstance().removeUpdates();
    }

    public static Location getLocation() {
        return GpsManager.getInstance().getLocation();
    }

    /**
     * Returns the currently set Update Frequency
     *
     * @param context
     * @return
     */
    public static int getUpdateFrequency(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int updateFrequency = sharedPreferences.getInt(UPDATE_FREQUENCY, PeriodicSynchronizer.DEFAULT_UPDATE_FREQUENCY);
        Log.e(CLASS_TAG, "updateFrequency: " + updateFrequency);
        return updateFrequency;
    }

    /**
     * Sets the update frequency by an integer referencing the indexes in the update_frequencies string-array
     *
     * @param context
     * @param frequency index of update frequencies. See update_frequencies string-array
     */
    public static void setUpdateFrequency(Context context, int frequency) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(UPDATE_FREQUENCY, frequency);
        editor.commit();
        Log.e(CLASS_TAG, "updateFrequency: " + frequency);
        PeriodicSynchronizer.reActivate(context);
    }

    public static void cancelPeriodicSynchronizer(Context context) {
        PeriodicSynchronizer.cancelPeriodicSynchronizer(context);
    }

    public static void activatePeriodicSynchronizer(Context context) {
        PeriodicSynchronizer.activatePeriodicSynchronizer(context, PeriodicSynchronizer.getInterval(context));
    }

    /**
     * Enables loading of different data. LOAD_EVENTCAPTURE, LOAD_TRACKER
     *
     * @param mode
     */
    public void enableLoading(Context context, String mode) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        if (mode.equals(LOAD_EVENTCAPTURE)) {
            editor.putBoolean(LOAD + MetaDataLoader.ASSIGNED_PROGRAMS, true);
            editor.putBoolean(LOAD + MetaDataLoader.OPTION_SETS, true);
            editor.putBoolean(LOAD + MetaDataLoader.PROGRAMS, true);
            editor.putBoolean(LOAD + MetaDataLoader.CONSTANTS, true);
            editor.putBoolean(LOAD + MetaDataLoader.PROGRAMRULES, true);
            editor.putBoolean(LOAD + MetaDataLoader.PROGRAMRULEVARIABLES, true);
            editor.putBoolean(LOAD + MetaDataLoader.PROGRAMRULEACTIONS, true);

            editor.putBoolean(LOAD + DataValueLoader.EVENTS, true);
            editor.putBoolean(LOAD + Program.SINGLE_EVENT_WITHOUT_REGISTRATION, true);
        } else if (mode.equals(LOAD_TRACKER)) {
            editor.putBoolean(LOAD + MetaDataLoader.ASSIGNED_PROGRAMS, true);
            editor.putBoolean(LOAD + MetaDataLoader.OPTION_SETS, true);
            editor.putBoolean(LOAD + MetaDataLoader.PROGRAMS, true);
            editor.putBoolean(LOAD + MetaDataLoader.CONSTANTS, true);
            editor.putBoolean(LOAD + MetaDataLoader.PROGRAMRULES, true);
            editor.putBoolean(LOAD + MetaDataLoader.PROGRAMRULEVARIABLES, true);
            editor.putBoolean(LOAD + MetaDataLoader.PROGRAMRULEACTIONS, true);

            editor.putBoolean(LOAD + DataValueLoader.EVENTS, true);
            editor.putBoolean(LOAD + DataValueLoader.ENROLLMENTS, true);
            editor.putBoolean(LOAD + DataValueLoader.TRACKED_ENTITY_INSTANCES, true);
            editor.putBoolean(LOAD + Program.SINGLE_EVENT_WITH_REGISTRATION, true);
            editor.putBoolean(LOAD + Program.MULTIPLE_EVENTS_WITH_REGISTRATION, true);
        }
        editor.commit();
    }

    /**
     * clears all loading flags
     *
     * @param context
     */
    public static void clearLoadFlags(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(LOAD + MetaDataLoader.ASSIGNED_PROGRAMS, false);
        editor.putBoolean(LOAD + MetaDataLoader.TRACKED_ENTITY_ATTRIBUTES, false);
        editor.putBoolean(LOAD + MetaDataLoader.CONSTANTS, false);
        editor.putBoolean(LOAD + MetaDataLoader.OPTION_SETS, false);
        editor.putBoolean(LOAD + MetaDataLoader.PROGRAMS, false);
        editor.putBoolean(LOAD + MetaDataLoader.PROGRAMRULES, false);
        editor.putBoolean(LOAD + MetaDataLoader.PROGRAMRULEVARIABLES, false);
        editor.putBoolean(LOAD + MetaDataLoader.PROGRAMRULEACTIONS, false);

        editor.putBoolean(LOAD + DataValueLoader.EVENTS, false);
        editor.putBoolean(LOAD + DataValueLoader.ENROLLMENTS, false);
        editor.putBoolean(LOAD + DataValueLoader.TRACKED_ENTITY_INSTANCES, false);
        editor.putBoolean(LOAD + Program.SINGLE_EVENT_WITHOUT_REGISTRATION, false);
        editor.commit();
    }

    /**
     * returns whether or not a load flag has been enabled.
     *
     * @param context
     * @param flag
     * @return
     */
    public static boolean isLoadFlagEnabled(Context context, String flag) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean(LOAD + flag, false);
    }

    public static boolean isInitialDataLoaded(Context context) {
        return (isMetaDataLoaded(context) && isDataValuesLoaded(context));
    }

    /**
     * Returns false if some meta data flags that have been enabled have not been downloaded.
     *
     * @param context
     * @return
     */
    public static boolean isMetaDataLoaded(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (isLoadFlagEnabled(context, MetaDataLoader.ASSIGNED_PROGRAMS)) {
            if (!sharedPreferences.getBoolean(LOADED + MetaDataLoader.ASSIGNED_PROGRAMS, false))
                return false;
        } else if (isLoadFlagEnabled(context, MetaDataLoader.OPTION_SETS)) {
            if (!sharedPreferences.getBoolean(LOADED + MetaDataLoader.OPTION_SETS, false))
                return false;
        } else if (isLoadFlagEnabled(context, MetaDataLoader.PROGRAMS)) {
            if (!sharedPreferences.getBoolean(LOADED + MetaDataLoader.PROGRAMS, false))
                return false;
        } else if (isLoadFlagEnabled(context, MetaDataLoader.TRACKED_ENTITY_ATTRIBUTES)) {
            if (!sharedPreferences.getBoolean(LOADED + MetaDataLoader.TRACKED_ENTITY_ATTRIBUTES, false))
                return false;
        } else if (isLoadFlagEnabled(context, MetaDataLoader.CONSTANTS)) {
            if (!sharedPreferences.getBoolean(LOADED + MetaDataLoader.CONSTANTS, false))
                return false;
        } else if (isLoadFlagEnabled(context, MetaDataLoader.PROGRAMRULES)) {
            if (!sharedPreferences.getBoolean(LOADED + MetaDataLoader.PROGRAMRULES, false))
                return false;
        } else if (isLoadFlagEnabled(context, MetaDataLoader.PROGRAMRULEVARIABLES)) {
            if (!sharedPreferences.getBoolean(LOADED + MetaDataLoader.PROGRAMRULEVARIABLES, false))
                return false;
        } else if (isLoadFlagEnabled(context, MetaDataLoader.PROGRAMRULEACTIONS)) {
            if (!sharedPreferences.getBoolean(LOADED + MetaDataLoader.PROGRAMRULEACTIONS, false))
                return false;
        }
        return true;
    }

    /**
     * Returns false if some data value flags that have been enabled have not been downloaded.
     *
     * @param context
     * @return
     */
    public static boolean isDataValuesLoaded(Context context) {
        Log.d(CLASS_TAG, "isdatavaluesloaded..");
        if (isLoadFlagEnabled(context, DataValueLoader.EVENTS)) {
            if (!DataValueLoader.isEventsLoaded(context)) return false;
        } else if (isLoadFlagEnabled(context, DataValueLoader.ENROLLMENTS)) {
            if (!DataValueLoader.isEnrollmentsLoaded(context)) return false;
        } else if (isLoadFlagEnabled(context, DataValueLoader.TRACKED_ENTITY_INSTANCES)) {
            if (!DataValueLoader.isTrackedEntityInstancesLoaded(context)) return false;
        }
        Log.d(CLASS_TAG, "data values are loaded.");
        return true;
    }

    public static boolean hasLoadedInitial(Context context) {
        if (!isMetaDataLoaded(context)) return false;
        else if (!isDataValuesLoaded(context)) return false;
        return true;
    }

    public ObjectMapper getObjectMapper() {
        if (objectMapper == null) objectMapper = new ObjectMapper();
        return objectMapper;
    }

    public MetaDataController getMetaDataController() {
        if (metaDataController == null) metaDataController = new MetaDataController();
        return metaDataController;
    }

    public DataValueController getDataValueController() {
        if (dataValueController == null) dataValueController = new DataValueController();
        return dataValueController;
    }

    /**
     * @param serverUrl
     */
    public void setServer(String serverUrl) {
        NetworkManager.getInstance().setServerUrl(serverUrl);
    }

    public static void saveCredentials(Context context, String serverUrl, String username, String password) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(USERNAME, username);
        editor.putString(PASSWORD, password);
        editor.putString(SERVER, serverUrl);
        String credentials = null;
        if (username != null && password != null)
            credentials = NetworkManager.getInstance().getBase64Manager().toBase64(username, password);
        editor.putString(CREDENTIALS, credentials);
        editor.commit();
    }

    public static String getUsername(Context context) {
        if (context != null) getInstance().context = context;
        if (getInstance().context == null) return null;
        SharedPreferences prefs = getInstance().context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String username = prefs.getString(USERNAME, null);
        return username;
    }

    public static String getServer(Context context) {
        if (context != null) getInstance().context = context;
        if (getInstance().context == null) return null;
        SharedPreferences prefs = getInstance().context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String server = prefs.getString(SERVER, null);
        return server;
    }

    public static String getCredentials(Context context) {
        if (context != null) getInstance().context = context;
        if (getInstance().context == null) return null;
        SharedPreferences prefs = getInstance().context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String credentials = prefs.getString(CREDENTIALS, null);
        return credentials;
    }

    /**
     * Tries to log in to the given DHIS 2 server
     *
     * @param username
     * @param password
     */
    public void login(String username, String password) {
        // TODO first check if we already have User through persistence layer
        // TODO if yes, return it, if not call network
        final ResponseHolder<User> holder = new ResponseHolder<>();
        new AuthUserTask(NetworkManager.getInstance(), new ApiRequestCallback<User>() {
            @Override
            public void onSuccess(Response response) {
                holder.setResponse(response);

                try {
                    User user = objectMapper.readValue(response.getBody(), User.class);
                    holder.setItem(user);
                } catch (IOException e) {
                    e.printStackTrace();
                    holder.setApiException(APIException.conversionError(response.getUrl(), response, e));
                }
                ResponseEvent<User> event = new ResponseEvent<User>(ResponseEvent.EventType.onLogin);
                event.setResponseHolder(holder);
                Dhis2Application.bus.post(event);
            }

            @Override
            public void onFailure(APIException exception) {
                holder.setApiException(exception);
                ResponseEvent event = new ResponseEvent(ResponseEvent.EventType.onLogin);
                event.setResponseHolder(holder);
                Dhis2Application.bus.post(event);
            }
        }, username, password).execute();
    }

    /**
     * Logs out the current user
     */
    public static void logout(Context context) {
        Dhis2.getInstance().setServer(null);
        Dhis2.getInstance().saveCredentials(context, null, null, null);
        NetworkManager.getInstance().setCredentials(null);
        getInstance().getMetaDataController().resetLastUpdated(context);
        getInstance().getMetaDataController().clearMetaDataLoadedFlags(context);
        getInstance().getDataValueController().clearDataValueLoadedFlags(context);
        getInstance().getMetaDataController().wipeMetaData();
        clearLoadFlags(context);
    }

    /**
     * initiates sending locally modified data items and loads updated items from the server.
     *
     * @param context
     */
    public static void sendLocalData(Context context) {
        if (getInstance().loading) return;
        getInstance().loading = true;
        getInstance().context = context;
        new Thread() {
            public void run() {
                getInstance().getDataValueController().synchronizeDataValues(getInstance().context);
            }
        }.start();
    }

    /**
     * Loads initial data. Which data is enabled is defined by the enableLoading method
     */
    public static void loadInitialData(Context context) {
        Log.d(CLASS_TAG, "loadInitialData");
        if (context != null) {
            getInstance().context = context;
            if (context instanceof Activity) getInstance().activity = (Activity) context;
        }
        if (context == null && getInstance().context == null) return;

        getInstance().loadingInitial = true;
        getInstance().loading = true;
        if (!isMetaDataLoaded(getInstance().context)) {
            loadInitialMetadata();
        } else if (!isDataValuesLoaded(getInstance().context)) {
            loadInitialDataValues();
        } else {
            onFinishLoading();
        }
    }

    public boolean isLoading() {
        if (Dhis2.getInstance().getMetaDataController().isLoading()) return true;
        if (Dhis2.getInstance().getDataValueController().isLoading()) return true;
        if (Dhis2.getInstance().getDataValueController().isSending()) return true;
        return false;
    }

    private static void loadInitialMetadata() {
        Log.d(CLASS_TAG, "loading initial metadata!");
        loadMetaData(getInstance().context);
    }

    private static void loadMetaData(Context context) {
        Log.d(CLASS_TAG, "loading metadata!");
        getInstance().getMetaDataController().loadMetaData(getInstance().context);
    }

    /**
     * Initiates synchronization with server. Updates MetaData, sends locally saved data, loads
     * new data values from server.
     *
     * @param context
     */
    public static void synchronize(Context context) {
        synchronizeMetaData(context);
    }

    /**
     * initiates synchronization of metadata from server
     *
     * @param context
     */
    public static void synchronizeMetaData(Context context) {
        Log.d(CLASS_TAG, "synchronizing metadata!");
        if (getInstance().loading) return;
        getInstance().loading = true;
        Dhis2.getInstance().getMetaDataController().synchronizeMetaData(context); //callback is onFinishLoading
    }

    /* called either from loadInitialMetadata, or in Subscribe method*/
    private static void loadInitialDataValues() {
        Log.d(CLASS_TAG, "loading initial datavalues");
        FlowContentObserver observer = getFlowContentObserverForAllTables();
        String message;
        if (getInstance().activity != null)
            message = getInstance().activity.getString(R.string.finishing_up);
        else
            message = "Finishing initial database setup. This may take several minutes so please be patient.";
        postProgressMessage(message);
        WaitForMetaDataSavingBlockThread blockThread = new WaitForMetaDataSavingBlockThread(observer);
        BlockingModelChangeListener listener = new BlockingModelChangeListener(blockThread);
        observer.addModelChangeListener(listener);
        blockThread.start();
    }

    public static void synchronizeDataValues(Context context) {
        getInstance().getDataValueController().synchronizeDataValues(context);
    }

    /**
     * Called after meta data and data values have finished loading
     */
    private static void onFinishLoading() {
        Log.d(CLASS_TAG, "onFinishLoading");
        boolean finished = false;
        if (getInstance().loadingInitial) {
            if (isMetaDataLoaded(getInstance().context)) {
                Log.d(CLASS_TAG, "has loaded init metadatapart");
                /**
                 * Initial loading of meta data is completed successfully, continue checking if
                 * required data values was loaded successfully.
                 */

                if (isDataValuesLoaded(getInstance().context)) {
                    Log.d(CLASS_TAG, "full loading success");
                    finished = true;
                } else {
                    //todo: implement re-trying of loading or sth. Could perhaps be handled further down (earlier) in the process
                    //todo: implement onFailedLoadingInitialDataValues
                    Log.d(CLASS_TAG, "full loading failed loading data values. Continuing anyways..");
                    finished = true;
                }
            } else {
                Log.d(CLASS_TAG, "failed loading!!!");
                /**
                 * Initial loading of meta data failed. Give the user feedback. Since meta data is
                 * missing the app will most likely not function very well, but we could allow the
                 * user to continue working regardless.
                 */
                onFailedLoadingInitialMetaData();
            }

            /**
             * Now we block and wait with sending the 'done' message while data is being saved to
             * to avoid race conditions with trying to access data.
             */


            if (finished) {
                FlowContentObserver observer = getFlowContentObserverForAllTables();
                String message;
                if (getInstance().activity != null)
                    message = getInstance().activity.getString(R.string.finishing_up);
                else
                    message = "Finishing initial database setup. This may take several minutes so please be patient.";
                postProgressMessage(message);
                FinishLoadingBlockThread blockThread = new FinishLoadingBlockThread(observer);
                BlockingModelChangeListener listener = new BlockingModelChangeListener(blockThread);
                observer.addModelChangeListener(listener);
                blockThread.start();
            } else getInstance().loading = false;
        } else {
            getInstance().loading = false;
        }
    }

    /**
     * Called if initial meta data loading fails. Gives a notification to user.
     */
    private static void onFailedLoadingInitialMetaData() {
        if (getInstance().activity != null) {
            DialogInterface.OnClickListener retryListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    loadInitialData(getInstance().activity);
                }
            };

            DialogInterface.OnClickListener cancelListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    MessageEvent event = new MessageEvent(BaseEvent.EventType.loadInitialDataFailed); //can be subscribed to for example in MainActivity or wherever loading was initiated.
                    Dhis2Application.bus.post(event);
                }
            };
            showConfirmDialog(getInstance().activity,
                    getInstance().activity.getString(R.string.error_message),
                    getInstance().activity.getString(R.string.loading_failed_metadata),
                    getInstance().activity.getString(R.string.retry),
                    getInstance().activity.getString(R.string.cancel),
                    retryListener,
                    cancelListener);
        } else {
            MessageEvent event = new MessageEvent(BaseEvent.EventType.loadInitialDataFailed); //can be subscribed to for example in MainActivity or wherever loading was initiated.
            Dhis2Application.bus.post(event);
        }
    }

    public static void showErrorDialog(final Activity activity, final String title, final String message) {
        if (activity == null) return;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new CustomDialogFragment(title, message,
                        "OK", null).show(activity.getFragmentManager(), title);
            }
        });
    }

    public static void showErrorDialog(final Activity activity, final String title,
                                       final String message, final int iconId) {
        if (activity == null) return;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new CustomDialogFragment(title, message,
                        "OK", iconId, null).show(activity.getFragmentManager(), title);
            }
        });
    }

    public static void showErrorDialog(final Activity activity, final String title, final String message, final DialogInterface.OnClickListener onConfirmClickListener) {
        if (activity == null) return;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new CustomDialogFragment(title, message,
                        "OK", onConfirmClickListener).show(activity.getFragmentManager(), title);
            }
        });
    }

    public static void showConfirmDialog(final Activity activity, final String title, final String message,
                                         final String confirmOption, final String cancelOption,
                                         DialogInterface.OnClickListener onClickListener) {
        new CustomDialogFragment(title, message, confirmOption, cancelOption, onClickListener).
                show(activity.getFragmentManager(), title);
    }

    public static void showConfirmDialog(final Activity activity, final String title, final String message,
                                         final String confirmOption, final String cancelOption,
                                         DialogInterface.OnClickListener onConfirmListener,
                                         DialogInterface.OnClickListener onCancelListener) {
        new CustomDialogFragment(title, message, confirmOption, cancelOption, onConfirmListener,
                onCancelListener).
                show(activity.getFragmentManager(), title);
    }

    public static void showConfirmDialog(final Activity activity, final String title, final String message,
                                         final String firstOption, final String secondOption, final String thirdOption,
                                         DialogInterface.OnClickListener firstOptionListener,
                                         DialogInterface.OnClickListener secondOptionListener,
                                         DialogInterface.OnClickListener thirdOptionListener) {
        new CustomDialogFragment(title, message, firstOption, secondOption, thirdOption,
                firstOptionListener, secondOptionListener, thirdOptionListener).
                show(activity.getFragmentManager(), title);
    }

    @Subscribe
    public void onResponse(LoadingEvent loadingEvent) {
        if (loadingEvent.eventType == BaseEvent.EventType.onLoadingMetaDataFinished) {
            if (!loadingEvent.success) {
                onFinishLoading();
            } else {
                if (isLoadingInitial()) {
                    loadInitialDataValues();
                } else {
                    synchronizeDataValues(context);
                }
            }
        } else if (loadingEvent.eventType == BaseEvent.EventType.onLoadDataValuesFinished) {
            onFinishLoading();
        } else if (loadingEvent.eventType == BaseEvent.EventType.onUpdateDataValuesFinished) {
            onFinishLoading();
        }
    }

    /**
     * Sends an event with feedback to user on loading. Picked up in LoadingFragment.
     *
     * @param message
     */
    public static void postProgressMessage(final String message) {
        new Thread() {
            @Override
            public void run() {
                LoadingMessageEvent event = new LoadingMessageEvent(BaseEvent.EventType.showRegisterEventFragment);
                event.message = message;
                Dhis2Application.bus.post(event);
            }
        }.start();
    }

    public static void setLoadingInitial(boolean loading) {
        getInstance().loadingInitial = loading;
    }

    public static boolean isLoadingInitial() {
        return getInstance().loadingInitial;
    }

    private static class WaitForMetaDataSavingBlockThread extends BlockThread {

        public WaitForMetaDataSavingBlockThread(FlowContentObserver observer) {
            super(observer);
        }

        @Override
        public void callback() {
            Log.d(CLASS_TAG, "init loading datavalues");
            if (!isDataValuesLoaded(getInstance().context)) {
                Log.d(CLASS_TAG, "init loadig datavalues trackerEnabled init loading");
                getInstance().getDataValueController().loadDataValues(getInstance().context, false);
            } else {
                onFinishLoading();
            }
        }
    }

    private static class FinishLoadingBlockThread extends BlockThread {

        public FinishLoadingBlockThread(FlowContentObserver observer) {
            super(observer);
        }

        @Override
        public void callback() {
            getInstance().loadingInitial = false;
            getInstance().loading = false;
            MessageEvent messageEvent = new MessageEvent(BaseEvent.EventType.onLoadingInitialDataFinished);
            Dhis2Application.bus.post(messageEvent);
        }
    }

    /**
     * Thread for blocking and waiting for DBFlow's TransactionManager to finish saving in the
     * background
     */
    private static class BlockThread extends Thread {
        private final FlowContentObserver observer;

        public BlockThread(FlowContentObserver observer) {
            this.observer = observer;
        }

        boolean block = true;

        public void run() {
            while (block) {
                Log.e(CLASS_TAG, "Blocking ..");
                try {
                    block = false;
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    block = true;
                }
            }
            observer.unregisterForContentChanges(Dhis2.getInstance().context);
            Log.e(CLASS_TAG, "done blocking ..");
            callback();
        }

        public void callback() {
        }
    }

    public static FlowContentObserver getFlowContentObserverForAllTables() {
        FlowContentObserver observer = new FlowContentObserver();
        observer.registerForContentChanges(getInstance().context, Constant.class);
        observer.registerForContentChanges(getInstance().context, DataElement.class);
        observer.registerForContentChanges(getInstance().context, DataValue.class);
        observer.registerForContentChanges(getInstance().context, Enrollment.class);
        observer.registerForContentChanges(getInstance().context, Event.class);
        observer.registerForContentChanges(getInstance().context, Option.class);
        observer.registerForContentChanges(getInstance().context, OptionSet.class);
        observer.registerForContentChanges(getInstance().context, OrganisationUnit.class);
        observer.registerForContentChanges(getInstance().context, OrganisationUnitProgramRelationship.class);
        observer.registerForContentChanges(getInstance().context, Program.class);
        observer.registerForContentChanges(getInstance().context, ProgramIndicator.class);
        observer.registerForContentChanges(getInstance().context, ProgramStage.class);
        observer.registerForContentChanges(getInstance().context, ProgramStageDataElement.class);
        observer.registerForContentChanges(getInstance().context, ProgramStageSection.class);
        observer.registerForContentChanges(getInstance().context, ProgramTrackedEntityAttribute.class);
        observer.registerForContentChanges(getInstance().context, TrackedEntity.class);
        observer.registerForContentChanges(getInstance().context, TrackedEntityAttribute.class);
        observer.registerForContentChanges(getInstance().context, TrackedEntityAttributeValue.class);
        observer.registerForContentChanges(getInstance().context, TrackedEntityInstance.class);
        observer.registerForContentChanges(getInstance().context, User.class);
        return observer;
    }

    /**
     * ModelChangeListener for blocking and waiting for DBFlow's TransactionManager to finish saving
     * in the background
     */
    private static class BlockingModelChangeListener implements FlowContentObserver.ModelChangeListener {
        private final BlockThread blockThread;

        public BlockingModelChangeListener(BlockThread blockThread) {
            this.blockThread = blockThread;
        }

        @Override
        public void onModelChanged() {
            blockThread.block = true;
        }

        @Override
        public void onModelSaved() {
            blockThread.block = true;
        }

        @Override
        public void onModelDeleted() {
        }

        @Override
        public void onModelInserted() {
            blockThread.block = true;
        }

        @Override
        public void onModelUpdated() {
            blockThread.block = true;
        }
    }

    ;
}
