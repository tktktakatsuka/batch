import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.*;

import javafx.scene.control.CheckBox;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class FirebaseRealtimeDBManager {

    private static DatabaseReference database;

    // Firebase初期化
    public FirebaseRealtimeDBManager() {
        try {
            FileInputStream serviceAccount = new FileInputStream(
                    "C:\\work\\batch\\artifact\\raceresult-c4af3-firebase-adminsdk-fmu17-0a792a675c.json");

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setDatabaseUrl("https://raceresult-c4af3-default-rtdb.firebaseio.com/")
                    .build();

            FirebaseApp.initializeApp(options);

            // データベース参照の取得
            database = FirebaseDatabase.getInstance().getReference();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * データを作成・追加する
     * 
     * @param path Firebase Realtime Databaseのパス
     * @param map  格納するデータ (Map形式)
     */
    // データを挿入するメソッド
    public static void insertData(String path, Object map) {
        ApiFuture<Void> future = database.child(path).push().setValueAsync(map);
        try {
            // 処理が完了するのを待つ
            future.get();
            System.out.println("Data inserted successfully at: " + path);
        } catch (Exception e) {
            System.err.println("Error inserting data: " + e.getMessage());
        }
    }

    public Map<String, Object> setData(String kaisaibi, String kaisaijo, String raceNo, String tyaku,
            String waku, String horseNumber, String horseName, String age, String weight,
            String jockey, String popular, String winOdds, String time, String tyakusa,
            String tuukazyun, String nobori, String tyoukyousi, String horseWeight, String raceTitle,
            String hassouTime) {
        Map<String, Object> raceData = new HashMap<>();
        raceData.put("kaisaibi", kaisaibi);
        raceData.put("kaisaijo", kaisaijo);
        raceData.put("raceNo", raceNo);
        raceData.put("tyaku", tyaku);
        raceData.put("waku", waku);
        raceData.put("horseNumber", horseNumber);
        raceData.put("horseName", horseName);
        raceData.put("age", age);
        raceData.put("weight", weight);
        raceData.put("jockey", jockey);
        raceData.put("popular", popular);
        raceData.put("winOdds", winOdds);
        raceData.put("time", time);
        raceData.put("tyakusa", tyakusa);
        raceData.put("tuukazyun", tuukazyun);
        raceData.put("nobori", nobori);
        raceData.put("tyoukyousi", tyoukyousi);
        raceData.put("horseWeight", horseWeight);
        raceData.put("raceTitle", raceTitle);
        raceData.put("hassouTime", hassouTime);
        return raceData;
    }

    // データを取得するメソッド
    public void getRaceResults(String raceId) {
        database.child("raceResults").child(raceId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    System.out.println("Race Result: " + snapshot.getValue());
                } else {
                    System.out.println("No data found for raceId: " + raceId);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                System.err.println("Error: " + error.getMessage());
            }
        });
    }

    /**
     * 条件付きデータの取得
     * 
     * @param path     Firebase Realtime Databaseのパス
     * @param childKey 検索条件のキー
     * @param value    検索する値
     * @param listener データ取得時のコールバック
     */
    public static void queryData(String path, String childKey, String value, ValueEventListener listener) {
        Query query = database.child(path).orderByChild(childKey).equalTo(value);
        query.addListenerForSingleValueEvent(listener);
    }

    public void getRaceTrendsKyakusitu(String kaisaibi, String raceNo, String kaisaijo) {

        FirebaseRealtimeDBManager.queryData("raceTrends" + "/" + kaisaijo, "kaisaibi", kaisaibi,
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        int nigeKyakusituCounter = 0;
                        int senkouKyakusituCounter = 0;
                        int oikomiKyakusituCounter = 0;
                        int sasiKyakusituCounter = 0;

                        int danigeKyakusituCounter = 0;
                        int dasenkouKyakusituCounter = 0;
                        int daoikomiKyakusituCounter = 0;
                        int dasasiKyakusituCounter = 0;

                        HashMap<String, String> map = new HashMap<>();
                        HashMap<String, String> damap = new HashMap<>();
                        for (DataSnapshot childSnapshot : snapshot.getChildren()) {
                            String sRaceNo = childSnapshot.child("raceNum").getValue(String.class);
                            String sKaisaijo = childSnapshot.child("kaisaijo").getValue(String.class);
                            String sKaisaibi = childSnapshot.child("kaisaibi").getValue(String.class);

                            // Rで開催場所が一致している場合にのみ処理
                            if (kaisaijo.equals(sKaisaijo) && sKaisaibi.equals(kaisaibi)) {
                                String sZyouken = childSnapshot.child("raceDistance").getValue(String.class);
                                String sbabaCondition = childSnapshot.child("raceCondition").getValue(String.class);
                                String uma1Kyakusitu = childSnapshot.child("uma1Kyakusitu").getValue(String.class);
                                String uma2Kyakusitu = childSnapshot.child("uma2Kyakusitu").getValue(String.class);
                                String uma3Kyakusitu = childSnapshot.child("uma3Kyakusitu").getValue(String.class);

                                if (sZyouken.contains("芝")) {
                                    if (uma1Kyakusitu.contains("逃げ")) {
                                        nigeKyakusituCounter++;
                                    } else if (uma1Kyakusitu.contains("先行")) {
                                        senkouKyakusituCounter++;
                                    } else if (uma1Kyakusitu.contains("追込")) {
                                        oikomiKyakusituCounter++;
                                    } else if (uma1Kyakusitu.contains("差し")) {
                                        sasiKyakusituCounter++;
                                    }

                                    if (uma2Kyakusitu.contains("逃げ")) {
                                        nigeKyakusituCounter++;
                                    } else if (uma2Kyakusitu.contains("先行")) {
                                        senkouKyakusituCounter++;
                                    } else if (uma2Kyakusitu.contains("追込")) {
                                        oikomiKyakusituCounter++;
                                    } else if (uma2Kyakusitu.contains("差し")) {
                                        sasiKyakusituCounter++;
                                    }

                                    if (uma3Kyakusitu.contains("逃げ")) {
                                        nigeKyakusituCounter++;
                                    } else if (uma3Kyakusitu.contains("先行")) {
                                        senkouKyakusituCounter++;
                                    } else if (uma3Kyakusitu.contains("追込")) {
                                        oikomiKyakusituCounter++;
                                    } else if (uma3Kyakusitu.contains("差し")) {
                                        sasiKyakusituCounter++;
                                    }

                                }

                                if (sZyouken.contains("ダ")) {
                                    if (uma1Kyakusitu.contains("逃げ")) {
                                        danigeKyakusituCounter++;
                                    } else if (uma1Kyakusitu.contains("先行")) {
                                        dasenkouKyakusituCounter++;
                                    } else if (uma1Kyakusitu.contains("追込")) {
                                        daoikomiKyakusituCounter++;
                                    } else if (uma1Kyakusitu.contains("差し")) {
                                        dasasiKyakusituCounter++;
                                    }

                                    if (uma2Kyakusitu.contains("逃げ")) {
                                        danigeKyakusituCounter++;
                                    } else if (uma2Kyakusitu.contains("先行")) {
                                        dasenkouKyakusituCounter++;
                                    } else if (uma2Kyakusitu.contains("追込")) {
                                        daoikomiKyakusituCounter++;
                                    } else if (uma2Kyakusitu.contains("差し")) {
                                        dasasiKyakusituCounter++;
                                    }

                                    if (uma3Kyakusitu.contains("逃げ")) {
                                        danigeKyakusituCounter++;
                                    } else if (uma3Kyakusitu.contains("先行")) {
                                        dasenkouKyakusituCounter++;
                                    } else if (uma3Kyakusitu.contains("追込")) {
                                        daoikomiKyakusituCounter++;
                                    } else if (uma3Kyakusitu.contains("差し")) {
                                        dasasiKyakusituCounter++;
                                    }

                                }

                            }

                        }
                        map.put("date", kaisaibi);
                        map.put("cose", "芝");
                        map.put("nige", String.valueOf(nigeKyakusituCounter));
                        map.put("senkou", String.valueOf(senkouKyakusituCounter));
                        map.put("sasi", String.valueOf(sasiKyakusituCounter));
                        map.put("oikomi", String.valueOf(oikomiKyakusituCounter));

                        damap.put("date", kaisaibi);
                        damap.put("cose", "ダ");
                        damap.put("nige", String.valueOf(danigeKyakusituCounter));
                        damap.put("senkou", String.valueOf(dasenkouKyakusituCounter));
                        damap.put("sasi", String.valueOf(dasasiKyakusituCounter));
                        damap.put("oikomi", String.valueOf(daoikomiKyakusituCounter));

                        
                        
                        // finalMap.put("ダ", damap);
                        insertData("raceStatistics" + "/" + kaisaijo + "/" + "ダ", (Object) damap);
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        System.err.println("Query failed: " + error.getMessage());
                    }
                });
    }

}
