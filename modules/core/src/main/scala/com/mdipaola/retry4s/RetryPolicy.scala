package com.mdipaola.retry4s

import cats._
import cats.data._
import cats.effect._
import cats.implicits._
import utils.BoundedArithmetic._
import scala.language.reflectiveCalls

case class RetryPolicy[F[_]] protected[RetryPolicy](policy: Kleisli[OptionT[F, ?], RetryStatus, DelayInMillis]) {

  def apply(rs: RetryStatus)(implicit F: Functor[F]): F[Option[RetryStatus]] =
    applyPolicy(rs).value

  def delay(rs: RetryStatus)(implicit F: Monad[F], timer: Timer[F]): F[Option[RetryStatus]] =
    applyAndDelay(rs).value

  def nextDelay(rs: RetryStatus)(implicit F: Functor[F]): F[Option[DelayInMillis]] =
    applyPolicy(rs).subflatMap(rs => rs.previousDelay).value

  protected[retry4s] def applyPolicy(rs: RetryStatus)(implicit F: Functor[F]): OptionT[F, RetryStatus] =
    policy(rs).map(delay => RetryStatus(rs.iterNum + 1, rs.cumulativeDelay boundedPlus delay, previousDelay = Some(delay)))

  protected[retry4s] def applyAndDelay(rs: RetryStatus)(implicit F: Monad[F], timer: Timer[F]): OptionT[F, RetryStatus] =
    applyPolicy(rs).flatMap(retryStatus => {
      retryStatus.previousDelay match {
        case Some(delay) => OptionT.liftF(timer.sleep(delay.toFiniteDurationMs).map(_ => retryStatus))
        case None => OptionT.none[F, RetryStatus]
      }
    })

  def limitRetriesByDelay(i: DelayInMillis)(implicit F: Monad[F]): RetryPolicy[F] = {
    def limit(delay: DelayInMillis): OptionT[F, DelayInMillis] =
      if (delay >= i) OptionT.none[F, DelayInMillis]
      else OptionT.pure[F](delay)

    RetryPolicy(policy.flatMapF(limit))
  }

  def limitRetriesByCumulativeDelay(cumulativeLimit: DelayInMillis)(implicit F: Monad[F]): RetryPolicy[F] = {
    def limit(retryStatus: RetryStatus, currDelay: DelayInMillis): OptionT[F, DelayInMillis] =
      if ((retryStatus.cumulativeDelay boundedPlus currDelay) > cumulativeLimit) OptionT.none[F, DelayInMillis]
      else OptionT.pure[F](currDelay)

    RetryPolicy(tapWithF(policy)(limit))
  }

  def capDelay(maxDelay: DelayInMillis)(implicit F: Functor[F]): RetryPolicy[F] =
    RetryPolicy(policy.map(fd => fd min maxDelay))

  def retrying_[A](f: RetryStatus => F[A])(check: PartialFunction[(RetryStatus, A), Boolean])(implicit F: Monad[F], timer: Timer[F]): F[A] =
    retryingF[A](f)((rs: RetryStatus, a: A) => F.pure(check.applyOrElse[(RetryStatus, A), Boolean]((rs, a), _ => false)))

  def retrying[A](f: RetryStatus => F[A])(check: (RetryStatus, A) => Boolean)(implicit F: Monad[F], timer: Timer[F]): F[A] =
    retryingF[A](f)((rs: RetryStatus, a: A) => F.pure(check(rs, a)))

  def retryingF_[A](f: RetryStatus => F[A])(check: PartialFunction[(RetryStatus, A), F[Boolean]])(implicit F: Monad[F], timer: Timer[F]): F[A] =
    retryingF[A](f)((rs: RetryStatus, a: A) => check.applyOrElse[(RetryStatus, A), F[Boolean]]((rs, a), _ => F.pure(false)))

  def retryingF[A](f: RetryStatus => F[A])(check: (RetryStatus, A) => F[Boolean])(implicit F: Monad[F], timer: Timer[F]): F[A] = {
    def go(rs: RetryStatus): F[A] =
      f(rs).flatMap(res =>
        check(rs, res).ifM(
          applyAndDelay(rs).semiflatMap(nextRs => go(nextRs)).getOrElseF(F.pure(res)),
          F.pure(res)
        )
      )

    go(RetryStatus.default)
  }

  def recovering[E] = new RetryPolicy.RecoveringPartiallyApplied[F, E](this)
  def recovering_[E] = new RetryPolicy.RecoveringPartiallyApplied_[F, E](this)
  def recoveringF[E] = new RetryPolicy.RecoveringFPartiallyApplied[F, E](this)
  def recoveringF_[E] = new RetryPolicy.RecoveringFPartiallyApplied_[F, E](this)

  def recoveringAll[E, A](f: RetryStatus => F[A])(implicit F: MonadError[F, E], timer: Timer[F]): F[A] = {
    def go(s: RetryStatus): F[A] =
      f(s).handleErrorWith(err =>
        applyAndDelay(s).semiflatMap(rs => go(rs)).getOrElseF(F.raiseError[A](err))
      )

    go(RetryStatus.default)
  }

  def stepping[E, A](f: RetryStatus => F[A],
                     check: E => F[Boolean],
                     schedule: RetryStatus => F[Unit],
                     s: RetryStatus
                    )(implicit F: MonadError[F, E]): F[Option[A]] = {
    def recover(err: E): F[Option[A]] =
      check(err).ifM(
        applyPolicy(s)
          .semiflatMap(schedule).map(_ => None)
          .getOrElseF(F.raiseError[Option[A]](err)), F.raiseError[Option[A]](err))

    f(s)
      .map(_.some)
      .recoverWith { case err => recover(err) }
  }

  def simulatePolicy(n: Int)(implicit F: Monad[F]): F[List[(Int, Option[DelayInMillis])]] = {
    def evalRetry(i: Int): StateT[F, RetryStatus, (Int, Option[DelayInMillis])] =
      for {
        stat <- StateT.get[F, RetryStatus]
        delay <- StateT.liftF(policy(stat).value)
        _ <- StateT.set[F, RetryStatus](
          stat.copy(
            iterNum = i + 1,
            cumulativeDelay = stat.cumulativeDelay boundedPlus delay.getOrElse(0L),
            previousDelay = delay
          )
        )
      } yield (i, delay)

    List.range(0, n).traverse(i => evalRetry(i)).runA(RetryStatus.default)
  }

}

object RetryPolicy {

  protected[retry4s] def apply[F[_]](f: RetryStatus => OptionT[F, DelayInMillis]): RetryPolicy[F] =
    RetryPolicy[F](Kleisli(f))

  implicit def liftPureTo[F[_]](rp: RetryPolicy[Id])(implicit F: Applicative[F]): RetryPolicy[F] =
    RetryPolicy[F]((rs: RetryStatus) => OptionT(F.pure(rp.policy(rs).value)))

  implicit def retryPolicyPureOps(rp: RetryPolicy[Id]): RetryPolicyPureOps =
    new RetryPolicyPureOps(rp)

  final class RetryPolicyPureOps(val rp: RetryPolicy[Id]) extends AnyVal {
    def liftTo[F2[_]](implicit F: Applicative[F2]): RetryPolicy[F2] =
      RetryPolicy.liftPureTo[F2](rp)
  }

  implicit def monoidForRetryPolicy[F[_] : Applicative]: Monoid[RetryPolicy[F]] =
    new Monoid[RetryPolicy[F]] {
      override def empty: RetryPolicy[F] =
        retryPolicyF(rs => 0L.some.pure[F])

      override def combine(x: RetryPolicy[F], y: RetryPolicy[F]): RetryPolicy[F] =
        retryPolicyF(rs => (x.policy(rs).toNested, y.policy(rs).toNested).mapN((fd1, fd2) => fd1 max fd2).value)
    }

  private[retry4s] final class RecoveringPartiallyApplied[F[_], E](val rp: RetryPolicy[F]) extends AnyVal {
    def apply[A](f: RetryStatus => F[A])(check: (RetryStatus, E) => Boolean)
                (implicit F: MonadError[F, E], timer: Timer[F]): F[A] =
      rp.recoveringF(f)((rs: RetryStatus, err: E) => F.pure(check(rs, err)))
  }

  private[retry4s] final class RecoveringPartiallyApplied_[F[_], E](val rp: RetryPolicy[F]) extends AnyVal {
    def apply[A](f: RetryStatus => F[A])(check: PartialFunction[(RetryStatus, E), Boolean])
                (implicit F: MonadError[F, E], timer: Timer[F]): F[A] =
      rp.recoveringF(f)((rs: RetryStatus, err: E) => F.pure(check.applyOrElse[(RetryStatus, E), Boolean]((rs, err), _ => false)))
  }

  private[retry4s] final class RecoveringFPartiallyApplied[F[_], E](val rp: RetryPolicy[F]) extends AnyVal {
    def apply[A](f: RetryStatus => F[A])(check: (RetryStatus, E) => F[Boolean])
                (implicit F: MonadError[F, E], timer: Timer[F]): F[A] = {
      def go(s: RetryStatus): F[A] =
        f(s).handleErrorWith(err =>
          check(s, err).ifM(
            rp.applyAndDelay(s).semiflatMap(rs => go(rs)).getOrElseF(F.raiseError[A](err)),
            F.raiseError[A](err)
          )
        )

      go(RetryStatus.default)
    }
  }

  private[retry4s] final class RecoveringFPartiallyApplied_[F[_], E](val rp: RetryPolicy[F]) extends AnyVal {
    def apply[A](f: RetryStatus => F[A])(check: PartialFunction[(RetryStatus, E), F[Boolean]])
                (implicit F: MonadError[F, E], timer: Timer[F]): F[A] =
      rp.recoveringF[E](f)((rs, err) => check.applyOrElse[(RetryStatus, E), F[Boolean]]((rs, err), _ => F.pure(false)))
  }

}

case class RetryStatus(iterNum: Int, cumulativeDelay: DelayInMillis, previousDelay: Option[DelayInMillis])

object RetryStatus {
  def default: RetryStatus = RetryStatus(0, 0L, None)
}