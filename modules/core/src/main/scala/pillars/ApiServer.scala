// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars

import cats.effect.IO
import cats.effect.Resource
import cats.syntax.all.*
import com.comcast.ip4s.*
import io.circe.Codec
import io.circe.derivation.Configuration
import io.github.iltotore.iron.*
import pillars.PillarsError.Code
import sttp.model.StatusCode

def server(using p: Pillars): Run[HttpServer] = p.apiServer

object ApiServer:
    def create(config: Config, infos: AppInfo, context: ModuleSupport.Context): Resource[IO, HttpServer] =
        if config.enabled then
            HttpServer(
              HttpServer.Usage.Api,
              config.http,
              config.openApi,
              infos,
              context.observability,
              context.logger,
              Nil
            )
        else
            HttpServer.noop.pure[IO].toResource
    end create

    trait Error extends PillarsError:
        override def status: StatusCode
        final override def code: Code = Code("API")
    end Error

    final case class Config(
        enabled: Boolean,
        http: HttpServer.Config = defaultHttp,
        openApi: HttpServer.Config.OpenAPI = HttpServer.Config.OpenAPI()
    ) extends pillars.Config

    given Configuration = pillars.Config.defaultCirceConfig
    given Codec[Config] = Codec.AsObject.derivedConfigured

    private val defaultHttp = HttpServer.Config(host = host"0.0.0.0", port = port"9876", logging = Logging.HttpConfig())

end ApiServer
