package examples

import cats._
import cats.effect._
import cats.implicits._
import com.mdipaola.retry4s._

object PolicyDefinition {

  // predefined policies
  val atMost5retries: RetryPolicy[Id] = limitRetries(5)
  val always50ms: RetryPolicy[Id] = constantDelay(50)
  val exponential: RetryPolicy[Id] = exponentialBackoff(100)
  val fibonacci: RetryPolicy[Id] = fibonacciBackoff(100)
  val fullJitter: RetryPolicy[IO] = fullJitterBackoff[IO](100)

  // custom policies
  val customPolicy: RetryPolicy[Id] = retryPolicy(rs => if ((rs.iterNum % 2) == 0) 0L.some else 50L.some)

  val customIo: RetryPolicy[IO] = retryPolicyF[IO](rs => IO { scala.util.Random.nextLong().some })


  // policy transformation
  val newPolicy: RetryPolicy[Id] = always50ms |+| atMost5retries

  val cappedExponentialPolicy: RetryPolicy[Id] = exponential.capDelay(400)

  val limitedByCumulativeDelayAndNumOfRetries: RetryPolicy[IO] =
    fullJitter.limitRetriesByCumulativeDelay(1000) |+| limitRetries(7)


  val explicitlyLifted: RetryPolicy[IO] = atMost5retries.liftTo[IO]
  val implicitlyLifted: RetryPolicy[IO] = atMost5retries


  val combined = List(atMost5retries, always50ms).combineAll

  val combinePureAndImpure = List[RetryPolicy[IO]](fullJitter, atMost5retries).combineAll
  val combinePureAndImpure2 = List(fullJitter, atMost5retries.liftTo[IO]).combineAll

}
