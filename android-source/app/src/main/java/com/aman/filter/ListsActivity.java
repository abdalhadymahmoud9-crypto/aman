package com.aman.filter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ListsActivity extends Activity {
    private TextView blockListView, allowListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lists);
        blockListView = findViewById(R.id.blockListView);
        allowListView = findViewById(R.id.allowListView);
        findViewById(R.id.addBlock).setOnClickListener(v -> add(R.id.blockInput, "blocked"));
        findViewById(R.id.addAllow).setOnClickListener(v -> add(R.id.allowInput, "allowed"));
        blockListView.setOnClickListener(v -> removeDialog("blocked"));
        allowListView.setOnClickListener(v -> removeDialog("allowed"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void add(int inputId, String list) {
        EditText in = findViewById(inputId);
        String d = normalizeDomain(in.getText().toString());
        if (d == null) {
            Toast.makeText(this, "Not a valid domain (example: example.com). Nothing added.", Toast.LENGTH_LONG).show();
            return;
        }
        SharedPreferences p = getSharedPreferences("lists", MODE_PRIVATE);
        Set<String> set = new HashSet<>(p.getStringSet(list, new HashSet<>()));
        set.add(d);
        String other = list.equals("allowed") ? "blocked" : "allowed";
        Set<String> otherSet = new HashSet<>(p.getStringSet(other, new HashSet<>()));
        otherSet.remove(d);
        p.edit().putStringSet(list, set).putStringSet(other, otherSet).apply();
        in.setText("");
        startService(new Intent(this, FilterVpnService.class).setAction(FilterVpnService.ACTION_RELOAD));
        refresh();
    }

    private void removeDialog(String list) {
        SharedPreferences p = getSharedPreferences("lists", MODE_PRIVATE);
        ArrayList<String> items = new ArrayList<>(p.getStringSet(list, new HashSet<>()));
        if (items.isEmpty()) return;
        Collections.sort(items);
        String[] arr = items.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Remove from " + (list.equals("allowed") ? "allowed" : "blocked"))
                .setItems(arr, (d, which) -> {
                    Set<String> set = new HashSet<>(p.getStringSet(list, new HashSet<>()));
                    set.remove(arr[which]);
                    p.edit().putStringSet(list, set).apply();
                    startService(new Intent(this, FilterVpnService.class).setAction(FilterVpnService.ACTION_RELOAD));
                    refresh();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    static String normalizeDomain(String raw) {
        String d = raw.trim().toLowerCase();
        if (d.isEmpty()) return null;
        d = d.replaceAll("^[a-z][a-z0-9+.-]*://", "");
        d = d.split("[/?#]")[0];
        d = d.replaceAll(":\\d+$", "");
        if (d.startsWith("www.")) d = d.substring(4);
        if (!d.matches("(?!-)[a-z0-9-]{1,63}(?<!-)(\\.(?!-)[a-z0-9-]{1,63}(?<!-))+")) return null;
        return d;
    }

    private void refresh() {
        SharedPreferences lists = getSharedPreferences("lists", MODE_PRIVATE);
        Set<String> blocked = lists.getStringSet("blocked", new HashSet<>());
        Set<String> allowed = lists.getStringSet("allowed", new HashSet<>());
        blockListView.setText(blocked.isEmpty() ? "No custom blocked sites." : joinSorted(blocked));
        allowListView.setText(allowed.isEmpty() ? "No allowed sites." : joinSorted(allowed));
    }

    private static String joinSorted(Set<String> set) {
        ArrayList<String> items = new ArrayList<>(set);
        Collections.sort(items);
        StringBuilder sb = new StringBuilder();
        for (String s : items) sb.append(s).append('\n');
        return sb.toString().trim();
    }
}
