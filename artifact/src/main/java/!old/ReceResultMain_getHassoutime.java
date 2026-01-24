import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

public class ReceResultMain_getHassoutime {
    private static FirebaseRealtimeDBManager manager;

    public static void main(String[] args) throws InterruptedException {
        // DB接続

        manager = new FirebaseRealtimeDBManager();
        List<String> joList = Config.TARGET_JO_LIST;

        for (String joName : joList) {
            String date = Config.TARGET_DATE_LIST;
            new ReceResultMain_getHassoutime().scrapingAndInsert(manager, date, joName);
        }
    }

    /**
     * @param dbManager
     * @param date
     * @param kaisaijo
     * @throws InterruptedException
     */
    private void scrapingAndInsert(FirebaseRealtimeDBManager dbManager, String date, String kaisaijo)
            throws InterruptedException {
        try {
            for (int raceSuu = 1; raceSuu < 13; raceSuu++) {
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
                String url2 = "";
                if (jocode.length() == 1) {
                    url1 = "https://www.keibalab.jp/db/race/" + date + "0" + jocode + "0" + raceSuu + "/";
                    url2 = "https://www.keibalab.jp/db/race/" + date + "0" + jocode + raceSuu + "/";
                } else if (jocode.length() == 2) {
                    url1 = "https://www.keibalab.jp/db/race/" + date + jocode + "0" + raceSuu + "/";
                    url2 = "https://www.keibalab.jp/db/race/" + date + jocode + raceSuu + "/";
                }

                if (raceSuu < 10) {
                    doc = Jsoup.connect(url1).get();
                } else {
                    doc = Jsoup.connect(url2).get();
                }

                if (doc != null) {
                    // クラス名が"content"の要素を抽出
                    Elements raceListElements = doc.getElementsByClass("raceList");
                    Elements std11Elements = raceListElements.select(".std11"); // std11クラスの要素を全て取得

                    Map<String, Object> hassouTimeMap = new HashMap<>();
                    int raceNum = 1;
                    for (org.jsoup.nodes.Element elem : std11Elements) {
                        hassouTimeMap.put(String.valueOf(raceNum), elem.text());
                        raceNum++;
                    }
                    // 日付をマップに追加
                    hassouTimeMap.put("date", date);
                    // 開催場をマップに追加
                    hassouTimeMap.put("kaisaijo", kaisaijo);
                    // データをFirebaseに挿入
                    FirebaseRealtimeDBManager.insertData("raceHassouTimeList" + "/" + kaisaijo + "/", hassouTimeMap);
                    System.out.println("Inserted data for " + kaisaijo + " on ");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
