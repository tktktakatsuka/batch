
import org.threeten.bp.DayOfWeek;
import org.threeten.bp.LocalDate;
import org.threeten.bp.YearMonth;
import org.threeten.bp.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.List;

public class WeekendDays {

    // 今月の土曜日と日曜日のうち、現在日時より前の日付を取得するメソッド
    public static List<String> getPastWeekendsInCurrentMonth() {
        List<String> pastWeekends = new ArrayList<>();


        // 現在の年月を取得
        YearMonth currentMonth = YearMonth.now();
        // 月の初日を取得
        LocalDate firstDayOfMonth = currentMonth.atDay(1);
        // 月の最終日を取得
        LocalDate lastDayOfMonth = currentMonth.atEndOfMonth();

        // 日付フォーマット（yyyyMMdd）
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

        // 現在の日付
        LocalDate today = LocalDate.now();

        // 1か月前の年月を取得
        YearMonth lastMonth = currentMonth.minusMonths(1);
        // 1か月前の最終日を取得
        LocalDate lastDayOfLastMonth = lastMonth.atEndOfMonth();

        // 1か月前の月の初日から最終日までループ
        for (LocalDate date = lastMonth.atDay(1); !date.isAfter(lastDayOfLastMonth); date = date.plusDays(1)) {
            // 土曜日か日曜日をチェック
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                // 現在の日付より前かをチェック
                if (date.isBefore(today) || date.isEqual(today)) {
                    // フォーマットしてリストに追加
                    pastWeekends.add(date.format(formatter));
                }
            }
        }

        // 初日から最終日までループ
        for (LocalDate date = firstDayOfMonth; !date.isAfter(lastDayOfMonth); date = date.plusDays(1)) {
            // 土曜日か日曜日をチェック
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                // 現在の日付より前かをチェック
                if (date.isBefore(today) || date.isEqual(today) ) {
                    // フォーマットしてリストに追加
                    pastWeekends.add(date.format(formatter));
                }
            }
        }
        pastWeekends.add("20250106");
        pastWeekends.add("20250113");
        return pastWeekends;
    }
}
