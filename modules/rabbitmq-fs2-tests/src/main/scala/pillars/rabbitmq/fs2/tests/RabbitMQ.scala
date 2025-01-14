// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars.rabbitmq.fs2.tests

import cats.data.NonEmptyList
import cats.effect.Async
import cats.effect.Resource
import cats.effect.std.Console
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import com.dimafeng.testcontainers.RabbitMQContainer
import fs2.io.net.Network
import org.typelevel.otel4s.trace.Tracer
import pillars.Module
import pillars.Modules
import pillars.ModuleSupport
import pillars.rabbitmq.fs2.RabbitMQ as RabbitMQModule
import pillars.rabbitmq.fs2.RabbitMQConfig

case class RabbitMQ(container: RabbitMQContainer) extends ModuleSupport:
    override type M = RabbitMQModule

    override def key: Module.Key = RabbitMQModule.Key

    override def load[F[_]: Async: Network: Tracer: Console](
        context: ModuleSupport.Context[F],
        modules: Modules[F]
    ): Resource[F, RabbitMQModule[F]] = RabbitMQModule(config)

    private val config = RabbitMQConfig(
      nodes = NonEmptyList.one(
        RabbitMQConfig.Node(
          host = Host.fromString(container.host).get,
          port = Port.fromInt(container.amqpPort).get
        )
      )
    )
end RabbitMQ
