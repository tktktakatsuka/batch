import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.HttpStatusException;
import org.jsoup.nodes.Document;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 開催日の日中に5分ごとに動かし、アプリに出す当日の情報を Supabase に入れる。
 * アプリはこれを読むだけで、keibalab には取りに行かない（利用者が増えても keibalab への負担は増えない）。
 *
 *   発走前: 当日の出馬表（馬体重・取消・騎手・天気・馬場）と単勝・複勝オッズ → race_entries
 *   発走後: 全着順 → race_results、払い戻し → race_payouts
 *
 * 発走が近いレースほどこまめに取り、遠いレースはときどき取る（keibalab に負担をかけないため）。
 * 使い方: live [yyyyMMdd] [--dry-run]（日付を省略すると今日）。その日の出馬表が無ければ何もしない
 */
public class RaceLiveJob {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    private static final Pattern POST_TIME = Pattern.compile("(\\d{1,2}):(\\d{2})発走");

    // 発走のこの分数前からは、出馬表とオッズを毎回取る
    static final int CLOSE_MINUTES = 60;
    // それより前は、この分数ごとに取り直す
    static final int REFRESH_MINUTES = 30;
    // 発走からこの分数たったら結果を取りに行く。結果か払い戻しが出るまで毎回取る
    static final int RESULT_AFTER_MINUTES = 3;
    // 発走からこの分数たっても出なければ、あとは夜の取り込みにまかせる
    static final int RESULT_GIVE_UP_MINUTES = 120;

    /** 1レース分の、Supabase にある状態 */
    static final class Race {
        final String course;
        final int raceNo;
        LocalTime post;
        // 取り込んだときの race_info（枠順確定前は発走時刻が入っていない）
        String raceInfo;
        // 馬番 → race_entries の id
        final Map<String, Long> ids = new LinkedHashMap<>();
        // 馬名 → race_entries の id（枠順確定前に取り込んで馬番が空の行のため）
        final Map<String, Long> idsByName = new LinkedHashMap<>();
        OffsetDateTime oddsUpdated;
        OffsetDateTime cardUpdated;
        boolean hasResult;
        boolean hasPayouts;

        Race(String course, int raceNo) {
            this.course = course;
            this.raceNo = raceNo;
        }

        String label() {
            return course + raceNo + "R";
        }
    }

    public static void main(String[] args) throws Exception {
        boolean dryRun = false;
        String date = null;
        // 試すとき用: --at=yyyyMMddHHmm でその時刻に動いたことにする
        OffsetDateTime now = OffsetDateTime.now(JST);
        for (String a : args) {
            if (a.equals("--dry-run")) dryRun = true;
            else if (a.startsWith("--at=")) now = java.time.LocalDateTime.parse(a.substring(5),
                    DateTimeFormatter.ofPattern("yyyyMMddHHmm")).atZone(JST).toOffsetDateTime();
            else date = a;
        }
        if (date == null) date = now.toLocalDate().format(YMD);

        SupabaseRest db = new SupabaseRest(dryRun);
        Map<String, Race> races = loadRaces(db, date);
        if (races.isEmpty()) {
            System.out.println(date + ": 出馬表がないので何もしません");
            return;
        }

        List<String> failed = new ArrayList<>();
        int cards = 0, odds = 0, results = 0;
        for (Race race : races.values()) {
            String code = courseCode(race.course);
            if (code == null) continue;
            // 枠順確定前に取り込んだ出馬表には発走時刻が無いので、当日の出馬表のページから1回だけ読む
            if (race.post == null) {
                if (race.cardUpdated != null) continue;
                try {
                    saveCard(db, date, code, race, now);
                    cards++;
                } catch (IOException e) {
                    System.err.println(date + " " + race.label() + ": 取得できませんでした (" + e.getMessage() + ")");
                    failed.add(race.label());
                }
                if (race.post == null) continue;
            }
            OffsetDateTime post = LocalDate.parse(date, YMD).atTime(race.post).atZone(JST).toOffsetDateTime();
            long minutesToPost = java.time.Duration.between(now, post).toMinutes();
            try {
                if (minutesToPost <= -RESULT_AFTER_MINUTES) {
                    // 発走後: 結果と払い戻しが揃うまで取る
                    if ((race.hasResult && race.hasPayouts) || minutesToPost < -RESULT_GIVE_UP_MINUTES) continue;
                    if (saveResult(db, date, code, race)) results++;
                } else {
                    // 発走前: 近ければ毎回、遠ければときどき
                    if (due(race.cardUpdated, now, minutesToPost)) {
                        saveCard(db, date, code, race, now);
                        cards++;
                    }
                    if (due(race.oddsUpdated, now, minutesToPost)) {
                        if (saveOdds(db, date, code, race, now)) odds++;
                    }
                }
            } catch (IOException e) {
                System.err.println(date + " " + race.label() + ": 取得できませんでした (" + e.getMessage() + ")");
                failed.add(race.label());
                // keibalab に続けてつながらないなら、残りを試しても無駄
                if (failed.size() >= 3) break;
            }
        }
        System.out.println(date + ": 出馬表 " + cards + " / オッズ " + odds + " / 結果 " + results + " レースを更新しました");
        if (!failed.isEmpty()) {
            System.err.println("取得できなかったレース: " + String.join(", ", failed));
            System.exit(1);
        }
    }

    /** 発走が近い（CLOSE_MINUTES 以内）か、前に取ってから REFRESH_MINUTES 以上たっていれば取る */
    static boolean due(OffsetDateTime updated, OffsetDateTime now, long minutesToPost) {
        if (minutesToPost <= CLOSE_MINUTES) return true;
        return updated == null || updated.plusMinutes(REFRESH_MINUTES).isBefore(now);
    }

    /** その日の出馬表と、もう入っている結果・払い戻しを読む。キーは 競馬場+レース番号（発走順に並べる） */
    static Map<String, Race> loadRaces(SupabaseRest db, String date) throws IOException, InterruptedException {
        Map<String, Race> races = new TreeMap<>();
        JsonArray entries = db.select("race_entries",
                "select=id,kaisaijo,race_no,horse_number,horse_name,race_info,odds_updated_at,card_updated_at&kaisaibi=eq." + date);
        for (JsonElement e : entries) {
            JsonObject o = e.getAsJsonObject();
            String course = str(o, "kaisaijo");
            int raceNo = o.get("race_no").getAsInt();
            Race race = races.computeIfAbsent(key(course, raceNo), k -> new Race(course, raceNo));
            String number = str(o, "horse_number");
            if (number != null) race.ids.put(number, o.get("id").getAsLong());
            String horse = Keibalab.horseName(str(o, "horse_name"));
            if (horse != null) race.idsByName.put(horse, o.get("id").getAsLong());
            if (race.post == null) race.post = postTime(str(o, "race_info"));
            if (race.raceInfo == null) race.raceInfo = str(o, "race_info");
            race.oddsUpdated = latest(race.oddsUpdated, time(str(o, "odds_updated_at")));
            race.cardUpdated = latest(race.cardUpdated, time(str(o, "card_updated_at")));
        }
        if (races.isEmpty()) return races;
        for (JsonElement e : db.select("race_results", "select=kaisaijo,race_no&rank=eq.1&kaisaibi=eq." + date)) {
            Race race = races.get(key(str(e.getAsJsonObject(), "kaisaijo"), e.getAsJsonObject().get("race_no").getAsInt()));
            if (race != null) race.hasResult = true;
        }
        for (JsonElement e : db.select("race_payouts", "select=kaisaijo,race_no&bet_type=eq." + enc("単勝") + "&kaisaibi=eq." + date)) {
            Race race = races.get(key(str(e.getAsJsonObject(), "kaisaijo"), e.getAsJsonObject().get("race_no").getAsInt()));
            if (race != null) race.hasPayouts = true;
        }
        // 発走順に並べ直す（発走が近いレースから取る）
        Map<String, Race> byPost = new TreeMap<>();
        for (Race r : races.values()) {
            byPost.put((r.post != null ? r.post.toString() : "99:99") + key(r.course, r.raceNo), r);
        }
        return byPost;
    }

    /** 当日の出馬表から 馬体重・取消・騎手・枠・天気・馬場 を書き込む（1頭1回の更新） */
    private static void saveCard(SupabaseRest db, String date, String code, Race race, OffsetDateTime now)
            throws IOException, InterruptedException {
        Document doc;
        try {
            doc = Keibalab.fetch(Keibalab.entriesUrl(date, code, race.raceNo));
        } catch (HttpStatusException e) {
            return;
        }
        LivePages.Card card = LivePages.card(doc);
        // 発走時刻が分かったら race_info にも書く（アプリの出走表に発走時刻が出るように）
        String fixedInfo = null;
        if (race.post == null && card.postTime != null) {
            race.post = postTime(card.postTime + "発走");
            if (race.raceInfo != null && race.raceInfo.contains(" 発走")) fixedInfo = race.raceInfo.replace(" 発走", " " + card.postTime + "発走");
        }
        String at = now.withOffsetSameInstant(ZoneOffset.UTC).toString();
        for (LivePages.CardHorse h : card.horses.values()) {
            Long id = race.ids.get(h.number);
            if (id == null && h.name != null) id = race.idsByName.get(h.name);
            if (id != null) race.ids.put(h.number, id);
            if (id == null) continue;
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("horse_weight", h.weight);
            values.put("scratched", h.scratched);
            values.put("card_jockey", h.jockey);
            if (h.waku != null) values.put("waku", h.waku);
            values.put("horse_number", h.number);
            values.put("weather", card.weather);
            values.put("ground", card.ground);
            values.put("card_updated_at", at);
            db.update("race_entries", idKey(id), values);
        }
        if (fixedInfo != null) {
            Map<String, String> key = new LinkedHashMap<>();
            key.put("kaisaibi", date);
            key.put("kaisaijo", race.course);
            key.put("race_no", String.valueOf(race.raceNo));
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("race_info", fixedInfo);
            db.update("race_entries", key, info);
        }
        race.cardUpdated = now;
    }

    /** 単勝・複勝オッズと人気を書き込む。発売前で表が空なら false */
    private static boolean saveOdds(SupabaseRest db, String date, String code, Race race, OffsetDateTime now)
            throws IOException, InterruptedException {
        Document doc;
        try {
            doc = Keibalab.fetch(Keibalab.raceUrl(date, code, race.raceNo) + "odds.html");
        } catch (HttpStatusException e) {
            return false;
        }
        Map<String, LivePages.Odds> odds = LivePages.odds(doc);
        if (odds.isEmpty()) return false;
        String at = now.withOffsetSameInstant(ZoneOffset.UTC).toString();
        for (Map.Entry<String, LivePages.Odds> e : odds.entrySet()) {
            Long id = race.ids.get(e.getKey());
            if (id == null) continue;
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("win_odds", e.getValue().win);
            values.put("popular", e.getValue().popularity != null ? String.valueOf(e.getValue().popularity) : null);
            values.put("place_odds_min", e.getValue().placeMin);
            values.put("place_odds_max", e.getValue().placeMax);
            values.put("odds_updated_at", at);
            db.update("race_entries", idKey(id), values);
        }
        race.oddsUpdated = now;
        return true;
    }

    /** 全着順と払い戻しを入れ直す。結果がまだ出ていなければ false */
    private static boolean saveResult(SupabaseRest db, String date, String code, Race race)
            throws IOException, InterruptedException {
        Document doc = RaceResultsJob.fetchRace(date, code, race.course, race.raceNo);
        if (doc == null) return false;
        List<Map<String, Object>> rows = RaceResultsJob.resultRows(doc, date, race.course, race.raceNo);
        if (rows.isEmpty()) return false;
        Map<String, String> key = new LinkedHashMap<>();
        key.put("kaisaibi", date);
        key.put("kaisaijo", race.course);
        key.put("race_no", String.valueOf(race.raceNo));
        db.delete("race_results", key);
        db.insert("race_results", rows);
        race.hasResult = true;
        List<Map<String, Object>> payouts = RaceResultsJob.payoutRows(doc, date, race.course, race.raceNo);
        if (!payouts.isEmpty()) {
            db.delete("race_payouts", key);
            db.insert("race_payouts", payouts);
            race.hasPayouts = true;
        }
        System.out.println(date + " " + race.label() + ": 結果 " + rows.size() + "頭・払い戻し " + payouts.size() + "件");
        return true;
    }

    /** 「… 15:40発走 …」→ 15:40。読めなければ null */
    static LocalTime postTime(String raceInfo) {
        if (raceInfo == null) return null;
        Matcher m = POST_TIME.matcher(raceInfo);
        return m.find() ? LocalTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))) : null;
    }

    private static String courseCode(String course) {
        for (Map.Entry<String, String> e : Keibalab.COURSES.entrySet()) {
            if (e.getValue().equals(course)) return e.getKey();
        }
        return null;
    }

    private static Map<String, String> idKey(long id) {
        Map<String, String> key = new LinkedHashMap<>();
        key.put("id", String.valueOf(id));
        return key;
    }

    private static String key(String course, int raceNo) {
        return course + String.format("%02d", raceNo);
    }

    private static OffsetDateTime latest(OffsetDateTime a, OffsetDateTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    private static OffsetDateTime time(String s) {
        if (s == null) return null;
        try {
            return OffsetDateTime.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(JsonObject o, String name) {
        JsonElement e = o.get(name);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
