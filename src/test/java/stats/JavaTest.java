package stats;

import scala.concurrent.duration.Duration;
import scala.concurrent.ExecutionContext;
import scala.collection.Seq$;
import static java.util.Arrays.asList;
import static scala.collection.JavaConversions.asScalaBuffer;

public class JavaTest {
  public static void main(String[] args) {
    Stats client = Stats.client().addr("localhost", 8125).scope("foo", "bar");
    System.out.println(client.counter("baz", "boom").sample(0.5).apply(1).str());
    System.out.println(client.time("baz", "boom").sample(0.5).apply(Duration.apply(100, "millis")).str());
    System.out.println(
      client.gauge(asScalaBuffer(asList("foo", "bar")).toList(), Countable$.MODULE$.ints()).apply(1).str());
    System.out.println(client.set(asScalaBuffer(asList("bar")), Countable$.MODULE$.ints()).apply(1).str());
  }
}
