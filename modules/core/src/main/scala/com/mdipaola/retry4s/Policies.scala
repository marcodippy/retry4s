package com.mdipaola.retry4s

import cats.Id
import cats.effect.Sync
import cats.implicits._

import scala.annotation.tailrec

trait Policies {

  def limitRetries(maxNumOfRetries: Int): RetryPolicy[Id] =
    retryPolicy(rs => if (rs.iterNum >= maxNumOfRetries) Option.empty else Some(0L))

  def constantDelay(delay: DelayInMillis): RetryPolicy[Id] =
    retryPolicy(_ => Some(delay))

  def exponentialBackoff(baseDelay: DelayInMillis): RetryPolicy[Id] =
    retryPolicy(rs => Some(baseDelay * scala.math.pow(2, rs.iterNum.doubleValue()).toLong)) //TODO fix this shit

  def fullJitterBackoff[F[_]](baseDelay: DelayInMillis, rnd: Int => DelayInMillis = scala.util.Random.nextInt(_).toLong)
                             (implicit F: Sync[F]): RetryPolicy[F] =
    retryPolicyF(rs => {
      val tmp = (baseDelay * scala.math.pow(2, rs.iterNum.doubleValue())).toInt //TODO fix this shit
      F.delay(rnd(tmp + 1)).map(_.some)
    })

  def fibonacciBackoff(baseDelay: DelayInMillis): RetryPolicy[Id] = {
    @tailrec
    def fib(i: Int, num: DelayInMillis, acc: DelayInMillis): DelayInMillis =
      if (i == 0) num
      else
        fib(i - 1, acc, acc + num) //TODO boundedPlus

    retryPolicy(rs => fib(rs.iterNum + 1, 0L, baseDelay).some)
  }
}
