# stats

collected numbers

```scala
val nums = stats.Stats()
val requests = nums.counter[Int]("api.requests.foo", "api.requests.all")
val = requests.inrc()

```

Doug Tangren (softprops) 2014
