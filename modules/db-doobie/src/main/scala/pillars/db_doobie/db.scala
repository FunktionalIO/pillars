// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars.db_doobie

import cats.effect.*
import cats.syntax.all.*
import com.zaxxer.hikari.HikariConfig
import doobie.*
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import fs2.io.file.Files
import io.circe.Codec
import io.circe.Decoder as CirceDecoder
import io.circe.Encoder as CirceEncoder
import io.circe.derivation.Configuration
import io.github.iltotore.iron.*
import io.github.iltotore.iron.circe.given
import io.github.iltotore.iron.constraint.all.*
import java.util.Properties
import pillars.Config.*
import pillars.Module
import pillars.Modules
import pillars.ModuleSupport
import pillars.Pillars
import pillars.probes.*

final case class DB(config: DatabaseConfig, transactor: Transactor[IO]) extends Module:
    override type ModuleConfig = DatabaseConfig

    override def probes: List[Probe] =
        val probe = new Probe:
            override def component: Component = Component(Component.Name("db"), Component.Type.Datastore)
            override def check: IO[Boolean]   = sql"select true".query[Boolean].unique.transact(transactor)
        probe.pure[List]
    end probes
end DB

def db(using p: Pillars): DB = p.module[DB](DB.Key)

object DB extends ModuleSupport:
    case object Key extends Module.Key:
        override val name: String = "db-doobie"

    override type M = DB
    override val key: Module.Key = DB.Key

    def load(
        context: ModuleSupport.Context,
        modules: Modules
    ): Resource[IO, DB] =
        import context.*
        given Files[IO] = Files.forAsync[IO]
        for
            _      <- Resource.eval(logger.info("Loading DB module"))
            config <- Resource.eval(reader.read[DatabaseConfig]("db"))
            _      <- Resource.eval(logger.info("DB module loaded"))
            xa     <- HikariTransactor.fromHikariConfig[IO](config.toHikariConfig)
        yield DB(config, xa)
        end for
    end load

    def load(config: DatabaseConfig): Resource[IO, DB] =
        HikariTransactor.fromHikariConfig[IO](config.toHikariConfig).map(xa => DB(config, xa))
    end load
end DB

final case class DatabaseConfig(
    driverClassName: DriverClassName,
    url: JdbcUrl,
    username: DatabaseUser,
    password: Secret[DatabasePassword],
    systemSchema: DatabaseSchema = DatabaseSchema.public,
    appSchema: DatabaseSchema = DatabaseSchema.public,
    poolSize: PoolSize = PoolSize(32),
    statementCache: StatementCacheConfig = StatementCacheConfig(),
    debug: Boolean = false,
    probe: ProbeConfig
) extends pillars.Config:
    def toHikariConfig: HikariConfig =
        val cfg = new HikariConfig
        cfg.setDriverClassName(driverClassName)
        cfg.setJdbcUrl(url)
        cfg.setUsername(username)
        cfg.setPassword(password.value)

        val props = new Properties
        props.put("cachePrepStmts", statementCache.enabled.toString)
        props.put("prepStmtCacheSize", statementCache.size.toString)
        props.put("prepStmtCacheSqlLimit", statementCache.sqlLimit.toString)

        cfg.setDataSourceProperties(props)
        cfg.setMaximumPoolSize(poolSize)

        cfg
    end toHikariConfig
end DatabaseConfig

object DatabaseConfig:
    given Configuration         = pillars.Config.defaultCirceConfig
    given Codec[DatabaseConfig] = Codec.AsObject.derivedConfigured
end DatabaseConfig

final case class StatementCacheConfig(
    enabled: Boolean = true,
    size: Size = Size(250),
    sqlLimit: Size = Size(2048)
)

object StatementCacheConfig:
    given Configuration               = pillars.Config.defaultCirceConfig
    given Codec[StatementCacheConfig] = Codec.AsObject.derivedConfigured
end StatementCacheConfig

private type SizeConstraint = DescribedAs[Positive0, "Size must be positive or zero"]
type Size                   = Size.T

object Size extends RefinedType[Int, SizeConstraint]

private type JdbcUrlConstraint =
    DescribedAs[Match["jdbc\\:[^:]+\\:.*"], "Driver class name must in jdbc:<subprotocol>:<subname> format"]
type JdbcUrl                   = JdbcUrl.T

object JdbcUrl extends RefinedType[String, JdbcUrlConstraint]

private type DriverClassNameConstraint = DescribedAs[Not[Blank], "Driver class name must not be blank"]
type DriverClassName                   = DriverClassName.T

object DriverClassName extends RefinedType[String, DriverClassNameConstraint]

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

private type PoolSizeConstraint = DescribedAs[GreaterEqual[1], "Pool size must be greater or equal to 1"]
type PoolSize                   = PoolSize.T

object PoolSize extends RefinedType[Int, PoolSizeConstraint]

private type VersionConstraint =
    DescribedAs[Not[Blank] & Match["^(\\d+\\.\\d+\\.\\d+)$"], "Schema version must be in the form of X.Y.Z"]
type SchemaVersion             = SchemaVersion.T

object SchemaVersion extends RefinedType[String, Not[Blank] & Match["^(\\d+\\.\\d+\\.\\d+)$"]]
