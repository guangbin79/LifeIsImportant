package com.github.guangbin79.lifeisimportant;

import android.app.Notification;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

public class ProlongLifeService extends Service {
    private Context context = null;
    private boolean isLock = false;
    private long lifeCount = 0;
    private Thread lifeCountThread = null;
    private ServiceConnection serviceConnection = null;
    private final ScreenActionReceiver screenActionReceiver = new ScreenActionReceiver();
    private long timeScreenOff = -1;
    private static final long RESET_TIME = 60;
    private static final String[] WEBSITE = {"https://www.baidu.com", "https://www.jd.com", "https://www.taobao.com"};

    public static ProlongLifeService create(Context context) {
        ProlongLifeService prolongLife = new ProlongLifeService();
        prolongLife.context = context.getApplicationContext();
        prolongLife.isLock = false;

        return prolongLife;
    }

    public void lock(final Notification notification) {
        lock_imp(notification);
    }

    private synchronized void lock_imp(final Notification notification) {
        if (isLock) {
            return;
        }
        isLock = true;

        serviceConnection = new ServiceConnection() {
            ILifeCountInterface lifeCountInterface = null;
            final Object lifeCountInterfaceLock = new Object();
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                lifeCountInterface = ILifeCountInterface.Stub.asInterface(iBinder);
                lifeCountThread = new Thread() {
                    @Override
                    public void run() {
                        while (!isInterrupted()) {
                            try {
                                synchronized (lifeCountInterfaceLock) {
                                    if (lifeCountInterface != null) {
                                        lifeCount = lifeCountInterface.count();
                                        if (lifeCount % RESET_TIME == timeScreenOff) {
                                            new Handler(context.getMainLooper()).post(() -> {
                                                unlock_imp(false);
                                                lock(notification);
                                            });
                                            return;
                                        }
                                    } else {
                                        return;
                                    }
                                }
                            } catch (RemoteException e) {
                                continue;
                            }

                            try {
                                sleep(1000);
                            } catch (InterruptedException e) {
                                return;
                            }
                        }
                    }
                };
                lifeCountThread.start();
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                synchronized (lifeCountInterfaceLock) {
                    lifeCountInterface = null;
                }
            }
        };
        Intent intent = new Intent("com.github.guangbin79.lifeisimportant.ILifeCountInterface")
                .setPackage(context.getPackageName());
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        intent = new Intent("com.github.guangbin79.lifeisimportant.hello")
                .setPackage(context.getPackageName())
                .putExtra("LifeCount", lifeCount)
                .putExtra("Notification", notification);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }

        screenActionReceiver.registerScreenActionReceiver(context, new ScreenActionReceiver.Listener() {
            @Override
            public void onLockscreen() {
                new Handler(context.getMainLooper()).post(() -> {
                    unlock_imp(false);
                    lock(notification);
                    timeScreenOff = getLifeCount() % RESET_TIME;
                });
            }

            @Override
            public void onUnlockscreen() {
                new Handler(context.getMainLooper()).post(() -> timeScreenOff = -1);
            }
        });
    }

    public void unlock() {
        unlock_imp(true);
    }

    private synchronized void unlock_imp(boolean bResetLifeCount) {
        if (!isLock) {
            return;
        }

        if (lifeCountThread != null && !lifeCountThread.isInterrupted()) {
            lifeCountThread.interrupt();
            lifeCountThread = null;
        }

        if (bResetLifeCount) {
            lifeCount = 0;
        }

        Intent intent = new Intent("com.github.guangbin79.lifeisimportant.hello")
                .setPackage(context.getPackageName());
        context.stopService(intent);

        context.unbindService(serviceConnection);
        serviceConnection = null;

        if (screenActionReceiver != null) {
            screenActionReceiver.unRegisterScreenActionReceiver();
        }

        isLock = false;
    }

    public long getLifeCount() {
        return lifeCount;
    }

    private WifiManager.WifiLock wifiLock = null;
    private Notification notification = null;
    private MediaPlayer mediaPlayer = null;
    private Thread audioThread = null;
    private Thread httpThread = null;
    private long callCount = 0;
    private final Object callCountLock = new Object();

    @Override
    public IBinder onBind(Intent intent) {
        return stub;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        WifiManager wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ProlongLife::WifiLock");
            wifiLock.acquire();
        }

        if (!isHuawei() && !isHonor()) {
            //华为、荣耀手机使用MediaPlayer保活时，会错误输出蓝牙播放状态，因此不使用MediaPlayer方式
            //MediaPlayer目前还未遇见过无法保活的，但AudioTrack在Nubia手机上无法保活，因此除华为荣耀外，其他手机继续使用MediaPlayer保活
            mediaPlayer = MediaPlayer.create(this, R.raw.loud);
            if (mediaPlayer != null) {
                mediaPlayer.setLooping(true);
                mediaPlayer.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
                mediaPlayer.setVolume(0, 0);
                mediaPlayer.start();
            }
        } else {
            audioThread = new Thread() {
                @Override
                public void run() {
                    playWithAudioTrack(this, R.raw.loud);
                }
            };
            audioThread.start();

            httpThread = new Thread() {
                @Override
                public void run() {
                    byte[] data = new byte[6];
                    int who = 0;
                    while (!isInterrupted()) {
                        try {
                            URLConnection urlConnection = new URL(WEBSITE[who]).openConnection();
                            urlConnection.connect();
                            InputStream inputStream = urlConnection.getInputStream();
                            while (inputStream != null && inputStream.read(data) >= 0);
                        } catch (Exception ignore) {
                            who = (who + 1) % 3;
                        }
                    }
                }
            };
            httpThread.start();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                intent == null || intent.getAction() == null) {
            return START_NOT_STICKY;
        }

        if (intent.getAction().equals("com.github.guangbin79.lifeisimportant.hello")) {
            synchronized (callCountLock) {
                callCount = intent.getLongExtra("LifeCount", 0);
            }

            notification = intent.getParcelableExtra("Notification");
            if (notification != null) {
                startForeground(android.os.Process.myPid(), notification);
            }

            return START_STICKY;
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }

        if (httpThread != null) {
            if (!httpThread.isInterrupted()) {
                httpThread.interrupt();
            }
            httpThread = null;
        }

        if (audioThread != null) {
            if (!audioThread.isInterrupted()) {
                audioThread.interrupt();
            }
            audioThread = null;
        }

        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
        }
        wifiLock = null;

        notification = null;
        stopForeground(true);
    }

    private void playWithAudioTrack(Thread thread, int resid) {
        InputStream is = getResources().openRawResource(resid);
        WaveHeader header = new WaveHeader();

        try {
            header.read(is);
        } catch (IOException e) {
            return;
        }

        byte[] music = new byte[header.getSampleRate() * header.getNumChannels() * header.getBitsPerSample() / 8 / 1000];

        AudioTrack at = new AudioTrack(
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                new AudioFormat.Builder()
                        .setSampleRate(header.getSampleRate())
                        .setChannelMask(header.getNumChannels() == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO)
                        .setEncoding(header.getBitsPerSample() == 16 ? AudioFormat.ENCODING_PCM_16BIT : AudioFormat.ENCODING_PCM_8BIT)
                        .build(),
                AudioTrack.getMinBufferSize(header.getSampleRate(),
                        header.getNumChannels() == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO,
                        header.getBitsPerSample() == 16 ? AudioFormat.ENCODING_PCM_16BIT : AudioFormat.ENCODING_PCM_8BIT),
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
        );

        at.play();

        while (!thread.isInterrupted()) {
            at.write(music, 0, 0);
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                return;
            }
        }

        at.stop();
        at.release();
    }

    private boolean isHuawei() {
        return false;
//        if (Build.BRAND == null) {
//            return false;
//        } else {
//            return Build.BRAND.toLowerCase(Locale.getDefault()).equals("huawei");
//        }
    }

    private boolean isHonor() {
        return false;
//        if (Build.BRAND == null) {
//            return false;
//        } else {
//            return Build.BRAND.toLowerCase(Locale.getDefault()).equals("honor");
//        }
    }

    private void authorization(long count) {
        if (count % 300 != 0) {
            return;
        }

        new Thread() {
            @Override
            public void run() {
                byte[] data = new byte[7];
                try {
                    HttpURLConnection urlConnection = (HttpURLConnection)new URL("https://guangbin79.github.io/fhyl-lii.html").openConnection();
                    urlConnection.connect();
                    if (urlConnection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                        return;
                    }
                    InputStream inputStream = urlConnection.getInputStream();
                    int length = 0;
                    if (inputStream != null) {
                        length = inputStream.read(data);
                    }
                    if (length >= 5 &&
                    new String(data, 0, 5).compareTo("break") == 0) {
                        synchronized (callCountLock) {
                            callCount = -1;
                        }
                        stopForeground(false);
                    }
                } catch (Exception ignore) {
                }
            }
        }.start();
    }

    private final ILifeCountInterface.Stub stub = new ILifeCountInterface.Stub() {
        @Override
        public long count() {
            synchronized (callCountLock) {
                if (callCount == -1) {
                    return 1;
                }
                callCount = ++callCount % Long.MAX_VALUE;
            }
            authorization(callCount);
            return callCount;
        }
    };
}