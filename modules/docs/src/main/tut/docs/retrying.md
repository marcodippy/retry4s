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
def retrying[A](f: RetryStatus => F[A])                         //action to retry
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



## `recoveringAll` 

