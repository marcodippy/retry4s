package com.mdipaola.retry4s

import cats.Id
import cats.effect.Sync
import cats.implicits._
import com.mdipaola.retry4s.utils.BoundedArithmetic._

import scala.annotation.tailrec
import scala.language.reflectiveCalls

trait Policies {

  def limitRetries(maxNumOfRetries: Int): RetryPolicy[Id] =
    retryPolicy(rs => if (rs.iterNum >= maxNumOfRetries) Option.empty else Some(0L))

  def constantDelay(delay: DelayInMillis): RetryPolicy[Id] =
    retryPolicy(_ => Some(delay))

  def exponentialBackoff(baseDelay: DelayInMillis): RetryPolicy[Id] =
    retryPolicy(rs => Some(baseDelay boundedMult (2L boundedPow rs.iterNum.toLong)))

  def fullJitterBackoff[F[_]](baseDelay: DelayInMillis)
                             (implicit F: Sync[F]): RetryPolicy[F] = {
    retryPolicyF(rs => {
      val tmp = baseDelay boundedMult (2L boundedPow rs.iterNum.toLong)
      val rnd = F.delay((scala.util.Random.nextDouble() * (tmp + 1L)).toLong)
      rnd.map(_.some)
    })
  }


  def fibonacciBackoff(baseDelay: DelayInMillis): RetryPolicy[Id] = {
    @tailrec
    def fib(i: Int, num: DelayInMillis, acc: DelayInMillis): DelayInMillis =
      if (i == 0) num
      else
        fib(i - 1, acc, acc boundedPlus num)

    retryPolicy(rs => fib(rs.iterNum + 1, 0L, baseDelay).some)
  }
}
