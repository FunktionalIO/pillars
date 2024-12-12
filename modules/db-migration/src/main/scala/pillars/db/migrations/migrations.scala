// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars.db.migrations

import cats.effect.Async
import cats.effect.Resource
import cats.effect.std.Console
import cats.syntax.all.*
import fs2.io.file.Files
import fs2.io.net.Network
import io.circe.Codec
import io.circe.derivation.Configuration
import io.github.iltotore.iron.*
import io.github.iltotore.iron.circe.given
import io.github.iltotore.iron.constraint.all.*
import org.flywaydb.core.Flyway
import org.typelevel.otel4s.trace.Tracer
import pillars.Config.Secret
import pillars.Module
import pillars.Modules
import pillars.ModuleSupport
import pillars.Pillars
import pillars.Run
import pillars.logger

final case class DBMigration[F[_]: Async: Console: Tracer: Network: Files](
    config: MigrationConfig
) extends Module[F]:
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

    inline def migrateModule(key: Module.Key): Run[F, F[Unit]] =
        for
            _ <- Async[F].delay:
                     flyway(
                       DatabaseSchema.pillars,
                       DatabaseTable(s"${key.name.replaceAll("[^0-9a-zA-Z$_]", "-")}_schema_history".assume),
                       "classpath:db/migrations"
                     ).migrate()
            _ <- logger.info(s"Migration completed for module ${key.name}")
        yield ()
    def migrate(
        path: String,
        schema: DatabaseSchema = DatabaseSchema.public,
        schemaHistoryTable: DatabaseTable = DatabaseTable("flyway_schema_history")
    ): Run[F, F[Unit]] =
        for
            _ <- Async[F].delay(flyway(schema, schemaHistoryTable, path).migrate())
            _ <- logger.info(s"Migration completed for $schema")
        yield ()

end DBMigration

def dbMigration[F[_]](using p: Pillars[F]): DBMigration[F] = p.module[DBMigration[F]](DBMigration.Key)

object DBMigration extends ModuleSupport:
    case object Key extends Module.Key:
        override val name: String = "db-migration"

    override type M[F[_]] = DBMigration[F]
    override val key: Module.Key = DBMigration.Key

    override def dependsOn: Set[ModuleSupport] = Set.empty

    def load[F[_]: Async: Network: Tracer: Console](
        context: ModuleSupport.Context[F],
        modules: Modules[F]
    ): Resource[F, DBMigration[F]] =
        given Files[F] = Files.forAsync[F]
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
    given Configuration = Configuration.default.withKebabCaseMemberNames.withKebabCaseConstructorNames.withDefaults

    given Codec[MigrationConfig] = Codec.AsObject.derivedConfigured

private type JdbcUrlConstraint =
    Match["jdbc\\:[^:]+\\:.*"] DescribedAs "JDBC URL must be in jdbc:<subprotocol>:<subname> format"
opaque type JdbcUrl <: String  = String :| JdbcUrlConstraint

object JdbcUrl extends RefinedTypeOps[String, JdbcUrlConstraint, JdbcUrl]

private type DatabaseNameConstraint = Not[Blank] DescribedAs "Database name must not be blank"
opaque type DatabaseName <: String  = String :| DatabaseNameConstraint

object DatabaseName extends RefinedTypeOps[String, DatabaseNameConstraint, DatabaseName]

private type DatabaseSchemaConstraint = Not[Blank] DescribedAs "Database schema must not be blank"
opaque type DatabaseSchema <: String  = String :| DatabaseSchemaConstraint

object DatabaseSchema extends RefinedTypeOps[String, DatabaseSchemaConstraint, DatabaseSchema]:
    val public: DatabaseSchema  = DatabaseSchema("public")
    val pillars: DatabaseSchema = DatabaseSchema("pillars")

private type DatabaseTableConstraint =
    (Not[Blank] & Match["""^[a-zA-Z_][0-9a-zA-Z$_]{0,63}$"""]) DescribedAs "Database table must be at most 64 characters (letter, digit, dollar sign or underscore) long and start with a letter or an underscore"
opaque type DatabaseTable <: String  = String :| DatabaseTableConstraint

object DatabaseTable extends RefinedTypeOps[String, DatabaseTableConstraint, DatabaseTable]

private type DatabaseUserConstraint = Not[Blank] DescribedAs "Database user must not be blank"
opaque type DatabaseUser <: String  = String :| DatabaseUserConstraint

object DatabaseUser extends RefinedTypeOps[String, DatabaseUserConstraint, DatabaseUser]

private type DatabasePasswordConstraint = Not[Blank] DescribedAs "Database password must not be blank"
opaque type DatabasePassword <: String  = String :| DatabasePasswordConstraint

object DatabasePassword extends RefinedTypeOps[String, DatabasePasswordConstraint, DatabasePassword]
