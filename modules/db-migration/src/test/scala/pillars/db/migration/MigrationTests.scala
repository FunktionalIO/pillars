// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars.db.migration

import cats.effect.IO
import com.dimafeng.testcontainers.PostgreSQLContainer
import io.github.iltotore.iron.*
import org.testcontainers.utility.DockerImageName
import org.typelevel.otel4s.trace.Tracer
import pillars.Config.Secret
import pillars.Pillars
import pillars.db.DatabaseConfig
import pillars.db.migrations.*
import pillars.db.sessions
import pillars.db.tests.DB
import pillars.tests.*
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*

class MigrationTests extends PillarsFromContainerForEach:
    override val moduleSupports: Set[ModuleTestSupport] = Set(DB)

    override val containerDef: PostgreSQLContainer.Def = PostgreSQLContainer.Def(
      dockerImageName = DockerImageName.parse("postgres:16.2"),
      databaseName = "pillars-migration",
      username = "pillars",
      password = "pillars"
    )

    private def configFor(dbConfig: DatabaseConfig): MigrationConfig =
        val url =
            s"jdbc:postgresql://${dbConfig.host}:${dbConfig.port}/${dbConfig.database}"
        MigrationConfig(
          url = JdbcUrl(url.refineUnsafe),
          username = DatabaseUser(dbConfig.username.assume),
          password = Some(Secret(DatabasePassword(dbConfig.password.value.assume)))
        )
    end configFor

    test("migration should run the scripts"):
        withPillars: p =>
            given Pillars[IO]           = p
            given Tracer[IO]            = p.observability.tracer
            val config: MigrationConfig = configFor(p.module[pillars.db.DB[IO]](DB.key).config)
            val migration               = DBMigration[IO](config)
            val result                  =
                for
                    _   <- migration.migrate("db/migrations")
                    res <- sessions.use: s =>
                               s.unique(sql"SELECT count(*) FROM test where d is not null".query(int8))
                yield res
            assertIO(result, 5L)

    test("migration should write in the history table"):
        withPillars: p =>
            given Pillars[IO]           = p
            given Tracer[IO]            = p.observability.tracer
            val config: MigrationConfig = configFor(p.module[pillars.db.DB[IO]](DB.key).config)
            val migration               = DBMigration[IO](config)
            val result                  =
                for
                    _   <- migration.migrate("db/migrations", DatabaseSchema.public, DatabaseTable("schema_history"))
                    res <- sessions.use: s =>
                               s.unique(sql"SELECT count(*) FROM schema_history".query(int8))
                yield res
            assertIO(result, 2L)

    test("running twice migrations should be the same as running once"):
        withPillars: p =>
            given Pillars[IO]           = p
            given Tracer[IO]            = p.observability.tracer
            val config: MigrationConfig = configFor(p.module[pillars.db.DB[IO]](DB.key).config)
            val migration               = DBMigration[IO](config)
            val result                  =
                for
                    _   <- migration.migrate("db/migrations")
                    _   <- migration.migrate("db/migrations")
                    res <- sessions.use: s =>
                               s.unique(sql"SELECT count(*) FROM test where d is not null".query(int8))
                yield res
            assertIO(result, 5L)
end MigrationTests
