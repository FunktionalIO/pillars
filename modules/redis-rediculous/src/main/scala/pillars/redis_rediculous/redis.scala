// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars.redis_rediculous

import cats.effect.*
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
import pillars.Module
import pillars.Modules
import pillars.ModuleSupport
import pillars.Pillars
import pillars.codec.given
import pillars.probes.*

extension (p: Pillars)
    def redis: Redis = p.module[Redis](Redis.Key)

final case class Redis(config: RedisConfig, connection: Resource[IO, RedisConnection[IO]]) extends Module:
    override type ModuleConfig = RedisConfig
    export connection.*

    override def probes: List[Probe] =
        val probe = new Probe:
            override def component: Component = Component(Component.Name("redis"), Component.Type.Datastore)
            override def check: IO[Boolean]   = connection.use: client =>
                RedisCommands.ping[io.chrisdavenport.rediculous.Redis[IO, *]].run(client).map:
                    case Ok | Pong => true
                    case _         => false
        probe.pure[List]
    end probes
end Redis

object Redis extends ModuleSupport:
    case object Key extends Module.Key:
        override val name: String = "redis-rediculous"
    def apply(using p: Pillars): Redis = p.module[Redis](Redis.Key)

    override type M = Redis
    override val key: Module.Key = Redis.Key

    def load(context: ModuleSupport.Context, modules: Modules): Resource[IO, Redis] =
        import context.*
        given Files[IO] = Files.forIO
        for
            _         <- Resource.eval(logger.info("Loading Redis module"))
            config    <- Resource.eval(reader.read[RedisConfig]("redis"))
            connection = create(config)
            _         <- Resource.eval(logger.info("Redis module loaded"))
        yield connection
        end for
    end load
    def load(config: RedisConfig): Resource[IO, Redis]                              =
        create(config).pure[Resource[IO, *]]
    end load

    private def create(config: RedisConfig) =
        val builder = RedisConnection.queued[IO]
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
