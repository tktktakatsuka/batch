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

    public static void main(String[] args) throws NoSuchAlgorithmException, KeyManagementException {
        // DB接続
        manager = new FirebaseRealtimeDBManager();
        List<String> joList = Config.TARGET_JO_LIST;

        for (String joName : joList) {
            // List<String> dateList = WeekendDays.getPastWeekendsInCurrentMonth();
            String date = Config.TARGET_DATE_LIST;

            new RaceTrendsMain().scrapingAndInsert(manager, date, joName);

        }
    }

    private void scrapingAndInsert(FirebaseRealtimeDBManager dbManager, String date, String kaisaijo)
            throws NoSuchAlgorithmException, KeyManagementException {
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
                            trendsData.put("raceNum", elements.get(0).text());
                            trendsData.put("raceName", elements.get(1).text());
                            trendsData.put("raceDistance", elements.get(2).text());
                            trendsData.put("raceCondition", elements.get(3).text());
                            trendsData.put("uma1Ban", elements.get(4).text());
                            trendsData.put("uma1Name", elements.get(5).text());
                            trendsData.put("uma2Ban", elements.get(6).text());
                            trendsData.put("uma2Name", elements.get(7).text());
                            trendsData.put("uma3Ban", elements.get(8).text());
                            trendsData.put("umaName", elements.get(9).text());
                            trendsData.put("pace", elements.get(10).text());
                            trendsData.put("uma1Kyakusitu", elements.get(12).text());
                            trendsData.put("uma2Kyakusitu", elements.get(14).text());
                            trendsData.put("uma3Kyakusitu", elements.get(16).text());
                            trendsData.put("uma1Jockey", elements.get(18).text());
                            trendsData.put("uma2Jockey", elements.get(20).text());
                            trendsData.put("uma3Jockey", elements.get(22).text());
                            trendsData.put("uma1tyokyosi", elements.get(24).text());
                            trendsData.put("uma2tyokyosi", elements.get(26).text());
                            trendsData.put("uma3tyokyosi", elements.get(28).text());
                            trendsData.put("uma1father", elements.get(30).text());
                            trendsData.put("uma2father", elements.get(32).text());
                            trendsData.put("uma3father", elements.get(34).text());
                            trendsData.put("uma1mather", elements.get(36).text());
                            trendsData.put("uma2mather", elements.get(38).text());
                            trendsData.put("uma3mather", elements.get(40).text());
                            trendsData.put("haraimodosiAre", elements.get(41).text());
                            trendsData.put("haraimodosiTansyo", elements.get(42).text());
                            trendsData.put("haraimodosiUmaren", elements.get(43).text());
                            trendsData.put("haraimodosi3Rentan", elements.get(44).text());
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

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

    }
}
