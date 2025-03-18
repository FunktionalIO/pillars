// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars

import cats.effect.Async
import cats.effect.Resource.ExitCase
import cats.effect.kernel.Async
import cats.syntax.all.*
import com.comcast.ip4s.*
import io.circe.Codec
import io.circe.derivation.Configuration
import pillars.AdminServer.Config
import sttp.model.StatusCode
import sttp.tapir.*

final case class AdminServer[F[_]: Async](
    config: Config,
    infos: AppInfo,
    obs: Observability[F],
    controllers: List[Controller[F]]
):
    def start(): F[Unit] =
        val logger = scribe.cats.effect[F]
        import logger.*
        if config.enabled then
            for
                _ <- info(s"Starting admin server on ${config.http.host}:${config.http.port}")
                _ <- HttpServer
                         .build(
                           "admin",
                           config.http,
                           config.openApi,
                           infos,
                           obs,
                           controllers.flatten
                         )
                         .onFinalizeCase:
                             case ExitCase.Errored(e) => error(s"Admin server stopped with error: $e")
                             case _                   => info("Admin server stopped")
                         .useForever
            yield ()
        else Async[F].unit
        end if
    end start
end AdminServer

object AdminServer:
    val baseEndpoint: Endpoint[Unit, Unit, (StatusCode, PillarsError.View), Unit, Any] =
        endpoint.in("admin").errorOut(PillarsError.View.output)

    final case class Config(
        enabled: Boolean,
        http: HttpServer.Config = defaultHttp,
        openApi: HttpServer.Config.OpenAPI = HttpServer.Config.OpenAPI()
    ) extends pillars.Config

    given Configuration = Configuration.default.withKebabCaseMemberNames.withKebabCaseConstructorNames.withDefaults
    given Codec[Config] = Codec.AsObject.derivedConfigured

    private val defaultHttp = HttpServer.Config(
      host = host"0.0.0.0",
      port = port"19876",
      logging = Logging.HttpConfig()
    )
end AdminServer
