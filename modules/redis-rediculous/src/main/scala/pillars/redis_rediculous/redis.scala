// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars.redis_rediculous

import cats.effect.*
import cats.effect.std.Console
import cats.implicits.*
import com.comcast.ip4s.*
import fs2.io.file.Files
import fs2.io.net.*
import io.chrisdavenport.rediculous.*
import io.chrisdavenport.rediculous.RedisConnection.Defaults
import io.chrisdavenport.rediculous.RedisProtocol.Status.*
import io.circe.Codec
import io.circe.Decoder as CirceDecoder
import io.circe.Encoder as CirceEncoder
import io.circe.derivation.Configuration
import io.github.iltotore.iron.*
import io.github.iltotore.iron.circe.given
import io.github.iltotore.iron.constraint.all.*
import org.typelevel.otel4s.trace.Tracer
import pillars.Module
import pillars.Modules
import pillars.ModuleSupport
import pillars.Pillars
import pillars.codec.given
import pillars.probes.*

extension [F[_]](p: Pillars[F])
    def redis: Redis[F] = p.module[Redis[F]](Redis.Key)

final case class Redis[F[_]: MonadCancelThrow](config: RedisConfig, connection: Resource[F, RedisConnection[F]])(using
    c: Concurrent[F]
) extends Module[F]:
    override type ModuleConfig = RedisConfig
    export connection.*

    override def probes: List[Probe[F]] =
        val probe = new Probe[F]:
            override def component: Component = Component(Component.Name("redis"), Component.Type.Datastore)
            override def check: F[Boolean]    = connection.use { client =>
                RedisCommands.ping[io.chrisdavenport.rediculous.Redis[F, *]].run(client) map {
                    case Ok | Pong => true
                    case _         => false
                }
            }
        probe.pure[List]
    end probes
end Redis

object Redis extends ModuleSupport:
    case object Key extends Module.Key:
        override val name: String = "redis-rediculous"
    def apply[F[_]](using p: Pillars[F]): Redis[F] = p.module[Redis[F]](Redis.Key)

    override type M[F[_]] = Redis[F]
    override val key: Module.Key = Redis.Key

    def load[F[_]: Async: Network: Tracer: Console](
        context: ModuleSupport.Context[F],
        modules: Modules[F]
    ): Resource[F, Redis[F]] =
        import context.*
        given Files[F] = Files.forAsync[F]
        for
            _         <- Resource.eval(logger.info("Loading Redis module"))
            config    <- Resource.eval(reader.read[RedisConfig]("redis"))
            connection = create(config)
            _         <- Resource.eval(logger.info("Redis module loaded"))
        yield connection
        end for
    end load
    def load[F[_]: Async: Network: Tracer: Console](config: RedisConfig): Resource[F, Redis[F]] =
        create(config).pure[Resource[F, *]]
    end load

    private def create[F[_]: Async: Network: Tracer: Console](config: RedisConfig) =
        val builder = RedisConnection.queued[F]
            .withHost(config.host)
            .withPort(config.port)
            .withMaxQueued(config.maxQueue)
            .withWorkers(config.workers)
            .withTLS
        Redis(
          config,
          config.password.fold(builder)(pwd => builder.withAuth(config.username, pwd)).build
        )
    end create
end Redis

final case class RedisConfig(
    host: Host = host"localhost",
    port: Port = port"6379",
    maxQueue: Int = Defaults.maxQueued,
    workers: Int = Defaults.workers,
    username: Option[RedisUser],
    password: Option[RedisPassword],
    probe: ProbeConfig
) extends pillars.Config

object RedisConfig:
    given Configuration      = Configuration.default.withKebabCaseMemberNames.withKebabCaseConstructorNames.withDefaults
    given Codec[RedisConfig] = Codec.AsObject.derivedConfigured
end RedisConfig

private type RedisUserConstraint = Not[Blank] DescribedAs "Redis user must not be blank"
opaque type RedisUser <: String  = String :| RedisUserConstraint

object RedisUser extends RefinedTypeOps[String, RedisUserConstraint, RedisUser]

private type RedisPasswordConstraint = Not[Blank] DescribedAs "Redis password must not be blank"
opaque type RedisPassword <: String  = String :| RedisPasswordConstraint

object RedisPassword extends RefinedTypeOps[String, RedisPasswordConstraint, RedisPassword]
