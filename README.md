# stats

collected numbers

```scala
val nums = stats.Stats()
val requests = nums.counter("api.requests.foo", "api.requests.all").sample(0.8)
val = requests.inrc()
```

Doug Tangren (softprops) 2014
