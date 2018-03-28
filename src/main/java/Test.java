import java.util.Calendar;

/**
 * Created by kuli on 22.02.17.
 */
public class Test {
    public static void main(String[] args) throws Throwable {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 45);
        System.out.println(cal.getTime());
    }
}
