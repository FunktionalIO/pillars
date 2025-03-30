// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars.flags

import cats.effect.IO
import cats.effect.Ref
import cats.effect.Resource
import cats.effect.Sync
import cats.syntax.all.*
import fs2.io.file.Files
import pillars.Controller
import pillars.Module
import pillars.Modules
import pillars.ModuleSupport
import pillars.Pillars

trait FeatureFlags extends Module:
    override type ModuleConfig = FlagsConfig
    def isEnabled(flag: Flag): IO[Boolean]
    def config: FlagsConfig
    def getFlag(name: Flag): IO[Option[FeatureFlag]]
    def flags: IO[List[FeatureFlag]]

    private[flags] def setStatus(flag: Flag, status: Status): IO[Option[FeatureFlag]]
    def when[A](flag: Flag)(thunk: => IO[A]): IO[Unit] =
        isEnabled(flag).flatMap:
            case true  => thunk.void
            case false => Sync[IO].unit

    extension (pillars: Pillars)
        def flags: FeatureFlags                            = this
        def when(flag: Flag)(thunk: => IO[Unit]): IO[Unit] = this.when(flag)(thunk)
    end extension
end FeatureFlags

object FeatureFlags extends ModuleSupport:
    case object Key extends Module.Key:
        def name: String = "feature-flags"
    end Key
    def noop(conf: FlagsConfig): FeatureFlags =
        new FeatureFlags:
            def isEnabled(flag: Flag): IO[Boolean]                   = false.pure[IO]
            override def config: FlagsConfig                         = conf
            def getFlag(name: Flag): IO[Option[FeatureFlag]]         = None.pure[IO]
            def flags: IO[List[FeatureFlag]]                         = List.empty.pure[IO]
            private[flags] def setStatus(flag: Flag, status: Status) = None.pure[IO]

    override type M = FeatureFlags

    override def key: Module.Key = FeatureFlags.Key

    def load(context: ModuleSupport.Context, modules: Modules): Resource[IO, FeatureFlags] =
        import context.*
        given Files[IO] = Files.forAsync[IO]
        Resource.eval:
            for
                _       <- logger.info("Loading Feature flags module")
                config  <- reader.read[FlagsConfig](key.name)
                manager <- createManager(config)
                _       <- logger.info("Feature flags module loaded")
            yield manager
    end load

    private[flags] def createManager(conf: FlagsConfig): IO[FeatureFlags] =
        if !conf.enabled then IO.pure(FeatureFlags.noop(conf))
        else
            val flags = conf.flags.groupBy(_.name).map((name, flags) => name -> flags.head)
            Ref
                .of[IO, Map[Flag, FeatureFlag]](flags)
                .map: ref =>
                    new FeatureFlags:
                        def flags: IO[List[FeatureFlag]] = ref.get.map(_.values.toList)

                        override def config: FlagsConfig = conf

                        def getFlag(name: Flag): IO[Option[FeatureFlag]] =
                            ref.get.map(_.get(name))

                        def isEnabled(flag: Flag): IO[Boolean] =
                            ref.get.map(_.get(flag).exists(_.isEnabled))

                        private[flags] def setStatus(flag: Flag, status: Status) =
                            ref
                                .updateAndGet: flags =>
                                    flags.updatedWith(flag):
                                        case Some(f) => Some(f.copy(status = status))
                                        case None    => None
                                .map(_.get(flag))

                        override def adminControllers: List[Controller] = flagController(this).pure[List]
        end if
    end createManager
end FeatureFlags
