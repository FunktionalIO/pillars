// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars.tests

import cats.effect.IO
import cats.effect.Resource
import cats.syntax.all.*
import com.dimafeng.testcontainers.Container
import com.dimafeng.testcontainers.LazyContainer
import com.dimafeng.testcontainers.SingleContainer
import com.dimafeng.testcontainers.lifecycle.*
import com.dimafeng.testcontainers.munit.*
import io.circe.Decoder
import io.github.iltotore.iron.*
import munit.CatsEffectSuite
import pillars.AdminServer
import pillars.ApiServer
import pillars.App
import pillars.AppInfo
import pillars.Config.PillarsConfig
import pillars.Controller
import pillars.HttpServer
import pillars.Module
import pillars.Modules
import pillars.Observability
import pillars.Pillars
import pillars.tests.build.BuildInfo
import scribe.Scribe

trait PillarsSuite extends CatsEffectSuite, TestContainersSuite:
    def moduleSupports: Set[ModuleTestSupport]

    def withPillars[T](test: Pillars => IO[T]): IO[T] =
        withContainers: container =>
            extractContainers(container) match
                case Left(error)       => fail(error)
                case Right(containers) =>
                    fromContainer(moduleSupports)(containers).use: p =>
                        test(p)

    def fromContainer(supports: Set[ModuleTestSupport])(containers: List[? <: SingleContainer[?]])
        : Resource[IO, Pillars] =
        val conf = PillarsConfig(
          name = App.Name("Tests"),
          api = ApiServer.Config(enabled = false),
          admin = AdminServer.Config(enabled = false),
          observability = Observability.Config()
        )
        for
            obs     <- Observability.noop.toResource
            modules <-
                (for
                    container <- containers
                    support   <- supports
                    modules   <- support.load(container)
                    pair       = modules.map(support.key -> _)
                yield pair).sequence.map(mods => Modules(mods.toMap))
        yield new Pillars:
            override def appInfo: AppInfo                                = BuildInfo.toAppInfo
            override def observability: Observability                    = obs
            override def config: PillarsConfig                           = conf
            override def apiServer: HttpServer                           = HttpServer.noop
            override def logger: Scribe[IO]                              = scribe.cats.io
            override def readConfig[T](using decoder: Decoder[T]): IO[T] =
                IO.raiseError(new NotImplementedError("readConfig is not available in tests"))
            override def module[T](key: Module.Key): T                   = modules.get(key)
            override def adminServer: HttpServer                         = HttpServer.noop
            override def adminControllers: List[Controller]              = Nil
        end for
    end fromContainer

    private def extractContainers(container: Any): Either[String, List[? <: SingleContainer[?]]] = container match
        case c: SingleContainer[?]            => List(c).asRight
        case and(c1: Andable, c2: Andable)    => (extractContainers(c1), extractContainers(c2)).tupled.map(_ ++ _)
        case l: LazyContainer[? <: Container] => extractContainers(l.container)
        case l: Seq[?]                        => l.flatTraverse(c => extractContainers(c)).map(_.toList)
        case _                                => Left(
              s"""Only single containers are supported.
               |If you need multiple containers please use pillars.tests.PillarsFromContainersForEach.
               |
               |Unsupported container: $container""".stripMargin
            )
end PillarsSuite

trait PillarsFromContainerForEach extends PillarsSuite, TestContainerForEach

trait PillarsFromContainerForAll extends PillarsSuite, TestContainerForAll

trait PillarsFromContainersForEach extends PillarsSuite, TestContainersForEach

trait PillarsFromContainersForAll extends PillarsSuite, TestContainersForAll
