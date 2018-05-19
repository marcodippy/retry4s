package com.mdipaola.retry4s

import cats.effect.IO
import com.mdipaola.retry4s.utils.BoundedArithmetic._
import org.scalacheck._
import org.scalatest.Assertion

import scala.annotation.tailrec
import scala.language.reflectiveCalls

class PoliciesTest extends BaseTest {

  val numOfRetries: Gen[Int] = Gen.choose(0, 100)
  val baseDelay: Gen[Long] = Gen.choose[Long](0, 500)

  test("limitRetries") {
    forAll(numOfRetries, numOfRetries) { (retries, maxRetries) =>
      limitRetries(maxRetries)
        .simulatePolicy(retries)
        .foreach {
          case (itNum, optDelay) =>
            if (itNum >= maxRetries)
              optDelay shouldBe None
            else
              optDelay shouldBe Some(0L)
        }
    }
  }

  test("constantDelay") {
    forAll(numOfRetries, baseDelay) { (retries, baseDelay) =>
      constantDelay(baseDelay)
        .simulatePolicy(retries)
        .foreach { case (_, delay) => delay shouldBe Some(baseDelay) }
    }
  }

  test("exponentialBackoff") {
    forAll(numOfRetries, baseDelay) { (retries, baseDelay) =>
      whenever(baseDelay > 0 && retries > 0) {
        val results = exponentialBackoff(baseDelay).simulatePolicy(retries)

        results.headOption.foreach { case (_, delay) => delay shouldBe Some(baseDelay) }

        results.tail.zip(results)
          .foreach { case ((_, Some(nextDelay)), (_, Some(delay))) => nextDelay shouldBe (delay boundedMult 2L) }
      }
    }
  }

  test("fullJitterBackoff") {
    forAll(numOfRetries, baseDelay) { (retries, baseDelay) =>
      whenever(baseDelay > 0 && (retries > 0)) {
        val simulate = fullJitterBackoff[IO](baseDelay).simulatePolicy(retries)
        val results = simulate.unsafeRunSync()

        results.foreach {
          case (itNum, Some(delay)) => delay shouldBe <=(baseDelay boundedMult (2 boundedPow itNum.toLong))
          case _ => fail
        }

        if (retries > 4) { //minimize odds to have the same results
          val results2 = simulate.unsafeRunSync()
          results2 should !==(results) //TODO rewrite better?
        }
      }
    }
  }

  test("fibonacci") {
    forAll(numOfRetries, baseDelay) { (retries, baseDelay) =>
      @tailrec
      def test(l: List[Option[DelayInMillis]]): Assertion =
        l match {
          case Nil => retries shouldBe 0
          case Some(delay1) :: Nil => {
            delay1 shouldBe baseDelay
          }
          case Some(delay1) :: Some(delay2) :: Nil => {
            delay1 shouldBe baseDelay
            delay2 shouldBe baseDelay
          }
          case Some(delay3) :: Some(delay2) :: Some(delay1) :: _ => {
            delay3 shouldBe (delay1 boundedPlus delay2)
            test(l.tail)
          }
          case _ => fail
        }

      val delays = fibonacciBackoff(baseDelay).simulatePolicy(retries).map(_._2)
      test(delays.reverse)
    }
  }
}
