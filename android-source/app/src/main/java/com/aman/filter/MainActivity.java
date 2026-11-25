package com.aman.filter;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import java.time.LocalDate;

public class MainActivity extends Activity {
    private TextView statusText, statsText, streakNumber;
    private Button toggleButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        statusText = findViewById(R.id.statusText);
        statsText = findViewById(R.id.statsText);
        streakNumber = findViewById(R.id.streakNumber);
        toggleButton = findViewById(R.id.toggleButton);
        toggleButton.setOnClickListener(v -> toggle());
        findViewById(R.id.manageSites).setOnClickListener(v ->
                startActivity(new Intent(this, ListsActivity.class)));
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void toggle() {
        boolean running = getSharedPreferences("stats", MODE_PRIVATE).getBoolean("running", false);
        if (running) {
            startService(new Intent(this, FilterVpnService.class).setAction(FilterVpnService.ACTION_STOP));
        } else {
            Intent prepare = VpnService.prepare(this);
            if (prepare != null) {
                startActivityForResult(prepare, 1);
            } else {
                startService(new Intent(this, FilterVpnService.class).setAction(FilterVpnService.ACTION_START));
            }
        }
        statusText.postDelayed(this::refresh, 800);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1 && resultCode == RESULT_OK) {
            startService(new Intent(this, FilterVpnService.class).setAction(FilterVpnService.ACTION_START));
        }
        statusText.postDelayed(this::refresh, 800);
    }

    private void refresh() {
        SharedPreferences stats = getSharedPreferences("stats", MODE_PRIVATE);
        boolean running = stats.getBoolean("running", false);
        statusText.setText(running ? "Filtering is ON" : "Filtering is OFF");
        statusText.setTextColor(running ? 0xFF1A7F4B : 0xFF8A8A8A);
        toggleButton.setText(running ? "Pause" : "Start");
        // Streak grounded in actual VPN uptime: persisted flag alone is not
        // trusted (process kill/reboot may skip onDestroy), the heartbeat is.
        Streak.State st = Streak.compute(running, FilterVpnService.IS_RUNNING,
                stats.getLong("heartbeat", 0), android.os.SystemClock.elapsedRealtime(),
                stats.getString("vpn_since", null), LocalDate.now());
        if (running && !st.alive) {
            // stale persisted state (killed/rebooted): clean it up
            stats.edit().putBoolean("running", false).remove("vpn_since").apply();
            running = false;
            statusText.setText("Filtering is OFF");
            statusText.setTextColor(0xFF8A8A8A);
            toggleButton.setText("Start");
        }
        streakNumber.setText(String.valueOf(st.days));
        statsText.setText("Blocked so far: " + stats.getLong("blocked", 0)
                + " of " + stats.getLong("queries", 0) + " checked"
                + "\nBuilt-in list: 90,304 domains");
    }
}
