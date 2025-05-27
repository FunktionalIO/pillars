// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars.db.migrations

import cats.effect.IO
import cats.effect.Resource
import fs2.io.file.Files
import io.circe.Codec
import io.circe.derivation.Configuration
import io.github.iltotore.iron.*
import io.github.iltotore.iron.circe.given
import io.github.iltotore.iron.constraint.all.*
import org.flywaydb.core.Flyway
import pillars.Config.Secret
import pillars.Module
import pillars.Modules
import pillars.ModuleSupport
import pillars.Pillars
import pillars.Run
import pillars.logger

final case class DBMigration(config: MigrationConfig) extends Module:
    override type ModuleConfig = MigrationConfig

    private def flyway(schema: DatabaseSchema, table: DatabaseTable, location: String) = Flyway
        .configure()
        .loggers("slf4j")
        .baselineVersion(config.baselineVersion)
        // Avoid Flyway complaining that migration files are missing.
        // Useful only for staging environment, because some migration files already passed and they can't be found anymore.
        // For the long term it disables a Flyway integrity check, but it's not an important one because migration
        // files are embedded within the application.
        // This configuration can be dropped if none of our environments have been setup with one of the migration_archive script.
        // (maybe after an hard reset of our staging env)
        .ignoreMigrationPatterns("*:missing")
        .baselineOnMigrate(true)
        .dataSource(config.url, config.username, config.password.map(_.value).getOrElse(""))
        .locations(location)
        .schemas(schema)
        .table(table)
        .load()

    inline def migrateModule(key: Module.Key): Run[IO[Unit]] =
        for
            _ <- IO.delay:
                     flyway(
                       DatabaseSchema.pillars,
                       DatabaseTable.assume(s"${key.name.replaceAll("[^0-9a-zA-Z$_]", "-")}_schema_history"),
                       "classpath:db/migrations"
                     ).migrate()
            _ <- logger.info(s"Migration completed for module ${key.name}")
        yield ()
    def migrate(
        path: String,
        schema: DatabaseSchema = DatabaseSchema.public,
        schemaHistoryTable: DatabaseTable = DatabaseTable("flyway_schema_history")
    ): Run[IO[Unit]] =
        for
            _ <- IO.delay(flyway(schema, schemaHistoryTable, path).migrate())
            _ <- logger.info(s"Migration completed for $schema")
        yield ()

end DBMigration

def dbMigration(using p: Pillars): DBMigration = p.module[DBMigration](DBMigration.Key)

object DBMigration extends ModuleSupport:
    case object Key extends Module.Key:
        override val name: String = "db-migration"

    override type M = DBMigration
    override val key: Module.Key = DBMigration.Key

    override def dependsOn: Set[ModuleSupport] = Set.empty

    def load(context: ModuleSupport.Context, modules: Modules): Resource[IO, DBMigration] =
        given Files[IO] = Files.forIO
        Resource.eval:
            for
                _      <- context.logger.info("Loading DB Migration module")
                config <- context.reader.read[MigrationConfig]("db-migration")
                _      <- context.logger.info("DB Migration module loaded")
            yield DBMigration(config)
            end for
    end load
end DBMigration

final case class MigrationConfig(
    url: JdbcUrl,
    username: DatabaseUser,
    password: Option[Secret[DatabasePassword]],
    systemSchema: DatabaseSchema = DatabaseSchema.public,
    appSchema: DatabaseSchema = DatabaseSchema.public,
    baselineVersion: String = "0"
) extends pillars.Config
object MigrationConfig:
    given Configuration          = pillars.Config.defaultCirceConfig
    given Codec[MigrationConfig] = Codec.AsObject.derivedConfigured

private type JdbcUrlConstraint =
    DescribedAs[Match["jdbc\\:[^:]+\\:.*"], "JDBC URL must be in jdbc:<subprotocol>:<subname> format"]
type JdbcUrl                   = JdbcUrl.T

object JdbcUrl extends RefinedType[String, JdbcUrlConstraint]

private type DatabaseNameConstraint = DescribedAs[Not[Blank], "Database name must not be blank"]
type DatabaseName                   = DatabaseName.T

object DatabaseName extends RefinedType[String, DatabaseNameConstraint]

private type DatabaseSchemaConstraint = DescribedAs[Not[Blank], "Database schema must not be blank"]
type DatabaseSchema                   = DatabaseSchema.T

object DatabaseSchema extends RefinedType[String, DatabaseSchemaConstraint]:
    val public: DatabaseSchema  = DatabaseSchema("public")
    val pillars: DatabaseSchema = DatabaseSchema("pillars")

private type DatabaseTableConstraint =
    DescribedAs[
      (Not[Blank] & Match["""^[a-zA-Z_][0-9a-zA-Z$_]{0,63}$"""]),
      "Database table must be at most 64 characters (letter, digit, dollar sign or underscore) long and start with a letter or an underscore"
    ]
type DatabaseTable                   = DatabaseTable.T

object DatabaseTable extends RefinedType[String, DatabaseTableConstraint]

private type DatabaseUserConstraint = DescribedAs[Not[Blank], "Database user must not be blank"]
type DatabaseUser                   = DatabaseUser.T

object DatabaseUser extends RefinedType[String, DatabaseUserConstraint]

private type DatabasePasswordConstraint = DescribedAs[Not[Blank], "Database password must not be blank"]
type DatabasePassword                   = DatabasePassword.T

object DatabasePassword extends RefinedType[String, DatabasePasswordConstraint]
