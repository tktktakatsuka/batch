import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

public class ReceResultMain {
    private static FirebaseRealtimeDBManager manager;

    public static void main(String[] args) throws InterruptedException {
        // DB接続

        manager = new FirebaseRealtimeDBManager();
        List<String> joList = Config.TARGET_JO_LIST;

        for (String joName : joList) {
            List<String> dateList = Config.TARGET_DATE_LIST;
            for (String date : dateList) {
                new ReceResultMain().scrapingAndInsert(manager, date, joName);
            }
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
                    Elements contentElements = doc.getElementsByClass("resulttable").select("tbody").select("td");
                    String raceTitle = doc.getElementsByClass("raceTitle").text();
                    String hassouTime = doc.getElementsByClass("classCourseSyokin").text();

                    int j = 0;
                    for (int i = 0; i < 5; i++) {
                        if (!contentElements.isEmpty()) {
                            // DB処理 データをインサート
                            Map<String, Object> raceData = manager.setData(date, kaisaijo, String.valueOf(raceSuu),
                                    contentElements.get(j).text(), contentElements.get(j + 1).text(),
                                    contentElements.get(j + 2).text(), contentElements.get(j + 3).text(),
                                    contentElements.get(j + 4).text(), contentElements.get(j + 5).text(),
                                    contentElements.get(j + 6).text(), contentElements.get(j + 7).text(),
                                    contentElements.get(j + 8).text(), contentElements.get(j + 9).text(),
                                    contentElements.get(j + 10).text(), contentElements.get(j + 11).text(),
                                    contentElements.get(j + 12).text(), contentElements.get(j + 13).text(),
                                    contentElements.get(j + 14).text(), raceTitle, hassouTime);
                            FirebaseRealtimeDBManager.insertData("raceResult" + "/" + kaisaijo + "/", raceData);
                        }
                        j = j + 15;
                        // 会員登録分の情報をskip
                        if (i == 0) {
                            j = j + 1;
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
