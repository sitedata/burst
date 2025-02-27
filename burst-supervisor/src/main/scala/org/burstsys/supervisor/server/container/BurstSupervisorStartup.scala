/* Copyright Yahoo, Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms. */
package org.burstsys.supervisor.server.container

import org.burstsys.agent.processors.BurstSystemEqlQueryProcessor
import org.burstsys.agent.processors.BurstSystemHydraQueryProcessor
import org.burstsys.dash.BurstDashService
import org.burstsys.hydra.HydraService
import org.burstsys.supervisor.server.torcher.BurstSupervisorTorcherService
import org.burstsys.vitals.errors._
import org.burstsys.vitals.logging._

import scala.language.postfixOps

/**
 * The startup of a container is where the horizontal strata of top level
 * services are wired together. All configuration is done using [[org.burstsys.vitals.properties.VitalsPropertySpecification]]
 * instances. All of these have defaults, can be set in the VM command line
 */
trait BurstSupervisorStartup extends Any {

  self: BurstSupervisorContainerContext =>

  final
  def startForeground: this.type = {
    try {
      /////////////////////////////////////////////////////////////////
      // hydra
      /////////////////////////////////////////////////////////////////
      _hydra = HydraService(this).start
      _hydraProcessor = BurstSystemHydraQueryProcessor(_agentServer, _hydra)
      _agentServer.registerLanguage(_hydraProcessor)

      /////////////////////////////////////////////////////////////////
      // eql
      /////////////////////////////////////////////////////////////////
      _eqlProcessor = BurstSystemEqlQueryProcessor(_agentServer, _catalogServer)
      _agentServer.registerLanguage(_eqlProcessor)

      // agent commands
      _agentServer.registerCache(data)

      /////////////////////////////////////////////////////////////////
      // webservice things
      /////////////////////////////////////////////////////////////////
      _torcher = BurstSupervisorTorcherService(_agentServer, _catalogServer)

      _rest = BurstDashService(bootModality, _agentClient, _catalogServer, this, _torcher).start

      this

    } catch safely {
      case t: Throwable =>
        log error burstStdMsg(s"$serviceName startup failed", t)
        throw VitalsException(t)
    }
  }

}
