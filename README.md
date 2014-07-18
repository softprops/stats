# stats

collected numbers

```scala
import scala.concurrent.ExecutionContext.Implicits.global

// create a new stats instance whose metrics will be prefixed with "api"
val nums = stats.Stats().prefix("api")

val requests = nums.counter("requests", "foo").sample(0.8)
val responseTimes = nums.time("responses")
val visitors = nums.set[Int]("visitors")
val memory = nums.gauge[Int]("memory")

requests.inrc
responseTimes.add(3.millis)
visitors.add(userId)
memory.add(bytes)
```

Doug Tangren (softprops) 2014
