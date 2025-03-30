// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars.redis_rediculous.tests

import cats.effect.Async
import cats.effect.Resource
import cats.effect.std.Console
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import com.dimafeng.testcontainers.RedisContainer
import fs2.io.net.Network
import org.typelevel.otel4s.trace.Tracer
import pillars.Module
import pillars.Modules
import pillars.ModuleSupport
import pillars.probes.ProbeConfig
import pillars.redis_rediculous.RedisConfig

case class Redis(container: RedisContainer) extends ModuleSupport:
    override type M[F[_]] = pillars.redis_rediculous.Redis[F]

    override def key: Module.Key = pillars.redis_rediculous.Redis.key

    override def load[F[_]: Async: Network: Tracer: Console](
        context: ModuleSupport.Context[F],
        modules: Modules[F]
    ): Resource[F, pillars.redis_rediculous.Redis[F]] = pillars.redis_rediculous.Redis.load[F](redisConfig)

    private def redisConfig: RedisConfig =
        RedisConfig(
          host = Host.fromString(container.host).get,
          port = Port.fromInt(container.container.getMappedPort(6379)).get,
          username = None,
          password = None,
          probe = ProbeConfig()
        )
    end redisConfig
end Redis
