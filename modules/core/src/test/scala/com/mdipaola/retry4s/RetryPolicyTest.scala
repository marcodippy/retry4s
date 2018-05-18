package com.mdipaola.retry4s

import cats.{Id, MonadError}
import cats.data.OptionT
import cats.effect.{IO, Timer}

class RetryPolicyTest extends BaseTest {

  test("applyPolicy") {
    forAll { (rp: RetryPolicy[Id], rs: RetryStatus) =>
      for {
        rs2 <- rp.applyPolicy(rs).value
      } {
        rs2.iterNum shouldBe (rs.iterNum + 1)
        rs2.cumulativeDelay shouldBe >=(rs.cumulativeDelay)
        rs2.previousDelay shouldBe rp.policy(rs).value
      }
    }
  }

  test("limitRetriesByDelay") {
    forAll { (rp: RetryPolicy[Id], delay: Delay, rs: RetryStatus) =>
      val unlimited: Option[RetryStatus] = rp.applyPolicy(rs).value
      val limited: Option[RetryStatus] = rp.limitRetriesByDelay(delay.get).applyPolicy(rs).value

      (unlimited, limited) match {
        case (Some(RetryStatus(_, _, Some(d1))), Some(RetryStatus(_, _, Some(d2)))) =>
          d1 shouldBe <=(delay.get)
          d2 shouldBe <=(delay.get)
        case (Some(RetryStatus(_, _, Some(d1))), None) => d1 shouldBe >=(delay.get)
        case (None, None) => succeed
        case _ => fail
      }
    }
  }

  test("limitRetriesByCumulativeDelay") {
    forAll { (rp: RetryPolicy[Id], delay: Delay, rs: RetryStatus) =>
      val unlimited: Option[RetryStatus] = rp.applyPolicy(rs).value
      val limited: Option[RetryStatus] = rp.limitRetriesByCumulativeDelay(delay.get).applyPolicy(rs).value

      (unlimited, limited) match {
        case (Some(RetryStatus(_, cumulativeDelay1, _)), Some(RetryStatus(_, cumulativeDelay2, _))) =>
          cumulativeDelay1 shouldBe <=(delay.get)
          cumulativeDelay2 shouldBe <=(delay.get)
          cumulativeDelay2 shouldBe >=(cumulativeDelay1)
        case (Some(RetryStatus(_, cumulativeDelay1, _)), None) => cumulativeDelay1 shouldBe >(delay.get)
        case (None, None) => succeed
        case _ => fail
      }
    }
  }

  test("capDelay") {
    forAll { (rp: RetryPolicy[Id], delay: Delay, rs: RetryStatus) =>
      val uncapped: Option[RetryStatus] = rp.applyPolicy(rs).value
      val capped: Option[RetryStatus] = rp.capDelay(delay.get).applyPolicy(rs).value

      (uncapped, capped) match {
        case (Some(RetryStatus(_, _, Some(d1))), Some(RetryStatus(_, _, Some(d2)))) =>
          d2 shouldBe (delay.get min d1)
        case (None, None) => succeed
        case _ => fail
      }
    }
  }

  test("applyAndDelay") {
    forAll { (rp: RetryPolicy[Id], rs: RetryStatus) =>
      val rs1: Option[RetryStatus] = rp.applyPolicy(rs).value

      rs1 match {
        case Some(RetryStatus(_, _, Some(dl1))) => {
          var slept = 0L
          implicit val timer: Timer[Id] = testTimer(slept += _)
          val rs2 = rp.applyAndDelay(rs).value
          slept shouldBe dl1
          rs2 shouldBe rs1
        }
        case Some(RetryStatus(_, _, None)) | None =>
          implicit val timer: Timer[Id] = testTimer(_ => fail)
          val rs2 = rp.applyAndDelay(rs).value
          rs2 shouldBe None
        case _ => fail
      }
    }
  }

  test("retrying") {
    forAll { (delayOp: DelayOp, successAtRetryNum: NumOfRetries, strResult: String) =>
      val succeedAtRetryNum = successAtRetryNum.get

      val chk: (RetryStatus, ActionResult) => Boolean = (rs, s) => rs.iterNum != succeedAtRetryNum
      val act: RetryStatus => ActionResult = rs => ActionResult(rs.iterNum, s"$strResult-${rs.iterNum}")

      val slept: scala.collection.mutable.ListBuffer[Long] = scala.collection.mutable.ListBuffer.empty[Long]

      val rp: RetryPolicy[Id] = retryPolicy(_ => delayOp.get)
      implicit val timer: Timer[Id] = testTimer(slept += _)
      val res: ActionResult = rp.retryingF(act)(chk)

      delayOp.get match {
        case Some(delay) => {
          res shouldBe ActionResult(succeedAtRetryNum, s"$strResult-$succeedAtRetryNum")
          slept shouldBe List.fill(succeedAtRetryNum)(delay)
        }
        case None =>
          res shouldBe ActionResult(0, s"$strResult-0")
          slept shouldBe Nil
      }
    }
  }

  test("recoveringAll") {
    forAll { (delayOp: DelayOp, successAtRetryNum: NumOfRetries, strResult: String) =>
      val rp: RetryPolicy[IO] = RetryPolicy(_ => OptionT.fromOption[IO](delayOp.get))

      val succeedAtRetryNum = successAtRetryNum.get

      val act: RetryStatus => IO[ActionResult] = rs =>
        if (rs.iterNum != succeedAtRetryNum)
          IO.raiseError[ActionResult](new IllegalArgumentException("oh sshhh"))
        else
          IO(ActionResult(rs.iterNum, s"$strResult-${rs.iterNum}"))

      val slept: scala.collection.mutable.ListBuffer[Long] = scala.collection.mutable.ListBuffer.empty[Long]
      val res: IO[ActionResult] = rp.recoveringAll(act)(implicitly[MonadError[IO, Throwable]], testTimer(slept += _))

      delayOp.get match {
        case Some(delay) => {
          res.unsafeRunSync() shouldBe ActionResult(succeedAtRetryNum, s"$strResult-$succeedAtRetryNum")
          slept shouldBe List.fill(succeedAtRetryNum)(delay)
        }
        case None => {
          if (succeedAtRetryNum > 0)
            an[IllegalArgumentException] should be thrownBy res.unsafeRunSync()
          else
            res.unsafeRunSync() shouldBe ActionResult(succeedAtRetryNum, s"$strResult-$succeedAtRetryNum")

          slept shouldBe Nil
        }
      }
    }
  }

  test("recoveringF.errorHandled") {
    forAll { (delayOp: DelayOp, successAtRetryNum: NumOfRetries, strResult: String) =>
      val rp: RetryPolicy[IO] = RetryPolicy(_ => OptionT.fromOption[IO](delayOp.get))

      val succeedAtRetryNum = successAtRetryNum.get

      val chkOk: (RetryStatus, Throwable) => IO[Boolean] = {
        case (_, _: IllegalArgumentException) => IO(true)
        case _ => IO(false)
      }

      val act: RetryStatus => IO[ActionResult] = rs =>
        if (rs.iterNum != succeedAtRetryNum)
          IO.raiseError[ActionResult](new IllegalArgumentException("oh sshhh"))
        else
          IO(ActionResult(rs.iterNum, s"$strResult-${rs.iterNum}"))

      val slept: scala.collection.mutable.ListBuffer[Long] = scala.collection.mutable.ListBuffer.empty[Long]
      val res: IO[ActionResult] = rp.recoveringF(act)(chkOk)(implicitly[MonadError[IO, Throwable]], testTimer(slept += _))

      delayOp.get match {
        case Some(delay) => {
          res.unsafeRunSync() shouldBe ActionResult(succeedAtRetryNum, s"$strResult-$succeedAtRetryNum")
          slept shouldBe List.fill(succeedAtRetryNum)(delay)
        }
        case None => {
          if (succeedAtRetryNum > 0)
            an[IllegalArgumentException] should be thrownBy res.unsafeRunSync()
          else
            res.unsafeRunSync() shouldBe ActionResult(succeedAtRetryNum, s"$strResult-$succeedAtRetryNum")

          slept shouldBe Nil
        }
      }
    }
  }

  test("recoveringF.errorNotHandled") {
    forAll { (delayOp: DelayOp, successAtRetryNum: NumOfRetries, strResult: String) =>
      val rp: RetryPolicy[IO] = RetryPolicy(_ => OptionT.fromOption[IO](delayOp.get))

      val succeedAtRetryNum = successAtRetryNum.get

      val chkKo: (RetryStatus, Throwable) => IO[Boolean] = (_, _) => IO(false)

      val act: RetryStatus => IO[ActionResult] = rs =>
        if (rs.iterNum != succeedAtRetryNum)
          IO.raiseError[ActionResult](new IllegalArgumentException("oh sshhh"))
        else
          IO(ActionResult(rs.iterNum, s"$strResult-${rs.iterNum}"))

      val slept: scala.collection.mutable.ListBuffer[Long] = scala.collection.mutable.ListBuffer.empty[Long]
      val res: IO[ActionResult] = rp.recoveringF(act)(chkKo)(implicitly[MonadError[IO, Throwable]], testTimer(slept += _))

      if (succeedAtRetryNum > 0)
        an[IllegalArgumentException] should be thrownBy res.unsafeRunSync()
      else
        res.unsafeRunSync() shouldBe ActionResult(succeedAtRetryNum, s"$strResult-$succeedAtRetryNum")

      slept shouldBe Nil
    }
  }

}
