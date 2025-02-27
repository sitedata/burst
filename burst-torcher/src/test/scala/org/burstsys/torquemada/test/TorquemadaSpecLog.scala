/* Copyright Yahoo, Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms. */
package org.burstsys.torquemada.test

import org.burstsys.vitals.logging._
import org.burstsys.vitals.metrics.VitalsMetricsRegistry
import org.apache.logging.log4j.Logger

trait TorquemadaSpecLog {

  VitalsMetricsRegistry.disable()

  VitalsLog.configureLogging("torcher", consoleOnly = true)

  def log: Logger

}
