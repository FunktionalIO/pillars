// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars.redis_rediculous.tests

import cats.effect.IO
import cats.effect.Resource
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import com.dimafeng.testcontainers.RedisContainer
import pillars.Module
import pillars.Modules
import pillars.ModuleSupport
import pillars.probes.ProbeConfig
import pillars.redis_rediculous.RedisConfig

case class Redis(container: RedisContainer) extends ModuleSupport:
    override type M = pillars.redis_rediculous.Redis

    override def key: Module.Key = pillars.redis_rediculous.Redis.key

    override def load(context: ModuleSupport.Context, modules: Modules): Resource[IO, pillars.redis_rediculous.Redis] =
        pillars.redis_rediculous.Redis.load(redisConfig)

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
