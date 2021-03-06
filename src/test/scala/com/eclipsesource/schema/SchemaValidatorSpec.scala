package com.eclipsesource.schema

import java.net.URL

import controllers.Assets
import play.api.libs.json._
import play.api.mvc.Handler
import play.api.test.{FakeApplication, PlaySpecification, WithServer}
import play.api.libs.json.Reads._
import play.api.libs.json._

class SchemaValidatorSpec extends PlaySpecification {

  val routes: PartialFunction[(String, String), Handler] = {
    case (_, path) => Assets.versioned("/", path)
  }

  val schema = JsonSource.schemaFromString(
    """{
      |  "type": "object",
      |  "properties": {
      |    "title": {
      |      "type": "string",
      |      "minLength": 10,
      |      "maxLength": 20
      |    },
      |    "speaker": {
      |      "type": "string",
      |      "pattern": "(Mr.|Mrs.)?[A-Za-z ]+"
      |    },
      |    "location": { "$ref": "http://localhost:1234/location.json" }
      |  }
      |}""".stripMargin).get

  case class Location(name: String)
  case class Talk(location: Location)

  implicit val locationReads = Json.reads[Location]
  val talkReads = Json.reads[Talk]
  implicit val locationWrites = Json.writes[Location]
  val talkWrites = Json.writes[Talk]
  implicit val talkFormat = Json.format[Talk]

  val resourceUrl: URL = getClass.getResource("/talk.json")
  val instance = Json.obj(
    "location" -> Json.obj(
      "name" -> "Munich"
    )
  )

  "SchemaValidator" should {

    "validate additionalProperties schema constraint via $ref" in
      new WithServer(app = new FakeApplication(withRoutes = routes), port = 1234) {

        val schema = JsonSource.schemaFromString(
          """{
            |  "additionalProperties": { "$ref": "http://localhost:1234/talk.json#/properties/title" }
            |}""".stripMargin).get

        // min length 10, max length 20
        val valid = Json.obj("title" -> "This is valid")
        val invalid = Json.obj("title" -> "Too short")

        SchemaValidator.validate(schema, valid).isSuccess must beTrue
        SchemaValidator.validate(schema, invalid).isFailure must beTrue
      }


    "validate oneOf constraint via $ref" in
      new WithServer(app = new FakeApplication(withRoutes = routes), port = 1234) {

        val schema = JsonSource.schemaFromString(
          """{
            |  "oneOf": [{ "$ref": "http://localhost:1234/talk.json#/properties/title" }]
            |}""".stripMargin).get

        val valid = JsString("Valid instance")
        val invalid = JsString("Too short")

        SchemaValidator.validate(schema, valid).isSuccess must beTrue
        SchemaValidator.validate(schema, invalid).isFailure must beTrue
      }

    "be validated via file based resource URL" in {
      val result = SchemaValidator.validate(resourceUrl, instance)
      result.isSuccess must beTrue
    }

    "validate via file base URL and Reads" in {
      val result = SchemaValidator.validate(resourceUrl, instance, talkReads)
      result.isSuccess must beTrue
      result.get.location.name must beEqualTo("Munich")
    }

    "validate via file base URL and Writes" in {
      val talk = Talk(Location("Munich"))
      val result = SchemaValidator.validate(resourceUrl, talk, talkWrites)
      result.isSuccess must beTrue
    }

    "validate via file base URL and Format" in {
      val talk = Talk(Location("Munich"))
      val result = SchemaValidator.validate(resourceUrl, talk)
      result.isSuccess must beTrue
      result.get.location.name must beEqualTo("Munich")
    }

    "validate with Reads" in
      new WithServer(app = new FakeApplication(withRoutes = routes), port = 1234) {
      val result = SchemaValidator.validate(schema, instance, talkReads)
      result.isSuccess must beTrue
      result.get.location.name must beEqualTo("Munich")
    }

    "validate with Wrties" in
      new WithServer(app = new FakeApplication(withRoutes = routes), port = 1234) {

        val talk = Talk(Location("Munich"))
        val result = SchemaValidator.validate(schema, talk, talkWrites)
        result.isSuccess must beTrue
      }
  }

  "Remote ref" should {
    "validate" in new WithServer(app = new FakeApplication(withRoutes = routes), port = 1234) {
      val resourceUrl: URL = new URL("http://localhost:1234/talk.json")
      val result = SchemaValidator.validate(resourceUrl, instance)
      result.isSuccess must beTrue
    }

  }

}
