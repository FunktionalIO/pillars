// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars.db_doobie.tests

import cats.effect.IO
import cats.effect.Resource
import com.dimafeng.testcontainers.JdbcDatabaseContainer
import io.github.iltotore.iron.*
import pillars.Config.Secret
import pillars.Module
import pillars.Modules
import pillars.ModuleSupport
import pillars.db_doobie.*
import pillars.probes.ProbeConfig

case class DB(container: JdbcDatabaseContainer) extends ModuleSupport:
    override type M = pillars.db_doobie.DB

    override def key: Module.Key = pillars.db_doobie.DB.key

    override def load(context: ModuleSupport.Context, modules: Modules): Resource[IO, pillars.db_doobie.DB] =
        pillars.db_doobie.DB.load(dbConfig)

    private def dbConfig: DatabaseConfig =
        DatabaseConfig(
          driverClassName = DriverClassName(container.driverClassName.assume),
          url = JdbcUrl(container.jdbcUrl.assume),
          username = DatabaseUser(container.username.assume),
          password = Secret(DatabasePassword(container.password.assume)),
          probe = ProbeConfig()
        )
end DB
