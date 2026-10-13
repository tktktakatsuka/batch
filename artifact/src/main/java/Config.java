import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 実行対象の日付と開催場。
 *
 * 環境変数（またはシステムプロパティ）で上書きできる。
 *   TARGET_DATE : yyyyMMdd（未指定なら日本時間の当日）
 *   TARGET_JO   : カンマ区切りの開催場名（未指定なら全10場）
 * 開催のない場はページが取得できずスキップされる。
 */
public class Config {
    public static final List<String> ALL_JO_LIST = Arrays.asList(
            "札幌", "函館", "福島", "新潟", "東京", "中山", "中京", "京都", "阪神", "小倉");

    public static final List<String> TARGET_JO_LIST = resolveJoList();
    public static final String TARGET_DATE_LIST = resolveDate();

    private static String setting(String name) {
        String v = System.getProperty(name);
        if (v == null || v.isBlank()) v = System.getenv(name);
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    private static String resolveDate() {
        String v = setting("TARGET_DATE");
        if (v != null) return v;
        return LocalDate.now(ZoneId.of("Asia/Tokyo")).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    private static List<String> resolveJoList() {
        String v = setting("TARGET_JO");
        if (v == null) return ALL_JO_LIST;
        List<String> list = new ArrayList<>();
        for (String s : v.split("\\s*,\\s*")) {
            if (!s.isEmpty()) list.add(s);
        }
        return list;
    }
}
