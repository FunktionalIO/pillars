// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars.httpclient

import cats.effect.*
import cats.effect.syntax.all.*
import org.http4s.Response
import org.http4s.client.Client
import org.typelevel.otel4s.trace.SpanKind
import org.typelevel.otel4s.trace.Tracer
import pillars.Observability
import scala.annotation.tailrec

case class Traces[F[_]: Async](tracer: Tracer[F]):
    def apply(client: Client[F]): Client[F] =
        Client: request =>
            for
                res      <- tracer
                                .spanBuilder(s"${request.method.name}")
                                .withSpanKind(SpanKind.Client)
                                .addAttributes(Observability.Attributes.fromRequest(request))
                                .build
                                .resource
                _        <- res.span.addEvent("Send request").toResource
                response <- client.run(request).handleErrorWith[Response[F]]: t =>
                                res.span.addEvent("Error").toResource !>
                                    res.span.addAttributes(Observability.Attributes.fromError(t)).toResource !>
                                    Resource.raiseError[F, Response[F], Throwable](t)
                _        <- res.span.addAttributes(Observability.Attributes.fromResponse(response)).toResource
                _        <- res.span.addEvent("Request received").toResource
            yield response
end Traces

object temp:
    def persistence(n: Int): Int =
        def sumOfDigits(n: Int): Int         =
            n.toString.map(_.asDigit).sum
        @tailrec
        def loop(number: Int, res: Int): Int =
            if number < 10 then res
            else loop(sumOfDigits(number), res + 1)
        loop(n, 0)
    end persistence
end temp
