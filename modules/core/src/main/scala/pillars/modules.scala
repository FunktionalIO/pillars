// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars

import cats.effect.Async
import cats.effect.Resource
import cats.effect.std.Console
import fs2.io.net.Network
import org.typelevel.otel4s.trace.Tracer
import pillars.Config.Reader
import pillars.probes.Probe
import scribe.Scribe

trait Module[F[_]]:
    type ModuleConfig <: Config
    def probes: List[Probe[F]] = Nil

    def adminControllers: List[Controller[F]] = Nil

    def config: ModuleConfig

end Module

object Module:
    trait Key:
        def name: String

        override def toString: String = s"Key($name)"
    end Key
end Module

case class Modules[F[_]](private val values: Map[Module.Key, Module[F]]):
    def add[K <: Module[F]](key: Module.Key)(value: K): Modules[F] = Modules(values + (key -> value))
    def get[K](key: Module.Key): K                                 = values(key).asInstanceOf[K]
    export values.size
    export values.values as all
    def probes: List[Probe[F]]                                     = all.flatMap(_.probes).toList
    def adminControllers: List[Controller[F]]                      = all.flatMap(_.adminControllers).toList
end Modules
object Modules:
    def empty[F[_]]: Modules[F] = Modules(Map.empty)

trait ModuleSupport:
    type M[F[_]] <: Module[F]
    def key: Module.Key

    def dependsOn: Set[ModuleSupport] = Set.empty

    def load[F[_]: Async: Network: Tracer: Console](
        context: ModuleSupport.Context[F],
        modules: Modules[F] = Modules.empty
    ): Resource[F, M[F]]

end ModuleSupport

object ModuleSupport:
    final case class Context[F[_]: Async: Network: Tracer: Console](
        observability: Observability[F],
        reader: Reader[F],
        logger: Scribe[F]
    )
end ModuleSupport
