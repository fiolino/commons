import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Created by kuli on 22.02.17.
 */
public class Test {
    public static void main(String[] args) throws Throwable {
        Predicate<String> p = Pattern.compile("^[\\p{L}\\d _-]*$").asPredicate();
        LineNumberReader r = new LineNumberReader(new InputStreamReader(System.in));
        String l;
        while ((l = r.readLine()) != null) {
            System.out.println(p.test(l));
        }
    }
}
