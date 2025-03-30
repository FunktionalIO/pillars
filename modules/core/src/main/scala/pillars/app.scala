// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars

import cats.effect.{IOApp as CEIOApp, *}
import cats.effect.std.Console
import com.monovore.decline.Command
import com.monovore.decline.Opts
import fs2.io.file.Path
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import pillars.App.Description
import pillars.App.Name
import pillars.App.Version
import pillars.probes.Probe

abstract class App(val modules: ModuleSupport*):
    def infos: AppInfo
    def probes: List[Probe]                = Nil
    def adminControllers: List[Controller] = Nil
    def run: Run[IO[Unit]]

    import pillars.given
    def run(args: List[String]): IO[ExitCode] =
        val command = Command(infos.name, infos.description):
            Opts.option[Path]("config", "Path to the configuration file").map: configPath =>
                Pillars(infos, modules, configPath).use: pillars =>
                    given Pillars = pillars
                    run.as(ExitCode.Success)

        command.parse(args, sys.env) match
            case Left(help)  => Console[IO].errorln(help).as(ExitCode.Error)
            case Right(prog) => prog
    end run
end App

abstract class IOApp(override val modules: ModuleSupport*) extends App(modules*), CEIOApp

object App:
    private type NameConstraint = Not[Blank]
    opaque type Name <: String  = String :| NameConstraint

    object Name extends RefinedTypeOps[String, NameConstraint, Name]

    private type VersionConstraint = SemanticVersion
    opaque type Version <: String  = String :| VersionConstraint

    object Version extends RefinedTypeOps[String, VersionConstraint, Version]

    private type DescriptionConstraint = Not[Blank]
    opaque type Description <: String  = String :| DescriptionConstraint

    object Description extends RefinedTypeOps[String, DescriptionConstraint, Description]
end App

case class AppInfo(name: App.Name, version: App.Version, description: App.Description)
trait BuildInfo:
    def name: String
    def version: String
    def description: String
    def toAppInfo: AppInfo = AppInfo(Name(name.assume), Version(version.assume), Description(description.assume))
end BuildInfo
