package examples

import cats.implicits._
import cats.effect._
import com.mdipaola.retry4s._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random


object Retrying extends App {
  val retryPolicy: RetryPolicy[IO] = limitRetries(8)

  val action: RetryStatus => IO[String] =
    rs => for {
      _   <- IO(println(s"Retrieving a string (attempt n ${rs.iterNum})..."))
      str <- IO {/* do stuff */ s"result_${8 - rs.iterNum}"}
      _   <- IO(println(s"retrieved $str!"))
    } yield str


  val res: IO[String] = retryPolicy.retrying(action)((retryStatus, result) => !result.endsWith(retryStatus.iterNum.toString))

  res.map(str => println(s"final result = $str")).unsafeRunSync() // final result = result_4
}

object Retrying2 extends App {
  val action: RetryStatus => IO[Either[String, String]] =
    rs => IO {
      if (rs.iterNum < 4) "oops".asLeft else s"result_${rs.iterNum}".asRight
    }

  limitRetries(5).liftTo[IO]
    .retrying(action)((_, result) => result.isLeft)
    .map(result => println(s"$result"))
    .unsafeRunSync() // Right(result_4)

  limitRetries(2).liftTo[IO]
    .retrying(action)((_, result) => result.isLeft)
    .map(result => println(s"$result"))
    .unsafeRunSync() // Left(oops)
}

object RetryingF extends App {
  val action: RetryStatus => IO[Either[String, String]] =
    rs => IO {
      if (rs.iterNum < 4) "oops".asLeft else s"result_${rs.iterNum}".asRight
    }

  val rp = limitRetries(5).liftTo[IO] |+| exponentialBackoff(100)

  val check: (RetryStatus, Either[String, String]) => IO[Boolean] =
    (rs, result) => IO(result.isLeft).flatTap(isError =>
      if (isError) {
        for {
          nextDelayOp <- rp.nextDelay(rs)
          retryingStr = nextDelayOp.fold("This was last retry :(")(del => s"Trying again in $del ms")
          _ <- IO(println(s"Something went wrong... $retryingStr"))
        } yield ()
      }
      else IO.unit
    )

  rp.retryingF(action)(check)
    .map(result => println(s"$result"))
    .unsafeRunSync() // Right(result_4)

  (limitRetries(2).liftTo[IO] |+| exponentialBackoff(100))
    .retryingF(action)(check)
    .map(result => println(s"$result"))
    .unsafeRunSync() // Left(oops)
}

object RetryingWithPartialFunction extends App {
  val action: RetryStatus => IO[Either[String, String]] = rs => IO {
    if (rs.iterNum < 4) "oops".asLeft else s"result_${rs.iterNum}".asRight
  }

  limitRetries(5).liftTo[IO]
    .retrying_(action){ case (_, a) => a.isLeft }
    .map(result => println(s"$result"))
    .unsafeRunSync() // Right(result_4)

  limitRetries(5).liftTo[IO]
    .retryingF_(action){ case (_, a) => IO(a.isLeft) }
    .map(result => println(s"$result"))
    .unsafeRunSync() // Right(result_4)
}



object RetryingWithDSL extends App {
  import com.mdipaola.retry4s.dsl._

  val retryPolicy: RetryPolicy[IO] = limitRetries(10)

  val action: IO[String] = IO(Random.nextInt(11)).map(num => s"result_$num")

  action
    .retryWithPolicy(retryPolicy)
    .whenResult((rs, result) => !(result endsWith rs.iterNum.toString))
    .map(str => println(s"final result = $str")).unsafeRunSync()

  retry(action)
    .withPolicy(retryPolicy)
    .whenResult((rs, result) => !(result endsWith rs.iterNum.toString))
    .map(str => println(s"final result = $str")).unsafeRunSync()
}

object Recovering extends App {
  val action: RetryStatus => IO[String] =
    rs =>
      if (rs.iterNum < 4) IO.raiseError[String](new IllegalArgumentException("boom"))
      else IO(s"result_${rs.iterNum}")

  limitRetries(5).liftTo[IO]
    .recovering[Throwable](action) {
      case (_, _: IllegalArgumentException) => true
      case _ => false
    }
    .map(result => println(s"$result"))
    .unsafeRunSync() // result_4
}

object RecoveringF extends App {
  val action: RetryStatus => IO[String] =
    rs =>
      if (rs.iterNum < 4) IO.raiseError[String](new IllegalArgumentException("boom"))
      else IO(s"result_${rs.iterNum}")

  limitRetries(5).liftTo[IO]
    .recoveringF[Throwable](action) {
      case (rs, _: IllegalArgumentException) => IO(println(s"this is retriable! - $rs")) *> IO.pure(true)
      case _ => IO.pure(false)
    }
    .map(println)
    .unsafeRunSync() // result_4
}

object RecoveringWithPartialFunction extends App {
  val action: RetryStatus => IO[String] =
    rs =>
      if (rs.iterNum < 4) IO.raiseError[String](new IllegalArgumentException("boom"))
      else IO(s"result_${rs.iterNum}")

  limitRetries(5).liftTo[IO]
    .recovering_[Throwable](action) { case (_, _: IllegalArgumentException) => true }
    .map(println)
    .unsafeRunSync() // result_4

  limitRetries(5).liftTo[IO]
    .recoveringF_[Throwable](action) { case (_, _: IllegalArgumentException) => IO(true) }
    .map(println)
    .unsafeRunSync() // result_4
}


object RecoveringWithDSL extends App {
  import com.mdipaola.retry4s.dsl._

  val retryPolicy: RetryPolicy[IO] = limitRetries(10)

  val action: IO[String] =
    for {
      rnd <- IO(Random.nextInt(11))
      pd  <- if (rnd >= 5) IO(s"result_$rnd") else IO.raiseError[String](new IllegalArgumentException("boom"))
    } yield pd

  action
    .retryWithPolicy(retryPolicy)
    .whenError_[Throwable]{ case (_, _: IllegalArgumentException) => true }
    .map(str => println(s"final result = $str")).unsafeRunSync()

  ((rs: RetryStatus) => action)
    .retryWithPolicy(retryPolicy)
    .whenError_[Throwable]{ case (_, _: IllegalArgumentException) => true }
    .map(str => println(s"final result = $str")).unsafeRunSync()
}

object RecoveringAll extends App {
  val action: RetryStatus => IO[String] =
    rs =>
      if (rs.iterNum < 3) IO.raiseError[String](new IllegalArgumentException("boom"))
      else IO(s"result_${rs.iterNum}")

  limitRetries(5).liftTo[IO]
    .recoveringAll(action)
    .map(println)
    .unsafeRunSync() // result_3
}

object SimulatePolicy extends App {
  val rp = exponentialBackoff(100L) |+| limitRetries(5)
  rp.simulatePolicy(7).foreach(println)

  val rp2 = fullJitterBackoff[IO](100) |+| limitRetries(3)
  rp2.simulatePolicy(5).map(_.foreach(println)).unsafeRunSync()
}