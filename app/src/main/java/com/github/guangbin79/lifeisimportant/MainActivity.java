package com.github.guangbin79.lifeisimportant;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    protected ProlongLifeService mProlongLife = null;
    protected Notification mNotification = null;

    @RequiresApi(api = Build.VERSION_CODES.M)
    private boolean isIgnoringBatteryOptimizations() {
        boolean isIgnoring = false;
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            isIgnoring = powerManager.isIgnoringBatteryOptimizations(getPackageName());
        }
        return isIgnoring;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void requestIgnoreBatteryOptimizations() {
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, 0x1111);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 0x1111) {
            if (!isIgnoringBatteryOptimizations()) {
                requestIgnoreBatteryOptimizations();
            } else {
                mProlongLife = ProlongLifeService.create(getApplicationContext());

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Notification.Builder builder = new Notification.Builder(this, getPackageName())
                            .setOnlyAlertOnce(true)
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setContentTitle("LifeIsImportant")
                            .setContentText("惜命如金");

                    NotificationChannel notificationChannel = new NotificationChannel(getPackageName(), "LifeIsImportant", NotificationManager.IMPORTANCE_HIGH);
                    notificationChannel.enableLights(false);
                    notificationChannel.enableVibration(false);
                    notificationChannel.setVibrationPattern(new long[]{0});
                    notificationChannel.setSound(null, null);
                    NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    manager.createNotificationChannel(notificationChannel);
                    builder.setChannelId(notificationChannel.getId());
                    mNotification = builder.build();
                    mProlongLife.lock(mNotification);
                } else {
                    mProlongLife.lock(null);
                }

                Handler handler = new Handler(getMainLooper());
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        long count = mProlongLife.getLifeCount();
                        TextView lifeShow = findViewById(R.id.textView_LifeShow);
                        lifeShow.setText("Life!Life!!Life!!! " + count);
                        handler.postDelayed(this, 1000);
                    }
                }, 1000);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestIgnoreBatteryOptimizations();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mProlongLife.unlock();
    }
}