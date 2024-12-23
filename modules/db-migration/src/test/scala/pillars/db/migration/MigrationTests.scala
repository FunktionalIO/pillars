// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars.db.migration

import cats.effect.IO
import cats.syntax.all.*
import com.comcast.ip4s.*
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.munit.TestContainerForEach
import io.circe.Decoder
import io.github.iltotore.iron.*
import munit.CatsEffectSuite
import org.testcontainers.utility.DockerImageName
import org.typelevel.otel4s.trace.Tracer
import pillars.Config.Secret
import pillars.Module
import pillars.Pillars
import pillars.db.DatabaseConfig
import pillars.db.PoolSize
import pillars.db.migrations.*
import pillars.probes.ProbeConfig
import scala.concurrent.duration.*
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*

class MigrationTests extends CatsEffectSuite, TestContainerForEach:

    override val containerDef: PostgreSQLContainer.Def = PostgreSQLContainer.Def(
      dockerImageName = DockerImageName.parse("postgres:16.2"),
      databaseName = "pillars-migration",
      username = "pillars",
      password = "pillars"
    )

    given Pillars[IO] = new Pillars[IO]:
        def appInfo                         = ???
        def observability                   = ???
        def config                          = ???
        def apiServer                       = ???
        def logger                          = scribe.cats.io
        def readConfig[T](using Decoder[T]) = ???
        def module[T](key: Module.Key): T   = ???

    given Tracer[IO]                                                          = Tracer.noop[IO]
    private def dbConfigFor(pgContainer: PostgreSQLContainer): DatabaseConfig =
        DatabaseConfig(
          host = Host.fromString(pgContainer.host).get,
          port = Port.fromInt(pgContainer.container.getMappedPort(5432)).get,
          database = pillars.db.DatabaseName(pgContainer.databaseName.assume),
          username = pillars.db.DatabaseUser(pgContainer.username.assume),
          password = Secret(pillars.db.DatabasePassword(pgContainer.password.assume)),
          poolSize = PoolSize(10),
          probe = ProbeConfig()
        )

    private def configFor(pgContainer: PostgreSQLContainer): MigrationConfig =
        val url =
            s"jdbc:postgresql://${pgContainer.host}:${pgContainer.container.getMappedPort(5432)}/${pgContainer.databaseName}"
        println(url)
        MigrationConfig(
          url = JdbcUrl(url.refineUnsafe),
          username = DatabaseUser(pgContainer.username.assume),
          password = Some(Secret(DatabasePassword(pgContainer.password.assume)))
        )
    end configFor

    test("migration should run the scripts"):
        withContainers { pgContainer =>
            val config: MigrationConfig  = configFor(pgContainer)
            val dbConfig: DatabaseConfig = dbConfigFor(pgContainer)
            val migration                = DBMigration[IO](config)
            val result                   =
                for
                    _   <- migration.migrate("db/migrations")
                    res <- session(dbConfig).use: s =>
                               s.unique(sql"SELECT count(*) FROM test where d is not null".query(int8))
                yield res
            assertIO(result, 5L)
        }

    test("migration should write in the history table"):
        withContainers { pgContainer =>
            val config: MigrationConfig  = configFor(pgContainer)
            val dbConfig: DatabaseConfig = dbConfigFor(pgContainer)
            val migration                = DBMigration[IO](config)
            val result                   =
                for
                    _   <- migration.migrate("db/migrations", DatabaseSchema.public, DatabaseTable("schema_history"))
                    res <- session(dbConfig).use: s =>
                               s.unique(sql"SELECT count(*) FROM schema_history".query(int8))
                yield res
            assertIO(result, 2L)
        }

    test("running twice migrations should be the same as running once"):
        withContainers { pgContainer =>
            val config: MigrationConfig  = configFor(pgContainer)
            val dbConfig: DatabaseConfig = dbConfigFor(pgContainer)
            val migration                = DBMigration[IO](config)
            val result                   =
                for
                    _   <- migration.migrate("db/migrations")
                    _   <- migration.migrate("db/migrations")
                    res <- session(dbConfig).use: s =>
                               s.unique(sql"SELECT count(*) FROM test where d is not null".query(int8))
                yield res
            assertIO(result, 5L)
        }

    private def session(dbConfig: DatabaseConfig) =
        Session.single[IO](
          host = dbConfig.host.toString,
          port = dbConfig.port.value,
          database = dbConfig.database,
          user = dbConfig.username,
          password = dbConfig.password.some.map(_.value),
          debug = dbConfig.debug
        )
end MigrationTests
