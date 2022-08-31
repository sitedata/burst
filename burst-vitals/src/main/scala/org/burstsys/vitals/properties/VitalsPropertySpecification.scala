/* Copyright Yahoo, Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms. */
package org.burstsys.vitals.properties

import org.burstsys.vitals.errors.VitalsException
import org.burstsys.vitals.properties.VitalsPropertyRegistry.descriptionPadding
import org.burstsys.vitals.properties.VitalsPropertyRegistry.keyPadding
import org.burstsys.vitals.properties.VitalsPropertyRegistry.sourcePadding
import org.burstsys.vitals.properties.VitalsPropertyRegistry.typeNamePadding
import org.burstsys.vitals.strings._

import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.reflect.ClassTag
import scala.reflect.classTag

/**
 * centralized Burst management of configuration properties
 *
 * @param key         the java property that can be set to change the value of this property
 * @param description an explaination of what this property configures
 * @param sensitive   does this property value contain a potentially sensitive value with which care should be taken
 * @param default     the default value to use if none is provided
 */
final case
class VitalsPropertySpecification[C <: VitalsPropertyAtomicDataType : ClassTag](
                                                                                 key: String, description: String,
                                                                                 sensitive: Boolean = false,
                                                                                 default: Option[C] = None
                                                                               ) {

  VitalsPropertyRegistry += this

  val typeName: String = classTag[C].runtimeClass.getSimpleName.initialCase

  val environmentVariableKey: String = propertyToEnvironment(key)

  lazy val listeners: mutable.Set[Option[C] => Unit] = ConcurrentHashMap.newKeySet[Option[C] => Unit].asScala

  private var setProgrammatically = false

  def useDefault(): Unit = {
    System.clearProperty(key)
  }

  def set(value: C): Unit = {
    if (value != null) {
      System.setProperty(key, value.toString)
      setProgrammatically = true
    } else {
      useDefault()
      setProgrammatically = false
    }

    val current = get
    listeners.foreach(l => l(current))
  }

  /**
   * try for this in the process environment, then the java properties, then either an optional default
   * or an exception...
   *
   * @return
   */
  def getOrThrow: C = {
    get match {
      case None => throw VitalsException(s"label=$key not found and no default provided")
      case Some(value) => value
    }
  }

  def get: Option[C] = {
    System.getenv(environmentVariableKey) match {
      case null => System.getProperty(key) match {
        case null => default
        case _ => Some(property(key, fallback))
      }
      case _ => Some(environment(environmentVariableKey, fallback))
    }
  }

  def source: String = {
    if (setProgrammatically)
      "runtime"
    else if (System.getenv(environmentVariableKey) != null)
      "env var"
    else if (System.getProperty(key) != null)
      "java prop"
    else
      "default"
  }

  def fallback: C = {
    default match {
      case Some(value) => value.asInstanceOf[C]
      case None =>
        val e = classTag[C].runtimeClass
        val value = if (e == classOf[Boolean]) {
          false
        } else if (e == classOf[Long]) {
          0L
        } else if (e == classOf[Int]) {
          0
        } else if (e == classOf[Double]) {
          0.0
        } else if (e == classOf[String]) {
          ""
        } else {
          throw VitalsException(s"unsupported default type '$e'")
        }
        value.asInstanceOf[C]
    }
  }

  override
  def toString: String = {
    val typeStr = s"[$typeName]".padded(typeNamePadding)
    val keyName = s"$key".padded(keyPadding)
    val sourceStr = s"[$source]".padded(sourcePadding)
    val desc = s"${description.initialCase}".padded(descriptionPadding)
    s" $typeStr $keyName $sourceStr - $desc [ ${get.map(v => if (sensitive) "REDACTED" else v).getOrElse("None")} ]"
  }

  private def propertyToEnvironment(key: String): String = {
    key.replace('.', '_').toUpperCase
  }

}

