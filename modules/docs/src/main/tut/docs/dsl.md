---
layout: docs
title: DSL
---

# DSL

If you're a DSLs fan, this library comes with a simple one providing an alternative syntax for retrying actions.
To use it you need to import `com.mdipaola.retry4s.dsl._`.

Some (totally random) examples:

```scala
IO(Random.nextInt(11)).map(num => s"result_$num")
  .retryWithPolicy(limitRetries(10))
  .whenResult((rs, result) => !(result endsWith rs.iterNum.toString))
  .map(str => println(s"final result = $str")).unsafeRunSync()

// equivalent to the example above
retry(IO(Random.nextInt(11)).map(num => s"result_$num"))
  .withPolicy(limitRetries(10))
  .whenResult((rs, result) => !(result endsWith rs.iterNum.toString))
  .map(str => println(s"final result = $str")).unsafeRunSync()
```

```scala
//if you need the action in the form RetryStatus => F[A]
retry((rs: RetryStatus) => IO(s"result_${rs.iterNum}".some))
  .withPolicy(limitRetries(10))
  .whenResult_{ case(_, Some(result)) if !(result endsWith "5") => true}
  .map(str => println(s"final result = $str")).unsafeRunSync()
```

Once you initialised the action and the retry policy you can use:
* `whenResult`: same as [retrying](./retrying.html#retrying)
* `whenError`: same as [recovering](./retrying.html#recovering)
* `onAllErrors`: same as [recoveringAll](./retrying.html#recoveringAll)

`whenResult` and `whenError` have the usual variants:
* `whenResultF` / `whenErrorF` for effectful retry checks
* `whenResult_` / `whenResultF_` / `whenError_` / `whenErrorF_` using partial functions for the check

```scala
IO(Random.nextInt(11))
  .flatMap(num => if (num < 8) IO.raiseError(new IllegalArgumentException) else IO(s"result_$num"))
  .retryWithPolicy(limitRetries(10))
  .whenError_[Throwable]{ case (_, _ : IllegalArgumentException) =>  true }
  .map(str => println(s"final result = $str")).unsafeRunSync()

IO(Random.nextInt(11))
  .flatMap(num => if (num < 8) IO.raiseError(new IllegalArgumentException) else IO(s"result_$num"))
  .retryWithPolicy(limitRetries(10))
  .onAllErrors
  .map(str => println(s"final result = $str")).unsafeRunSync()
```

