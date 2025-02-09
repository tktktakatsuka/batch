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

public class RaceStatistics {
    private static FirebaseRealtimeDBManager manager;
    public static void main(String[] args) {
        // String placeName = "Toyoake";
        // String placeName = "Funabashi";
        String placeName = "Kyoto";
        // String placeName = "Tokyo";        
        // String placeName = "Kitakyushu";

        // DB処理 データをインサート
        new FirebaseRealtimeDBManager().getRaceTrendsKyakusitu("20250209", "", "東京");

    }

}
