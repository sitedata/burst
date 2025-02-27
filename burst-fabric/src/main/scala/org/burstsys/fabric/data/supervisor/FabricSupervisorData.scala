/* Copyright Yahoo, Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms. */
package org.burstsys.fabric.data.supervisor

import java.util.concurrent.ConcurrentHashMap

import org.burstsys.fabric.container.FabricSupervisorService
import org.burstsys.fabric.container.supervisor.FabricSupervisorContainer
import org.burstsys.fabric.data
import org.burstsys.fabric.data.model.ops.FabricCacheOps
import org.burstsys.fabric.data.supervisor.op.{FabricDataAffinityOp, FabricDataCacheOps}
import org.burstsys.fabric.data.supervisor.store.{getSupervisorStore, startSupervisorStores, stopSupervisorStores}
import org.burstsys.fabric.data.model.slice.FabricSlice
import org.burstsys.fabric.metadata.model.datasource.FabricDatasource
import org.burstsys.fabric.net.server.FabricNetServerListener
import org.burstsys.fabric.topology.supervisor.FabricTopologyListener
import org.burstsys.fabric.topology.model.node.worker.FabricWorkerNode
import org.burstsys.vitals.VitalsService.VitalsServiceModality
import org.burstsys.vitals.uid._
import org.burstsys.vitals.errors.VitalsException
import org.burstsys.vitals.errors._

import scala.jdk.CollectionConverters._
import org.burstsys.vitals.logging._

import scala.concurrent.Future

/**
  * supervisor side control of distributed cell data
  */
trait FabricSupervisorData extends FabricSupervisorService with FabricCacheOps {

  /**
    * get slices for a particular store. We let the data service manage which workers to use.
    *
    * @param guid       the global operation UID
    * @param datasource the data view to slice
    * @return
    */
  def slices(guid: VitalsUid, datasource: FabricDatasource): Future[Array[FabricSlice]]

  /**
    * the set of workers that are thought likely to have a slice's data in cache. Note that this is not
    * a guarantee in the future (once we are sparkfree)
    *
    * @param slice
    * @return
    */
  def affineWorkers(slice: FabricSlice): Array[FabricWorkerNode]


}

object FabricSupervisorData {

  def apply(container: FabricSupervisorContainer): FabricSupervisorData =
    FabricSupervisorDataContext(container: FabricSupervisorContainer)

}

private[data] final case
class FabricSupervisorDataContext(container: FabricSupervisorContainer) extends FabricSupervisorData
  with FabricNetServerListener with FabricDataAffinityOp with FabricDataCacheOps {

  override def modality: VitalsServiceModality = container.bootModality

  override val serviceName: String = s"fabric-supervisor-data"

  override def toString: String = serviceName

  //////////////////////////////////////////////////////////////////////////////////////////////////////
  // state
  //////////////////////////////////////////////////////////////////////////////////////////////////////

  //////////////////////////////////////////////////////////////////////////////////////////////////////
  // API
  //////////////////////////////////////////////////////////////////////////////////////////////////////

  override
  def slices(guid: VitalsUid, datasource: FabricDatasource): Future[Array[FabricSlice]] = {
    val tag = s"FabricSupervisorData.slices(guid=$guid, datasource=$datasource)"
    ensureRunning
    try {
      if (container.topology.healthyWorkers.isEmpty)
        throw VitalsException(s"FAB_SLICES_NO_WORKERS! $tag")
      getSupervisorStore(datasource).slices(guid, container.topology.healthyWorkers, datasource)
    } catch safely {
      case t: Throwable =>
        log error burstStdMsg(t)
        throw t
    }
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // lifecycle
  /////////////////////////////////////////////////////////////////////////////////////////////////////////////

  override
  def start: this.type = {
    ensureNotRunning
    log info startingMessage
    container.netServer talksTo this
    markRunning
    this
  }

  override
  def stop: this.type = {
    ensureRunning
    log info stoppingMessage
    markNotRunning
    this
  }

}
