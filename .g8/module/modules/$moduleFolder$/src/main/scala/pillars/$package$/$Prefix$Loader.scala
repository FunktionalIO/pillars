package pillars.$package$

import cats.effect.Async
import cats.effect.Resource
import cats.effect.std.Console
import cats.syntax.applicative.*
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import com.comcast.ip4s.host
import com.comcast.ip4s.port
import fs2.io.file.Files
import fs2.io.net.Network
import io.circe.Codec
import io.circe.derivation.Configuration
import io.github.iltotore.iron.*
import io.github.iltotore.iron.circe.given
import io.github.iltotore.iron.constraint.all.*
import org.typelevel.otel4s.trace.Tracer
import pillars.Config.Secret
import pillars.Module
import pillars.ModuleDef
import pillars.Modules
import pillars.Pillars
import pillars.codec.given
import pillars.probes.Component
import pillars.probes.Probe
import scala.language.postfixOps

trait $Prefix$Client[F[_]]

extension [F[_]](p: Pillars[F])
    def $lowerCaseModuleName$[F[_]](using p: Pillars[F]): $Prefix$[F] = p.module[$Prefix$[F]]($Prefix$.Key)

final case class $Prefix$[F[_]: Async](client: $Prefix$Client[F]) extends Module[F]:
    export client.*

    override def probes: List[Probe[F]] =
        val probe = new Probe[F]:
            override def component: Component =
                Component(Component.Name("$lowerCaseModuleName$"), Component.Type.Datastore)
            override def check: F[Boolean]    = true.pure[F]
        probe.pure[List]
    end probes
end $Prefix$

object $Prefix$ :
    case object Key extends Module.Key:
        override val name: String = "$lowerCaseModuleName$"

    def apply[F[_]](using p: Pillars[F]): $Prefix$[F] = p.module[$Prefix$[F]]($Prefix$.Key)

    def apply[F[_]: Async](config: $Prefix$Config): Resource[F, $Prefix$[F]] =
        ??? // Implement your client creation here

end $Prefix$

object $Prefix$Module extends ModuleDef:
    override type M[F[_]] = $Prefix$[F]
    override val key: Module.Key = $Prefix$.Key

    override def load[F[_]: Async: Network: Tracer: Console](
        context: Loader.Context[F],
        modules: Modules[F]
    ): Resource[F, $Prefix$[F]] =
        import context.*
        given Files[F] = Files.forAsync[F]
        for
            _      <- Resource.eval(logger.info("Loading $Prefix$ module"))
            config <- Resource.eval(configReader.read[$Prefix$Config]("$lowerCaseModuleName$"))
            client <- $Prefix$[F](config)
            _      <- Resource.eval(logger.info("$Prefix$ module loaded"))
        yield client
        end for
    end load
end $Prefix$Loader

case class $Prefix$Config(
    host: Host = host"localhost",
    port: Port = port"5672",
    username: Option[$Prefix$User] = None,
    password: Option[Secret[$Prefix$Password]] = None
)

object $Prefix$Config:
    given Configuration         = Configuration.default.withKebabCaseMemberNames.withKebabCaseConstructorNames.withDefaults
    given Codec[$Prefix$Config] = Codec.AsObject.derivedConfigured
end $Prefix$Config

private type $Prefix$UserConstraint = Not[Blank] DescribedAs "$Prefix$ user must not be blank"
opaque type $Prefix$User <: String  = String :| $Prefix$UserConstraint
object $Prefix$User extends RefinedTypeOps[String, $Prefix$UserConstraint, $Prefix$User]

private type $Prefix$PasswordConstraint = Not[Blank] DescribedAs "$Prefix$ password must not be blank"
opaque type $Prefix$Password <: String  = String :| $Prefix$PasswordConstraint
object $Prefix$Password extends RefinedTypeOps[String, $Prefix$PasswordConstraint, $Prefix$Password]
