/* Copyright Yahoo, Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms. */
package org.burstsys.alloy.store.mini.supervisor

import org.burstsys.alloy.store.mini
import org.burstsys.fabric.container.supervisor.FabricSupervisorContainer
import org.burstsys.fabric.data.model.store.FabricStoreName
import org.burstsys.fabric.data.supervisor.store.FabricStoreSupervisor

import scala.language.postfixOps

/**
 * A specialized 'mini' Fabric store that supports the creation of small targeted datasets that can be
 * used for semantic/unit/performance tests
 */
final case
class MiniStoreSupervisor(container: FabricSupervisorContainer) extends FabricStoreSupervisor
  with MiniMetadataLookup with MiniSlicer {

  override lazy val storeName: FabricStoreName = mini.MiniStoreName

  ///////////////////////////////////////////////////////////////////
  // LIFECYCLE
  ///////////////////////////////////////////////////////////////////

  override
  def start: this.type = {
    ensureNotRunning
    log info startingMessage
    markRunning
    this
  }

  override
  def stop: this.type = {
    ensureRunning
    log info stoppingMessage
    clearMetadata()
    markNotRunning
    this
  }
}
