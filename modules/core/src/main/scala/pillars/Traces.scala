// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars

import cats.effect.IO
import cats.syntax.all.*
import org.typelevel.otel4s.trace.Span
import org.typelevel.otel4s.trace.SpanKind
import org.typelevel.otel4s.trace.Tracer
import sttp.monad.MonadError
import sttp.tapir.Endpoint
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.*
import sttp.tapir.server.interpreter.BodyListener
import sttp.tapir.server.model.ServerResponse

final case class Traces(tracer: Tracer[IO]) extends EndpointInterceptor[IO]:
    override def apply[B](
        responder: Responder[IO, B],
        endpointHandler: EndpointHandler[IO, B]
    ): EndpointHandler[IO, B] =
        new EndpointHandler[IO, B]:
            override def onDecodeSuccess[A, U, I](
                ctx: DecodeSuccessContext[IO, A, U, I]
            )(using monad: MonadError[IO], bodyListener: BodyListener[IO, B]): IO[ServerResponse[B]] =
                trace(ctx.endpoint, ctx.request, endpointHandler.onDecodeSuccess(ctx))
            end onDecodeSuccess

            override def onDecodeFailure(ctx: DecodeFailureContext)(using
                monad: MonadError[IO],
                bodyListener: BodyListener[IO, B]
            ): IO[Option[ServerResponse[B]]] =
                tracer.joinOrRoot(ctx.request.headers.map(h => h.name -> h.value).toMap):
                    tracer
                        .spanBuilder(spanName(ctx.endpoint))
                        .withSpanKind(SpanKind.Server)
                        .addAttributes(Observability.Attributes.fromTapirRequest(ctx.request))
                        .addAttributes(Observability.Attributes.fromTapirEndpoint(ctx.endpoint))
                        .build
                        .use: span =>
                            for
                                _        <- span.addEvent("Send request")
                                response <- handle(span, endpointHandler.onDecodeFailure(ctx))
                                _        <- response.traverse_(r =>
                                                span.addAttributes(Observability.Attributes.fromTapirResponse(r))
                                            )
                                _        <- span.addEvent("Request received")
                            yield response

            override def onSecurityFailure[A](ctx: SecurityFailureContext[IO, A])(using
                monad: MonadError[IO],
                bodyListener: BodyListener[IO, B]
            ): IO[ServerResponse[B]] =
                trace(ctx.endpoint, ctx.request, endpointHandler.onSecurityFailure(ctx))

            private def trace[O, I, U, A](
                endpoint: Endpoint[A, I, ?, ?, ?],
                request: ServerRequest,
                execution: IO[ServerResponse[O]]
            ): IO[ServerResponse[O]] =
                tracer.joinOrRoot(request.headers.map(h => h.name -> h.value).toMap):
                    tracer
                        .spanBuilder(spanName(endpoint))
                        .withSpanKind(SpanKind.Server)
                        .addAttributes(Observability.Attributes.fromTapirRequest(request))
                        .addAttributes(Observability.Attributes.fromTapirEndpoint(endpoint))
                        .build
                        .use: span =>
                            for
                                _        <- span.addEvent("Send request")
                                response <- handle(span, execution)
                                _        <- span.addAttributes(Observability.Attributes.fromTapirResponse(response))
                                _        <- span.addEvent("Request received")
                            yield response

            private def spanName(endpoint: Endpoint[?, ?, ?, ?, ?]): String =
                s"${endpoint.method.map(_.method).getOrElse("*")} ${endpoint.showPathTemplate(showQueryParam = None)}"

            private def handle[T](span: Span[IO], execution: IO[T]): IO[T] =
                execution.onError:
                    case error: Throwable =>
                        span.addEvent("Error") *>
                            span.addAttributes(Observability.Attributes.fromError(error))

end Traces
