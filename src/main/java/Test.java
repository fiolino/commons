import java.lang.invoke.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static java.lang.invoke.MethodType.methodType;

/**
 * Created by kuli on 22.02.17.
 */
public class Test {
    public static void main(String[] args) throws Throwable {
        MethodType type = methodType(void.class, String.class, List.class);
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodHandle handle = lookup.findStatic(Test.class, "printSum", type);

        MethodType samMethodType = methodType(void.class, Collection.class);
        CallSite callSite = LambdaMetafactory.metafactory(lookup, "printAll", methodType(PrintAll.class, String.class),
                samMethodType, handle, methodType(void.class, ArrayList.class));

        PrintAll all = (PrintAll) callSite.getTarget().invokeExact("Hello");

        all.printAll(new ArrayList<>(Arrays.asList(45, 99)));

        MethodHandle newInteger = lookup.findConstructor(Integer.class, methodType(void.class, String.class));
        callSite = LambdaMetafactory.metafactory(lookup, "apply", methodType(Function.class),
                methodType(Object.class, Object.class), newInteger, methodType(Object.class, String.class));
        Function<String, Integer> func = (Function<String, Integer>) callSite.getTarget().invokeExact();
        Integer i = func.apply("123");
        System.out.println(i);
    }

    private static void printSum(String text, List<Integer> values) {
        int sum = values.stream().mapToInt(Integer::intValue).sum();
        System.out.println(text + " " + sum);
    }
}
