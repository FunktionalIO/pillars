// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars

import cats.Parallel
import cats.effect.*
import cats.effect.std.Console
import cats.syntax.all.*
import fs2.io.file.Path
import fs2.io.net.Network
import io.circe.Decoder
import org.typelevel.otel4s.trace.Tracer
import pillars.Config.PillarsConfig
import pillars.Config.Reader
import pillars.graph.*
import pillars.probes.ProbeManager
import pillars.probes.probesController
import scribe.*

/**
 * The Pillars trait defines the main components of the application.
 */
trait Pillars[F[_]]:
    /**
     * The application information.
     */
    def appInfo: AppInfo

    /**
     * Component for observability. It allows you to create spans and metrics.
     */
    def observability: Observability[F]

    /**
     * The configuration for the application.
     */
    def config: PillarsConfig

    /**
     * The API server for the application.
     *
     * It has to be manually started by calling the `start` method in the application.
     */
    def apiServer: ApiServer[F]

    /**
     * The logger for the application.
     */
    def logger: Scribe[F]

    /**
     * Reads a configuration from the configuration.
     *
     * @return the configuration.
     */
    def readConfig[T](using Decoder[T]): F[T]

    /**
     * Gets a module from the application.
     *
     * @return the module.
     */
    def module[T](key: Module.Key): T
end Pillars

/**
 * The Pillars object provides methods to initialize the application.
 */
object Pillars:
    /**
     * Creates a new instance of Pillars.
     *
     * Modules are loaded from the classpath using the ServiceLoader mechanism, and are loaded in topological order
     *
     * @param path The path to the configuration file.
     * @return a resource that will create a new instance of Pillars.
     */
    def apply[F[_]: LiftIO: Async: Console: Network: Parallel](
        infos: AppInfo,
        modules: Seq[ModuleSupport],
        path: Path
    ): Resource[F, Pillars[F]] =
        val configReader = Reader[F](path)
        for
            _config        <- Resource.eval(configReader.read[PillarsConfig])
            obs            <- Observability.init[F](infos, _config.observability)
            given Tracer[F] = obs.tracer
            _              <- Resource.eval(Logging.init(_config.log))
            _logger         = ScribeImpl[F](Sync[F])
            context         = ModuleSupport.Context(obs, configReader, _logger)
            _              <- Resource.eval(_logger.info("Loading modules..."))
            _modules       <- loadModules(modules, context)
            _              <- Resource.eval(_logger.debug(s"Loaded ${_modules.size} modules"))
            probes         <- ProbeManager.build[F](_modules, obs)
            _              <- Spawn[F].background(probes.start())
            _              <- Spawn[F].background:
                                  AdminServer[F](_config.admin, infos, obs, _modules.adminControllers :+ probesController(probes))
                                      .start()
        yield new Pillars[F]:
            override def appInfo: AppInfo                      = infos
            override def observability: Observability[F]       = obs
            override def config: PillarsConfig                 = _config
            override def apiServer: ApiServer[F]               =
                ApiServer.init(config.api, infos, observability, logger)
            override def logger: Scribe[F]                     = _logger
            override def readConfig[T](using Decoder[T]): F[T] = configReader.read[T]
            override def module[T](key: Module.Key): T         = _modules.get(key)
        end for
    end apply

    inline def apply[F[_]](using p: Pillars[F]): Pillars[F] = p

    /**
     * Loads the modules for the application.
     *
     * @param context The context for loading the modules.
     * @return a resource that will instantiate the modules.
     */
    private def loadModules[F[_]: Async: Network: Tracer: Console](
        modules: Seq[ModuleSupport],
        context: ModuleSupport.Context[F]
    ): Resource[F, Modules[F]] =
        scribe.info(s"Found ${modules.size} modules: ${modules.map(_.key).map(_.name).mkString(", ")}")
        modules.topologicalSort(_.dependsOn) match
            case Left(value)  => throw value
            case Right(value) =>
                value.foldLeftM(Modules.empty[F]):
                    case (acc, loader) =>
                        loader.load(context, acc).map(acc.add(loader.key))
        end match
    end loadModules

end Pillars
