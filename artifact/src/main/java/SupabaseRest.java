import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
    // 送り直すまでの待ち時間（秒）。この回数だけ送り直す
    private static final long[] RETRY_WAIT_SECONDS = {5, 15, 45};
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
        send(HttpRequest.newBuilder(URI.create(url + "/rest/v1/" + table + "?" + eqQuery(eq))).DELETE(), "DELETE " + table);
    }

    private static String eqQuery(Map<String, String> eq) {
        StringJoiner query = new StringJoiner("&");
        for (Map.Entry<String, String> e : eq.entrySet()) {
            query.add(e.getKey() + "=eq." + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return query.toString();
    }

    /** eq 条件に一致する行の列を更新する */
    public void update(String table, Map<String, String> eq, Map<String, Object> values) throws IOException, InterruptedException {
        if (dryRun) {
            System.out.println("[dry-run] UPDATE " + table + " " + eq + " " + values);
            return;
        }
        send(HttpRequest.newBuilder(URI.create(url + "/rest/v1/" + table + "?" + eqQuery(eq)))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(gson.toJson(values), StandardCharsets.UTF_8)),
                "UPDATE " + table);
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
                .timeout(Duration.ofSeconds(60))
                .build();
        // Supabase が一時的に 504 Gateway Timeout などを返すことがあるので、間をあけて何度か送り直す
        for (int attempt = 1; ; attempt++) {
            String error;
            boolean retryable;
            try {
                HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = res.statusCode();
                if (status / 100 == 2) return;
                error = what + " failed (" + status + "): " + res.body();
                // 4xx（429 以外）は送り直しても同じ結果になる
                retryable = status / 100 == 5 || status == 429;
            } catch (IOException e) {
                error = what + " failed: " + e;
                retryable = true;
            }
            if (!retryable || attempt > RETRY_WAIT_SECONDS.length) throw new IOException(error);
            long wait = RETRY_WAIT_SECONDS[attempt - 1];
            System.err.println(error + " → " + wait + "秒後に送り直します（" + attempt + "回目）");
            Thread.sleep(wait * 1000);
        }
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isBlank();
    }
}
