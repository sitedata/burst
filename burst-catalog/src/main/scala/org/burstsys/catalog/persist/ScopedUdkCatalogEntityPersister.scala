/* Copyright Yahoo, Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms. */
package org.burstsys.catalog.persist

import org.burstsys.relate.RelateExceptions.BurstDuplicateKeyException
import org.burstsys.relate.RelateExceptions.BurstUnknownPrimaryKeyException
import org.burstsys.relate.RelatePersister
import org.burstsys.relate.dialect.SelectLockLevel
import org.burstsys.relate.dialect.SelectLockLevel.NoLock
import org.burstsys.relate.dialect.SelectLockLevel.UpdateLock
import org.burstsys.relate.handleSqlException
import org.burstsys.relate.throwMappedException
import org.burstsys.vitals.errors.VitalsException
import scalikejdbc._

abstract class ScopedUdkCatalogEntityPersister[E <: ScopedUdkCatalogEntity] extends NamedCatalogEntityPersister[E] {

  def scopeTableName: String
  private def scopeTable: SQLSyntax = SQLSyntax.createUnsafely(scopeTableName)

  def scopeFkField: String
  private def scopeField: SQLSyntax = SQLSyntax.createUnsafely(scopeFkField)

  final def findEntityByUdkIn(scope: String, udk: String, lockLevel: SelectLockLevel = NoLock)(implicit session: DBSession): Option[E] = {
    sql"""
    SELECT ${this.table}.*
    FROM ${this.table} JOIN $scopeTable s ON ${this.table}.$scopeField = s.pk
    WHERE ${this.table}.${this.column.udk} = {udk} AND s.udk = {scope}
    ${service.dialect.lockClause(lockLevel)}""".bindByName(
      Symbol("udk") -> udk,
      Symbol("scope") -> scope
    ).map(resultToEntity).single().apply()
  }

  /**
   * Inserts or update the provided entity.
   *
   * In practice, pk is more important internally than to consumers. Consumers prefer
   * to use udks, as they tend to hold semantic meaning in downstream systems. Because
   * downstream systems prefer udks they can run into race conditions when creating entities
   * since udks must be unique.
   *
   * New entities should always have pk == 0, but pk == 0 does not imply that
   * the user intends to create the entity, since downstream systems often refer to objects by udk.
   *
   * @param entity the entity to save
   * @return a tuple containing the primary key of the entity (useful for inserts)
   *         and a flag indicating whether or not a write actually occurred
   */
  final def upsertEntity(scope: String, entity: E)(implicit session: DBSession): (E, Boolean) = {
    try {
      entity.pk match {
        // pk is garbage
        case pk if pk < 0 => throw VitalsException(s"Invalid pk $pk")

        // if the pk is provided it is trusted implicitly. Passing a pk allows clients to update a udk
        case pk if pk > 0 =>
          findEntityByPk(entity.pk, lockLevel = UpdateLock) match {
            // if the provided pk doesn't exist we can do nothing
            case None => throw BurstUnknownPrimaryKeyException(entity.pk)
            case Some(stored) => updateEntityIfChanged(entity, stored, updatesForEntityByPk(entity, stored))
          }

        // pk == 0 is the default case for objects passed in without a pk, since pk is `Long` not `Option[Long]`
        case pk if pk == 0 =>
          findEntityByUdkIn(scope, entity.udk.get, lockLevel = UpdateLock) match {
            // if the provided udk doesn't exist, we are creating a new entity
            case None => (insertAndFetch(entity), true)
            // if the provided udk exists, we are updating an existing entity
            case Some(stored) => updateEntityIfChanged(entity, stored, updatesForEntityByUdk(entity, stored))
          }
      }
    } catch handleSqlException(service.dialect) {
      // handle race conditions arising from concurrent attempts to create the same entity by udk
      case ex: BurstDuplicateKeyException =>
        if (entity.udk.isEmpty) {
          throw BurstDuplicateKeyException("Cannot update or insert entity", ex.cause)
        }
        try {
          findEntityByUdkIn(scope, entity.udk.get, lockLevel = UpdateLock) match {
            case None => throw VitalsException("Cannot update or insert entity", ex)
            case Some(stored) => updateEntityIfChanged(entity, stored, updatesForEntityByUdk(entity, stored))
          }
        } catch throwMappedException(service.dialect)
    }
  }
}
