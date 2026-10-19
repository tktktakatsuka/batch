import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

/**
 * 開催日にアプリへ出す情報（当日の出馬表・単勝/複勝オッズ・払い戻し）を keibalab のページから読む。
 * 以前はアプリが利用者の端末から直接読んでいたが、keibalab への負担を減らすため PC のバッチで読んで
 * Supabase に入れ、アプリはそれを読む形にした（読み方はアプリにあったものと同じ）。
 */
public final class LivePages {

    public static final List<String> PAYOUT_TYPES = Arrays.asList("単勝", "複勝", "枠連", "馬連", "ワイド", "馬単", "3連複", "3連単");

    /** 当日の出馬表の1頭分 */
    public static final class CardHorse {
        public String number;
        public String waku;
        public String weight;
        public boolean scratched;
        public String jockey;
        // 馬名（印なし）。馬番が入っていない出馬表の行と照らし合わせるため
        public String name;
    }

    /** 当日の出馬表。horses は 馬番（先頭の0なし）→ 1頭分 */
    public static final class Card {
        public final Map<String, CardHorse> horses = new LinkedHashMap<>();
        public String weather;
        public String ground;
        // 「15:40」。ページに無ければ null
        public String postTime;
    }

    /** 1頭分のオッズ */
    public static final class Odds {
        public String win;
        public String placeMin;
        public String placeMax;
        public Integer popularity;
    }

    /** 払い戻し1件 */
    public static final class Payout {
        public final String type;
        public final String numbers;
        public final int yen;

        Payout(String type, String numbers, int yen) {
            this.type = type;
            this.numbers = numbers;
            this.yen = yen;
        }
    }

    private LivePages() {
    }

    /** syutsuba.html。取消・除外の馬は行が灰色（bgGray）になり、オッズと馬体重が空になる */
    public static Card card(Document doc) {
        Card card = new Card();
        Element table = doc.selectFirst("table[summary$=出馬表]");
        if (table != null) {
            Map<String, Integer> col = new HashMap<>();
            Elements headers = table.select("thead th");
            if (headers.isEmpty() && table.selectFirst("tr") != null) headers = table.selectFirst("tr").select("th,td");
            for (int i = 0; i < headers.size(); i++) col.put(headers.get(i).text().trim(), i);
            for (Element tr : table.select("tbody tr")) {
                if (tr.selectFirst("td[data-hsnm]") == null) continue;
                Elements td = tr.select("td");
                String number = stripZeros(cell(td, col, "馬"));
                if (number == null) continue;
                CardHorse h = new CardHorse();
                h.number = number;
                h.waku = stripZeros(cell(td, col, "枠"));
                h.weight = weight(cell(td, col, "馬体重"));
                h.scratched = tr.hasClass("bgGray");
                h.jockey = cell(td, col, "騎手");
                h.name = Keibalab.horseName(tr.selectFirst("td[data-hsnm]").attr("data-hsnm"));
                card.horses.put(number, h);
            }
        }
        List<Element> conditions = doc.select(".weather_ground li");
        card.weather = conditions.size() > 0 ? Keibalab.valueOrNull(conditions.get(0).text()) : null;
        card.ground = conditions.size() > 1 ? Keibalab.valueOrNull(conditions.get(1).text()) : null;
        java.util.regex.Matcher post = java.util.regex.Pattern.compile("(\\d{1,2}:\\d{2})発走").matcher(doc.text());
        card.postTime = post.find() ? post.group(1) : null;
        return card;
    }

    /** 「490(+8)」「490(0)」の形だけを馬体重とみなす（「計不」や空欄は null）。初出走は「440(初)」 */
    static String weight(String s) {
        if (s == null) return null;
        String t = s.replaceAll("\\s+", "").replace('（', '(').replace('）', ')').replace('＋', '+').replace('－', '-');
        if (t.matches("\\d{3}\\(-+\\)")) return t.replaceAll("\\(-+\\)", "(初)");
        return t.matches("\\d{3}\\([+-]?\\d+\\)") ? t : null;
    }

    /** odds.html の単勝・複勝の表。列は 枠番・馬番・馬名・単勝・複勝(下限)・"-"・複勝(上限)・人気。馬番 → オッズ */
    public static Map<String, Odds> odds(Document doc) {
        Map<String, Odds> odds = new LinkedHashMap<>();
        Element table = doc.selectFirst("table#oddsTanTable");
        if (table == null) return odds;
        for (Element tr : table.select("tr")) {
            Elements td = tr.select("td");
            if (td.size() < 8) continue;
            String number = stripZeros(td.get(1).text());
            if (number == null) continue;
            Odds o = new Odds();
            o.win = numberOrNull(td.get(3).text());
            o.placeMin = numberOrNull(td.get(4).text());
            o.placeMax = numberOrNull(td.get(6).text());
            String popularity = numberOrNull(td.get(7).text());
            if (o.win == null && o.placeMin == null) continue;
            o.popularity = o.win != null && popularity != null ? (int) Double.parseDouble(popularity) : null;
            odds.put(number, o);
        }
        return odds;
    }

    /** 結果のページの払い戻し。表は「券種・組番・払戻」が1行に2組ずつ並び、複勝・ワイドは改行で複数入る。確定前なら空 */
    public static List<Payout> payouts(Document doc) {
        List<Payout> payouts = new ArrayList<>();
        Element table = doc.selectFirst(".haraimodoshi table");
        if (table == null) return payouts;
        for (Element tr : table.select("tr")) {
            Elements td = tr.select("td");
            for (int i = 0; i + 2 < td.size(); i += 3) {
                String type = td.get(i).text().trim();
                if (!PAYOUT_TYPES.contains(type)) continue;
                List<String> numbers = lines(td.get(i + 1));
                List<String> yens = lines(td.get(i + 2));
                for (int j = 0; j < Math.min(numbers.size(), yens.size()); j++) {
                    String digits = yens.get(j).replaceAll("[^0-9]", "");
                    if (digits.isEmpty()) continue;
                    payouts.add(new Payout(type, numbers.get(j), Integer.parseInt(digits)));
                }
            }
        }
        return payouts;
    }

    /** <br> で区切られた行を取り出す */
    private static List<String> lines(Element cell) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (Node node : cell.childNodes()) {
            if (node instanceof Element && ((Element) node).tagName().equals("br")) {
                if (!current.toString().trim().isEmpty()) lines.add(current.toString().trim());
                current.setLength(0);
            } else if (node instanceof TextNode) {
                current.append(((TextNode) node).text());
            } else if (node instanceof Element) {
                current.append(((Element) node).text());
            }
        }
        if (!current.toString().trim().isEmpty()) lines.add(current.toString().trim());
        return lines;
    }

    private static String cell(Elements td, Map<String, Integer> col, String header) {
        Integer i = col.get(header);
        if (i == null || i >= td.size()) return null;
        return Keibalab.valueOrNull(td.get(i).text());
    }

    private static String numberOrNull(String s) {
        String t = s.trim();
        try {
            Double.parseDouble(t);
            return t;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String stripZeros(String number) {
        if (number == null) return null;
        String t = number.trim().replaceFirst("^0+(?=\\d)", "");
        return t.matches("\\d+") ? t : null;
    }
}
