package com.mdipaola.retry4s

import cats.{Monad, MonadError}
import cats.effect.Timer

object dsl {
  implicit def toRetryable[F[_] : Monad : Timer, A](action: F[A]): Retryable[F, A] =
    new Retryable[F, A](_ => action)

  implicit def toRetryable2[F[_] : Monad : Timer, A](pd: RetryStatus => F[A]): Retryable[F, A] =
    new Retryable[F, A](pd)

  def retry[F[_] : Monad : Timer, A](action: F[A]): Retryable[F, A] = new Retryable[F, A](_ => action)

  def retry[F[_] : Monad : Timer, A](action: RetryStatus => F[A]): Retryable[F, A] = new Retryable[F, A](action)

  private[dsl] final class Retryable[F[_], A](private val action: RetryStatus => F[A]) extends AnyVal {
    def retryWithPolicy(rp: RetryPolicy[F]): WithPolicy[F, A] =
      new WithPolicy[F, A]((rp, action))

    def withPolicy(rp: RetryPolicy[F]): WithPolicy[F, A] =
      new WithPolicy[F, A]((rp, action))
  }

  private[dsl] final class WithPolicy[F[_], A](private val in: (RetryPolicy[F], RetryStatus => F[A])) extends AnyVal {
    def rp: RetryPolicy[F] = in._1
    def action: RetryStatus => F[A] = in._2

    def whenResult(check: (RetryStatus, A) => Boolean)(implicit F: Monad[F], timer: Timer[F]): F[A] =
      rp.retrying(action)(check)

    def whenResult_(check: PartialFunction[(RetryStatus, A), Boolean])(implicit F: Monad[F], timer: Timer[F]): F[A] =
      rp.retrying_(action)(check)

    def whenResultF(check: (RetryStatus, A) => F[Boolean])(implicit F: Monad[F], timer: Timer[F]): F[A] =
      rp.retryingF(action)(check)

    def whenResultF_(check: PartialFunction[(RetryStatus, A), F[Boolean]])(implicit F: Monad[F], timer: Timer[F]): F[A] =
      rp.retryingF_(action)(check)


    def whenError[E](check: (RetryStatus, E) => Boolean)(implicit ME: MonadError[F, E], timer: Timer[F]): F[A] =
      rp.recovering(action)(check)

    def whenError_[E](check: PartialFunction[(RetryStatus, E), Boolean])(implicit ME: MonadError[F, E], timer: Timer[F]): F[A] =
      rp.recovering_(action)(check)

    def whenErrorF[E](check: (RetryStatus, E) => F[Boolean])(implicit ME: MonadError[F, E], timer: Timer[F]): F[A] =
      rp.recoveringF(action)(check)

    def whenErrorF_[E](check: PartialFunction[(RetryStatus, E), F[Boolean]])(implicit ME: MonadError[F, E], timer: Timer[F]): F[A] =
      rp.recoveringF_(action)(check)


    def onAllErrors[E](implicit ME: MonadError[F, E], timer: Timer[F]): F[A] =
      rp.recoveringAll(action)
  }

}