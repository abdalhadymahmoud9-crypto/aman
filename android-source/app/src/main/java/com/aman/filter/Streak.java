package com.aman.filter;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Streak = calendar days the filter VPN has been continuously running,
 * start day = day 1; pause or any stop resets it to 0.
 *
 * A persisted flag alone cannot prove the VPN is alive: onDestroy may be
 * skipped on kill/reboot, and a wall-clock heartbeat can still look "fresh"
 * if the UI opens moments after the process died or right after a reboot.
 * So "alive" requires ALL of:
 *   1. persisted running flag,
 *   2. live service marker in this process (FilterVpnService.IS_RUNNING;
 *      a killed process restarts with it false - this catches reboot and
 *      kill-just-before-reopen with no timeout window),
 *   3. a heartbeat written as SystemClock.elapsedRealtime() (monotonic,
 *      resets at boot) that is fresh and not from a previous boot.
 * The heartbeat additionally catches a wedged service thread in a live process.
 */
public class Streak {
    static final long HEARTBEAT_LIMIT_MS = 120_000;

    public static class State {
        public final boolean alive;
        public final long days;
        State(boolean alive, long days) { this.alive = alive; this.days = days; }
    }

    /**
     * @param runningPref        persisted running flag
     * @param serviceAlive       FilterVpnService.IS_RUNNING in the current process
     * @param heartbeatElapsedMs last heartbeat in elapsedRealtime() ms, <=0 if none
     * @param nowElapsedMs       current elapsedRealtime() ms
     * @param vpnSince           date the VPN tunnel actually started, null if none
     * @param today              current date
     */
    public static State compute(boolean runningPref, boolean serviceAlive,
                                long heartbeatElapsedMs, long nowElapsedMs,
                                String vpnSince, LocalDate today) {
        boolean heartbeatFresh = heartbeatElapsedMs > 0
                && heartbeatElapsedMs <= nowElapsedMs               // previous boot -> larger value
                && (nowElapsedMs - heartbeatElapsedMs) <= HEARTBEAT_LIMIT_MS;
        boolean alive = runningPref && serviceAlive && heartbeatFresh;
        if (!alive || vpnSince == null) return new State(false, 0);
        LocalDate start;
        try {
            start = LocalDate.parse(vpnSince);
        } catch (Exception e) {
            return new State(false, 0);
        }
        if (start.isAfter(today)) return new State(true, 1);
        return new State(true, ChronoUnit.DAYS.between(start, today) + 1);
    }
}
