# stats

[![Build Status](https://travis-ci.org/softprops/stats.svg?branch=master)](https://travis-ci.org/softprops/stats)

numbers, collected

## usage

Stats is a non-blocking Scala front end for reported metrics to [statsd](https://github.com/etsy/statsd/) over [UPD](http://en.wikipedia.org/wiki/User_Datagram_Protocol).

### connecting

Creating a client is simple. The default client is configured for testing with a statsd server locally on port `8125`.

```scala
import scala.concurrent.ExecutionContext.Implicits.global
val cli = stats.Stats()
```

You can specify a remote address as an InetSocketAddress as the first argument to stats or use the `addr` method to return a client pointing and an alternative host.

```scala
val hosted = cli.addr(host, port)
```

### metric names

Statd servers are largely considered with 2 things, metric names and metric values. Metric names are lists of period (`.`) separated path segments.
These names translate to file paths so most naming rules that apply to file names also apply to metric names. If your application has specific name
formatting needs you can override the default escaping strategy pass setting the clients `format` member.

```scala
val formatted = cli.formatNames { segments: Iterable[String] =>
  segments.map(customFormat).mkString(".")
}
```

Names also often represent some hierarchy encoding a metric's context. For this reason may of the stats client support name scoping.

```scala
val scoped = cli.scope(serviceName)
```

The above will prepend `serviceName` to the name of all metrics

### metric values

Statsd defines a set of [metric types](https://github.com/etsy/statsd/blob/master/docs/metric_types.md) which a statsd server is able to interpret.
This client provides type-safe interfaces for each of those.

#### counting

The simpliest type of metric is a counter

```scala
val counts = cli.counter("requests", endpointName)
```

`counts` is a reference to a stat named "requests.endpointName" and defines a handleful of operations

```scala
// counters can incr & decr a metric name
sampledRequests.incr
sampleRequests.decr

// counters may also be incr & and decr with a specific value
sampledRequests.incr(10)
sampledRequests.decr(5)
```

Note each operation results of a [Future](http://www.scala-lang.org/api/current/index.html#scala.concurrent.Future) of type boolean where the boolean represents the packet data being sent fully.

Counting, and other metric types, all support request sampling. By default any metric operation will be reported to a statsd server. You can override
this behavior by setting a custom sample rate, a number between 0 and 1.

```scala
val sampledRequests = requests.sample(0.8)
```

The above will only report metric data at a rate of 0.8. Sampling is sometimes helpful to reduce load under heavy periods of requests.

#### timing

Timers are recorders of time based information in milliseconds. To remove ambiguity the interface for timers operate on [FiniteDurations](http://www.scala-lang.org/api/current/index.html#scala.concurrent.duration.FiniteDuration).

```scala
val latency = cli.time("responses")

// record time
latency.add(200 millis)
latency.add(2 seconds)
```

#### gauges

Gauges provide an interface for recording information about arbitrary countable values. Countable types are currently defined as Ints, Doubles, and Floats
. Since a type bound is defined you are required to specify the type of value you wish to record.

```scala
val memory = cli.gauge[Int]("memory")

// record int values
memory.add(1024)
memory.add(80000000)
```

#### Sets

Sets are similar to guages except that what gets recorded is only unique occurances of events within an interval of time ( the flush period defined in your statsd server )

```scala
val visitors = cli.set[Int]("visitors")

visitors.add(memberA)
visitors.add(memberB)
visitors.add(memberA) // only one memberA event will actually be recorded
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

Each individual stat instances sample rate will be honored in a multi metric send.

Doug Tangren (softprops) 2014
