/* Copyright Yahoo, Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms. */
package org.burstsys.synthetic.samplestore.service

import org.burstsys.nexus
import org.burstsys.samplesource.service.SampleSourceSupervisorService
import org.burstsys.samplestore.api
import org.burstsys.samplestore.api.BurstSampleStoreDataSource
import org.burstsys.samplestore.api.SampleStoreApiRequestInvalidException
import org.burstsys.samplestore.api.SampleStoreDataLocus
import org.burstsys.samplestore.api.SampleStoreGeneration
import org.burstsys.synthetic.samplestore.configuration
import org.burstsys.tesla.thread.request.TeslaRequestFuture
import org.burstsys.vitals
import org.burstsys.vitals.properties.VitalsPropertyMap
import org.burstsys.vitals.properties._
import org.burstsys.vitals.uid.newBurstUid

import scala.concurrent.Future

case class SyntheticSampleSourceCoordinator() extends SampleSourceSupervisorService {

  /**
   * @return The name of this sample source supervisor.
   */
  override def name: String = SynteticSampleSourceName

  /**
   * Figures out the loci and other partition properties
   * required by the burst cell to make subsequent sample source queries
   * to the sample source worker
   *
   * @param guid
   * @param dataSource
   * @param listenerProperties
   * @return
   */
  override def getViewGenerator(
                                 guid: String,
                                 dataSource: BurstSampleStoreDataSource,
                                 listenerProperties: VitalsPropertyMap
                               ): Future[api.SampleStoreGeneration] = {
    TeslaRequestFuture {

      val properties = mergeProperties(dataSource, listenerProperties)
      val extended = properties.extend
      val lociCount = extended.getValueOrProperty(configuration.defaultLociCountProperty)
      val useLocalHost = extended.getValueOrProperty(configuration.defaultUseLocalHostProperty)
      val (host, addr) = if (useLocalHost) ("localhost", "127.0.0.1") else {
        throw SampleStoreApiRequestInvalidException("Only localhost mode is currently supported", null)
      }
      val loci = for (_ <- 1 to lociCount) yield SampleStoreDataLocus(newBurstUid, addr, host, nexus.port, properties)
      val hashIsInvariant = extended.getValueOrProperty(configuration.defaultPersistentHashProperty)
      val hash = if (hashIsInvariant) InvariantHash else newBurstUid

      SampleStoreGeneration(guid, hash, loci.toArray, dataSource.view.schemaName, Some(dataSource.view.viewMotif))
    }
  }

}
