package com.eclipsesource.schema.internal.validators

import com.eclipsesource.schema.internal.constraints.Constraints.ArrayConstraints
import com.eclipsesource.schema.internal.{Context, Results, SchemaUtil}
import play.api.data.mapping.{Rule, Success, VA}
import play.api.libs.json.{JsArray, JsValue}

trait ArrayConstraintValidator {

  def validate(json: JsValue, arrayConstraints: ArrayConstraints, context: Context): VA[JsValue] = {
    val reader = for {
      minItemsRule <- validateMinItems
      maxItemsRule <- validateMaxItems
      uniqueRule <- validateUniqueness
    } yield { minItemsRule |+| maxItemsRule |+| uniqueRule }
    reader.run((arrayConstraints, context)).validate(json)
  }

  def validateMaxItems: scalaz.Reader[(ArrayConstraints, Context), Rule[JsValue, JsValue]] =
    scalaz.Reader { case (constraints, context) =>
      val maxItems = constraints.maxItems
      Rule.fromMapping {
        case json@JsArray(values) => maxItems match {
          case Some(max) => if (values.size <= max) {
            Success(json)
          } else {
            Results.failure(
              s"Too many items. ${values.size} items found, but only $max item(s) are allowed.",
              context.schemaPath.toString(),
              context.instancePath.toString(),
              context.root,
              json
            )
          }
          case None => Success(json)
        }
        case other => expectedArray(other, context)
      }
    }

  def validateMinItems: scalaz.Reader[(ArrayConstraints, Context), Rule[JsValue, JsValue]] =
    scalaz.Reader { case (constraints, context) =>
      val minItems = constraints.minItems.getOrElse(0)
      Rule.fromMapping {
        case json@JsArray(values) =>
          if (values.size >= minItems) {
            Success(json)
          } else {
            Results.failure(
              s"Not enough items. ${values.size} items found, but at least $minItems item(s) need to be present.",
              context.schemaPath.toString(),
              context.instancePath.toString(),
              context.root,
              json
            )
          }
        case other => expectedArray(other, context)
      }
    }

  def validateUniqueness: scalaz.Reader[(ArrayConstraints, Context), Rule[JsValue, JsValue]] =
    scalaz.Reader { case (constraints, context) =>
      val isUnique = constraints.unique.getOrElse(false)
      Rule.fromMapping {
        case json@JsArray(values) if isUnique =>
          if (values.distinct.size == values.size) {
            Success(json)
          } else {
            Results.failure(
              s"[${values.mkString(", ")}] contains duplicates",
              context.schemaPath.toString(),
              context.instancePath.toString(),
              context.root,
              json
            )
          }
        case arr@JsArray(_) => Success(arr)
        case other => expectedArray(other, context)
      }
    }

  private[validators] def expectedArray(json: JsValue, context: Context) =
    Results.failure(
      s"Wrong type. Expected array, was ${SchemaUtil.typeOfAsString(json)}",
      context.schemaPath.toString(),
      context.instancePath.toString(),
      context.root,
      json
    )
}
