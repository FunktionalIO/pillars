// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars.db.tests

import cats.effect.Async
import cats.effect.Resource
import cats.effect.std.Console
import cats.syntax.all.*
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import com.dimafeng.testcontainers.Container
import com.dimafeng.testcontainers.PostgreSQLContainer
import fs2.io.net.Network
import io.github.iltotore.iron.*
import org.typelevel.otel4s.trace.Tracer
import pillars.Config.Secret
import pillars.Module
import pillars.db
import pillars.db.DatabaseConfig
import pillars.db.DB as DBModule
import pillars.db.PoolSize
import pillars.probes.ProbeConfig
import pillars.tests.ModuleTestSupport

object DB extends ModuleTestSupport:
    override def key: Module.Key = DBModule.key

    def load[F[_]: Async: Network: Tracer: Console](container: Container): Option[Resource[F, Module[F]]] =
        container match
            case c: PostgreSQLContainer => DBModule.load(configFor(c)).some
            case _                      => None

    private def configFor(container: PostgreSQLContainer): DatabaseConfig =
        DatabaseConfig(
          host = Host.fromString(container.host).get,
          port = Port.fromInt(container.container.getMappedPort(5432)).get,
          database = pillars.db.DatabaseName(container.databaseName.assume),
          username = pillars.db.DatabaseUser(container.username.assume),
          password = Secret(pillars.db.DatabasePassword(container.password.assume)),
          poolSize = PoolSize(10),
          probe = ProbeConfig()
        )

end DB
