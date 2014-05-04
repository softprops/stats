# stats

collected numbers

```scala
val nums = stats.Stats()
val requests = nums.counter("api.requests.foo", "api.requests.all").sample(0.8)
val timings = nums.time("api.responses.foo")
requests.inrc()
timings.add(3.millis)
```

Doug Tangren (softprops) 2014
