import java.util.Arrays;

/**
 * 使い方:
 *   (引数なし)                    これまでどおり Config の日付・競馬場で結果と傾向を取得する
 *   entries [日付...] [--dry-run]  出馬表を race_entries に登録する（日付省略で今日から7日以内）
 *   results [日付...] [--dry-run]  全着順の結果を race_results に登録する（日付省略で直近4日）
 *                                 日付は yyyyMMdd または yyyyMMdd-yyyyMMdd（期間）
 *   --dry-run を付けると、取得した内容を表示するだけで Supabase には書き込まない
 */
public class ExecuteMain {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            ReceResultMain.main(args);
            RaceTrendsMain.main(args);
            return;
        }
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (args[0]) {
            case "entries":
                RaceEntriesMain.main(rest);
                break;
            case "results":
                RaceResultsJob.main(rest);
                break;
            default:
                System.err.println("不明なコマンドです: " + args[0] + "（entries / results）");
                System.exit(1);
        }
    }
}
