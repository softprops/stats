# stats

collected numbers

```scala
val nums = stats.Stats().prefix("api")
val requests = nums.counter("requests", "foo").sample(0.8)
val timings = nums.time("responses")
requests.inrc()
timings.add(3.millis)
```

Doug Tangren (softprops) 2014
