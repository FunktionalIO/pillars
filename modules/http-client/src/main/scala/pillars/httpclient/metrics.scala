// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars.httpclient

import cats.effect.Clock
import cats.effect.IO
import cats.effect.Resource
import cats.syntax.all.*
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.http4s.client.Client
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.metrics.*
import pillars.Observability
import pillars.Observability.*
import scala.concurrent.duration.FiniteDuration

final case class MetricsCollection(
    responseDuration: Histogram[IO, Long],
    activeRequests: UpDownCounter[IO, Long],
    totalRequests: Counter[IO, Long],
    requestBodySize: Histogram[IO, Long],
    responseBodySize: Histogram[IO, Long]
)

object MetricsCollection:
    def create(meter: Meter[IO]): IO[MetricsCollection] =
        (
          meter.histogram[Long]("http.client.request.duration")
              .withUnit("ms")
              .withDescription("Response time in milliseconds")
              .withExplicitBucketBoundaries(
                BucketBoundaries(5, 10, 25, 50, 75, 100, 250, 500, 750, 1000, 2500, 5000, 7500, 10000)
              )
              .create,
          meter
              .upDownCounter[Long]("http.client.active_requests")
              .withUnit("{request}")
              .withDescription("Active requests")
              .create,
          meter
              .counter[Long]("http.client.request.total")
              .withUnit("{request}")
              .withDescription("Number of requests")
              .create,
          meter.histogram[Long]("http.client.request.body.size")
              .withUnit("By")
              .withDescription(
                "The size of the request payload body in bytes. This is the number of bytes transferred excluding headers and is often, but not always, present as the Content-Length header."
              )
              .create,
          meter.histogram[Long]("http.client.response.body.size")
              .withUnit("By")
              .withDescription(
                "The size of the response payload body in bytes. This is the number of bytes transferred excluding headers and is often, but not always, present as the Content-Length header."
              )
              .create
        ).mapN(MetricsCollection.apply)
end MetricsCollection

object ClientMetrics:
    def apply(observability: Observability): IO[ClientMetrics] =
        MetricsCollection.create(observability.metrics).map(ClientMetrics.apply)

final case class ClientMetrics(metrics: MetricsCollection):

    def middleware(client: Client[IO]): Client[IO] =
        Client(instrument(client))

    private def instrument(client: Client[IO])(request: Request[IO]): Resource[IO, Response[IO]] =
        val requestAttributes = extractAttributes(request)

        def recordRequest(start: FiniteDuration, end: FiniteDuration, attributes: List[Attribute[String]]) =
            for
                _ <- metrics.responseDuration.record((end - start).toMillis, attributes*)
                _ <- metrics.totalRequests.inc(attributes*)
            yield ()

        Clock[IO].monotonic.toResource.flatMap: start =>
            val happyPath: Resource[IO, Response[IO]] =
                for
                    _                 <- Resource.make(
                                           metrics.activeRequests.inc(requestAttributes*)
                                         )(_ =>
                                             metrics.activeRequests.dec(requestAttributes*)
                                         )
                    response          <- client.run(request)
                    end               <- Resource.eval(Clock[IO].monotonic)
                    responseAttributes = extractAttributes(response)
                    _                 <- Resource.eval:
                                             recordRequest(start, end, requestAttributes ++ responseAttributes)
                yield response
            happyPath.handleErrorWith: (e: Throwable) =>
                Resource.eval:
                    Clock[IO].monotonic
                        .flatMap: now =>
                            recordRequest(start, now, requestAttributes ++ extractAttributes(e))
                        .flatMap: _ =>
                            IO.raiseError[Response[IO]](e)
    end instrument

    private def extractAttributes(value: Request[IO] | Response[IO] | Throwable) =
        val l = value match
            case request: Request[IO]   =>
                List(
                  "http.route"          -> s"${request.uri.path.addEndsWithSlash.renderString}",
                  "http.request.host"   -> s"${request.uri.host.map(_.value).getOrElse("")}",
                  "http.request.method" -> request.method.name,
                  "url.scheme"          -> request.uri.scheme.map(_.value).getOrElse("")
                )
            case response: Response[IO] =>
                List(
                  "http.response.status"      -> {
                      response.status.responseClass match
                          case Status.Informational => "1xx"
                          case Status.Successful    => "2xx"
                          case Status.Redirection   => "3xx"
                          case Status.ClientError   => "4xx"
                          case Status.ServerError   => "5xx"
                  },
                  "http.response.status_code" -> response.status.code.toString
                )
            case e: Throwable           =>
                List(
                  "error.type" -> e.getClass.getName
                )
        l.map { case (name, value) => value.toAttribute(name) }
    end extractAttributes

end ClientMetrics
