package com.mercan.fuzulbutce;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import org.json.*;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class ImportActivity extends Activity {
    private static final int REQ_IMPORT = 8101;
    private static final String PREF = "fuzul_budget";
    private static final String KEY = "plan";
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(24, 63, 95));

        String saved = getSharedPreferences(PREF, MODE_PRIVATE).getString(KEY, null);
        if (saved != null && !saved.trim().isEmpty()) {
            openMain();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(26), dp(20), dp(26));
        root.setBackgroundColor(Color.rgb(245,247,250));

        TextView title = text("Fuzul Bütçe", 26, Color.rgb(31,41,55), true);
        root.addView(title);
        root.addView(text("İlk kurulum", 14, Color.rgb(15,118,110), true), top(6));
        root.addView(text("Plan dosyanı bir kez içe aktar. Sonrasında uygulama verileri telefonda saklar.", 15, Color.rgb(107,114,128), false), top(14));

        Button pick = button("Plan dosyasını seç");
        pick.setOnClickListener(v -> pickFile());
        root.addView(pick, top(22));

        Button paste = button("JSON metnini yapıştır");
        paste.setOnClickListener(v -> pasteJson());
        root.addView(paste, top(10));

        status = text("", 13, Color.rgb(185,28,28), false);
        root.addView(status, top(16));

        setContentView(root);
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/json", "text/plain", "application/octet-stream", "application/x-json"
        });
        startActivityForResult(i, REQ_IMPORT);
    }

    private void pasteJson() {
        final EditText input = new EditText(this);
        input.setMinLines(8);
        input.setGravity(android.view.Gravity.TOP);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setHint("{ \"contract\": ..., \"months\": [...] }");

        new AlertDialog.Builder(this)
                .setTitle("Plan JSON metni")
                .setView(input)
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("İçe aktar", (d, w) -> importRaw(input.getText().toString()))
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT || resultCode != RESULT_OK || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        try {
            final int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try { getContentResolver().takePersistableUriPermission(uri, flags); } catch (Exception ignored) {}

            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) throw new IOException("Dosya açılamadı");
            String raw = readAll(in);
            importRaw(raw);
        } catch (Exception e) {
            showError("Dosya okunamadı", e);
        }
    }

    private void importRaw(String raw) {
        try {
            if (raw == null) throw new IllegalArgumentException("Dosya boş");
            raw = raw.replace("\uFEFF", "").trim();

            int first = raw.indexOf('{');
            int last = raw.lastIndexOf('}');
            if (first < 0 || last <= first) {
                throw new JSONException("JSON başlangıcı bulunamadı");
            }
            if (first > 0 || last < raw.length() - 1) raw = raw.substring(first, last + 1);

            JSONObject obj = new JSONObject(raw);
            JSONObject contract = obj.optJSONObject("contract");
            JSONArray months = obj.optJSONArray("months");
            if (contract == null) throw new JSONException("contract bölümü yok");
            if (months == null || months.length() == 0) throw new JSONException("months bölümü yok");
            if (!contract.has("amount") || !contract.has("allocationDate")) {
                throw new JSONException("Sözleşme alanları eksik");
            }

            getSharedPreferences(PREF, MODE_PRIVATE)
                    .edit()
                    .putString(KEY, obj.toString())
                    .apply();

            Toast.makeText(this, "Plan başarıyla yüklendi", Toast.LENGTH_LONG).show();
            openMain();
        } catch (Exception e) {
            showError("Plan dosyası geçerli değil", e);
        }
    }

    private void showError(String title, Exception e) {
        String detail = e.getClass().getSimpleName();
        if (e.getMessage() != null && !e.getMessage().trim().isEmpty()) detail += ": " + e.getMessage();
        if (status != null) status.setText(title + "\n" + detail);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(detail + "\n\nİstersen 'JSON metnini yapıştır' seçeneğini de kullanabilirsin.")
                .setPositiveButton("Tamam", null)
                .show();
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        in.close();
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return v;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(15);
        return b;
    }

    private LinearLayout.LayoutParams top(int n) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(n);
        return p;
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }
}
