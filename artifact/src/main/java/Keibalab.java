import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * keibalab.jp からページを取得する。
 * レースID は yyyyMMdd + 競馬場コード2桁 + レース番号2桁（例: 202610170511 = 2026/10/17 東京11R）。
 */
public class Keibalab {

    private static final String BASE = "https://www.keibalab.jp/db/race/";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";
    // 相手のサーバーに負担をかけないよう、1ページごとに待つ
    private static final long INTERVAL_MS = 1500;

    /** 競馬場コード → 競馬場名 */
    public static final Map<String, String> COURSES = new LinkedHashMap<>();
    static {
        COURSES.put("01", "札幌");
        COURSES.put("02", "函館");
        COURSES.put("03", "福島");
        COURSES.put("04", "新潟");
        COURSES.put("05", "東京");
        COURSES.put("06", "中山");
        COURSES.put("07", "中京");
        COURSES.put("08", "京都");
        COURSES.put("09", "阪神");
        COURSES.put("10", "小倉");
    }

    private static long lastFetch = 0;

    public static synchronized Document fetch(String url) throws IOException {
        long wait = lastFetch + INTERVAL_MS - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            return Jsoup.connect(url).userAgent(USER_AGENT).timeout(30_000).get();
        } finally {
            lastFetch = System.currentTimeMillis();
        }
    }

    /** その日に開催がある競馬場コードを返す（開催が無ければ空） */
    public static TreeSet<String> courseCodesOn(String date) throws IOException {
        Document day = fetch(BASE + date + "/");
        Pattern p = Pattern.compile("/db/race/" + date + "(\\d{2})\\d{2}/");
        TreeSet<String> codes = new TreeSet<>();
        Matcher m = p.matcher(day.html());
        while (m.find()) {
            if (COURSES.containsKey(m.group(1))) codes.add(m.group(1));
        }
        return codes;
    }

    public static String raceUrl(String date, String courseCode, int raceNo) {
        return BASE + date + courseCode + String.format("%02d", raceNo) + "/";
    }

    public static String entriesUrl(String date, String courseCode, int raceNo) {
        return raceUrl(date, courseCode, raceNo) + "syutsuba.html";
    }

    /**
     * 馬名の先頭の「(外)」「(地)」「[父]」などの印を外す。
     * 出馬表（印なし）と結果（印あり）で同じ馬を照合できるようにするため。
     */
    public static String horseName(String s) {
        if (s == null) return null;
        String t = s.trim().replaceFirst("^[\\(（\\[［][^\\)）\\]］]{1,2}[\\)）\\]］]\\s*", "");
        return t.isEmpty() ? null : t;
    }

    /** "-" や空欄を null にする（枠順確定前の枠・馬番など） */
    public static String valueOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() || t.equals("-") || t.equals("…") ? null : t;
    }
}
