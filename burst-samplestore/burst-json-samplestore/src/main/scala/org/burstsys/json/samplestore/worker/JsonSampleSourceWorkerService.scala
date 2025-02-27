/* Copyright Yahoo, Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms. */
package org.burstsys.json.samplestore.worker

import org.burstsys.brio
import org.burstsys.brio.json.JsonPressSource
import org.burstsys.brio.model.schema.BrioSchema
import org.burstsys.brio.press.pipeline
import org.burstsys.json.samplestore.JsonBrioSampleSourceName
import org.burstsys.json.samplestore.configuration.alloyLocationPropertyKey
import org.burstsys.json.samplestore.configuration.alloySkipIndexStreamPropertyKey
import org.burstsys.json.samplestore.configuration.jsonLociCountProperty
import org.burstsys.json.samplestore.configuration.jsonVersionProperty
import org.burstsys.nexus.stream.NexusStream
import org.burstsys.samplesource.service.SampleSourceWorkerService
import org.burstsys.samplestore.api.configuration.burstSampleStoreHeartbeatInterval
import org.burstsys.tesla.thread.request.TeslaRequestFuture
import org.burstsys.tesla.thread.request.teslaRequestExecutor
import org.burstsys.vitals.errors.VitalsException
import org.burstsys.vitals.errors.safely
import org.burstsys.vitals.instrument.VitalsElapsedTimer
import org.burstsys.vitals.logging.burstStdMsg

import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

case class JsonSampleSourceWorkerService() extends SampleSourceWorkerService {

  override def name: String = JsonBrioSampleSourceName

  override def feedStream(stream: NexusStream): Future[Unit] = {
    val guid = stream.guid
    val tag = s"AlloySampleSourceWorkerService.feedStream(guid=$guid)"
    TeslaRequestFuture {
      log info s"$tag start"
      try {
        JsonLoadThrottle(guid, pressToStream(stream))
      } catch safely {
        case e: Exception =>
          log.warn(s"$tag Unable to schedule stream feeder. reason=${e.getLocalizedMessage}")
          stream.completeExceptionally(e)
      } finally {
        log info burstStdMsg(s"stopping heartbeat")
      }
    }
  }

  private def pressToStream(stream: NexusStream): Unit = {
    val tag = s"AlloySampleSourceWorkerService.feedStream(guid=${stream.guid}, suid=${stream.suid})"
    log info burstStdMsg(s"$tag started")
    try {
      val timer = VitalsElapsedTimer("AlloySampleSourceFeedTimer")
      try {
        timer.start
        val itemReplication: Integer = Math.max(stream.getOrDefault[Int](jsonLociCountProperty.key, 0), 0)
        val itemSkip: Integer = Math.max(stream.getOrDefault[Int](alloySkipIndexStreamPropertyKey, 0), 0)
        val dataLocation = stream.get[String](alloyLocationPropertyKey)
        val jsonVersion = stream.getOrDefault[Int](jsonVersionProperty.key, 1)

        log info burstStdMsg(s"$tag replication=$itemReplication skip=$itemSkip location=$dataLocation version=$jsonVersion")

        val jsonSource: InputStream = {
          val fileName = Paths.get(dataLocation)
          if (fileName.getFileName.toString.endsWith(".gz"))
            new GZIPInputStream(new FileInputStream(fileName.toFile))
          else
            new FileInputStream(fileName.toFile)
        }
        val schema = BrioSchema(stream.schema.toLowerCase)

        var skipCount = itemSkip
        var totalBytes = 0
        val itemCount = new AtomicInteger(0)
        val rejectedCount = new AtomicInteger(0)
        val futures = for (jb <- brio.json.getJsonSource(schema, jsonSource)) yield {
          val f = pipeline.pressToFuture(stream.guid, JsonPressSource(schema, jb), schema, 3, 10000000)
          f onComplete {
            case Success(buffer) =>
              log debug burstStdMsg(s"putting item in stream")
              totalBytes += buffer.currentUsedMemory
              itemCount.incrementAndGet()
              stream put buffer
            case Failure(t) =>
              log debug burstStdMsg(s"discarding item")
              rejectedCount.incrementAndGet()
          }
          f
        }
        Await.result(Future.sequence(futures), (1 minute))

        timer.stop
        log info burstStdMsg(s"$tag completed itemCount=$itemCount totalBytes=$totalBytes")
        stream.complete(itemCount.intValue(), expectedItemCount = futures.size, potentialItemCount = futures.size, rejectedCount.intValue())
        JsonSampleSourceWorkerReporter.onWorkerCompletion(totalBytes, timer)
      } catch safely {
        case t: Throwable =>
          timer.stop
          log info burstStdMsg(s"$tag failed", t)
          stream.completeExceptionally(t)
          JsonSampleSourceWorkerReporter.onWorkerFailure(timer)
      }
    } catch safely {
      case t: Throwable =>
        val msg = s"$tag failed"
        stream.completeExceptionally(t)
        log error burstStdMsg(msg, t)
        throw VitalsException(msg, t)
    }
  }

}
