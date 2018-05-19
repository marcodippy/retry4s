package com.mdipaola.retry4s

import cats.{Eq, Functor, Monad}
import cats.data.OptionT
import cats.effect.Timer
import cats.tests.CatsSuite
import org.scalacheck.{Arbitrary, Cogen, Gen}

import scala.concurrent.duration.{FiniteDuration, TimeUnit}

trait BaseTest extends CatsSuite {
  def testTimer[F[_]](checkMillis: Long => Unit)(implicit F: Monad[F]): Timer[F] = new Timer[F] {
    override def clockRealTime(unit: TimeUnit): F[DelayInMillis] = fail

    override def clockMonotonic(unit: TimeUnit): F[DelayInMillis] = fail

    override def shift: F[Unit] = fail

    override def sleep(duration: FiniteDuration): F[Unit] =
      F.pure(checkMillis(duration.length))
  }

  val delayGen: Gen[Long] = Gen.choose[Long](0, 500)
  val retriesGen: Gen[Int] = Gen.choose(0, 100)

  case class NumOfRetries(get: Int)

  implicit val arbNumOfRetries: Arbitrary[NumOfRetries] = Arbitrary(retriesGen.map(NumOfRetries))

  case class Delay(get: Long)

  implicit val arbDelay: Arbitrary[Delay] = Arbitrary(delayGen.map(Delay))

  case class DelayOp(get: Option[Long])

  implicit val arbDelayOp: Arbitrary[DelayOp] = Arbitrary(Gen.option(delayGen).map(DelayOp))

  implicit val arbRetryStatus: Arbitrary[RetryStatus] = Arbitrary {
    for {
      iterNum <- retriesGen
      cumulativeDelay <- Gen.choose(0L, 10000)
      previousDelay <- Gen.option(delayGen)
    } yield
      RetryStatus(iterNum, cumulativeDelay, previousDelay)
  }


  implicit val retryStatusCogen: Cogen[RetryStatus] = Cogen(_.cumulativeDelay)
  val arbPolicy: Arbitrary[RetryStatus => DelayOp] = Arbitrary.arbFunction1[RetryStatus, DelayOp]

  implicit def arbRetryPolicyPure[F[_]](implicit A: Arbitrary[RetryStatus => DelayOp]): Arbitrary[RetryPolicy[F]] = Arbitrary {
    for {
      f <- Arbitrary.arbitrary[RetryStatus => DelayOp].map(_.andThen(_.get))
    } yield retryPolicy(f).asInstanceOf[RetryPolicy[F]]
  }

  implicit def eqForRetryPolicy[F[_] : Functor](implicit rs: RetryStatus, eqRs: Eq[OptionT[F, RetryStatus]]): Eq[RetryPolicy[F]] =
    new Eq[RetryPolicy[F]] {
      override def eqv(rp1: RetryPolicy[F], rp2: RetryPolicy[F]): Boolean =
        eqRs.eqv(rp1.applyPolicy(rs), rp2.applyPolicy(rs))

    }

  implicit def eqForRetryStatus: Eq[RetryStatus] = Eq.fromUniversalEquals[RetryStatus]

  case class ActionResult(iterNum: Int, result: String)

}
