# stats

[![Build Status](https://travis-ci.org/softprops/stats.svg?branch=master)](https://travis-ci.org/softprops/stats)

numbers, exported.

## usage

Stats is a non-blocking Scala frontend for reporting metrics to [statsd](https://github.com/etsy/statsd/) over [UPD](http://en.wikipedia.org/wiki/User_Datagram_Protocol).

### connecting

Creating a new stats client is simple. The default client is configured for exporting stats to a statsd server hosted locally listening on port `8125`.

```scala
import scala.concurrent.ExecutionContext.Implicits.global
val cli = stats.Stats()
```

You can specify a remote address as an InetSocketAddress as a Stats constructor argument or use the `addr` method to return a new client pointing to an alternative host address.

```scala
val hosted = cli.addr(host, port)
```

### metric names

Statd servers are largely concerned with 2 things: metric names and metric values. Metric names are lists of period (`.`) separated path segments.
These names translate to file paths so most naming rules that apply to file names also apply to metric names. If your application has specific name
formatting needs, you can override the default escaping strategy with the client's `formatNames` member.

```scala
val formatted = cli.formatNames { segments: Iterable[String] =>
  segments.map(customFormat).mkString(".")
}
```

Names often represent an encoding of a metric's hierarchy of context. For this reason, many of the stats client interfaces support name scoping with the `scope` method.

```scala
val scoped = cli.scope("svr").scope("serviceName")
```

The above will prepend `srv.serviceName` to the name of all reported metrics

### Metric values

Statsd defines a set of [metric types](https://github.com/etsy/statsd/blob/master/docs/metric_types.md) which a statsd server is able to interpret.
This client provides type-safe interfaces for each of those.

#### Counting

The simplest type of metric is a counter

```scala
val counts = cli.counter("requests", endpointName)
```

`counts` is a reference to a stat named "requests.endpointName" and defines a handful of operations

```scala
// counters can incr & decr a metric name
counts.incr
counts.decr

// counters may also be incr & and decr with a specific value
counts.incr(10)
counts.decr(5)
```

Note each operation results of a [Future](http://www.scala-lang.org/api/current/index.html#scala.concurrent.Future) of type boolean where the boolean value represents the packet data being sent fully.

Counting, and other metric types, all support request sampling. By default, any metric operation will be reported to a statsd server. You can override
this behavior by setting a custom sample rate, a number between 0 and 1.

```scala
val sampledRequests = requests.sample(0.8)
```

The above will only report metric data at a rate of 0.8. Sampling is sometimes helpful to reduce load under heavy periods of requests.

#### Timing

Timers are recorders of time based information in milliseconds. To remove any ambiguity, interface for timers operate on std library [FiniteDurations](http://www.scala-lang.org/api/current/index.html#scala.concurrent.duration.FiniteDuration).

```scala
val latency = cli.time("responses")

// record time
latency.add(200 millis)
latency.add(2 seconds)
```

#### Gauges

Gauges provide an interface for recording information about arbitrary countable values. Gauges have the unique property retaining only the last value recorded within a configured flush interval. Countable types are currently defined for Ints, Doubles, and Floats
. Since a type bound is defined, you are required to specify the type of value you wish to record.

```scala
val memory = cli.gauge[Int]("memory")

// record int values
memory.add(1024)
memory.add(80000000)
```

#### Sets

Sets are similar to gauges except that only unique occurances of values are recorded within a configured flush interval.

```scala
val visitors = cli.set[Int]("visitors")

visitors.add(memberA)
visitors.add(memberB)
visitors.add(memberA) // only one memberA event will actually be recorded for a given flush interval
```

#### Multi stats

You can also send multiple stats at once by creating a set of stat "instances" and asking the stats client to send them all at once. A stat instance
can be created by calling the `apply` method of the type of stat providing the value to record.

```scala
cli.multi(
  requests(1), // increment by one
  latency(1 second),
  memory(2014),
  visitors(memberB)
)
```

Each individual stat instances sample rate will be honored in a multi metric send. You should take note of your networks configured packet size limits.
Some general guidelines are defined [here](https://github.com/etsy/statsd/blob/master/docs/metric_types.md#multi-metric-packets). Stats will make a best effort to group stats according to this clients `packetMax` size which defaults to `1500`. You can configure this as follows.

```scala
val sized = cli.packetMax(512)
```

### Logging


It's useful to know when things fail. Since all stating operation result in a Future result you could register a logging listener for failures at
each callsite but that can become error prone and tedious. Stats provides a convenient way to do this in one place but registering a logging function.

```scala
val logged = cli.addr("deadhost").log {
  case Failure(NonFatal(e)) =>
     println(s"failed to sent packet of one or more stats")
  case _ =>
}
logged.counter("this", "will", "fail").incr
```

### Inspecting

You can inspect the value a given metric will report by calling the `str` member of a given stats instance. This can help you get to know what to
expect with you look for your metric in a the graphite UI.

```scala
// foo.bar:1000|ms
val timerStr = cli.time("foo", "bar")(1.second).str
```

Note that metric names are specified as a varargs list of path segments. You may be template to hard code a pre-encoded path ( one containing a separator). In these cases those periods will be escaped to remove ambiguity.

```scala
// foo_bar.baz:1000|ms
val timerStr = cli.time("foo.bar", "baz")(1.second).str
```

### Java

Care was taken to ensure stats interfaces are also tolerable to use in java-based applications;

```java
import stats.Stats;
import scala
public class Main {
  public void main(String[] args) {
    Stats client = Stats.client().scope("foo", "bar");
    client.counter("baz", "boom").incr();
  }
}
```

Doug Tangren (softprops) 2014
