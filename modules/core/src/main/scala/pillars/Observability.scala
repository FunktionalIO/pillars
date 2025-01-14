// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars

import cats.Parallel
import cats.effect.Async
import cats.effect.LiftIO
import cats.effect.Resource
import cats.effect.std.Console
import cats.effect.syntax.all.*
import cats.syntax.all.*
import io.circe.Codec
import io.circe.derivation.Configuration
import io.github.iltotore.iron.*
import io.github.iltotore.iron.circe.given
import io.github.iltotore.iron.constraint.all.*
import org.http4s.Request
import org.http4s.Response
import org.http4s.Uri
import org.http4s.headers.`User-Agent`
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.AttributeKey
import org.typelevel.otel4s.Attributes as OtelAttributes
import org.typelevel.otel4s.metrics.Meter
import org.typelevel.otel4s.sdk.OpenTelemetrySdk
import org.typelevel.otel4s.sdk.TelemetryResource
import org.typelevel.otel4s.sdk.exporter.otlp.autoconfigure.OtlpExportersAutoConfigure
import org.typelevel.otel4s.trace.Tracer
import sttp.tapir.Endpoint
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.EndpointInterceptor
import sttp.tapir.server.interceptor.Interceptor
import sttp.tapir.server.model.ServerResponse

final case class Observability[F[_]](tracer: Tracer[F], metrics: Meter[F], interceptor: Interceptor[F]):
    export metrics.*
    export tracer.span
    export tracer.spanBuilder
end Observability
object Observability:
    def apply[F[_]: Pillars]: Run[F, Observability[F]] = Pillars[F].observability
    def noop[F[_]: LiftIO: Async]: F[Observability[F]] =
        Observability(Tracer.noop[F], Meter.noop[F], EndpointInterceptor.noop[F]).pure[F]

    def init[F[_]: LiftIO: Async: Parallel: Console](appInfo: AppInfo, config: Config): Resource[F, Observability[F]] =
        if config.isEnabled then
            for
                otel4s       <- OpenTelemetrySdk.autoConfigured[F]: builder =>
                                    builder
                                        .addExportersConfigurer(OtlpExportersAutoConfigure[F])
                                        .addResourceCustomizer: (resource, otelConfig) =>
                                            val configured =
                                                TelemetryResource(
                                                  OtelAttributes(
                                                    appInfo.name.toAttribute("service.name"),
                                                    appInfo.version.toAttribute("service.version")
                                                  )
                                                )
                                            resource.mergeUnsafe(configured)
                sdk           = otel4s.sdk
                tracer       <- (if config.traces.enabled then
                                     sdk.tracerProvider.get(config.traces.name.getOrElse(config.serviceName))
                                 else Tracer.noop[F].pure[F]).toResource
                meter        <- (if config.metrics.enabled then
                                     sdk.meterProvider.get(config.metrics.name.getOrElse(config.serviceName))
                                 else Meter.noop[F].pure[F]).toResource
                tapirMetrics <- Metrics.init[F](meter).toResource
            yield Observability(tracer, meter, tapirMetrics.metricsInterceptor())
        else
            noop.toResource
    final case class Config(
        enabled: Boolean = false,
        metrics: Config.Metrics = Config.Metrics(),
        traces: Config.Traces = Config.Traces(),
        serviceName: ServiceName = ServiceName("pillars")
    ) extends pillars.Config:
        def isEnabled: Boolean = enabled && (metrics.enabled || traces.enabled)
    end Config

    object Config:
        given Configuration = Configuration.default.withKebabCaseMemberNames.withKebabCaseConstructorNames.withDefaults
        given Codec[Config] = Codec.AsObject.derivedConfigured

        final case class Metrics(
            enabled: Boolean = false,
            name: Option[ServiceName] = None
        ) derives Codec.AsObject
        final case class Traces(
            enabled: Boolean = false,
            name: Option[ServiceName] = None
        ) derives Codec.AsObject
    end Config

    private type ServiceNameConstraint = Not[Blank]
    opaque type ServiceName <: String  = String :| ServiceNameConstraint
    object ServiceName extends RefinedTypeOps[String, ServiceNameConstraint, ServiceName]

    extension [A <: String](value: A)
        def toAttribute(name: String): Attribute[String] = Attribute(name, value)

    extension [A <: Long](value: Long)
        def toAttribute(name: String): Attribute[Long] = Attribute(name, value)

    extension [A <: Double](value: Double)
        def toAttribute(name: String): Attribute[Double] = Attribute(name, value)

    extension [A <: Boolean](value: Boolean)
        def toAttribute(name: String): Attribute[Boolean] = Attribute(name, value)

    object Attributes:
        def fromRequest[F[_]](request: Request[F]): OtelAttributes =
            val scheme = request.uri.scheme.map(_.value).getOrElse("http")
            OtelAttributes
                .newBuilder
                .addOne("http.request.method", request.method.name)
                .addOne("url.scheme", scheme)
                .addOne("network.protocol.name", scheme)
                .addOne("url.full", request.uri.renderString)
                .addOne("url.query", request.uri.query.renderString)
                .addOne("server.address", request.uri.authority.map(_.host.renderString).getOrElse("localhost"))
                .addOne(
                  "server.port",
                  request.uri.authority.map(_.port).getOrElse(if request.uri.scheme.contains(Uri.Scheme.https) then 443
                  else 80).toString
                )
                .addOne("network.protocol.version", request.httpVersion.renderString)
                .addOne("user.agent", request.headers.get(`User-Agent`.name).map(_.head.value).getOrElse("Unknown"))
                .result()
        end fromRequest

        def fromTapirRequest[F[_]](request: ServerRequest): OtelAttributes =
            OtelAttributes
                .newBuilder
                .addOne("http.request.method", request.method.method)
                .addOne("url.full", request.uri.toString)
                .addOne("url.query", request.queryParameters.toString)
                .addOne("url.scheme", request.uri.scheme.getOrElse("http"))
                .addOne("network.protocol.name", request.protocol)
                .addOne("server.address", request.uri.authority.map(_.host).getOrElse("localhost"))
                .addOne(
                  "server.port",
                  request.uri.authority.map(_.port).getOrElse(if request.uri.scheme.contains("https") then 443
                  else 80).toString
                )
                .addOne("user.agent", request.headers(`User-Agent`.name.toString))
                .result()

        def fromTapirEndpoint[F[_]](endpoint: Endpoint[?, ?, ?, ?, ?]): OtelAttributes =
            OtelAttributes
                .newBuilder
                .addOne("http.route", endpoint.showPathTemplate(showQueryParam = None))
                .result()

        def fromTapirResponse[F[_]](response: ServerResponse[?]): OtelAttributes =
            responseAttributes(response.code.code)

        def fromResponse[F[_]](response: Response[F]): OtelAttributes =
            responseAttributes(response.status.code)

        private def responseAttributes(status: Int) =
            OtelAttributes
                .newBuilder
                .addOne("http.response.status_code", status.toString)
                .addOne(
                  "http.response.status",
                  status match
                      case s if s < 200 => "1xx"
                      case s if s < 300 => "2xx"
                      case s if s < 400 => "3xx"
                      case s if s < 500 => "4xx"
                      case _            => "5xx"
                )
                .result()

        def fromError[F[_]](error: Throwable): OtelAttributes =
            OtelAttributes
                .newBuilder
                .addOne[Boolean]("error", true)
                .addOne("error.type", error.getClass.getName)
                .result()
    end Attributes
end Observability
