// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars

import cats.effect.Async
import cats.effect.Resource.ExitCase
import cats.syntax.all.*
import com.comcast.ip4s.*
import io.circe.Codec
import io.circe.derivation.Configuration
import io.github.iltotore.iron.*
import pillars.Controller.HttpEndpoint
import pillars.PillarsError.Code
import scala.annotation.targetName
import scribe.Scribe
import sttp.model.StatusCode

trait ApiServer[F[_]]:

    def start(endpoints: List[HttpEndpoint[F]]): F[Unit]

    @targetName("startWithEndpoints")
    def start(endpoints: HttpEndpoint[F]*): F[Unit] =
        start(endpoints.toList)

    @targetName("startWithControllers")
    def start(controllers: Controller[F]*): F[Unit] =
        start(controllers.toList.flatten)

end ApiServer

def server[F[_]](using p: Pillars[F]): Run[F, ApiServer[F]] = p.apiServer

object ApiServer:
    def init[F[_]: Async](
        config: Config,
        infos: AppInfo,
        observability: Observability[F],
        logger: Scribe[F]
    ): ApiServer[F] =
        (endpoints: List[HttpEndpoint[F]]) =>
            Async[F].whenA(config.enabled):
                for
                    _ <- logger.info(s"Starting API server on ${config.http.host}:${config.http.port}")
                    _ <- HttpServer
                             .build("api", config.http, config.openApi, infos, observability, endpoints)
                             .onFinalizeCase:
                                 case ExitCase.Errored(e) => logger.error(s"API server stopped with error: $e")
                                 case _                   => logger.info("API server stopped")
                             .useForever
                yield ()
    trait Error extends PillarsError:
        override def status: StatusCode
        final override def code: Code = Code("API")
    end Error

    final case class Config(
        enabled: Boolean,
        http: HttpServer.Config = defaultHttp,
        openApi: HttpServer.Config.OpenAPI = HttpServer.Config.OpenAPI()
    ) extends pillars.Config

    given Configuration = Configuration.default.withKebabCaseMemberNames.withKebabCaseConstructorNames.withDefaults
    given Codec[Config] = Codec.AsObject.derivedConfigured

    private val defaultHttp = HttpServer.Config(
      host = host"0.0.0.0",
      port = port"9876",
      logging = Logging.HttpConfig()
    )

    def noop[F[_]: Async]: ApiServer[F] = _ => Async[F].unit
end ApiServer
