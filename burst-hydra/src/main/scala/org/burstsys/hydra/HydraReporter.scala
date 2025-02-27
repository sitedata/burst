/* Copyright Yahoo, Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms. */
package org.burstsys.hydra

import org.burstsys.vitals.reporter._
import org.burstsys.vitals.reporter.metric.VitalsReporterUnitOpMetric

private[hydra]
object HydraReporter extends VitalsReporter {

  final val dName: String = "hydra"

  /////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // private state
  /////////////////////////////////////////////////////////////////////////////////////////////////////////////

  private[this]
  val _parseMetric = VitalsReporterUnitOpMetric("hydra_parse", "lines")
  this += _parseMetric

  /////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // LIFECYCLE
  /////////////////////////////////////////////////////////////////////////////////////////////////////////////

  override def sample(sampleMs: Long): Unit = {
    super.sample(sampleMs)
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // API
  /////////////////////////////////////////////////////////////////////////////////////////////////////////////

  final
  def recordParse(elapsedNs: Long, source: String): Unit = {
    newSample()
    _parseMetric.recordOpWithTimeAndSize(ns = elapsedNs, units = Predef.augmentString(source).linesIterator.size)
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // REPORT
  /////////////////////////////////////////////////////////////////////////////////////////////////////////////

  override
  def report: String = {
    if (nullData) return ""
    s"${_parseMetric.report}"
  }

}
