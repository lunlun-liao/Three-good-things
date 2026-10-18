package com.lunlun.threegoodthings;

import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.json.*;

/** Atomic, app-private storage. Backup schema is shared with the web version. */
final class JournalStore {
    static final String[] DEFAULT_CATEGORIES = {"生活小確幸", "家人／朋友", "工作／學習", "健康／照顧自己", "自己的進步", "其他"};
    static final int MAX_BYTES = 20 * 1024 * 1024;
    static final LocalDate MIN_DATE = LocalDate.of(1900, 1, 1), MAX_DATE = LocalDate.of(2100, 12, 31);
    static final class Entry {
        final String id, text, reflection, category;
        final LocalDate date;
        Entry(String id, LocalDate date, String text, String reflection, String category) {
            this.id = id; this.date = date; this.text = text; this.reflection = reflection; this.category = category;
        }
        JSONObject json() throws JSONException {
            return new JSONObject().put("id", id).put("date", date.toString()).put("text", text).put("reflection", reflection).put("category", category);
        }
    }
    private final AtomicFile file;
    private List<Entry> entries = new ArrayList<>();
    final boolean readable;
    JournalStore(File path) {
        file = new AtomicFile(path);
        boolean ok = true;
        if (file.getBaseFile().exists() || new File(path + ".bak").exists()) {
            try (InputStream input = file.openRead()) { entries = validate(new JSONArray(readText(input))); }
            catch (Exception error) { ok = false; }
        }
        readable = ok;
    }
    List<Entry> entries() { return new ArrayList<>(entries); }
    static String trim(String text) { return text.replaceAll("^[\\s\\p{Z}\uFEFF]+|[\\s\\p{Z}\uFEFF]+$", ""); }
    static boolean validDate(LocalDate date) { return date != null && !date.isBefore(MIN_DATE) && !date.isAfter(MAX_DATE); }
    private static String string(JSONObject object, String key) throws JSONException {
        Object value = object.get(key);
        if (!(value instanceof String)) throw new JSONException("Invalid field");
        return (String)value;
    }
    static List<Entry> validate(JSONArray array) throws Exception {
        if (array.length() > 50000) throw new IOException("最多可儲存 50,000 筆紀錄。");
        Set<String> ids = new HashSet<>();
        List<Entry> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.getJSONObject(i);
            String id = string(object, "id"), rawDate = string(object, "date"), text = string(object, "text"), reflection = string(object, "reflection"), category = string(object, "category");
            LocalDate date = LocalDate.parse(rawDate);
            if (id.isEmpty() || id.length() > 100 || !ids.add(id) || !validDate(date) || !date.toString().equals(rawDate) || trim(text).isEmpty() || text.length() > 2000 || reflection.length() > 2000 || trim(category).isEmpty() || category.length() > 40) throw new IOException("紀錄格式不正確。");
            result.add(new Entry(id, date, trim(text), trim(reflection), trim(category)));
        }
        return result;
    }
    static JSONArray array(List<Entry> data) throws JSONException {
        JSONArray result = new JSONArray();
        for (Entry entry : data) result.put(entry.json());
        return result;
    }
    void save(List<Entry> next) throws Exception {
        if (!readable) throw new IOException("無法讀取原有資料，暫停寫入以保護紀錄。");
        List<Entry> checked = validate(array(next));
        byte[] bytes = array(checked).toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES - 1024) throw new IOException("資料超過 20 MB，請先備份並整理紀錄。");
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(bytes);
            file.finishWrite(output);
        } catch (Exception error) { if (output != null) file.failWrite(output); throw error; }
        entries = checked;
    }
    String backup() throws Exception {
        if (!readable) throw new IOException("無法讀取原有紀錄。");
        return new JSONObject().put("app", "three-good-things").put("version", 1).put("exportedAt", Instant.now().toString()).put("entries", array(entries)).toString();
    }
    static List<Entry> readBackup(String text) throws Exception {
        JSONObject object = new JSONObject(text);
        if (!"three-good-things".equals(object.opt("app")) || !(object.opt("version") instanceof Number) || ((Number)object.get("version")).doubleValue() != 1.0) throw new IOException("請選擇「三件好事」版本 1 的 JSON 備份。");
        return validate(object.getJSONArray("entries"));
    }
    static List<Entry> merge(List<Entry> current, List<Entry> incoming) throws Exception {
        Set<String> ids = new HashSet<>();
        List<Entry> next = new ArrayList<>(current);
        for (Entry entry : current) ids.add(entry.id);
        for (Entry entry : incoming) if (ids.add(entry.id)) next.add(entry);
        return validate(array(next));
    }
    static String readText(InputStream input) throws IOException {
        if (input == null) throw new IOException("無法開啟檔案。");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > MAX_BYTES) throw new IOException("備份檔案不可超過 20 MB。");
            output.write(buffer, 0, count);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }
}
