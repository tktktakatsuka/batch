
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

public class ExecuteMain { 
    public static void main(String[] args) throws KeyManagementException, NoSuchAlgorithmException, InterruptedException {
        ReceResultMain.main(args);
        RaceTrendsMain.main(args);
    }   
}