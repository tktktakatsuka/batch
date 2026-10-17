import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 1レース分の単勝オッズ（keibalab の JSON {"normal":{"01":"7.3",…},"race":{"posttm":"1345",…}}）。
 */
public class WinOdds {

    /** 馬番（先頭の0なし）→ オッズ */
    private final Map<String, String> odds;
    /** 発走時刻 "1345"。読めなければ null */
    public final String postTime;

    private WinOdds(Map<String, String> odds, String postTime) {
        this.odds = odds;
        this.postTime = postTime;
    }

    static WinOdds parse(String json) {
        Map<String, String> odds = new HashMap<>();
        String postTime = null;
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            return new WinOdds(odds, null);
        }
        if (!root.isJsonObject()) return new WinOdds(odds, null);
        JsonObject obj = root.getAsJsonObject();
        JsonElement normal = obj.get("normal");
        if (normal != null && normal.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : normal.getAsJsonObject().entrySet()) {
                String number = stripZeros(e.getKey());
                if (number != null && e.getValue().isJsonPrimitive()) odds.put(number, e.getValue().getAsString().trim());
            }
        }
        JsonElement race = obj.get("race");
        if (race != null && race.isJsonObject()) {
            JsonElement t = race.getAsJsonObject().get("posttm");
            if (t != null && t.isJsonPrimitive()) postTime = t.getAsString();
        }
        return new WinOdds(odds, postTime);
    }

    public boolean isEmpty() {
        return odds.isEmpty();
    }

    /** その馬のオッズ。発売前・取消（数字でない）なら null */
    public String oddsOf(String horseNumber) {
        String number = stripZeros(horseNumber);
        String value = number != null ? odds.get(number) : null;
        return toNumber(value) != null ? value : null;
    }

    /** オッズの低い順の順位（＝人気）。オッズが無ければ null */
    public String popularityOf(String horseNumber) {
        Double mine = toNumber(oddsOf(horseNumber));
        if (mine == null) return null;
        int rank = 1;
        for (String value : odds.values()) {
            Double other = toNumber(value);
            if (other != null && other < mine) rank++;
        }
        return String.valueOf(rank);
    }

    private static String stripZeros(String number) {
        if (number == null) return null;
        String t = number.trim().replaceFirst("^0+(?=\\d)", "");
        return t.matches("\\d+") ? t : null;
    }

    private static Double toNumber(String s) {
        if (s == null) return null;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
