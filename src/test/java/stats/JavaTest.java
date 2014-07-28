package stats;

import scala.concurrent.duration.Duration;
import scala.concurrent.ExecutionContext;

public class JavaTest {
  public static void main(String[] args) {
    Stats client = Stats.client().addr("localhost", 8125).scope("foo", "bar");
    System.out.println(client.counter("baz", "boom").sample(0.5).apply(1).str());
    System.out.println(client.time("baz", "boom").sample(0.5).apply(Duration.apply(100, "millis")).str());
    //System.out.println(client.gauge("bar", Countable$.MODULE$.IntCounts).apply(1).str());
    //System.out.println(client.set("bar", Countable$.MODULE$.IntCounts).apply(1).str());
  }
}
