// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars.db

import cats.effect.*
import cats.syntax.all.*
import com.comcast.ip4s.*
import fs2.io.file.Files
import fs2.io.net.Network
import io.circe.Codec
import io.circe.Decoder as CirceDecoder
import io.circe.Encoder as CirceEncoder
import io.circe.derivation.Configuration
import io.github.iltotore.iron.*
import io.github.iltotore.iron.circe.given
import io.github.iltotore.iron.constraint.all.*
import org.typelevel.otel4s.trace.Tracer
import pillars.Config.*
import pillars.Module
import pillars.Modules
import pillars.ModuleSupport
import pillars.Pillars
import pillars.codec.given
import pillars.probes.*
import scala.concurrent.duration.Duration
import scribe.Level
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*
import skunk.util.Typer

def sessions(using p: Pillars): DB = p.module[DB](DB.Key)

final case class DB(config: DatabaseConfig, pool: Resource[IO, Session[IO]]) extends Module:

    override type ModuleConfig = DatabaseConfig
    export pool.*

    override def probes: List[Probe] =
        val probe = new Probe:
            override def component: Component = Component(Component.Name("db"), Component.Type.Datastore)
            override def check: IO[Boolean]   = pool.use(session => session.unique(sql"select true".query(bool)))
        probe.pure[List]
    end probes
end DB

object DB extends ModuleSupport:
    case object Key extends Module.Key:
        override val name: String = "db"

    override type M = DB
    override val key: Module.Key = DB.Key

    def load(context: ModuleSupport.Context, modules: Modules): Resource[IO, DB] =
        import context.*
        given Files[IO] = Files.forIO
        for
            _               <- Resource.eval(logger.info("Loading DB module"))
            config          <- Resource.eval(reader.read[DatabaseConfig]("db"))
            given Tracer[IO] = if config.tracing then context.observability.tracer else Tracer.noop[IO]
            poolRes         <- createPool(config)
            _               <- Resource.eval(logger.info("DB module loaded"))
        yield DB(config, poolRes)
        end for
    end load

    private def createPool(config: DatabaseConfig)(using Tracer[IO]) =
        Session.pooled[IO](
          host = config.host.toString,
          port = config.port.value,
          database = config.database,
          user = config.username,
          password = config.password.some.map(_.value),
          max = config.poolSize,
          debug = config.debug,
          strategy = config.typerStrategy,
          parameters = Session.DefaultConnectionParameters ++ config.extraParameters,
          commandCache = config.commandCache,
          queryCache = config.queryCache,
          parseCache = config.parseCache,
          readTimeout = config.readTimeout,
          redactionStrategy = config.redactionStrategy,
          ssl = config.ssl
        )

    def load(config: DatabaseConfig)(using Tracer[IO]): Resource[IO, DB] =
        createPool(config).map(pool => DB(config, pool))
end DB

final case class DatabaseConfig(
    host: Host = host"localhost",
    port: Port = port"5432",
    database: DatabaseName,
    username: DatabaseUser,
    password: Secret[DatabasePassword],
    ssl: SSL = SSL.None,
    systemSchema: DatabaseSchema = DatabaseSchema.public,
    appSchema: DatabaseSchema = DatabaseSchema.public,
    // TODO: Add system and application schemas (default = public)
    poolSize: PoolSize = PoolSize(32),
    debug: Boolean = false,
    probe: ProbeConfig = ProbeConfig(),
    logging: LoggingConfig = LoggingConfig(),
    tracing: Boolean = false,
    typerStrategy: Typer.Strategy = Typer.Strategy.BuiltinsOnly,
    extraParameters: Map[String, String] = Map.empty,
    commandCache: Int = 1024,
    queryCache: Int = 1024,
    parseCache: Int = 1024,
    readTimeout: Duration = Duration.Inf,
    redactionStrategy: RedactionStrategy = RedactionStrategy.OptIn
) extends pillars.Config

object DatabaseConfig:
    given Configuration         = pillars.Config.defaultCirceConfig
    given Codec[DatabaseConfig] = Codec.AsObject.derivedConfigured
    import pillars.Logging.Config.given
    given Codec[LoggingConfig]  = Codec.AsObject.derivedConfigured

    given CirceEncoder[Typer.Strategy] = CirceEncoder.encodeString.contramap:
        case Typer.Strategy.BuiltinsOnly => "BuiltinsOnly"
        case Typer.Strategy.SearchPath   => "SearchPath"
    given CirceDecoder[Typer.Strategy] = CirceDecoder.decodeString.map(_.toLowerCase).emap:
        case "builtinsonly" => Right(Typer.Strategy.BuiltinsOnly)
        case "searchpath"   => Right(Typer.Strategy.SearchPath)
        case other          => Left(s"Invalid Typer strategy: $other")

    given CirceDecoder[SSL] = CirceDecoder.decodeString.map(_.toLowerCase).emap:
        case "none"    => Right(SSL.None)
        case "trusted" => Right(SSL.Trusted)
        case "system"  => Right(SSL.System)
        case other     => Left(s"Invalid SSL mode: $other")
    given CirceEncoder[SSL] = CirceEncoder.encodeString.contramap:
        case SSL.None    => "none"
        case SSL.Trusted => "trusted"
        case SSL.System  => "system"

    given CirceDecoder[RedactionStrategy] = CirceDecoder.decodeString.map(_.toLowerCase).emap:
        case "none"  => Right(RedactionStrategy.None)
        case "all"   => Right(RedactionStrategy.All)
        case "optin" => Right(RedactionStrategy.OptIn)
        case other   => Left(s"Invalid SSL mode: $other")
    given CirceEncoder[RedactionStrategy] = CirceEncoder.encodeString.contramap:
        case RedactionStrategy.None  => "none"
        case RedactionStrategy.All   => "all"
        case RedactionStrategy.OptIn => "OptIn"

end DatabaseConfig

final case class LoggingConfig(
    enabled: Boolean = false,
    level: Level = Level.Debug,
    statements: Boolean = false,
    timing: Boolean = false
)

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
