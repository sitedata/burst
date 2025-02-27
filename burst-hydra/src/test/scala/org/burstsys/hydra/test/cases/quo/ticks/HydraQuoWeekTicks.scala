/* Copyright Yahoo, Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms. */
package org.burstsys.hydra.test.cases.quo.ticks

import org.burstsys.fabric.execution.model.result.group.FabricResultGroup
import org.burstsys.fabric.execution.model.result.row.FabricResultRow
import org.burstsys.hydra.test.cases.quo.parameters.HydraQuoParameters01.{analysisName, frameName}
import org.burstsys.hydra.test.cases.support.HydraUseCase

object HydraQuoWeekTicks extends HydraUseCase(1, 1, "quo") {

  //  override val sweep = new BB141C6490357423B8237827EF3B872F9

  override val frameSource: String =
    s"""
     | frame $frameName {
     |   cube user {
     |     limit = 100000
     |     cube user.sessions {
     |       dimensions {
     |         'weekTicks':verbatim[long]
     |       }
     |     }
     |   }
     |   user.sessions => {
     |     pre => {
     |       $analysisName.$frameName.'weekTicks' = weekTicks(3)
     |       insert($analysisName.$frameName)
     |     }
     |   }
     | }
    """.stripMargin

  override def
  validate(implicit result: FabricResultGroup): Unit = {

    val r = result.resultSets(result.resultSetNames(frameName))
    assertLimits(r)
    found(r.rowSet) should equal(expected)
  }

  def found(rowSet: Array[FabricResultRow]): Array[_] = {
    rowSet.map {
      row => row.cells(0).asLong
    }.sorted
  }

  val expected: Array[Any] = Array(1814400000)


}
