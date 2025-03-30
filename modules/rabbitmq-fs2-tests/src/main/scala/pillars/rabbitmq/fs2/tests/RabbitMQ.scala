// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars.rabbitmq.fs2.tests

import cats.effect.IO
import cats.effect.Resource
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import com.dimafeng.testcontainers.RabbitMQContainer
import pillars.Module
import pillars.Modules
import pillars.ModuleSupport
import pillars.rabbitmq.fs2.RabbitMQ as RabbitMQModule
import pillars.rabbitmq.fs2.RabbitMQConfig

case class RabbitMQ(container: RabbitMQContainer) extends ModuleSupport:
    override type M = RabbitMQModule

    override def key: Module.Key = RabbitMQModule.Key

    override def load(context: ModuleSupport.Context, modules: Modules): Resource[IO, RabbitMQModule] =
        RabbitMQModule(config)

    private val config = RabbitMQConfig(
      host = Host.fromString(container.host).get,
      port = Port.fromInt(container.amqpPort).get
    )
end RabbitMQ
