package com.aman.filter;

import java.time.LocalDate;

public class TestStreak {
    static final long NOW = 10_000_000L; // elapsedRealtime: ms since boot
    static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    public static void main(String[] a) {
        // fresh start today, service alive, heartbeat just written: day 1
        check("start today = day 1", Streak.compute(true, true, NOW, NOW, "2026-11-25", TODAY).days == 1);
        // 5th calendar day, still alive
        check("5th calendar day", Streak.compute(true, true, NOW, NOW, "2026-11-21", TODAY).days == 5);
        // kill just before UI open: process restarted, static marker false, heartbeat 5s old -> DEAD (no false ON)
        check("kill just before open -> dead", !Streak.compute(true, false, NOW - 5_000, NOW, "2026-11-21", TODAY).alive
                && Streak.compute(true, false, NOW - 5_000, NOW, "2026-11-21", TODAY).days == 0);
        // reboot 30s ago with pre-boot wall-fresh heartbeat: new process, static false -> DEAD
        check("reboot within 120s window -> dead", !Streak.compute(true, false, NOW, NOW, "2026-11-21", TODAY).alive);
        // reboot edge: somehow static restored but heartbeat from previous boot (larger elapsed than now) -> DEAD
        check("pre-boot heartbeat value -> dead", !Streak.compute(true, true, 900_000_000L, NOW, "2026-11-21", TODAY).alive);
        // service thread wedged in live process: static true, heartbeat stale -> DEAD
        check("wedged thread, stale heartbeat -> dead", !Streak.compute(true, true, NOW - 600_000, NOW, "2026-11-21", TODAY).alive);
        // paused: 0
        check("paused -> 0", Streak.compute(false, false, NOW, NOW, "2026-11-25", TODAY).days == 0);
        // restart after pause -> day 1
        check("restart after pause -> day 1", Streak.compute(true, true, NOW, NOW, "2026-11-25", TODAY).days == 1);
        // heartbeat boundary
        check("heartbeat at limit -> alive", Streak.compute(true, true, NOW - 120_000, NOW, "2026-11-25", TODAY).alive);
        check("heartbeat past limit -> dead", !Streak.compute(true, true, NOW - 120_001, NOW, "2026-11-25", TODAY).alive);
        // missing/corrupt persisted date: safe 0
        check("missing since -> 0", Streak.compute(true, true, NOW, NOW, null, TODAY).days == 0);
        check("corrupt date -> 0", Streak.compute(true, true, NOW, NOW, "not-a-date", TODAY).days == 0);
        // future start date: day 1, never negative
        check("future start -> day 1", Streak.compute(true, true, NOW, NOW, "2026-11-26", TODAY).days == 1);
        // start failure path: flag false, no marker -> 0
        check("start failure -> 0", Streak.compute(false, false, 0, NOW, null, TODAY).days == 0);
        System.out.println("ALL STREAK TESTS DONE");
    }
    static void check(String n, boolean ok) { System.out.println((ok?"PASS":"FAIL")+" | "+n); if (!ok) System.exit(1); }
}
