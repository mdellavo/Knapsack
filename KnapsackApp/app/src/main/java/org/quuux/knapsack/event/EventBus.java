package org.quuux.knapsack.event;

import com.squareup.otto.Bus;

public class EventBus {

    private static Bus instance;

    public static Bus getInstance() {
        if (instance == null)
            instance = new Bus();
        return instance;
    }
}
