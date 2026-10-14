import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringJoiner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Supabase の REST API で行をまとめて削除・登録する。
 * 定期実行では環境変数 SUPABASE_URL と SUPABASE_SECRET_KEY（secret キー）を使う。
 * 環境変数が無いときは supabase.properties の url / key / auth_token を使う（手元での実行用）。
 */
public class SupabaseRest {

    private final String url;
    private final String apiKey;
    private final String bearer;
    private final boolean dryRun;
    private final HttpClient http = HttpClient.newHttpClient();
    // 一括登録では全行のキーが揃っている必要があるので、空の値も null として送る
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    public SupabaseRest(boolean dryRun) {
        this.dryRun = dryRun;
        Properties props = new Properties();
        try (InputStream is = SupabaseRest.class.getClassLoader().getResourceAsStream("supabase.properties")) {
            if (is != null) props.load(new java.io.InputStreamReader(is, StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }

        String envUrl = System.getenv("SUPABASE_URL");
        String secret = System.getenv("SUPABASE_SECRET_KEY");
        this.url = notEmpty(envUrl) ? envUrl : props.getProperty("supabase.url", "").trim();
        if (notEmpty(secret)) {
            this.apiKey = secret;
            this.bearer = secret;
        } else {
            this.apiKey = props.getProperty("supabase.key", "").trim();
            String token = props.getProperty("supabase.auth_token", "").trim();
            this.bearer = notEmpty(token) ? token : apiKey;
        }
        if (!dryRun && (url.isEmpty() || apiKey.isEmpty())) {
            throw new IllegalStateException("SUPABASE_URL / SUPABASE_SECRET_KEY が設定されていません");
        }
    }

    /** eq 条件に一致する行を削除する */
    public void delete(String table, Map<String, String> eq) throws IOException, InterruptedException {
        if (dryRun) {
            System.out.println("[dry-run] DELETE " + table + " " + eq);
            return;
        }
        StringJoiner query = new StringJoiner("&");
        for (Map.Entry<String, String> e : eq.entrySet()) {
            query.add(e.getKey() + "=eq." + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        send(HttpRequest.newBuilder(URI.create(url + "/rest/v1/" + table + "?" + query)).DELETE(), "DELETE " + table);
    }

    /** 行をまとめて登録する */
    public void insert(String table, List<Map<String, Object>> rows) throws IOException, InterruptedException {
        if (rows.isEmpty()) return;
        if (dryRun) {
            System.out.println("[dry-run] INSERT " + table + " " + rows.size() + "行");
            for (Map<String, Object> r : rows) System.out.println("  " + r);
            return;
        }
        String body = gson.toJson(rows);
        send(HttpRequest.newBuilder(URI.create(url + "/rest/v1/" + table))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)), "INSERT " + table);
    }

    private void send(HttpRequest.Builder builder, String what) throws IOException, InterruptedException {
        HttpRequest request = builder
                .header("apikey", apiKey)
                .header("Authorization", "Bearer " + bearer)
                .header("Prefer", "return=minimal")
                .build();
        HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() / 100 != 2) {
            throw new IOException(what + " failed (" + res.statusCode() + "): " + res.body());
        }
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isBlank();
    }
}
