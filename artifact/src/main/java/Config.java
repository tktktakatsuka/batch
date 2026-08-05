import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

public class Config {
    public static final List<String> TARGET_JO_LIST = Arrays.asList("札幌", "小倉","新潟");
    public static final String TARGET_DATE_LIST =  "20260802";
    // public static final String TARGET_DATE_LIST =  LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // joList.add("新潟");
        // joList.add("中京");
        // joList.add("札幌");
        // joList.add("京都");
        // joList.add("東京");
        // joList.add("福島");
        // joList.add("小倉");
        // joList.add("函館");
        // joList.add("中山");
        // joList.add("阪神");
}