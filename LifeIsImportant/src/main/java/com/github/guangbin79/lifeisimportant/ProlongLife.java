package com.github.guangbin79.lifeisimportant;

import android.app.Notification;
import android.content.Context;

public class ProlongLife {
    private ProlongLifeService service;

    private ProlongLife() {
        service = null;
    }

    public static ProlongLife create(Context context) {
        ProlongLife prolongLife = new ProlongLife();
        prolongLife.service = ProlongLifeService.create(context);

        return prolongLife;
    }

    public synchronized void lock(Notification notification) {
        service.lock(notification);
    }

    public synchronized void unlock() {
        service.unlock();
    }

    public long getLifeCount() {
        return service.getLifeCount();
    }
}
