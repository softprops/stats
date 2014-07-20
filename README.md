# stats

[![Build Status](https://travis-ci.org/softprops/stats.svg?branch=master)](https://travis-ci.org/softprops/stats)

numbers, collected

## usage

```scala
import scala.concurrent.ExecutionContext.Implicits.global

// create a new stats instance whose metrics will be prefixed with "api"
val nums = stats.Stats().scope("api")

// define some stats. all stats may be sampled ( 0 to 1 )

val requests = nums.counter("requests", "foo").sample(0.8)
val responseTimes = nums.time("responses")
val visitors = nums.set[Int]("visitors")
val memory = nums.gauge[Int]("memory")

// push some stats

// counters can incr & desc a metric name
requests.incr

// timings may record finite durations
responseTimes.add(3.millis)

// sets record unique records of happenings
visitors.add(userId)

// gauges record values that represent some arbitary polled metric
odelay.Delay(5.seconds) {
  memory.add(bytes)
}
```

Doug Tangren (softprops) 2014
