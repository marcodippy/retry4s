---
layout: docs
title: Creating a retry policy
---

# Creating a retry policy

A `RetryPolicy[F]` is essentially a function `RetryStatus => F[Option[Long]]` 
taking as input some stats about the previous retries and possibly returning a delay in millisecond for the next retry 
(`None` means _no more retries_). `F` is the effect used to calculate the next retry.


The easiest way to create a retry policy is choosing one of the predefined ones:

```tut:silent
import cats._
import cats.effect._
import com.mdipaola.retry4s._

// retry immediately, but only up to 5 times
val atMost5retries: RetryPolicy[Id] = limitRetries(5)

// retry infinite times with a delay of 50 ms between each retry
val always50ms: RetryPolicy[Id] = constantDelay(50)

// delay(n) = delay(n - 2) + delay(n - 1), starting with a base delay of 100 ms
// No limit on the max number of retries
val fibonacci: RetryPolicy[Id] = fibonacciBackoff(100)

// Grow delay exponentially each iteration. Each delay will increase by a factor of two.
// No limit on the max number of retries
val exponential: RetryPolicy[Id] = exponentialBackoff(100)

// https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
// No limit on the max number of retries
val fullJitter: RetryPolicy[IO] = fullJitterBackoff[IO](100)
```

Note that while the first four strategies are `RetryPolicy[Id]`, the `fullJitter` one is parametrised by `IO`: 
this is because it needs to generate a random number, and to do this in a pure fashion, 
`F` must be able to suspend side effects (there must be a `cats.effect.Sync` instance for `F`).


## Combining policies

There is a (commutative) `Monoid` instance defined for `RetryPolicy[F]` (where `F` is an `Applicative`) for which
* `empty` is a policy that retries immediately (`delay = 0`) with no limit on the number of retries
* `combine` creates a policy using the larger of the delays that each input policy returns 
(if either policy returns `None`, the combined policy returns `None` as well, meaning "stop retrying")

This means that we can easily combine policies as shown here:

```tut:book
import cats.implicits._

always50ms |+| atMost5retries //max 5 retries with 50 ms delay between each of them

exponentialBackoff(100) |+| limitRetries(5)

// atMost5retries is a RetryPolicy[Id], but in this case it's automatically lifted to IO (via an implicit conversion)
fullJitterBackoff[IO](100) |+| atMost5retries

// if you want to specify the policy to be lifted first, you need to lift it manually
atMost5retries.liftTo[IO] |+| fullJitterBackoff[IO](100)

// in some cases you need to guide scala's inference a bit 
List[RetryPolicy[IO]](fullJitter, atMost5retries).combineAll

List(fullJitter, atMost5retries.liftTo[IO]).combineAll
```

As the name suggests, `liftTo[F]` is used to lift a `RetryPolicy[Id]` to a `RetryPolicy[F]`
 


## Transforming policies

`RetryPolicy` defines a few combinators to transform a policy

```tut:silent
// the delay won't grow above 500 ms
exponentialBackoff(50).capDelay(500) 

// the policy will stop retrying once delay for a single retry reached 500 ms 
exponentialBackoff(50).limitRetriesByDelay(500)

//the policy will stop retrying once the cumulative delay over all retries reached 500 ms 
exponentialBackoff(50).limitRetriesByCumulativeDelay(500)
```


## Creating a custom policy

If you need anything more complex, creating a custom policy is pretty straightforward:

```tut:book
val policy1 = retryPolicy(rs => if ((rs.iterNum % 2) == 0) 0L.some else 50L.some)

val policy2 = retryPolicyF[IO](rs => IO { scala.util.Random.nextLong().some })
```


 




