package com.mdipaola.retry4s

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration

object utils {

  object BoundedArithmetic {
    private val MAX_MS: Long = (Long.MaxValue / 1000) / 1000

    implicit def safePow(a: Long) = new {
      def boundedPow(b: Long): Long = Math.pow(a.toDouble, b.toDouble).toLong
    }

    implicit def safeSum(a: Long) = new {
      def boundedPlus(b: Long): Long =
        try {
          Math.addExact(a, b)
        } catch {
          case _: ArithmeticException => Long.MaxValue
        }
    }

    implicit def safeMul(a: Long) = new {
      def boundedMult(b: Long): Long =
        try {
          Math.multiplyExact(a, b)
        } catch {
          case _: ArithmeticException => Long.MaxValue
        }
    }

    implicit def toFiniteDurationInMillis(millis: Long) = new {
      def toFiniteDurationMs: FiniteDuration = FiniteDuration(millis min MAX_MS, TimeUnit.MILLISECONDS)
    }
  }

}