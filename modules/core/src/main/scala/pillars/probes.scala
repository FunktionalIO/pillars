// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars

import cats.Monoid
import cats.effect.*
import cats.effect.std.MapRef
import cats.syntax.all.*
import fs2.Stream
import io.circe.*
import io.circe.derivation.Configuration
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import pillars.AdminServer.baseEndpoint
import pillars.codec.given
import pillars.probes.Component.Name
import pillars.probes.endpoints.*
import pillars.probes.views.CheckStatus
import pillars.probes.views.HealthStatus
import scala.concurrent.duration.*
import sttp.tapir.Endpoint
import sttp.tapir.Schema
import sttp.tapir.given
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.stringBody

object probes:
    final case class Component(name: Name, `type`: Component.Type, description: Option[String] = None)

    object Component:
        private type NameConstraint = Not[Blank] & Not[Contain[":"]]
        opaque type Name <: String  = String :| NameConstraint
        object Name extends RefinedTypeOps[String, NameConstraint, Name]

        enum Type:
            case System, Datastore, Component
    end Component

    // tag::probe[]
    trait Probe:
        def component: Component //// <1>
        def check: IO[Boolean] //// <2>
        def config: ProbeConfig = ProbeConfig() //// <3>
    end Probe
    // end::probe[]

    trait ProbeManager:
        def status: IO[Map[Component, Status]]
        def globalStatus: IO[Status]
        def start(): IO[Unit]
    end ProbeManager

    object ProbeManager:
        def build(modules: Modules, observability: Observability): Resource[IO, ProbeManager] =
            import observability.*
            Resource.eval:
                val probes = modules.all.flatMap(_.probes).toList
                val limits = probes.map(c => c.component -> c.config.failureCount).toMap
                MapRef.ofConcurrentHashMap[IO, Component, Int]().map: componentErrors =>
                    val streams: List[Stream[IO, Unit]] =
                        probes.map: probe =>
                            Stream.fixedRate[IO](probe.config.interval)
                                .evalMap: _ =>
                                    def incrementErrorCount =
                                        componentErrors(probe.component).update:
                                            case Some(value) => Some(value + 1)
                                            case None        => Some(1)

                                    observability.span(s"[${probe.component.name}] probe").surround:
                                        probe.check
                                            .attemptTap:
                                                case Right(value) if value =>
                                                    componentErrors(probe.component).update(_ => 0.some)
                                                case Left(e)               =>
                                                    observability.recordException(e) *> incrementErrorCount
                                                case _                     => incrementErrorCount
                                            .void
                    new ProbeManager:
                        def start(): IO[Unit] = Stream(streams*).parJoinUnbounded.compile.drain

                        def status: IO[Map[Component, Status]] =
                            limits.toList
                                .traverse: (component, limit) =>
                                    componentErrors(component).get.map:
                                        case Some(value) =>
                                            if value == 0 then component -> Status.pass
                                            else if value < limit then component -> Status.warn
                                            else component                       -> Status.fail
                                        case None        => component -> Status.pass
                                .map(_.toMap)

                        def globalStatus: IO[Status] =
                            status.map(_.values.toList.foldLeft(Status.pass)(_ |+| _))
                    end new
        end build
    end ProbeManager
    enum Status:
        case pass, warn, fail

    object Status:
        given Monoid[Status] with
            def empty: Status                         = Status.pass
            def combine(x: Status, y: Status): Status =
                (x, y) match
                    case (Status.pass, Status.pass) => Status.pass
                    case (Status.fail, _)           => Status.fail
                    case (_, Status.fail)           => Status.fail
                    case _                          => Status.warn
        end given
    end Status

    given Schema[Status] = Schema.derived

    given Encoder[Status] = Encoder.encodeString.contramap(_.toString.toLowerCase)

    given Decoder[Status] = Decoder.decodeString.emap: status =>
        status.toLowerCase match
            case "pass" => Right(Status.pass)
            case "warn" => Right(Status.warn)
            case "fail" => Right(Status.fail)
            case other  => Left(s"Unknown status: $other")

    given Schema[Component.Type] = Schema.derived

    given Encoder[Component.Type] = Encoder.encodeString.contramap(_.toString.toLowerCase)

    given Decoder[Component.Type] = Decoder.decodeString.emap: status =>
        status.toLowerCase match
            case "system"    => Right(Component.Type.System)
            case "datastore" => Right(Component.Type.Datastore)
            case "component" => Right(Component.Type.Component)
            case other       => Left(s"Unknown component type: $other")

    final case class ProbeConfig(
        timeout: FiniteDuration = 5.seconds,
        interval: FiniteDuration = 10.seconds,
        failureCount: Int = 3
    ) extends pillars.Config

    object ProbeConfig:
        given Configuration      = pillars.Config.defaultCirceConfig
        given Codec[ProbeConfig] = Codec.AsObject.derivedConfigured
    end ProbeConfig

    val livenessController: Controller = List(liveness.serverLogicSuccess(_ => "OK".pure[IO]))

    def probesController(manager: ProbeManager): Controller =
        val ready = readiness.serverLogicSuccess: _ =>
            manager.status.map: statuses =>
                val checks       = statuses.map: (component, status) =>
                    CheckStatus(component.name, component.`type`, status)
                val globalStatus = statuses.values.foldLeft(Status.pass)(_ |+| _)
                HealthStatus(globalStatus, checks.toList)
        livenessController :+ ready
    end probesController

    object endpoints:
        private val prefix                                                 = baseEndpoint.in("probes")
        def liveness: Endpoint[Unit, Unit, HttpErrorResponse, String, Any] =
            prefix.get.in("healthz").description("Liveness probe").out(stringBody)

        def readiness: Endpoint[Unit, Unit, HttpErrorResponse, HealthStatus, Any] =
            prefix.get
                .in("health")
                .description("Readiness probe")
                .out(jsonBody[HealthStatus])

        def all: Seq[Endpoint[Unit, Unit, HttpErrorResponse, ?, Any]] = List(liveness, readiness)
    end endpoints
    object views:
        final case class HealthStatus(status: Status, checks: List[CheckStatus]) derives Codec.AsObject, Schema
        final case class CheckStatus(componentId: String, componentType: Component.Type, status: Status)
            derives Codec.AsObject, Schema
    end views

end probes
