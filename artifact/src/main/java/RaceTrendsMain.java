import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.jsoup.Jsoup;
import org.jsoup.select.Elements;

import firebase.FirebaseRealtimeDBManager;

import org.jsoup.nodes.Document;

public class RaceTrendsMain {
    private static FirebaseRealtimeDBManager manager;
    // 接続できずに取り込めなかった開催（日付 競馬場）
    private static final java.util.List<String> FAILED = new java.util.ArrayList<>();

    public static void main(String[] args) throws NoSuchAlgorithmException, KeyManagementException {
        // DB接続
        manager = new FirebaseRealtimeDBManager();
        List<String> joList = Config.TARGET_JO_LIST;

        for (String joName : joList) {
            // List<String> dateList = WeekendDays.getPastWeekendsInCurrentMonth();
            String date = Config.TARGET_DATE_LIST;

            new RaceTrendsMain().scrapingAndInsert(manager, date, joName);

        }
        if (!FAILED.isEmpty()) {
            System.err.println("[race_trends] 取り込めなかった開催（この日付で取り込み直してください）: " + String.join(", ", FAILED));
            System.exit(1);
        }
    }

    private void scrapingAndInsert(FirebaseRealtimeDBManager dbManager, String date, String kaisaijo)
            throws NoSuchAlgorithmException, KeyManagementException {
        // 既に Supabase に入っていればスキップ（重複挿入防止）
        java.util.Set<String> existing = FirebaseRealtimeDBManager.fetchExistingValues("race_trends", date, kaisaijo, "kaisaijo");
        if (existing == null) {
            System.err.println("[race_trends] " + date + " " + kaisaijo + ": 既存データを確認できないため中止します");
            return;
        }
        if (!existing.isEmpty()) {
            System.out.println("[race_trends] " + date + " " + kaisaijo + ": 登録済みのためスキップ");
            return;
        }
        try {
            String jocode = "";
            switch (kaisaijo) {
                case "東京":
                    jocode = "5";
                    break;
                case "京都":
                    jocode = "8";
                    break;
                case "新潟":
                    jocode = "4";
                    break;
                case "福島":
                    jocode = "3";
                    break;
                case "中山":
                    jocode = "6";
                    break;
                case "中京":
                    jocode = "7";
                    break;
                case "阪神":
                    jocode = "9";
                    break;
                case "小倉":
                    jocode = "10";
                    break;
                case "函館":
                    jocode = "2";
                    break;
                case "札幌":
                    jocode = "1";
                    break;
            }
            // Webページを取得
            Document doc = null;
            String url1 = "";
            if (jocode.length() == 1) {
                url1 = "https://www.keibalab.jp/db/race/" + date + "0" + jocode + "/" + "trend.html";
            } else if (jocode.length() == 2) {
                url1 = "https://www.keibalab.jp/db/race/" + date + jocode + "/" + "trend.html";
            }

            // SSLContext を作成
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] { new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }

                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            } }, new SecureRandom());

            doc = Jsoup.connect(url1).sslSocketFactory(sslContext.getSocketFactory()).get();

            if (doc != null) {
                // クラス名が"content"の要素を抽出
                Elements contentElements = doc.getElementsByClass("DbTable").select("tbody").select("tbody")
                        .select("tbody").select("tr");

                for (int i = 0; i < 12; i++) {
                    if (!contentElements.isEmpty()) {
                        Map<String, Object> trendsData = new HashMap<>();
                        Thread.sleep(100);
                        try {

                            Elements elements = contentElements.get(i).select("td");
                            trendsData.put("kaisaibi", date);
                            trendsData.put("kaisaijo", kaisaijo);
                            trendsData.put("racenum", elements.get(0).text());
                            trendsData.put("racename", elements.get(1).text());
                            trendsData.put("racedistance", elements.get(2).text());
                            trendsData.put("racecondition", elements.get(3).text());
                            trendsData.put("uma1ban", elements.get(4).text());
                            trendsData.put("uma1name", elements.get(5).text());
                            trendsData.put("uma2ban", elements.get(6).text());
                            trendsData.put("uma2name", elements.get(7).text());
                            trendsData.put("uma3ban", elements.get(8).text());
                            trendsData.put("umaname", elements.get(9).text());
                            trendsData.put("pace", elements.get(10).text());
                            trendsData.put("uma1kyakusitu", elements.get(12).text());
                            trendsData.put("uma2kyakusitu", elements.get(14).text());
                            trendsData.put("uma3kyakusitu", elements.get(16).text());
                            trendsData.put("uma1jockey", elements.get(18).text());
                            trendsData.put("uma2jockey", elements.get(20).text());
                            trendsData.put("uma3jockey", elements.get(22).text());
                            trendsData.put("uma1tyokyosi", elements.get(24).text());
                            trendsData.put("uma2tyokyosi", elements.get(26).text());
                            trendsData.put("uma3tyokyosi", elements.get(28).text());
                            trendsData.put("uma1father", elements.get(30).text());
                            trendsData.put("uma2father", elements.get(32).text());
                            trendsData.put("uma3father", elements.get(34).text());
                            trendsData.put("uma1mather", elements.get(36).text());
                            trendsData.put("uma2mather", elements.get(38).text());
                            trendsData.put("uma3mather", elements.get(40).text());
                            trendsData.put("haraimodosiare", elements.get(41).text());
                            trendsData.put("haraimodositansyo", elements.get(42).text());
                            trendsData.put("haraimodosiumaren", elements.get(43).text());
                            trendsData.put("haraimodosi3rentan", elements.get(44).text());
                            trendsData.put("banusi1", elements.get(46).text());
                            trendsData.put("banusi2", elements.get(48).text());
                            trendsData.put("banusi3", elements.get(50).text());
                            trendsData.put("seisann1", elements.get(52).text());
                            trendsData.put("seisann2", elements.get(54).text());
                            trendsData.put("seisann3", elements.get(56).text());
                            // DB処理 データをインサート
                            FirebaseRealtimeDBManager.insertData("raceTrends" + "/" + kaisaijo + "/", trendsData);

                        } catch (Exception e) {
                            // なにもしない
                            FirebaseRealtimeDBManager.insertData("raceTrends" + "/" + kaisaijo + "/", trendsData);
                        }

                    }
                }
            }

        } catch (org.jsoup.HttpStatusException e) {
            // その日に開催していない競馬場はページが無い（404）ので、失敗にはしない
            System.out.println("[race_trends] " + date + " " + kaisaijo + ": 傾向のページがありません (" + e.getStatusCode() + ")");
        } catch (IOException | InterruptedException e) {
            // 接続できなかった（keibalab に弾かれたなど）。最後にまとめて失敗として終える
            System.err.println("[race_trends] " + date + " " + kaisaijo + ": 取得できませんでした (" + e.getMessage() + ")");
            FAILED.add(date + " " + kaisaijo);
        }

    }
}
