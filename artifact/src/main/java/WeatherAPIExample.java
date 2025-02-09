import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class WeatherAPIExample {
    private static FirebaseRealtimeDBManager manager;
    public static void main(String[] args) {
        // String placeName = "Toyoake";
        // String placeName = "Funabashi";

        String placeName = "Kyoto";
        // String placeName = "Tokyo";
        // String placeName = "Kitakyushu";
        manager = new FirebaseRealtimeDBManager();
        String apiKey = "64a5d249b10c22ec892476c7dceac880"; // ここに取得したAPIキーを入力
        String urlString = "https://api.openweathermap.org/data/2.5/weather?q="+placeName+",JP&appid=" + apiKey
                + "&lang=ja&units=metric";
        Map<String, Object> map = getWeather(urlString);

        // DB処理 データをインサート
        FirebaseRealtimeDBManager.insertData("raceTemp", map);

    }

    private static Map<String, Object> getWeather(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            StringBuilder response = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            // レスポンス文字列を JSON オブジェクトに変換
            JsonObject jsonResponse = JsonParser.parseString(response.toString()).getAsJsonObject();

            // JSON オブジェクトからデータを取得して表示
            String cityName = jsonResponse.get("name").getAsString(); // 都市名
            if ("船橋市".equals(cityName)) {
                cityName = "中山";
            }
            if ("Toyoake".equals(cityName)) {
                cityName = "中京";
            }
            if ("北九州市".equals(cityName)) {
                cityName = "小倉";
            }

            String temp = jsonResponse.getAsJsonObject("main").get("temp").getAsString(); // 気温
            String description = jsonResponse.getAsJsonArray("weather")
                    .get(0)
                    .getAsJsonObject()
                    .get("description")
                    .getAsString(); // 天気の説明
            // 今日の日付を取得
            LocalDate today = LocalDate.now();
            // カスタム形式で表示 (例: "yyyy年MM月dd日")
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
            String formattedDate = today.format(formatter);

            Map<String, Object> raceData = new HashMap<>();
            raceData.put("kaisaibi",formattedDate);
            raceData.put("cityName", cityName);
            raceData.put("temp", temp);
            raceData.put("description", description);
            raceData.put("isKaisai", "true");
            return raceData;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
