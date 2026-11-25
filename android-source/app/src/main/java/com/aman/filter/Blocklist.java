package com.aman.filter;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/** On-device suffix blocklist. Nothing here touches the network. */
public class Blocklist {
    private final Set<String> builtin = new HashSet<>();
    private Set<String> customBlocked = new HashSet<>();
    private Set<String> customAllowed = new HashSet<>();
    private final Context ctx;

    public Blocklist(Context ctx) { this.ctx = ctx.getApplicationContext(); }

    public int builtinCount() { return builtin.size(); }

    public void load() throws Exception {
        builtin.clear();
        InputStream raw = ctx.getAssets().open("blocklist.txt.gz");
        BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(raw)));
        String line;
        while ((line = r.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty()) builtin.add(line);
        }
        r.close();
        reloadCustom();
    }

    public void reloadCustom() {
        SharedPreferences p = ctx.getSharedPreferences("lists", Context.MODE_PRIVATE);
        customBlocked = new HashSet<>(p.getStringSet("blocked", new HashSet<String>()));
        customAllowed = new HashSet<>(p.getStringSet("allowed", new HashSet<String>()));
    }

    private static boolean suffixIn(String host, Set<String> set) {
        String h = host;
        while (true) {
            if (set.contains(h)) return true;
            int dot = h.indexOf('.');
            if (dot < 0) return false;
            h = h.substring(dot + 1);
        }
    }

    /** True if this hostname should be blocked. Allow list always wins. */
    public boolean isBlocked(String host) {
        if (host == null || host.isEmpty()) return false;
        if (suffixIn(host, customAllowed)) return false;
        if (suffixIn(host, customBlocked)) return true;
        return suffixIn(host, builtin);
    }
}
