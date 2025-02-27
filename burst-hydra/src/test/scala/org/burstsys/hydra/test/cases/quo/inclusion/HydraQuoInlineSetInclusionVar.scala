/* Copyright Yahoo, Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms. */
package org.burstsys.hydra.test.cases.quo.inclusion

import org.burstsys.fabric.execution.model.result.group.FabricResultGroup
import org.burstsys.fabric.execution.model.result.row.FabricResultRow
import org.burstsys.hydra.test.cases.quo.conditionals.HydraQuoConditionals02.{analysisName, frameName}
import org.burstsys.hydra.test.cases.support.HydraUseCase


object HydraQuoInlineSetInclusionVar extends HydraUseCase(1, 1, "quo") {

//  override lazy val sweep: HydraSweep = new B3C97343CA8144905BCF9035FD267BC8E

  override def analysisSource: String =
    s"""
       |hydra $analysisName() {
       |  schema quo
       |  frame $frameName  {
       |    cube user {
       |      limit = 1
       |      cube user.sessions.events {
       |        aggregates {
       |          count:sum[long]
       |        }
       |      }
       |    }
       |    user.sessions.events => {
       |      pre => {
       |        val lv1:boolean = user.sessions.events.eventId in (6049337, 4498119)
       |        if( lv1 ) {
       |          $analysisName.$frameName.count =  1
       |          insert($analysisName.$frameName)
       |        }
       |      }
       |    }
       |  }
       |}
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

  val expected: Array[Any] =
    Array(14834)



}
