import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.jsoup.HttpStatusException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * 出馬表を keibalab から取得して Supabase の race_entries に登録する。
 *
 * 使い方: entries [yyyyMMdd ...] [--dry-run]
 *   日付を省略すると、今日から7日以内で開催がある日をすべて取得する。
 *   同じ開催日・競馬場の行は入れ直すので、枠順確定後や出走取消のあとに再実行してよい。
 */
public class RaceEntriesMain {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    public static void main(String[] args) throws Exception {
        boolean dryRun = false;
        List<String> dates = new ArrayList<>();
        for (String a : args) {
            if (a.equals("--dry-run")) dryRun = true;
            else dates.add(a);
        }
        if (dates.isEmpty()) {
            LocalDate today = LocalDate.now();
            for (int i = 0; i < 7; i++) dates.add(today.plusDays(i).format(YMD));
        }

        SupabaseRest db = new SupabaseRest(dryRun);
        // 取り込めなかった開催。最後にまとめて出し、失敗として終える（ワークフローが Issue で知らせる）
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
                try {
                    for (int raceNo = 1; raceNo <= 12; raceNo++) {
                        rows.addAll(scrapeRace(date, code, course, raceNo));
                    }
                } catch (IOException e) {
                    // 一部のレースだけで入れ直すと残りのレースの出馬表が消えるので、この開催は前のまま残す
                    System.err.println(date + " " + course + ": 取得できないレースがあったため登録しません (" + e.getMessage() + ")");
                    failed.add(date + " " + course);
                    continue;
                }
                if (rows.isEmpty()) {
                    System.out.println(date + " " + course + ": 出馬表がまだありません");
                    continue;
                }
                Map<String, String> key = new LinkedHashMap<>();
                key.put("kaisaibi", date);
                key.put("kaisaijo", course);
                try {
                    db.delete("race_entries", key);
                    db.insert("race_entries", rows);
                    System.out.println(date + " " + course + ": " + rows.size() + "頭を登録しました");
                } catch (IOException e) {
                    System.err.println(date + " " + course + ": 登録に失敗しました (" + e.getMessage() + ")");
                    failed.add(date + " " + course);
                }
            }
        }
        if (!failed.isEmpty()) {
            System.err.println("取り込めなかった開催（この日付で取り込み直してください）: " + String.join(", ", failed));
            System.exit(1);
        }
    }

    static List<Map<String, Object>> scrapeRace(String date, String code, String course, int raceNo) throws IOException {
        List<Map<String, Object>> rows = new ArrayList<>();
        Document doc;
        try {
            doc = Keibalab.fetch(Keibalab.entriesUrl(date, code, raceNo));
        } catch (HttpStatusException e) {
            // 12R まで無い開催などはページが無い
            return rows;
        } catch (IOException e) {
            throw new IOException(course + raceNo + "R: " + e.getMessage(), e);
        }
        Element table = doc.selectFirst("table[summary$=出馬表]");
        if (table == null) return rows;

        // 見出しの位置で列を探す（列の追加・並び替えに強くするため）
        Map<String, Integer> col = new HashMap<>();
        Elements headers = table.select("thead th");
        if (headers.isEmpty()) headers = table.select("tr").first().select("th,td");
        for (int i = 0; i < headers.size(); i++) col.put(headers.get(i).text().trim(), i);

        Element titleEl = doc.selectFirst(".raceTitle");
        Element infoEl = doc.selectFirst(".classCourseSyokin");
        String title = titleEl != null ? raceName(titleEl) : null;
        String info = infoEl != null ? infoEl.text().trim() : null;

        for (Element tr : table.select("tbody tr")) {
            Elements td = tr.select("td");
            Element nameCell = tr.selectFirst("td[data-hsnm]");
            if (nameCell == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("kaisaibi", date);
            row.put("kaisaijo", course);
            row.put("race_no", raceNo);
            row.put("race_title", title);
            row.put("race_info", info);
            row.put("waku", cell(td, col, "枠"));
            row.put("horse_number", cell(td, col, "馬"));
            row.put("horse_name", Keibalab.horseName(nameCell.attr("data-hsnm")));
            row.put("sex_age", cell(td, col, "性齢"));
            row.put("weight", cell(td, col, "斤量"));
            row.put("jockey", cell(td, col, "騎手"));
            row.put("trainer", cell(td, col, "調教師"));
            rows.add(row);
        }
        addOdds(rows, date, code, course, raceNo);
        return rows;
    }

    /**
     * 単勝オッズと人気を行に足す。発売前やオッズが取れないときは空のまま登録する
     * （一括登録では全行のキーを揃える必要があるので、空でも列は入れる）。
     */
    private static void addOdds(List<Map<String, Object>> rows, String date, String code, String course, int raceNo) {
        WinOdds odds = null;
        boolean drawn = rows.stream().anyMatch(r -> r.get("horse_number") != null);
        if (drawn) {
            try {
                odds = Keibalab.winOdds(date, code, raceNo);
            } catch (Exception e) {
                System.err.println(date + " " + course + raceNo + "R: オッズを取得できませんでした (" + e.getMessage() + ")");
            }
        }
        String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
        for (Map<String, Object> row : rows) {
            String number = (String) row.get("horse_number");
            String win = odds != null ? odds.oddsOf(number) : null;
            row.put("win_odds", win);
            row.put("popular", win != null ? odds.popularityOf(number) : null);
            row.put("odds_updated_at", win != null ? now : null);
        }
    }

    /** レース名だけを取り出す（条件や距離が同じ要素に入っている場合は除く） */
    private static String raceName(Element titleEl) {
        Element h = titleEl.selectFirst("h1, h2");
        String text = (h != null ? h : titleEl).text().trim();
        return text.isEmpty() ? null : text;
    }

    private static String cell(Elements td, Map<String, Integer> col, String header) {
        Integer i = col.get(header);
        if (i == null || i >= td.size()) return null;
        return Keibalab.valueOrNull(td.get(i).text());
    }
}
