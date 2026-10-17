import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 開催日の単勝オッズを keibalab から取得して、race_entries の win_odds / popular を更新する。
 * 開催日に30分ごとに動かす想定。発走から時間がたったレースはオッズが確定しているので取りに行かない。
 *
 * 使い方: odds [yyyyMMdd] [--dry-run]（日付を省略すると今日）
 */
public class RaceOddsJob {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    // 発走後もしばらくはオッズが動く（締切・確定まで）ので、この分数までは取りに行く
    private static final int MINUTES_AFTER_START = 10;

    public static void main(String[] args) throws Exception {
        boolean dryRun = false;
        String date = null;
        for (String a : args) {
            if (a.equals("--dry-run")) dryRun = true;
            else date = a;
        }
        boolean today = date == null || date.equals(LocalDate.now(JST).format(YMD));
        if (date == null) date = LocalDate.now(JST).format(YMD);
        LocalTime now = LocalTime.now(JST);

        SupabaseRest db = new SupabaseRest(dryRun);
        TreeSet<String> codes = Keibalab.courseCodesOn(date);
        if (codes.isEmpty()) {
            System.out.println(date + ": 開催がありません");
            return;
        }
        List<String> failed = new ArrayList<>();
        int updatedRaces = 0;
        for (String code : codes) {
            String course = Keibalab.COURSES.get(code);
            for (int raceNo = 1; raceNo <= 12; raceNo++) {
                WinOdds odds;
                try {
                    odds = Keibalab.winOdds(date, code, raceNo);
                } catch (IOException e) {
                    System.err.println(date + " " + course + raceNo + "R: オッズを取得できませんでした (" + e.getMessage() + ")");
                    failed.add(course + raceNo + "R");
                    continue;
                }
                if (odds.isEmpty()) continue;
                if (today && finished(odds.postTime, now)) continue;
                try {
                    updateRace(db, date, course, raceNo, odds);
                    updatedRaces++;
                } catch (IOException e) {
                    System.err.println(date + " " + course + raceNo + "R: 更新に失敗しました (" + e.getMessage() + ")");
                    failed.add(course + raceNo + "R");
                }
            }
        }
        System.out.println(date + ": " + updatedRaces + "レースのオッズを更新しました");
        if (!failed.isEmpty()) {
            System.err.println("更新できなかったレース: " + String.join(", ", failed));
            System.exit(1);
        }
    }

    /** 発走から MINUTES_AFTER_START 分以上たっていれば true。発走時刻が読めなければ false（取りに行く） */
    static boolean finished(String postTime, LocalTime now) {
        if (postTime == null || !postTime.matches("\\d{4}")) return false;
        LocalTime start = LocalTime.of(Integer.parseInt(postTime.substring(0, 2)), Integer.parseInt(postTime.substring(2)));
        return now.isAfter(start.plusMinutes(MINUTES_AFTER_START));
    }

    /** 出走表に載っている馬番ごとにオッズを書き込む（1頭1回の更新） */
    private static void updateRace(SupabaseRest db, String date, String course, int raceNo, WinOdds odds)
            throws IOException, InterruptedException {
        String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
        for (int number = 1; number <= 18; number++) {
            String win = odds.oddsOf(String.valueOf(number));
            if (win == null) continue;
            Map<String, String> key = new LinkedHashMap<>();
            key.put("kaisaibi", date);
            key.put("kaisaijo", course);
            key.put("race_no", String.valueOf(raceNo));
            key.put("horse_number", String.valueOf(number));
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("win_odds", win);
            values.put("popular", odds.popularityOf(String.valueOf(number)));
            values.put("odds_updated_at", now);
            db.update("race_entries", key, values);
        }
    }
}
