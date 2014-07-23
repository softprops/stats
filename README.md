# stats

[![Build Status](https://travis-ci.org/softprops/stats.svg?branch=master)](https://travis-ci.org/softprops/stats)

numbers, collected

## usage

```scala
import scala.concurrent.ExecutionContext.Implicits.global

// create a new stats instance whose metrics will be prefixed with "api"
val nums = stats.Stats().scope("api")

// define some stats

// all stats define a metric name as a series of string values which will be concatenated when reported

val requests = nums.counter("requests", "foo")
val responseTimes = nums.time("responses")
val visitors = nums.set[Int]("visitors")
val memory = nums.gauge[Int]("memory")

// all stats may be sampled
// stat instances are immutable. applying a sample
// on the original stat will have no side effect

val sampledRequests = requests.sample(0.8)

// all stats may be further scoped with a prepending set of identifiers

val specialMemory = memory.scope("special")

// push some stats

// counters can incr & desc a metric name
sampledRequests.incr
sampleRequests.decr

// counters may also be incr & and decr with a specific value
sampledRequests.incr(10)
sampledRequests.decr(5)


// timings record finite durations
responseTimes.add(3.millis)

// sets record unique records of happenings
visitors.add(userId)

// gauges record values that represent some arbitary polled metric
odelay.Delay(5.seconds) {
  memory.add(bytes)
}
```

Doug Tangren (softprops) 2014
