/* Copyright Yahoo, Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms. */
package org.burstsys.zap.route.factory

import org.burstsys.tesla
import org.burstsys.tesla.TeslaTypes.{TeslaMemoryOffset, TeslaMemoryPtr, TeslaMemorySize}
import org.burstsys.tesla.block.{TeslaBlock, TeslaBlockAnyVal}
import org.burstsys.tesla.part.TeslaPartPool
import org.burstsys.tesla.pool.TeslaPoolId
import org.burstsys.zap.route.{ZapRoute, ZapRouteBuilder, ZapRouteContext, ZapRouteReporter}

import scala.language.postfixOps

final
case class ZapRoutePool(poolId: TeslaPoolId, partByteSize: TeslaMemoryOffset)
  extends TeslaPartPool[ZapRoute] with ZapRouteShop {

  override val partQueueSize: Int = 5e3.toInt // 5e3

  @inline override
  def grabZapRoute(builder: ZapRouteBuilder, startSize: TeslaMemorySize): ZapRoute  = {
    markPartGrabbed()
    val part = partQueue poll match {
      case null =>
        incrementPartsAllocated()
        ZapRouteReporter.alloc(partByteSize)
        val b = tesla.block.factory.grabBlock(startSize)
        ZapRouteContext(b.blockBasePtr).initialize(poolId)
      case r =>
        r.reset(builder)
        r
    }
    incrementPartsInUse()
    ZapRouteReporter.grab()
    part
  }

  @inline override
  def releaseZapRoute(route: ZapRoute): Unit = {
    decrementPartsInUse()
    ZapRouteReporter.release()
    partQueue add route
  }

  /**
   *
   * @param part
   * @return
   */
  @inline override
  def freePart(part: ZapRoute): TeslaMemoryPtr = {
    val block = TeslaBlockAnyVal(part.blockBasePtr)
    tesla.block.factory.releaseBlock(block)
    ZapRouteReporter.free(partByteSize)
    partByteSize
  }
}
