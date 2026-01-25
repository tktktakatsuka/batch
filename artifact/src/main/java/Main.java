import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.*;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public class Main {

    private static DatabaseReference database;

    public static void main(String[] args) {
        try {
            // Firebase 初期化
            FileInputStream serviceAccount = new FileInputStream(
                    "C:\\work\\batch\\artifact\\raceresult-c4af3-firebase-adminsdk-fmu17-0a792a675c.json");

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setDatabaseUrl("https://raceresult-c4af3-default-rtdb.firebaseio.com/")
                    .build();

            FirebaseApp.initializeApp(options);
            database = FirebaseDatabase.getInstance().getReference();

            // 削除対象パスと日付
            String path = "raceResult/京都";
            String targetDate = "20260124"; // Firebase に保存されている形式に合わせる

            deleteDataByDate(path, targetDate);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 指定パス内で targetDate に一致するデータを削除する
     *
     * @param path       Firebase のパス
     * @param targetDate 削除対象の日付 (yyyyMMdd)
     */
    public static void deleteDataByDate(String path, String targetDate) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        database.child(path).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    System.out.println("指定パスにデータが存在しません: " + path);
                    latch.countDown();
                    return;
                }

                int deleteCount = 0;

                for (DataSnapshot child : snapshot.getChildren()) {
                    // Firebase に保存されている日付キーを指定 ("date" または "kaisaibi")
                    String childDate = child.child("date").getValue(String.class);
                    if (childDate != null && childDate.equals(targetDate)) {
                        child.getRef().removeValue((error, ref) -> {
                            if (error == null) {
                                System.out.println("削除成功: " + ref.getKey());
                            } else {
                                System.err.println("削除失敗: " + error.getMessage());
                            }
                        });
                        deleteCount++;
                    }
                }

                System.out.println("合計 " + deleteCount + " 件のデータを削除しました。");
                latch.countDown();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                System.err.println("削除クエリキャンセル: " + error.getMessage());
                latch.countDown();
            }
        });

        latch.await(); // 削除完了まで待機
        System.out.println("削除処理終了");
    }
}
