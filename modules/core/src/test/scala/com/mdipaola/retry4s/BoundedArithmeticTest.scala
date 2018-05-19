package com.mdipaola.retry4s

import utils.BoundedArithmetic._
import scala.language.reflectiveCalls

class BoundedArithmeticTest extends BaseTest {

  test("boundedPlus") {
    (1 boundedPlus 1) shouldBe 2
    (1 boundedPlus Long.MaxValue) shouldBe Long.MaxValue
  }

  test("boundedMult") {
    (1 boundedMult 2) shouldBe 2
    (2 boundedMult Long.MaxValue) shouldBe Long.MaxValue
  }

  test("boundedPow") {
    (2 boundedPow 3) shouldBe 8
    (2 boundedPow 10000) shouldBe Long.MaxValue
  }

}
