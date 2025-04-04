// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars

import cats.effect.*
import cats.syntax.all.*
import fs2.io.file.Path
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
trait Pillars:

    /**
     * The application information.
     */
    def appInfo: AppInfo

    /**
     * Component for observability. It allows you to create spans and metrics.
     */
    def observability: Observability

    /**
     * The configuration for the application.
     */
    def config: PillarsConfig

    /**
     * The API server for the application.
     *
     */
    def apiServer: HttpServer

    /**
     * The admin server for the application.
     *
     */
    def adminServer: HttpServer

    /**
     * The logger for the application.
     */
    def logger: Scribe[IO]

    /**
     * Reads a configuration from the configuration.
     *
     * @return the configuration.
     */
    def readConfig[T](using Decoder[T]): IO[T]

    /**
     * Gets a module from the application.
     *
     * @return the module.
     */
    def module[T](key: Module.Key): T

    /**
     * The admin controllers for the application.
     *
     * @return the admin controllers.
     */
    def adminControllers: List[Controller]

    /**
     * The API controllers for the application.
     *
     * @return the API controllers.
     */
    def apiControllers: List[Controller] = Nil

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
    def apply(
        infos: AppInfo,
        modules: Seq[ModuleSupport],
        path: Path
    ): Resource[IO, Pillars] =
        val configReader = Reader(path)
        for
            _config         <- Resource.eval(configReader.read[PillarsConfig])
            obs             <- Observability.init(infos, _config.observability)
            given Tracer[IO] = obs.tracer
            _               <- Resource.eval(Logging.init(_config.log))
            _logger          = scribe.cats.io
            context          = ModuleSupport.Context(obs, configReader, _logger)
            _adminServer    <- AdminServer.create(_config.admin, infos, context)
            _               <- Resource.eval(_logger.info("Loading modules..."))
            _modules        <- loadModules(modules, context)
            _               <- Resource.eval(_logger.debug(s"Loaded ${_modules.size} modules"))
            probes          <- ProbeManager.build(_modules, obs)
            _               <- Spawn[IO].background(probes.start())
            _apiServer      <- ApiServer.create(_config.api, infos, context)
        yield new Pillars:
            override def appInfo: AppInfo                       = infos
            override def observability: Observability           = obs
            override def config: PillarsConfig                  = _config
            override def apiServer: HttpServer                  = _apiServer
            override def adminServer: HttpServer                = _adminServer
            override def logger: Scribe[IO]                     = _logger
            override def readConfig[T](using Decoder[T]): IO[T] = configReader.read[T]
            override def module[T](key: Module.Key): T          = _modules.get(key)
            override def adminControllers: List[Controller]     = _modules.adminControllers :+ probesController(probes)
        end for
    end apply

    inline def apply(using p: Pillars): Pillars = p

    /**
     * Loads the modules for the application.
     *
     * @param context The context for loading the modules.
     * @return a resource that will instantiate the modules.
     */
    private def loadModules(modules: Seq[ModuleSupport], context: ModuleSupport.Context): Resource[IO, Modules] =
        scribe.info(s"Found ${modules.size} modules: ${modules.map(_.key).map(_.name).mkString(", ")}")
        modules.topologicalSort(_.dependsOn) match
            case Left(value)  => throw value
            case Right(value) =>
                value.foldLeftM(Modules.empty):
                    case (acc, loader) =>
                        loader.load(context, acc).map(acc.add(loader.key))
        end match
    end loadModules

end Pillars
