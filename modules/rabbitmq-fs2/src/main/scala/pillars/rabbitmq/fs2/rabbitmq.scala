// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars.rabbitmq.fs2

import cats.effect.IO
import cats.effect.Resource
import cats.syntax.applicative.*
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import com.comcast.ip4s.host
import com.comcast.ip4s.port
import dev.profunktor.fs2rabbit.config.Fs2RabbitConfig
import dev.profunktor.fs2rabbit.config.Fs2RabbitNodeConfig
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import fs2.io.file.Files
import io.circe.Codec
import io.circe.derivation.Configuration
import io.github.iltotore.iron.*
import io.github.iltotore.iron.circe.given
import io.github.iltotore.iron.constraint.all.*
import pillars.Config.Secret
import pillars.Module
import pillars.Modules
import pillars.ModuleSupport
import pillars.Pillars
import pillars.codec.given
import pillars.probes.Component
import pillars.probes.Probe
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps

def rabbit(using p: Pillars): RabbitMQ = p.module[RabbitMQ](RabbitMQ.Key)

final case class RabbitMQ(config: RabbitMQConfig, client: RabbitClient[IO]) extends Module:
    override type ModuleConfig = RabbitMQConfig
    export client.*

    override def probes: List[Probe] =
        val probe = new Probe:
            override def component: Component = Component(Component.Name("rabbitmq"), Component.Type.Datastore)
            override def check: IO[Boolean]   = true.pure[IO]
        probe.pure[List]
    end probes
end RabbitMQ

object RabbitMQ extends ModuleSupport:
    case object Key extends Module.Key:
        override val name: String = "rabbitmq"

    def apply(using p: Pillars): RabbitMQ = p.module[RabbitMQ](RabbitMQ.Key)

    def apply(config: RabbitMQConfig): Resource[IO, RabbitMQ] =
        RabbitClient.default[IO](config.convert).resource.map(apply(config, _))

    override type M = RabbitMQ
    override val key: Module.Key = RabbitMQ.Key

    override def load(
        context: ModuleSupport.Context,
        modules: Modules
    ): Resource[IO, RabbitMQ] =
        import context.*
        given Files[IO] = Files.forIO
        for
            _      <- Resource.eval(logger.info("Loading RabbitMQ module"))
            config <- Resource.eval(reader.read[RabbitMQConfig]("rabbitmq"))
            client <- RabbitMQ(config)
            _      <- Resource.eval(logger.info("RabbitMQ module loaded"))
        yield client
        end for
    end load

end RabbitMQ

case class RabbitMQConfig(
    host: Host = host"localhost",
    port: Port = port"5672",
    virtualHost: RabbitMQVirtualHost = RabbitMQVirtualHost("/"),
    connectionTimeout: FiniteDuration = 5 seconds,
    ssl: Boolean = true,
    username: Option[RabbitMQUser] = None,
    password: Option[Secret[RabbitMQPassword]] = None,
    requeueOnNack: Boolean = true,
    requeueOnReject: Boolean = true,
    internalQueueSize: Option[Int :| Positive] = Some(1024),
    requestedHeartbeat: FiniteDuration = 60 seconds,
    automaticRecovery: Boolean = true,
    automaticTopologyRecovery: Boolean = true,
    clientProvidedConnectionName: Option[RabbitMQConnectionName] = None
) extends pillars.Config

object RabbitMQConfig:
    final case class Node(host: Host, port: Port) extends pillars.Config derives Codec.AsObject
    given Configuration         = Configuration.default.withKebabCaseMemberNames.withKebabCaseConstructorNames.withDefaults
    given Codec[RabbitMQConfig] = Codec.AsObject.derivedConfigured

    given Conversion[RabbitMQConfig.Node, Fs2RabbitNodeConfig] =
        node => Fs2RabbitNodeConfig(node.host.toString, node.port.value)
    given Conversion[RabbitMQConfig, Fs2RabbitConfig]          = cfg =>
        Fs2RabbitConfig(
          host = cfg.host.toString,
          port = cfg.port.value,
          virtualHost = cfg.virtualHost,
          connectionTimeout = cfg.connectionTimeout,
          ssl = cfg.ssl,
          username = cfg.username,
          password = cfg.password.map(_.value),
          requeueOnNack = cfg.requeueOnNack,
          requeueOnReject = cfg.requeueOnReject,
          internalQueueSize = cfg.internalQueueSize,
          requestedHeartbeat = cfg.requestedHeartbeat,
          automaticRecovery = cfg.automaticRecovery,
          clientProvidedConnectionName = cfg.clientProvidedConnectionName
        )
end RabbitMQConfig

private type RabbitMQVirtualHostConstraint = Not[Blank] DescribedAs "RabbitMQ virtual host must not be blank"
opaque type RabbitMQVirtualHost <: String  = String :| RabbitMQVirtualHostConstraint
object RabbitMQVirtualHost extends RefinedTypeOps[String, RabbitMQVirtualHostConstraint, RabbitMQVirtualHost]

private type RabbitMQUserConstraint = Not[Blank] DescribedAs "RabbitMQ user must not be blank"
opaque type RabbitMQUser <: String  = String :| RabbitMQUserConstraint
object RabbitMQUser extends RefinedTypeOps[String, RabbitMQUserConstraint, RabbitMQUser]

private type RabbitMQPasswordConstraint = Not[Blank] DescribedAs "RabbitMQ password must not be blank"
opaque type RabbitMQPassword <: String  = String :| RabbitMQPasswordConstraint
object RabbitMQPassword extends RefinedTypeOps[String, RabbitMQPasswordConstraint, RabbitMQPassword]

private type RabbitMQConnectionNameConstraint = Not[Blank] DescribedAs "RabbitMQ connection name must not be blank"
opaque type RabbitMQConnectionName <: String  = String :| RabbitMQConnectionNameConstraint
object RabbitMQConnectionName extends RefinedTypeOps[String, RabbitMQConnectionNameConstraint, RabbitMQConnectionName]
