// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars.httpclient

import cats.effect.Async
import cats.effect.Resource
import cats.effect.std.Console
import cats.effect.syntax.all.*
import cats.syntax.all.*
import fs2.io.file.Files
import fs2.io.net.Network
import io.circe.Codec
import io.circe.Decoder
import io.circe.Encoder
import io.circe.derivation.Configuration
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import org.http4s.ProductComment
import org.http4s.ProductId
import org.http4s.Request
import org.http4s.Response
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.client.middleware.FollowRedirect
import org.http4s.client.middleware.Logger
import org.http4s.headers.`User-Agent`
import org.http4s.netty.client.NettyClientBuilder
import org.typelevel.otel4s.trace.Tracer
import pillars.Logging
import pillars.Module
import pillars.Modules
import pillars.ModuleSupport
import pillars.Pillars
import pillars.PillarsError
import pillars.PillarsError.*
import pillars.Run
import pillars.syntax.all.*
import sttp.tapir.AnyEndpoint
import sttp.tapir.DecodeResult
import sttp.tapir.Endpoint
import sttp.tapir.PublicEndpoint
import sttp.tapir.ValidationError
import sttp.tapir.client.http4s.Http4sClientInterpreter
import sttp.tapir.client.http4s.Http4sClientOptions

def http[F[_]](using p: Pillars[F]): HttpClient[F] = p.module[HttpClient[F]](HttpClient.Key)

final case class HttpClient[F[_]: Async](config: HttpClient.Config)(client: org.http4s.client.Client[F])
    extends pillars.Module[F], Client[F]:
    override type ModuleConfig = HttpClient.Config
    export client.*

    private val interpreter = Http4sClientInterpreter[F](Http4sClientOptions.default)

    def call[SI, I, EO, O, R](
        endpoint: PublicEndpoint[I, EO, O, R],
        uri: Option[Uri],
        handler: FailureHandler[F, EO, O] = FailureHandler.default[F, EO, O]
    )(input: I): F[Either[EO, O]] =
        callRequest(endpoint, uri)(interpreter.toRequest(endpoint, uri)(input))
    end call

    def callSecure[SI, I, EO, O, R](
        endpoint: Endpoint[SI, I, EO, O, R],
        uri: Option[Uri],
        handler: FailureHandler[F, EO, O] = FailureHandler.default[F, EO, O]
    )(securityInput: SI, input: I): F[Either[EO, O]] =
        callRequest(endpoint, uri)(interpreter.toSecureRequest(endpoint, uri)(securityInput)(input))
    end callSecure

    private def callRequest[I, EO, O](
        endpoint: AnyEndpoint,
        uri: Option[Uri],
        handler: FailureHandler[F, EO, O] = FailureHandler.default[F, EO, O]
    )(interpret: (Request[F], Response[F] => F[DecodeResult[Either[EO, O]]])) =
        val (request, parseResponse) = interpret
        client
            .run(request)
            .use(parseResponse)
            .flatMap:
                case DecodeResult.Value(v)         => v.pure[F]
                case failure: DecodeResult.Failure => handler.handle(endpoint, uri, failure)
    end callRequest

end HttpClient

object HttpClient extends ModuleSupport:
    case object Key extends Module.Key:
        override def name: String = "http-client"

    override type M[F[_]] = HttpClient[F]

    override def key: Module.Key = HttpClient.Key

    override def load[F[_]: Async: Network: Tracer: Console](
        context: pillars.ModuleSupport.Context[F],
        modules: Modules[F]
    ): Resource[F, HttpClient[F]] =
        import context.*
        given Files[F] = Files.forAsync[F]

        for
            _       <- Resource.eval(logger.info("Loading HTTP client module"))
            conf    <- Resource.eval(reader.read[HttpClient.Config]("http-client"))
            metrics <- ClientMetrics(observability).toResource
            client  <- NettyClientBuilder[F]
                           .withHttp2
                           .withNioTransport
                           .withUserAgent(conf.userAgent)
                           .withMaxConnectionsPerKey(conf.maxConnections)
                           .resource
                           .map: client =>
                               val logging        =
                                   if conf.logging.enabled then
                                       Logger[F](
                                         logHeaders = conf.logging.headers,
                                         logBody = conf.logging.body,
                                         logAction = conf.logging.logAction
                                       )
                                   else identity[Client[F]]
                               val followRedirect =
                                   if conf.followRedirect then FollowRedirect[F](10) else identity[Client[F]]
                               client
                                   |> metrics.middleware
                                   |> logging
                                   |> followRedirect
                                   |> HttpClient(conf)
            _       <- Resource.eval(logger.info("HTTP client module loaded"))
        yield client
        end for
    end load

    final case class Config(
        maxConnections: Int = 10,
        followRedirect: Boolean = true,
        userAgent: `User-Agent` = Config.defaultUserAgent,
        logging: Logging.HttpConfig = Logging.HttpConfig()
    ) extends pillars.Config

    object Config:
        given Configuration         = Configuration.default.withKebabCaseMemberNames.withKebabCaseConstructorNames.withDefaults
        given Decoder[`User-Agent`] = Decoder.decodeString.emap(s =>
            `User-Agent`.parse(10)(s).leftMap(f => s"Invalid User-Agent '$s': ${f.message}")
        )

        private def encodeUserAgent(ua: `User-Agent`): String =
            def encodeProductId(p: ProductId): String = p.version.fold(p.value)(v => s"${p.value}/$v")
            val productStr                            = encodeProductId(ua.product)
            ua.rest.map {
                case p: ProductId          => encodeProductId(p)
                case ProductComment(value) => s"($value)"
            }.prepended(productStr).mkString(" ")
        end encodeUserAgent

        given Encoder[`User-Agent`] = Encoder.encodeString.contramap(encodeUserAgent)
        given Codec[Config]         = Codec.AsObject.derivedConfigured

        private val defaultUserAgent: `User-Agent` = `User-Agent`(ProductId("pillars", None), ProductId("netty", None))
    end Config

    enum Error(endpoint: AnyEndpoint, uri: Option[Uri], val number: ErrorNumber, val message: Message)
        extends PillarsError:
        case DecodingError(endpoint: AnyEndpoint, uri: Option[Uri], raw: String, cause: Throwable) extends Error(
              endpoint,
              uri,
              ErrorNumber(1001),
              Message.assume(s"Cannot decode output $raw. Cause is $cause")
            )
        case Missing(endpoint: AnyEndpoint, uri: Option[Uri])
            extends Error(endpoint, uri, ErrorNumber(1002), Message("Missing"))
        case Multiple[R](endpoint: AnyEndpoint, uri: Option[Uri], vs: Seq[R])
            extends Error(endpoint, uri, ErrorNumber(1003), Message("Multiple response"))
        case InvalidInput(endpoint: AnyEndpoint, uri: Option[Uri], errors: List[ValidationError[?]])
            extends Error(endpoint, uri, ErrorNumber(1004), Message("Invalid input"))
        case Mismatch(endpoint: AnyEndpoint, uri: Option[Uri], expected: String, actual: String)
            extends Error(endpoint, uri, ErrorNumber(1005), Message("Type mismatch"))

        override def code: Code = Code("HTTP")

        override def details: Option[Message] =
            Message.option(s"""
              |uri: $uri
              |endpoint: $endpoint
              |""")
    end Error

end HttpClient

trait FailureHandler[F[_], EO, O]:
    def handle(endpoint: AnyEndpoint, uri: Option[Uri], failure: DecodeResult.Failure): F[Either[EO, O]]

object FailureHandler:
    def default[F[_]: Async, EO, O]: FailureHandler[F, EO, O] =
        (endpoint: AnyEndpoint, uri: Option[Uri], failure: DecodeResult.Failure) =>
            import HttpClient.Error.*
            failure match
                case DecodeResult.Error(raw, error)          =>
                    DecodingError(endpoint, uri, raw, error).raiseError[F, Either[EO, O]]
                case DecodeResult.Missing                    => Missing(endpoint, uri).raiseError[F, Either[EO, O]]
                case DecodeResult.Multiple(vs)               => Multiple(endpoint, uri, vs).raiseError[F, Either[EO, O]]
                case DecodeResult.Mismatch(expected, actual) =>
                    Mismatch(endpoint, uri, expected, actual).raiseError[F, Either[EO, O]]
                case DecodeResult.InvalidValue(errors)       =>
                    InvalidInput(endpoint, uri, errors).raiseError[F, Either[EO, O]]
            end match
end FailureHandler

private[httpclient] final case class Config(followRedirect: Boolean)

extension [I, EO, O, R](endpoint: PublicEndpoint[I, EO, O, R])
    def call[F[_]](uri: Option[Uri])(input: I): Run[F, F[Either[EO, O]]] =
        http.call(endpoint, uri)(input)

extension [SI, I, EO, O, R](endpoint: Endpoint[SI, I, EO, O, R])
    def call[F[_]](uri: Option[Uri])(securityInput: SI, input: I): Run[F, F[Either[EO, O]]] =
        http.callSecure(endpoint, uri)(securityInput, input)

//trait ClientMiddleware:
//    implicit class ClientMiddlewareOps[F[_]: Tracer: Async, A](client: Client[F]):
//        def runTraced(request: Request[F]): F[Response[F]] =
//            Tracer[F].spanBuilder("client-request")
//                .addAttribute(Attribute("http.method", request.method.name))
//                .addAttribute(Attribute("http.url", request.uri.renderString))
//                .withSpanKind(SpanKind.Client)
//                .wrapResource(client.run(request))
//                .build
//                .use:
//                    case span @ Span.Res(response) =>
//                        for
//                            _ <- span.addAttribute(Attribute("http.status-code", response.status.code.toLong))
//                            _ <- if response.status.isSuccess then
//                                span.setStatus(Status.Ok)
//                            else
//                                span.setStatus(Status.Error)
//                        yield response
