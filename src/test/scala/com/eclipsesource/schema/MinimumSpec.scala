package com.eclipsesource.schema

import com.eclipsesource.schema.test.JsonSpec
import org.specs2.mutable.Specification

class MinimumSpec extends Specification with JsonSpec {
  validate("minimum")
}
