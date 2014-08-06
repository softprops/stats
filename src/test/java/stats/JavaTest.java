package stats;

import scala.concurrent.duration.Duration;
import scala.concurrent.ExecutionContext;
import scala.collection.Seq;
import static java.util.Arrays.asList;
import static scala.collection.JavaConversions.asScalaBuffer;

public class JavaTest {
  private static final Numeric<Integer> INTS = new Numeric<Integer>() {
    public Integer defaultValue() {
      return -1;
    }
    public Integer negate(Integer value) {
      return -1;
    }
    public String apply(Integer value) {
      return Integer.toString(value);
    }
  };

  private static <T> Seq<T> seq(T... values) {
    return asScalaBuffer(asList(values));
  }

  public static void main(String[] args) {
    Stats client = Stats.client().addr("localhost", 8125).scope("foo", "bar");

    // counter
    System.out.println(client.counter("baz", "boom").sample(0.5).apply(1).str());

    // time
    System.out.println(client.time("baz", "boom").sample(0.5).apply(Duration.apply(100, "millis")).str());

    // generic methods need to provide a type class instance for the java.lang type to support
    Sampled<Integer> guage = client.gauge(seq("baz", "boom"), INTS);
    System.out.println(guage.apply(1).str());

    // not providing scope(...) with a Seq throws java.lang.AbstractMethodError
    System.out.println(guage.scope(seq("zoom", "doom")).apply(3).str());

    System.out.println(client.set(seq("bar"), INTS).apply(1).str());
  }
}
