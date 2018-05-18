package com.mdipaola.retry4s

import cats._

class MonoidInstanceTest extends BaseTest {

  def M[F[_] : Monad]: Monoid[RetryPolicy[F]] = implicitly[Monoid[RetryPolicy[F]]]

  test("associativity") {
    forAll { (rs: RetryStatus, rp1: RetryPolicy[Id], rp2: RetryPolicy[Id], rp3: RetryPolicy[Id]) =>
      implicit val _ = rs
      ((rp1 |+| rp2) |+| rp3) should ===(rp1 |+| (rp2 |+| rp3))
    }
  }

  test("leftIdentity") {
    forAll { (rs: RetryStatus, rp: RetryPolicy[Id]) =>
      implicit val _ = rs
      rp should ===(M[Id].empty |+| rp)
    }
  }

  test("rightIdentity") {
    forAll { (rs: RetryStatus, rp: RetryPolicy[Id]) =>
      implicit val _ = rs
      rp should ===(rp |+| M[Id].empty)
    }
  }

  test("repeat0") {
    forAll { (rs: RetryStatus, rp: RetryPolicy[Id]) =>
      implicit val _ = rs
      M[Id].combineN(rp, 0) should ===(M[Id].empty)
    }
  }

  test("repeat1") {
    forAll { (rs: RetryStatus, rp: RetryPolicy[Id]) =>
      implicit val _ = rs
      M[Id].combineN(rp, 1) should ===(rp)
    }
  }

  test("repeat2") {
    forAll { (rs: RetryStatus, rp: RetryPolicy[Id]) =>
      implicit val _ = rs
      M[Id].combineN(rp, 1) should ===(rp |+| rp)
    }
  }

  test("collect0") {
    forAll { (rs: RetryStatus, rp: RetryPolicy[Id]) =>
      implicit val _ = rs
      M[Id].combineAll(Nil) should ===(M[Id].empty)
    }
  }

  test("combineAll") {
    forAll { (rs: RetryStatus, rps: Vector[RetryPolicy[Id]]) =>
      implicit val _ = rs
      M[Id].combineAll(rps) should ===((M[Id].empty +: rps).reduce(M[Id].combine))
    }
  }

  test("combineAllOption") {
    forAll { (rs: RetryStatus, rps: Vector[RetryPolicy[Id]]) =>
      implicit val _: RetryStatus = rs
      M[Id].combineAllOption(rps) should ===(rps.reduceOption(M[Id].combine))
    }
  }

  test("isId") {
    forAll { (rs: RetryStatus, rp: RetryPolicy[Id]) =>
      implicit val _: RetryStatus = rs
      (rp === M[Id].empty) should ===(M[Id].isEmpty(rp))
    }
  }

  test("combine") {
    forAll { (rs: RetryStatus, dlOp1: Option[Long], dlOp2: Option[Long]) =>
      implicit val _: RetryStatus = rs

      val res: RetryPolicy[Id] = retryPolicy(rs => dlOp1) |+| retryPolicy(rs => dlOp2)

      (dlOp1, dlOp2) match {
        case (None, _) | (_, None) => res should ===(retryPolicy(rs => None))
        case (Some(dl1), Some(dl2)) => res should ===(retryPolicy(rs => Some(dl1 max dl2)))
      }
    }
  }

}
