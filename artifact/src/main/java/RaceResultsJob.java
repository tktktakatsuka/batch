import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * レース結果（全着順）を keibalab から取得して Supabase の race_results に登録する。
 *
 * 使い方: results [yyyyMMdd | yyyyMMdd-yyyyMMdd ...] [--dry-run]
 *   日付を省略すると、今日を含む直近4日分を取得する。
 *   同じ開催日・競馬場の行は入れ直すので、何度実行しても重複しない。
 */
public class RaceResultsJob {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    public static void main(String[] args) throws Exception {
        boolean dryRun = false;
        List<String> dates = new ArrayList<>();
        for (String a : args) {
            if (a.equals("--dry-run")) {
                dryRun = true;
            } else if (a.contains("-")) {
                LocalDate from = LocalDate.parse(a.split("-")[0], YMD);
                LocalDate to = LocalDate.parse(a.split("-")[1], YMD);
                for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) dates.add(d.format(YMD));
            } else {
                dates.add(a);
            }
        }
        if (dates.isEmpty()) {
            LocalDate today = LocalDate.now();
            for (int i = 3; i >= 0; i--) dates.add(today.minusDays(i).format(YMD));
        }

        SupabaseRest db = new SupabaseRest(dryRun);
        // 1つの開催で失敗しても残りは取り込み、最後に失敗した開催をまとめて知らせる
        List<String> failed = new ArrayList<>();
        for (String date : dates) {
            TreeSet<String> codes;
            try {
                codes = Keibalab.courseCodesOn(date);
            } catch (IOException e) {
                System.err.println(date + ": 開催の一覧を取得できませんでした (" + e.getMessage() + ")");
                failed.add(date);
                continue;
            }
            for (String code : codes) {
                String course = Keibalab.COURSES.get(code);
                List<Map<String, Object>> rows = new ArrayList<>();
                for (int raceNo = 1; raceNo <= 12; raceNo++) {
                    rows.addAll(scrapeRace(date, code, course, raceNo));
                }
                if (rows.isEmpty()) {
                    System.out.println(date + " " + course + ": 結果がまだありません");
                    continue;
                }
                Map<String, String> key = new LinkedHashMap<>();
                key.put("kaisaibi", date);
                key.put("kaisaijo", course);
                try {
                    db.delete("race_results", key);
                    db.insert("race_results", rows);
                    System.out.println(date + " " + course + ": " + rows.size() + "頭分の結果を登録しました");
                } catch (IOException e) {
                    System.err.println(date + " " + course + ": 登録に失敗しました (" + e.getMessage() + ")");
                    failed.add(date + " " + course);
                }
            }
        }
        if (!failed.isEmpty()) {
            System.err.println("登録できなかった開催（この日付で取り込み直してください）: " + String.join(", ", failed));
            System.exit(1);
        }
    }

    static List<Map<String, Object>> scrapeRace(String date, String code, String course, int raceNo) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Document doc;
        try {
            doc = Keibalab.fetch(Keibalab.raceUrl(date, code, raceNo));
        } catch (Exception e) {
            System.err.println(date + " " + course + raceNo + "R: 取得できませんでした (" + e.getMessage() + ")");
            return rows;
        }
        Element table = doc.selectFirst("table.resulttable");
        if (table == null) return rows;

        Map<String, Integer> col = new HashMap<>();
        Element headerRow = table.selectFirst("tr");
        Elements headers = headerRow.select("th,td");
        for (int i = 0; i < headers.size(); i++) col.put(headers.get(i).text().trim(), i);

        Element titleEl = doc.selectFirst(".raceTitle");
        Element infoEl = doc.selectFirst(".classCourseSyokin");
        String title = titleEl != null ? titleEl.text().trim() : null;
        String info = infoEl != null ? infoEl.text().trim() : null;

        for (Element tr : table.select("tr")) {
            if (tr == headerRow) continue;
            Elements td = tr.select("td");
            String horse = Keibalab.horseName(cell(td, col, "馬名"));
            if (horse == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("kaisaibi", date);
            row.put("kaisaijo", course);
            row.put("race_no", raceNo);
            row.put("race_title", title);
            row.put("rank", cell(td, col, "着"));
            row.put("waku", cell(td, col, "枠"));
            row.put("horse_number", cell(td, col, "馬"));
            row.put("horse_name", horse);
            row.put("age", cell(td, col, "性齢"));
            row.put("weight", cell(td, col, "斤量"));
            row.put("jockey", cell(td, col, "騎手"));
            row.put("popular", cell(td, col, "人"));
            row.put("win_odds", cell(td, col, "単勝"));
            row.put("time", cell(td, col, "タイム"));
            row.put("tyakusa", cell(td, col, "着差"));
            row.put("tuukazyun", cell(td, col, "通過順"));
            row.put("nobori", cell(td, col, "上り"));
            row.put("tyoukyousi", cell(td, col, "調教師"));
            row.put("horse_weight", cell(td, col, "馬体重"));
            row.put("hassou_time", info);
            rows.add(row);
        }
        return rows;
    }

    private static String cell(Elements td, Map<String, Integer> col, String header) {
        Integer i = col.get(header);
        if (i == null || i >= td.size()) return null;
        return Keibalab.valueOrNull(td.get(i).text());
    }
}
