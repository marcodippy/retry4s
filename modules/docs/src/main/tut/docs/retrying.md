---
layout: docs
title: Retrying
---

# Retrying actions

There are three combinators to apply your retry policies:

* [`retrying`](#retrying) to retry actions that use their output type to signal a failure (like `Option`, `Either`, `EitherT`)
* [`recovering`](#recovering) lets you specify the retry condition on a specific error `E` when the result is a `MonadError[F, E]` 
* [`recoveringAll`](#recoveringAll) will retry any error `E` when the result is a `MonadError[F, E]`

The first important thing to know is that your action returning an `A` needs to be in the form `RetryStatus => F[A]`.
This is because you might need additional information regarding the retry status (eg: the attempt number) when performing your action.

All the retry combinators require at least that `F` is a `Monad` and there is a `cats.effect.Timer[F]` available (needed to delay the retries). 



## `retrying`

```scala
def retrying[A](f: RetryStatus => F[A])                         //action to retry
               (check: (RetryStatus, A) => Boolean)             //wheter to retry or not
               (implicit F: Monad[F], timer: Timer[F]): F[A]
```

Example:
```scala
val action : RetryStatus => IO[Option[String]] =
    rs => IO(println("running action...")).map(_ => None)

limitRetries(3).liftTo[IO]
  .retrying(action)((retryStatus, result) => result.isEmpty)
  .map(println).unsafeRunSync()  

/* when run will produce
    running action...
    running action...
    running action...
    running action...
    None
*/
```

If your retry check requires an effect `F` you can use the `retryingF` variant:
```scala
def retryingF[A](f: RetryStatus => F[A])                         //action to retry
               (check: (RetryStatus, A) => F[Boolean])          //wheter to retry or not
               (implicit F: Monad[F], timer: Timer[F]): F[A]
```
```scala
limitRetries(3).liftTo[IO]
  .retryingF(action)((rs, result) => IO(println(s"result $result at retry ${rs.iterNum}")).map(_ => result.isEmpty))
  
/* when run will produce: 
    running action...
    result None at retry 0
    running action...
    result None at retry 1
    running action...
    result None at retry 2
    running action...
    result None at retry 3
    None
*/
```
If you like partial functions, both `retrying` and `retryingF` have a variant where the retry check is a partial function 
(`retrying_` and `retryingF_` respectively)

```scala
limitRetries(3).liftTo[IO].retrying_(action){ case (_, None) => true }
```



## `recovering`

```scala
def recovering[A](f: RetryStatus => F[A])                                   //action to retry
                 (check: (RetryStatus, E) => Boolean)                       //wheter to retry or not
                 (implicit F: MonadError[F, E], timer: Timer[F]): F[A]
```

This time the monad used for the return type already has error handling capabilities (`MonadError`) 
and the retry check is based on the error `E` (eg: for `cats.effect.IO` it's `Throwable`).  

Example:
```scala
val action: RetryStatus => IO[String] =
  rs => IO(println("running action...")) *> (
    if (rs.iterNum < 3) IO.raiseError[String](new IllegalArgumentException("boom"))
    else IO(s"result_${rs.iterNum}")
  )

limitRetries(5).liftTo[IO]
  .recoveringAll[Throwable](action) {                    // here you need to specify the type of the error to handle
    case (_, _: IllegalArgumentException) => true
    case _ => false
  }
  .map(println).unsafeRunSync()  

/* when run will produce
    running action...
    running action...
    running action...
    running action...
    result_3
*/
```

As `retrying`, also `recovering` has a `recoveringF` variant (for effectful retry checks) 
and `recovering_` and `recoveringF_` using partial functions for the check. 



## `recoveringAll` 

```scala
def recovering[A](f: RetryStatus => F[A])(implicit F: MonadError[F, E], timer: Timer[F]): F[A]
```

This one only takes the action to retry and will retry on any type of error `E`


Example:
```scala
val action: RetryStatus => IO[String] =
  rs =>
    if (rs.iterNum < 3) IO.raiseError[String](new IllegalArgumentException("boom"))
    else IO(s"result_${rs.iterNum}")

limitRetries(5).liftTo[IO]
  .recoveringAll(action)
  .map(println).unsafeRunSync()
  
/* when run will produce
    running action...
    running action...
    running action...
    running action...
    result_3
*/
```

---

## `simulatePolicy`

`RetryPolicy[F]` defines also a function for simulating the policy:
```scala
def simulatePolicy(n: Int)(implicit F: Monad[F]): F[List[(Int, Option[DelayInMillis])]]
``` 
taking a number of retries to simulate and returning, for each retry, a tuple of the number of attempt and the optional
delay calculated at that iteration.

Example:
```scala
val rp = exponentialBackoff(100L) |+| limitRetries(5)
rp.simulatePolicy(7).foreach(println)

/* will produce
(0,Some(100))
(1,Some(200))
(2,Some(400))
(3,Some(800))
(4,Some(1600))
(5,None)
(6,None)
*/
 
 
val rp2 = fullJitterBackoff[IO](100) |+| limitRetries(3)
rp2.simulatePolicy(5).map(_.foreach(println)).unsafeRunSync()

/* will produce
(0,Some(51))
(1,Some(168))
(2,Some(241))
(3,None)
(4,None)
*/
```