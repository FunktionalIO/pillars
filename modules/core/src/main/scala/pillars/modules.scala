// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars

import cats.effect.IO
import cats.effect.Resource
import pillars.Config.Reader
import pillars.probes.Probe
import scribe.Scribe

trait Module:
    type ModuleConfig <: Config
    def probes: List[Probe] = Nil

    def adminControllers: List[Controller] = Nil

    def config: ModuleConfig

end Module

object Module:
    trait Key:
        def name: String

        override def toString: String = s"Key($name)"
    end Key
end Module

case class Modules(private val values: Map[Module.Key, Module]):
    def add[K <: Module](key: Module.Key)(value: K): Modules = Modules(values + (key -> value))
    def get[K](key: Module.Key): K                           = values(key).asInstanceOf[K]
    export values.size
    export values.values as all
    def probes: List[Probe]                                  = all.flatMap(_.probes).toList
    def adminControllers: List[Controller]                   = all.flatMap(_.adminControllers).toList
end Modules
object Modules:
    def empty: Modules = Modules(Map.empty)

trait ModuleSupport:
    type M <: Module
    def key: Module.Key

    def dependsOn: Set[ModuleSupport] = Set.empty

    def load(context: ModuleSupport.Context, modules: Modules = Modules.empty): Resource[IO, M]

end ModuleSupport

object ModuleSupport:
    final case class Context(
        observability: Observability,
        reader: Reader,
        logger: Scribe[IO]
    )
end ModuleSupport
