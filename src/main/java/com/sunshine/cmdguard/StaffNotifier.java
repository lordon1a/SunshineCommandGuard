package com.sunshine.cmdguard;

import net.kyori.adventure.text.Component;

/** Sends a message to online staff. Implemented by the main class. */
public interface StaffNotifier {

    /** Sends a pre-rendered message to every online staff member. */
    void notifyStaff(Component message);
}
