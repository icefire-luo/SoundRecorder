package com.danielkim.soundrecorder;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.danielkim.soundrecorder.activities.MainActivity;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Daniel on 12/28/2014.
 */
public class RecordingService extends Service {

    private static final String LOG_TAG = "RecordingService";
    private static final boolean isMediaRecorder = false;

    private Integer mARecorderBits   = AudioFormat.ENCODING_PCM_16BIT;
//  private Integer mARecorderChans  = AudioFormat.CHANNEL_IN_MONO;
    private Integer mARecorderChans  = AudioFormat.CHANNEL_IN_STEREO;
    private Integer mARecorderSource = MediaRecorder.AudioSource.MIC;
//  private Integer mARecorderSource = MediaRecorder.AudioSource.CAMCORDER;
//  private Integer mARecorderSource = MediaRecorder.AudioSource.VOICE_RECOGNITION;
//  private Integer mARecorderSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION;

//  private Integer mARecorderSamplerate  = 44100;
    private Integer mARecorderSamplerate  = 48000;


    private String mFileName = null;
    private String mFilePath = null;

    private MediaRecorder mRecorder = null;
    private AudioRecorderT mARecorder = null;

    private DBHelper mDatabase;

    private long mStartingTimeMillis = 0;
    private long mElapsedMillis = 0;
    private int mElapsedSeconds = 0;
    private OnTimerChangedListener onTimerChangedListener = null;
    private static final SimpleDateFormat mTimerFormat = new SimpleDateFormat("mm:ss", Locale.getDefault());

    private Timer mTimer = null;
    private TimerTask mIncrementTimerTask = null;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public interface OnTimerChangedListener {
        void onTimerChanged(int seconds);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mDatabase = new DBHelper(getApplicationContext());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startRecording();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if ( mRecorder != null || mARecorder != null ) {
                stopRecording();
         }
        super.onDestroy();
    }

    public void startRecording() {
        setFileNameAndPath();

        if( isMediaRecorder == true){
            mRecorder = new MediaRecorder();
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mRecorder.setOutputFile(mFilePath);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            //mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.VORBIS);
            //mRecorder.setAudioChannels(1);
            mRecorder.setAudioChannels(2);
            mRecorder.setAudioSamplingRate(44100);
            mRecorder.setAudioEncodingBitRate(192000);

            if (MySharedPreferences.getPrefHighQuality(this)) {
                mRecorder.setAudioSamplingRate(44100);
                mRecorder.setAudioEncodingBitRate(192000);
            }

            try {
                mRecorder.prepare();
                mRecorder.start();
                mStartingTimeMillis = System.currentTimeMillis();

                //startTimer();
                //startForeground(1, createNotification());

            } catch (IOException e) {
                Log.e(LOG_TAG, "prepare() failed");
            }
        }else{
            mARecorder = AudioRecorderT.getInstance();
            mARecorder.setAudioEncoding(mARecorderBits);
            mARecorder.setAudioSource(mARecorderSource);
            mARecorder.setChannelConfig(mARecorderChans);

            mARecorder.setFrequence(mARecorderSamplerate);
            mARecorder.setFilePath(mFilePath);

            mARecorder.startRecordAndFile();
            mStartingTimeMillis = System.currentTimeMillis();

            String fullFileName = mARecorder.getFileName();
            mFileName = fullFileName.substring(fullFileName.lastIndexOf("/")+1, fullFileName.length());
            mFilePath += mFileName;
            //startTimer();
            //startForeground(1, createNotification());
          }
    }

    public void setFileNameAndPath(){
        int count = 0;
        File f;

        do{
            count++;

            mFileName = getString(R.string.default_file_name)
                    + "_" + (mDatabase.getCount() + count) + ".mp4";
            mFilePath = Environment.getExternalStorageDirectory().getAbsolutePath();
            //mFilePath += "/DolbyTest/" + mFileName;
            //mFilePath += "/DolbyTest/recorders/";
            mFilePath += "/SoundRecorder/";

            f = new File(mFilePath);
        }while (f.exists() && !f.isDirectory());
    }

    public void stopRecording() {
        if( isMediaRecorder == true) {
            mRecorder.stop();
            mElapsedMillis = (System.currentTimeMillis() - mStartingTimeMillis);
            mRecorder.release();
            Toast.makeText(this, getString(R.string.toast_recording_finish) + " " + mFilePath, Toast.LENGTH_LONG).show();

            //remove notification
            if (mIncrementTimerTask != null) {
                mIncrementTimerTask.cancel();
                mIncrementTimerTask = null;
            }

            mRecorder = null;
        }else {
            mARecorder.stopRecordAndFile();
            mElapsedMillis = (System.currentTimeMillis() - mStartingTimeMillis);
            Toast.makeText(this, getString(R.string.toast_recording_finish) + " " + mFilePath, Toast.LENGTH_LONG).show();

            //remove notification
            if (mIncrementTimerTask != null) {
                mIncrementTimerTask.cancel();
                mIncrementTimerTask = null;
            }

            mARecorder = null;
        }

        try {
            mDatabase.addRecording(mFileName, mFilePath, mElapsedMillis);

        } catch (Exception e){
            Log.e(LOG_TAG, "exception", e);
        }
    }

    private void startTimer() {
        mTimer = new Timer();
        mIncrementTimerTask = new TimerTask() {
            @Override
            public void run() {
                mElapsedSeconds++;
                if (onTimerChangedListener != null)
                    onTimerChangedListener.onTimerChanged(mElapsedSeconds);
                NotificationManager mgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                mgr.notify(1, createNotification());
            }
        };
        mTimer.scheduleAtFixedRate(mIncrementTimerTask, 1000, 1000);
    }

    //TODO:
    private Notification createNotification() {
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(getApplicationContext())
                        .setSmallIcon(R.drawable.ic_mic_white_36dp)
                        .setContentTitle(getString(R.string.notification_recording))
                        .setContentText(mTimerFormat.format(mElapsedSeconds * 1000))
                        .setOngoing(true);

        mBuilder.setContentIntent(PendingIntent.getActivities(getApplicationContext(), 0,
                new Intent[]{new Intent(getApplicationContext(), MainActivity.class)}, 0));

        return mBuilder.build();
    }
}
