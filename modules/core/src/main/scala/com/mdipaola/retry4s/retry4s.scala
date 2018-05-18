package com.mdipaola

import cats.Id
import cats.data.{Kleisli, OptionT}

package object retry4s extends Policies {
  type DelayInMillis = Long

  def retryPolicyF[F[_]](f: RetryStatus => F[Option[DelayInMillis]]): RetryPolicy[F] =
    RetryPolicy[F](Kleisli((rs: RetryStatus) => OptionT(f(rs))))

  def retryPolicy(f: RetryStatus => Option[DelayInMillis]): RetryPolicy[Id] =
    RetryPolicy[Id](Kleisli((rs: RetryStatus) => OptionT.fromOption[Id](f(rs))))

  import cats.FlatMap
  import cats.data.Kleisli

  def tapWithF[F[_], A, B, C](k: Kleisli[F, A, B])(f: (A, B) => F[C])(implicit F: FlatMap[F]): Kleisli[F, A, C] =
    Kleisli((a: A) => F.flatMap(k.run(a))(b => f(a, b)))
}