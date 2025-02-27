/* Copyright Yahoo, Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms. */
package org.burstsys.fabric.net.server

import java.util.concurrent.ConcurrentHashMap

import org.burstsys.fabric.container.FabricSupervisorService
import org.burstsys.fabric.container.supervisor.FabricSupervisorContainer
import org.burstsys.fabric.net.FabricNetIoMode.FabricNetIoMode
import org.burstsys.fabric.net.message.{FabricNetInboundFrameDecoder, FabricNetOutboundFrameEncoder}
import org.burstsys.fabric.net.receiver.FabricNetReceiver
import org.burstsys.fabric.net.server.connection.FabricNetServerConnection
import org.burstsys.fabric.net.transmitter.FabricNetTransmitter
import org.burstsys.fabric.net.{FabricNetIoMode, FabricNetLink, FabricNetLocator, FabricNetReporter, FabricNetworkConfig}
import org.burstsys.vitals.VitalsService.{VitalsPojo, VitalsServiceModality}
import org.burstsys.vitals.errors._
import org.burstsys.vitals.logging._
import org.burstsys.vitals.healthcheck.VitalsHealthMonitoredService
import org.burstsys.vitals.net.{VitalsHostAddress, VitalsHostPort}
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel._
import io.netty.channel.epoll.{EpollEventLoopGroup, EpollServerSocketChannel}
import io.netty.channel.kqueue.{KQueueEventLoopGroup, KQueueServerSocketChannel}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.duration.Duration._
import scala.language.postfixOps

/**
 * The server (data provider) side of the next protocol
 * The ''server'' binds to a port given to it by the network stack. This port is then sent to clients. This is
 * ''not'' a ''well known'' port
 */
trait FabricNetServer extends FabricSupervisorService with FabricNetLink with FabricNetLocator {

  /**
   * a listener for protocol events
   */
  def talksTo(listener: FabricNetServerListener*): this.type

  /**
   *
   * @return
   */
  def netConfig: FabricNetworkConfig

}

object FabricNetServer {

  def apply(container: FabricSupervisorContainer, netConfig: FabricNetworkConfig): FabricNetServer =
    FabricNetServerContext(container, netConfig)
}

/**
 * The server (data provider) side of the next protocol
 * The ''server'' binds to a port given to it by the network stack. This port is then sent to clients. This is
 * ''not'' a ''well known'' port
 */
private[server] final case
class FabricNetServerContext(container: FabricSupervisorContainer, netConfig: FabricNetworkConfig) extends FabricNetServer
  with FabricNetServerNetty with FabricNetLocator with FabricNetServerListener with VitalsHealthMonitoredService {

  override def toString: String = serviceName

  override def serviceName: String = s"fabric-net-server(containerId=${container.containerIdGetOrThrow}, $netSupervisorUrl)"

  override val modality: VitalsServiceModality = VitalsPojo

  ////////////////////////////////////////////////////////////////////////////////////
  // Config
  ////////////////////////////////////////////////////////////////////////////////////

  def ioMode: FabricNetIoMode = FabricNetIoMode.NioIoMode

  def timeout: Duration = Inf

  ////////////////////////////////////////////////////////////////////////////////////
  // State
  ////////////////////////////////////////////////////////////////////////////////////

  private[this]
  var _serverChannel: Channel = _

  private[this]
  var _listenGroup: EventLoopGroup = _

  private[this]
  var _connectionGroup: EventLoopGroup = _

  private[this]
  var _transportClass: Class[_ <: ServerChannel] = _

  private[this]
  var _listenerSet = ConcurrentHashMap.newKeySet[FabricNetServerListener].asScala

  private[this]
  var _connections = new ConcurrentHashMap[(VitalsHostAddress, VitalsHostPort), FabricNetServerConnection].asScala

  ////////////////////////////////////////////////////////////////////////////////////
  // API
  ////////////////////////////////////////////////////////////////////////////////////

  override def channel: Channel = _serverChannel

  override
  def talksTo(listeners: FabricNetServerListener*): this.type = {
    _listenerSet ++= listeners
    _connections.values foreach (_.talksTo(listeners: _*))
    this
  }

  ////////////////////////////////////////////////////////////////////////////////////
  // Pipeline
  ////////////////////////////////////////////////////////////////////////////////////


  /**
   * The callback for handling channel disconnect messages
   */
  override def onNetServerDisconnect(connection: FabricNetServerConnection): Unit = {
    _connections.remove((connection.remoteAddress, connection.remotePort))
  }

  private
  def initializer: ChannelInitializer[SocketChannel] = new ChannelInitializer[SocketChannel] {

    override def initChannel(channel: SocketChannel): Unit = {

      val connectionPipeline = channel.pipeline

      val transmitter = FabricNetTransmitter(container, isServer = true, channel)
      val receiver = FabricNetReceiver(container, isServer = true, channel)
      val connection = FabricNetServerConnection(container, channel, transmitter, receiver)
      FabricNetReporter.recordConnectOpen()
      connection.talksTo(_listenerSet.toSeq: _*)
      connection.talksTo(FabricNetServerContext.this)
      connection.start

      val clientAddress = connection.remoteAddress
      val clientPort = connection.remotePort
      _connections += (clientAddress, clientPort) -> connection
      log info s"NEW_CLIENT_CONNECTION $serviceName (now ${_connections.size} total) address=$clientAddress, port=$clientPort"

      // inbound goes in forward pipeline order
      connectionPipeline.addLast("server-inbound-stage-1", FabricNetInboundFrameDecoder())
      connectionPipeline.addLast("server-inbound-stage-2", receiver)

      // outbound goes in reverse pipeline order
      connectionPipeline.addLast("server-outbound-stage-2", FabricNetOutboundFrameEncoder())
      connectionPipeline.addLast("server-outbound-stage-1", transmitter)

    }

  }

  private
  def setupIoMode(): Unit = {
    ioMode match {
      case FabricNetIoMode.EPollIoMode =>
        _listenGroup = new EpollEventLoopGroup(1) // single threaded listener
        // lots of threads for lots of connections
        _connectionGroup = new EpollEventLoopGroup(netConfig.maxConnections)
        _transportClass = classOf[EpollServerSocketChannel]

      case FabricNetIoMode.NioIoMode =>
        log info burstStdMsg(s"fabric network server started in $ioMode with  $netConfig")
        //        _listenGroup = new NioEventLoopGroup(1) // single threaded listener
        _listenGroup = new NioEventLoopGroup()
        // lots of threads for lots of connections
        //        _connectionGroup = new NioEventLoopGroup(netConfig.maxConnections)
        _connectionGroup = new NioEventLoopGroup()
        _transportClass = classOf[NioServerSocketChannel]

      case FabricNetIoMode.KqIoMode =>
        _listenGroup = new KQueueEventLoopGroup(1) // single threaded listener
        // lots of threads for lots of connections
        _connectionGroup = new KQueueEventLoopGroup(netConfig.maxConnections)
        _transportClass = classOf[KQueueServerSocketChannel]

      case _ => ???
    }
  }

  ////////////////////////////////////////////////////////////////////////////////////
  // Lifecycle
  ////////////////////////////////////////////////////////////////////////////////////

  def start: this.type = {
    synchronized {
      ensureNotRunning
      log info startingMessage
      try {
        setupIoMode()

        val bootstrap = new ServerBootstrap
        bootstrap.group(_listenGroup, _connectionGroup).channel(_transportClass)
        setNettyOptions(bootstrap, netConfig).childHandler(initializer)
        val channelFuture = bootstrap.bind(netSupervisorAddress, netSupervisorPort)

        if (!channelFuture.awaitUninterruptibly.isSuccess) {
          val cause = channelFuture.cause
          val msg = s"$serviceName: server failed startup to $netSupervisorAddress($netSupervisorPort): ${cause.getLocalizedMessage}"
          log error msg
          // log error getAllThreadsDump.mkString("\n")
          throw VitalsException(msg, cause)
        }

        _serverChannel = channelFuture.channel()
      } catch safely {
        case t: Throwable =>
          log error burstStdMsg(s"$serviceName: could not bind", t)
          throw t
      }


      markRunning
      this
    }
  }

  def stop: this.type = {
    ensureRunning
    synchronized {
      log info stoppingMessage
      try
        _serverChannel.close.syncUninterruptibly
      finally {
        _listenGroup.shutdownGracefully
        _connectionGroup.shutdownGracefully
      }
      _listenGroup = null
      _serverChannel = null
      markNotRunning
      this
    }
  }

}
