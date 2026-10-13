package firebase;

import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.*;

// import javafx.scene.control.CheckBox;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class FirebaseRealtimeDBManager {

    private static DatabaseReference database;

    // Firebase初期化
    public FirebaseRealtimeDBManager() {
        try {
            // ★ すでに初期化済みなら再初期化しない
            if (FirebaseApp.getApps().isEmpty()) {
                FileInputStream serviceAccount = new FileInputStream(
                        "C:\\work\\batch\\artifact\\raceresult-c4af3-firebase-adminsdk-fmu17-0a792a675c.json");

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .setDatabaseUrl("https://raceresult-c4af3-default-rtdb.firebaseio.com/")
                        .build();

                FirebaseApp.initializeApp(options);
            }

            // データベース参照の取得
            database = FirebaseDatabase.getInstance().getReference();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * データを作成・追加する
     * 
     * @param path Firebase Realtime Databaseのパス
     * @param map  格納するデータ (Map形式)
     */
    // データを挿入するメソッド
    public static void insertData(String path, Object map) {
        // Supabaseに挿入する実装へ置き換え
        // SUPABASE_URL と SUPABASE_KEY を環境変数 or プロパティファイルで指定してください
        Properties props = loadProperties();
        String supabaseUrl = System.getenv("SUPABASE_URL");
        if (supabaseUrl == null || supabaseUrl.isEmpty()) supabaseUrl = props.getProperty("supabase.url");
        String supabaseKey = System.getenv("SUPABASE_KEY");
        if (supabaseKey == null || supabaseKey.isEmpty()) supabaseKey = props.getProperty("supabase.key");
        // ユーザーの access token を環境変数またはプロパティから取得できるようにする
        String supabaseAuthToken = System.getenv("SUPABASE_AUTH_TOKEN");
        if (supabaseAuthToken == null || supabaseAuthToken.isEmpty()) supabaseAuthToken = props.getProperty("supabase.auth_token");

        if (supabaseUrl == null || supabaseUrl.isEmpty() || supabaseKey == null || supabaseKey.isEmpty()) {
            System.err.println("SUPABASE_URL or SUPABASE_KEY not set in env or supabase.properties. Falling back to Firebase insertion.");
            try {
                ApiFuture<Void> future = database.child(path).push().setValueAsync(map);
                future.get();
                System.out.println("Data inserted successfully at: " + path);
            } catch (Exception e) {
                System.err.println("Error inserting data to Firebase: " + e.getMessage());
            }
            return;
        }

        // path を分解してテーブル名と追加カラムを取得する (例: "raceStatistics/kaisaijo/芝")
        String[] parts = path.split("/");
        if (parts.length == 0) {
            System.err.println("Invalid path: " + path);
            return;
        }
        String table = parts[0];
        // プロパティにマッピングがあれば使用 (例: supabase.table.raceResult=race_trends)
        String mapped = props.getProperty("supabase.table." + table);
        if ((mapped == null || mapped.isEmpty())) mapped = props.getProperty("supabase.table." + table.toLowerCase());
        if ((mapped == null || mapped.isEmpty())) mapped = props.getProperty("supabase.table." + toSnakeCase(table));
        if (mapped != null && !mapped.isEmpty()) {
            table = mapped;
        }

        // map は Map<String,Object> と想定
        Map<String, Object> payloadMap = new HashMap<>();
        if (map instanceof Map) {
            //noinspection unchecked
            payloadMap.putAll((Map<String, Object>) map);
        } else {
            payloadMap.put("value", map == null ? null : map.toString());
        }

        if (parts.length >= 2) {
            payloadMap.put("kaisaijo", parts[1]);
        }
        if (parts.length >= 3) {
            payloadMap.put("cose", parts[2]);
        }

        // 指定テーブル向けに送信フィールドをフィルタ
        String columnsProp = props.getProperty("supabase.table_columns." + table);
        java.util.Set<String> allowed = null;
        if (columnsProp != null && !columnsProp.isEmpty()) {
            allowed = new java.util.HashSet<>();
            for (String c : columnsProp.split("\\s*,\\s*")) {
                allowed.add(c.trim());
            }
        }

        Map<String, Object> filtered = new HashMap<>();
        for (Map.Entry<String, Object> e : payloadMap.entrySet()) {
            String key = e.getKey();
            Object val = e.getValue();
            // 特別マッピング
            String keySnake;
            if ("tyaku".equals(key)) {
                keySnake = "rank";
            } else {
                keySnake = toSnakeCase(key);
            }

            if (allowed == null) {
                // 許可リストがない場合は全て snake_case に変換して送る
                filtered.put(keySnake, val);
            } else {
                if (allowed.contains(keySnake)) {
                    filtered.put(keySnake, val);
                }
            }
        }

        String json = toJson(filtered);

        try {
            URL url = new URL(supabaseUrl + "/rest/v1/" + table);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            if (supabaseKey != null && !supabaseKey.isEmpty()) {
                conn.setRequestProperty("apikey", supabaseKey);
            }
            if (supabaseAuthToken != null && !supabaseAuthToken.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + supabaseAuthToken);
            } else if (supabaseKey != null && !supabaseKey.isEmpty()) {
                // フォールバック: key を Bearer トークンとして使う（publishable key 等）
                conn.setRequestProperty("Authorization", "Bearer " + supabaseKey);
            }
            conn.setRequestProperty("Prefer", "return=representation");
            // スキーマ指定（デフォルト public）。プロパティ supabase.schema で上書き可能
            String schema = props.getProperty("supabase.schema", "public");
            conn.setRequestProperty("Accept-Profile", schema);
            conn.setRequestProperty("Content-Profile", schema);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = json.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int code = conn.getResponseCode();
            BufferedReader br;
            if (code >= 200 && code < 300) {
                br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
            } else {
                br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "utf-8"));
            }
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }

            if (code >= 200 && code < 300) {
                System.out.println("Supabase insert succeeded: " + response.toString());
            } else {
                System.err.println("Supabase insert failed (code=" + code + "): " + response.toString());
            }

        } catch (Exception e) {
            System.err.println("Error inserting data to Supabase: " + e.getMessage());
        }
    }

    /**
     * Supabase に指定日・開催場のデータが既にあるか確認し、column の値の一覧を返す。
     * 重複挿入を防ぐために使う。取得に失敗した場合は null を返す（呼び出し側で処理を中止する）。
     *
     * @param table  例: race_results
     * @param date   kaisaibi (yyyyMMdd)
     * @param jo     kaisaijo (例: 東京)
     * @param column 取得するカラム (例: race_no)
     */
    public static java.util.Set<String> fetchExistingValues(String table, String date, String jo, String column) {
        Properties props = loadProperties();
        String supabaseUrl = System.getenv("SUPABASE_URL");
        if (supabaseUrl == null || supabaseUrl.isEmpty()) supabaseUrl = props.getProperty("supabase.url");
        String supabaseKey = System.getenv("SUPABASE_KEY");
        if (supabaseKey == null || supabaseKey.isEmpty()) supabaseKey = props.getProperty("supabase.key");
        String supabaseAuthToken = System.getenv("SUPABASE_AUTH_TOKEN");
        if (supabaseAuthToken == null || supabaseAuthToken.isEmpty()) supabaseAuthToken = props.getProperty("supabase.auth_token");
        if (supabaseUrl == null || supabaseUrl.isEmpty() || supabaseKey == null || supabaseKey.isEmpty()) {
            System.err.println("SUPABASE_URL or SUPABASE_KEY not set. Cannot check existing data.");
            return null;
        }
        try {
            String cs = java.nio.charset.StandardCharsets.UTF_8.name();
            String query = "select=" + java.net.URLEncoder.encode(column, cs)
                    + "&kaisaibi=eq." + java.net.URLEncoder.encode(date, cs)
                    + "&kaisaijo=eq." + java.net.URLEncoder.encode(jo, cs);
            URL url = new URL(supabaseUrl + "/rest/v1/" + table + "?" + query);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("apikey", supabaseKey);
            String bearer = (supabaseAuthToken != null && !supabaseAuthToken.isEmpty()) ? supabaseAuthToken : supabaseKey;
            conn.setRequestProperty("Authorization", "Bearer " + bearer);
            conn.setRequestProperty("Accept-Profile", props.getProperty("supabase.schema", "public"));
            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, "utf-8"))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            if (code < 200 || code >= 300) {
                System.err.println("Supabase select failed (code=" + code + "): " + sb);
                return null;
            }
            java.util.Set<String> values = new java.util.HashSet<>();
            for (com.google.gson.JsonElement el : com.google.gson.JsonParser.parseString(sb.toString()).getAsJsonArray()) {
                com.google.gson.JsonElement v = el.getAsJsonObject().get(column);
                if (v != null && !v.isJsonNull()) values.add(v.getAsString());
            }
            return values;
        } catch (Exception e) {
            System.err.println("Error checking existing data in Supabase: " + e.getMessage());
            return null;
        }
    }

    // シンプルな Map -> JSON シリアライザ（値は文字列として扱う）
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            String k = e.getKey();
            Object v = e.getValue();
            sb.append('"').append(escapeJson(k)).append('"').append(":");
            if (v == null) {
                sb.append("null");
            } else {
                // 値は文字列としてエスケープして出力
                sb.append('"').append(escapeJson(v.toString())).append('"');
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private static String toSnakeCase(String s) {
        if (s == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else if (c == '-') {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // supabase.properties をクラスパスからロード
    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream is = FirebaseRealtimeDBManager.class.getClassLoader().getResourceAsStream("supabase.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException e) {
            // 無視: ファイルがない場合は環境変数を使う
        }
        return props;
    }

    // データを取得するメソッド
    public void getRaceResults(String raceId) {
        database.child("raceResults").child(raceId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    System.out.println("Race Result: " + snapshot.getValue());
                } else {
                    System.out.println("No data found for raceId: " + raceId);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                System.err.println("Error: " + error.getMessage());
            }
        });
    }

    /**
     * 条件付きデータの取得
     * 
     * @param path     Firebase Realtime Databaseのパス
     * @param childKey 検索条件のキー
     * @param value    検索する値
     * @param listener データ取得時のコールバック
     */
    public static void queryData(String path, String childKey, String value, ValueEventListener listener) {
        Query query = database.child(path).orderByChild(childKey).equalTo(value);
        query.addListenerForSingleValueEvent(listener);
    }

    public void getRaceTrendsKyakusitu(String kaisaibi, String raceNo, String kaisaijo) {
        FirebaseRealtimeDBManager.queryData("raceTrends" + "/" + kaisaijo, "kaisaibi", kaisaibi,
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        int nigeKyakusituCounter = 0;
                        int senkouKyakusituCounter = 0;
                        int oikomiKyakusituCounter = 0;
                        int sasiKyakusituCounter = 0;

                        int danigeKyakusituCounter = 0;
                        int dasenkouKyakusituCounter = 0;
                        int daoikomiKyakusituCounter = 0;
                        int dasasiKyakusituCounter = 0;

                        HashMap<String, String> map = new HashMap<>();
                        HashMap<String, String> damap = new HashMap<>();
                        for (DataSnapshot childSnapshot : snapshot.getChildren()) {
                            String sRaceNo = childSnapshot.child("raceNum").getValue(String.class);
                            String sKaisaijo = childSnapshot.child("kaisaijo").getValue(String.class);
                            String sKaisaibi = childSnapshot.child("kaisaibi").getValue(String.class);

                            // Rで開催場所が一致している場合にのみ処理
                            if (kaisaijo.equals(sKaisaijo) && sKaisaibi.equals(kaisaibi)) {
                                String sZyouken = childSnapshot.child("raceDistance").getValue(String.class);
                                String sbabaCondition = childSnapshot.child("raceCondition").getValue(String.class);
                                String uma1Kyakusitu = childSnapshot.child("uma1Kyakusitu").getValue(String.class);
                                String uma2Kyakusitu = childSnapshot.child("uma2Kyakusitu").getValue(String.class);
                                String uma3Kyakusitu = childSnapshot.child("uma3Kyakusitu").getValue(String.class);

                                if (sZyouken.contains("芝")) {
                                    if (uma1Kyakusitu.contains("逃げ")) {
                                        nigeKyakusituCounter++;
                                    } else if (uma1Kyakusitu.contains("先行")) {
                                        senkouKyakusituCounter++;
                                    } else if (uma1Kyakusitu.contains("追込")) {
                                        oikomiKyakusituCounter++;
                                    } else if (uma1Kyakusitu.contains("差し")) {
                                        sasiKyakusituCounter++;
                                    }

                                    if (uma2Kyakusitu.contains("逃げ")) {
                                        nigeKyakusituCounter++;
                                    } else if (uma2Kyakusitu.contains("先行")) {
                                        senkouKyakusituCounter++;
                                    } else if (uma2Kyakusitu.contains("追込")) {
                                        oikomiKyakusituCounter++;
                                    } else if (uma2Kyakusitu.contains("差し")) {
                                        sasiKyakusituCounter++;
                                    }

                                    if (uma3Kyakusitu.contains("逃げ")) {
                                        nigeKyakusituCounter++;
                                    } else if (uma3Kyakusitu.contains("先行")) {
                                        senkouKyakusituCounter++;
                                    } else if (uma3Kyakusitu.contains("追込")) {
                                        oikomiKyakusituCounter++;
                                    } else if (uma3Kyakusitu.contains("差し")) {
                                        sasiKyakusituCounter++;
                                    }

                                }

                                if (sZyouken.contains("ダ")) {
                                    if (uma1Kyakusitu.contains("逃げ")) {
                                        danigeKyakusituCounter++;
                                    } else if (uma1Kyakusitu.contains("先行")) {
                                        dasenkouKyakusituCounter++;
                                    } else if (uma1Kyakusitu.contains("追込")) {
                                        daoikomiKyakusituCounter++;
                                    } else if (uma1Kyakusitu.contains("差し")) {
                                        dasasiKyakusituCounter++;
                                    }

                                    if (uma2Kyakusitu.contains("逃げ")) {
                                        danigeKyakusituCounter++;
                                    } else if (uma2Kyakusitu.contains("先行")) {
                                        dasenkouKyakusituCounter++;
                                    } else if (uma2Kyakusitu.contains("追込")) {
                                        daoikomiKyakusituCounter++;
                                    } else if (uma2Kyakusitu.contains("差し")) {
                                        dasasiKyakusituCounter++;
                                    }

                                    if (uma3Kyakusitu.contains("逃げ")) {
                                        danigeKyakusituCounter++;
                                    } else if (uma3Kyakusitu.contains("先行")) {
                                        dasenkouKyakusituCounter++;
                                    } else if (uma3Kyakusitu.contains("追込")) {
                                        daoikomiKyakusituCounter++;
                                    } else if (uma3Kyakusitu.contains("差し")) {
                                        dasasiKyakusituCounter++;
                                    }

                                }

                            }

                        }
                        map.put("date", kaisaibi);
                        map.put("cose", "芝");
                        map.put("nige", String.valueOf(nigeKyakusituCounter));
                        map.put("senkou", String.valueOf(senkouKyakusituCounter));
                        map.put("sasi", String.valueOf(sasiKyakusituCounter));
                        map.put("oikomi", String.valueOf(oikomiKyakusituCounter));

                        damap.put("date", kaisaibi);
                        damap.put("cose", "ダ");
                        damap.put("nige", String.valueOf(danigeKyakusituCounter));
                        damap.put("senkou", String.valueOf(dasenkouKyakusituCounter));
                        damap.put("sasi", String.valueOf(dasasiKyakusituCounter));
                        damap.put("oikomi", String.valueOf(daoikomiKyakusituCounter));

                        // finalMap.put("ダ", damap);
                        // insertData("raceStatistics" + "/" + kaisaijo + "/" + "ダ", (Object) damap);
                        insertData("raceStatistics" + "/" + kaisaijo + "/" + "芝", (Object) map);
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        System.err.println("Query failed: " + error.getMessage());
                    }
                });
    }

    public Map<String, Object> setData(String kaisaibi, String kaisaijo, String raceNo, String tyaku,
            String waku, String horseNumber, String horseName, String age, String weight,
            String jockey, String popular, String winOdds, String time, String tyakusa,
            String tuukazyun, String nobori, String tyoukyousi, String horseWeight, String raceTitle,
            String hassouTime) {
        Map<String, Object> raceData = new HashMap<>();
        raceData.put("kaisaibi", kaisaibi);
        raceData.put("kaisaijo", kaisaijo);
        raceData.put("raceNo", raceNo);
        raceData.put("tyaku", tyaku);
        raceData.put("waku", waku);
        raceData.put("horseNumber", horseNumber);
        raceData.put("horseName", horseName);
        raceData.put("age", age);
        raceData.put("weight", weight);
        raceData.put("jockey", jockey);
        raceData.put("popular", popular);
        raceData.put("winOdds", winOdds);
        raceData.put("time", time);
        raceData.put("tyakusa", tyakusa);
        raceData.put("tuukazyun", tuukazyun);
        raceData.put("nobori", nobori);
        raceData.put("tyoukyousi", tyoukyousi);
        raceData.put("horseWeight", horseWeight);
        raceData.put("raceTitle", raceTitle);
        raceData.put("hassouTime", hassouTime);
        return raceData;
    }

    public Map<String, Object> setHassouData(String kaisaibi, String raceNo, String raceTitle, String hassouTime,
            String kaisaijo) {
        Map<String, Object> hassouData = new HashMap<>();
        hassouData.put("kaisaibi", kaisaibi);
        hassouData.put("raceNo", raceNo);
        hassouData.put("raceTitle", raceTitle);
        hassouData.put("hassouTime", hassouTime);
        hassouData.put("kaisaijo", kaisaijo);
        return hassouData;
    }

    public static void deleteDataByDate(String path, String targetDate) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        database.child(path).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    System.out.println("指定パスにデータが存在しません: " + path);
                    latch.countDown();
                    return;
                }

                for (DataSnapshot child : snapshot.getChildren()) {
                    // 日付キー名を Firebase に合わせる ("date" または "kaisaibi")
                    String childDate = child.child("date").getValue(String.class);
                    if (childDate != null && childDate.equals(targetDate)) {
                        child.getRef().removeValue((error, ref) -> {
                            if (error == null) {
                                System.out.println("削除成功: " + ref.getKey());
                            } else {
                                System.err.println("削除失敗: " + error.getMessage());
                            }
                        });
                    }
                }
                latch.countDown();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                System.err.println("削除クエリキャンセル: " + error.getMessage());
                latch.countDown();
            }
        });

        latch.await(); // 削除完了まで待機
        System.out.println("削除処理終了");
    }

}
