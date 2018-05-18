package com.mdipaola.retry4s

import cats.effect.IO
import org.scalacheck._
import org.scalatest.Assertion

import scala.annotation.tailrec

class PoliciesTest extends BaseTest {

  //TODO increase these once you implemented bounded arithmetic
  val numOfRetries: Gen[Int] = Gen.choose(0, 20)
  val baseDelay: Gen[Long] = Gen.choose[Long](0, 300)

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
          .foreach { case ((_, Some(nextDelay)), (_, Some(delay))) => nextDelay shouldBe (delay * 2) }
      }
    }
  }

  test("fullJitterBackoff") {
    forAll(numOfRetries, baseDelay) { (retries, baseDelay) =>
      whenever(baseDelay > 0 && (retries > 0)) {
        val simulate = fullJitterBackoff[IO](baseDelay).simulatePolicy(retries)
        val results = simulate.unsafeRunSync()
        results.foreach {
          case (itNum, Some(delay)) => delay shouldBe <=(baseDelay * scala.math.pow(2, itNum.doubleValue()).toLong)
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
            delay3 shouldBe (delay1 + delay2)
            test(l.tail)
          }
          case _ => fail
        }

      val delays = fibonacciBackoff(baseDelay).simulatePolicy(retries).map(_._2)
      test(delays.reverse)
    }
  }
}
