package pillars.db_doobie

import cats.effect.*
import cats.effect.std.Console
import cats.syntax.all.*
import com.zaxxer.hikari.HikariConfig
import doobie.*
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import fs2.io.file.Files
import fs2.io.net.Network
import io.circe.Codec
import io.circe.Decoder as CirceDecoder
import io.circe.Encoder as CirceEncoder
import io.circe.derivation.Configuration
import io.github.iltotore.iron.*
import io.github.iltotore.iron.circe.given
import io.github.iltotore.iron.constraint.all.*
import java.util.Properties
import org.typelevel.otel4s.trace.Tracer
import pillars.Config.*
import pillars.Loader
import pillars.Module
import pillars.Modules
import pillars.Pillars
import pillars.probes.*

extension [F[_]](p: Pillars[F])
    def db: DB[F] = p.module[DB[F]](DB.Key)

final case class DB[F[_]: MonadCancelThrow](transactor: Resource[F, Transactor[F]]) extends Module[F]:
    export transactor.*

    override def probes: List[Probe[F]] =
        val probe = new Probe[F]:
            override def component: Component = Component(Component.Name("db"), Component.Type.Datastore)
            override def check: F[Boolean]    = transactor.use(xa => sql"select true".query[Boolean].unique.transact(xa))
        probe.pure[List]
    end probes
end DB

object DB:
    case object Key extends Module.Key:
        override val name: String = "db-doobie"
    def apply[F[_]](using p: Pillars[F]): DB[F] = p.module[DB[F]](DB.Key)

class DBLoader extends Loader:
    override type M[F[_]] = DB[F]
    override val key: Module.Key = DB.Key

    def load[F[_]: Async: Network: Tracer: Console](
        context: Loader.Context[F],
        modules: Modules[F]
    ): Resource[F, DB[F]] =
        import context.*
        given Files[F] = Files.forAsync[F]
        for
            _      <- Resource.eval(logger.info("Loading DB module"))
            config <- Resource.eval(configReader.read[DatabaseConfig]("db"))
            _      <- Resource.eval(logger.info("DB module loaded"))
        yield DB(HikariTransactor.fromHikariConfig[F](config.toHikariConfig))
        end for
    end load
end DBLoader

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
):
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
    given Configuration         = Configuration.default.withKebabCaseMemberNames.withKebabCaseConstructorNames.withDefaults
    given Codec[DatabaseConfig] = Codec.AsObject.derivedConfigured
end DatabaseConfig

final case class StatementCacheConfig(
    enabled: Boolean = true,
    size: Size = Size(250),
    sqlLimit: Size = Size(2048)
)

object StatementCacheConfig:
    given Configuration = Configuration.default.withKebabCaseMemberNames.withKebabCaseConstructorNames.withDefaults

    given Codec[StatementCacheConfig] = Codec.AsObject.derivedConfigured
end StatementCacheConfig

private type SizeConstraint = Positive0 DescribedAs "Size must be positive or zero"
opaque type Size <: Int     = Int :| SizeConstraint

object Size extends RefinedTypeOps[Int, SizeConstraint, Size]

private type JdbcUrlConstraint =
    Match["jdbc\\:[^:]\\:.*"] DescribedAs "Driver class name must in jdbc:<subprotocol>:<subname> format"
opaque type JdbcUrl <: String  = String :| JdbcUrlConstraint

object JdbcUrl extends RefinedTypeOps[String, JdbcUrlConstraint, JdbcUrl]

private type DriverClassNameConstraint = Not[Blank] DescribedAs "Driver class name must not be blank"
opaque type DriverClassName <: String  = String :| DriverClassNameConstraint

object DriverClassName extends RefinedTypeOps[String, DriverClassNameConstraint, DriverClassName]

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

private type PoolSizeConstraint = GreaterEqual[1] DescribedAs "Pool size must be greater or equal to 1"
opaque type PoolSize <: Int     = Int :| PoolSizeConstraint

object PoolSize extends RefinedTypeOps[Int, PoolSizeConstraint, PoolSize]

private type VersionConstraint      = Not[Blank] & Match["^(\\d+\\.\\d+\\.\\d+)$"] DescribedAs
    "Schema version must be in the form of X.Y.Z"
opaque type SchemaVersion <: String = String :| VersionConstraint

object SchemaVersion extends RefinedTypeOps[String, Not[Blank] & Match["^(\\d+\\.\\d+\\.\\d+)$"], SchemaVersion]
