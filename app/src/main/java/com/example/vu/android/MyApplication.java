package com.example.vu.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ProcessLifecycleOwner;

import java.util.List;

import io.sentry.Sentry;
import io.sentry.UserFeedback;
import io.sentry.android.core.SentryAndroid;
import io.sentry.SentryLevel;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.User;


public class MyApplication extends Application implements LifecycleObserver {

    private MyBaseActivity mCurrentActivity = null;

    public MyBaseActivity getCurrentActivity () {
        return mCurrentActivity ;
    }
    public void setCurrentActivity (MyBaseActivity mCurrentActivity) {
        this.mCurrentActivity = mCurrentActivity ;
    }

    private String getApplicationName() {
        Context  context = this.getApplicationContext();
        return context.getApplicationInfo().loadLabel(context.getPackageManager()).toString();
    }



    @Override
    public void onCreate() {
        super.onCreate();
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);

        SentryAndroid.init(this, options -> {

            try {
                PackageInfo pInfo = this.getPackageManager().getPackageInfo(this.getPackageName(), 0);
                String version = pInfo.versionName;
                options.setTag("versionName", pInfo.versionName);
                options.setTag("applicationName", this.getApplicationName());
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }

            options.setAttachThreads(true);
            options.setBeforeSend((event, hint) -> {

                //Remove PII
                List<SentryException> exceptions = event.getExceptions();
                if(exceptions != null && exceptions.size() > 0){
                    SentryException exception = exceptions.get(0);
                    if("NegativeArraySizeException".equals(exception.getType())) {
                        User user = event.getUser();
                        user.setIpAddress(null);
                    }
                }
                SentryException currentException = event.getExceptions().get(0);
                if(currentException != null && currentException.getType().endsWith("ItemDeliveryProcessException")){
                    this.launchUserFeedback(event.getEventId());
                }

                event.setExtra("fullStoryURL", this.mCurrentActivity.getFullStorySessionURL());

                //Drop event
                if (SentryLevel.DEBUG.equals(event.getLevel()))
                    return null;
                else
                    return event;

            });
        });

    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    private void onAppBackgrounded() {
        Log.d("YourApplication", "YourApplication is in background");
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    private void onAppForegrounded() {
        Log.d("YourApplication", "YourApplication is in foreground");
    }

    private void launchUserFeedback(SentryId sentryId){

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mCurrentActivity);
        final EditText editTextName1 = new EditText(MyApplication.this);
        editTextName1.setHint("OMG! What happened??");

        LinearLayout layoutName = new LinearLayout(this);
        layoutName.setOrientation(LinearLayout.VERTICAL);
        layoutName.setPadding(60, 20, 60, 20);
        layoutName.addView(editTextName1);
        alertDialogBuilder.setView(layoutName);

        alertDialogBuilder.setTitle("Ooops, Checkout Failed!");
        alertDialogBuilder.setPositiveButton("Submit", new DialogInterface.OnClickListener(){
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                Toast.makeText(mCurrentActivity,"Thank you!",Toast.LENGTH_LONG).show();
                String txt = editTextName1.getText().toString(); // variable to collect user input

                UserFeedback userFeedback = new UserFeedback(sentryId);
                userFeedback.setComments( txt );
                userFeedback.setEmail("john.doe@example.com");
                userFeedback.setName("John Doe");
                Sentry.captureUserFeedback(userFeedback);

            }
        });

        alertDialogBuilder.setNegativeButton("Cancel",new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }


}
