import Dependencies.effect
import xerial.sbt.Sonatype.GitHubHosting

ThisBuild / tlBaseVersion := "0.4" // your current series x.y

ThisBuild / organization := "com.rlemaitre"
ThisBuild / homepage     := Some(url("https://pillars.dev"))
ThisBuild / startYear    := Some(2023)
ThisBuild / licenses     := Seq("EPL-2.0" -> url("https://www.eclipse.org/legal/epl-2.0/"))
ThisBuild / developers ++= List(
  // your GitHub handle and name
  tlGitHubDev("rlemaitre", "Raphaël Lemaitre")
)

//ThisBuild / sonatypeCredentialHost := sonatypeCentralHost
ThisBuild / sonatypeProjectHosting := Some(GitHubHosting(
  "FunktionalIO",
  "pillars",
  "github.com.lushly070@passmail.net"
))
ThisBuild / scmInfo                := Some(
  ScmInfo(url("https://github.com/FunktionalIO/pillars"), "scm:git:git@github.com:FunktionalIO/pillars.git")
)

ThisBuild / scalaVersion := versions.scala // the default Scala

ThisBuild / tlCiHeaderCheck          := true
ThisBuild / tlCiScalafmtCheck        := true
ThisBuild / tlCiMimaBinaryIssueCheck := true
ThisBuild / tlCiDependencyGraphJob   := true
ThisBuild / autoAPIMappings          := true

lazy val sharedSettings = Seq(
  scalaVersion   := "3.5.2",
  libraryDependencies ++= Seq(
    "org.scalameta" %% "munit" % versions.munit.core % Test
  ),
  // Headers
  headerMappings := headerMappings.value + (HeaderFileType.scala -> HeaderCommentStyle.cppStyleLineComment),
  headerLicense  := Some(HeaderLicense.Custom(
    """|Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
           |This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
           |For more information see LICENSE or https://opensource.org/license/epl-2-0
           |""".stripMargin
  ))
)

enablePlugins(ScalaUnidocPlugin)

outputStrategy := Some(StdoutOutput)

val libDependencySchemes = Seq(
  "org.typelevel" %% "otel4s-core-trace" % VersionScheme.Always
)

def module(module: String, pkg: String, dependencies: Seq[ModuleID] = Seq.empty, desc: String = "") =
    Project(module, file(s"modules/$module"))
        .enablePlugins(BuildInfoPlugin)
        .settings(sharedSettings)
        .settings(
          name                   := s"pillars-$module",
          description            := desc,
          libraryDependencies ++= dependencies,
          buildInfoKeys          := Seq[BuildInfoKey](name, version, description),
          buildInfoPackage       := s"$pkg.build",
          tlMimaPreviousVersions := Set(),
          libraryDependencySchemes ++= libDependencySchemes,
          unusedCompileDependenciesFilter -= moduleFilter("org.typelevel", "scalac-compat-annotation")
        )

lazy val core = module(
  "core",
  "pillars",
  Dependencies.effect ++
      Dependencies.json ++
      Dependencies.tapir ++
      Dependencies.http4s ++
      Dependencies.http4sServer ++
      Dependencies.model ++
      Dependencies.decline ++
      Dependencies.logging ++
      Dependencies.observability ++
      Dependencies.tests,
  "pillars-core is a scala 3 library providing base services for writing backend applications"
)

lazy val munit = module(
  "munit",
  "pillars.munit",
  Dependencies.munit,
  "pillars-munit is a scala 3 library providing munit services for writing backend applications"
).dependsOn(core)

lazy val dbSkunk = module(
  "db-skunk",
  "pillars.db",
  Dependencies.skunk ++
      Dependencies.testContainers ++ // for test helpers
      Dependencies.tests,
  "pillars-db-skunk is a scala 3 library providing database services for writing backend applications using skunk"
).dependsOn(core)

lazy val dbDoobie = module(
  "db-doobie",
  "pillars.doobie",
  Dependencies.doobie ++
      Dependencies.tests,
  "pillars-db-doobie is a scala 3 library providing database services for writing backend applications using doobie"
).dependsOn(core)

lazy val redisRediculous = module(
  "redis-rediculous",
  "pillars.redis",
  Dependencies.rediculous ++
      Dependencies.tests,
  "pillars-redis-rediculous is a scala 3 library providing redis services for writing backend applications using rediculous"
).dependsOn(core)

lazy val dbMigrations = module(
  "db-migration",
  "pillars.db.migrations",
  Dependencies.migrations ++
      Dependencies.migrationsRuntime ++
      Dependencies.tests ++
      Dependencies.testContainersPostgres.map(_ % Test),
  "pillars-db-migration is a scala 3 library providing database migrations"
).dependsOn(core, dbSkunk % "test->compile")

lazy val rabbitmqFs2 = module(
  "rabbitmq-fs2",
  "pillars.rabbitmq.fs2",
  Dependencies.fs2Rabbit ++
      Dependencies.tests ++
      Dependencies.testContainersRabbit.map(_ % Test),
  "pillars-rabbitmq-fs2 is a scala 3 library providing RabbitMQ services for writing backend applications using fs2-rabbit"
).dependsOn(core)

lazy val flags = module(
  "flags",
  "pillars.flags",
  Dependencies.literally ++
      Dependencies.tapirIron ++
      Dependencies.tests,
  "pillars-flag is a scala 3 library providing feature flag services for writing backend applications"
).dependsOn(core)

lazy val httpClient = module(
  "http-client",
  "pillars.httpclient",
  Dependencies.http4sClient ++
      Dependencies.http4s ++
      Dependencies.tests,
  "pillars-http-client is a scala 3 library providing http client services for writing backend applications"
).dependsOn(core)

// tag::example[]
lazy val example = Project("pillars-example", file("modules/example"))
    .enablePlugins(BuildInfoPlugin) // //<1>
    .settings(sharedSettings)
    .settings(
      name                   := "pillars-example",                                            // //<2>
      description            := "pillars-example is an example of application using pillars", // //<3>
      libraryDependencies ++= Dependencies.tests ++ Dependencies.migrationsRuntime, // //<4>
      buildInfoKeys          := Seq[BuildInfoKey](name, version, description),                // //<5>
      buildInfoOptions       := Seq(BuildInfoOption.Traits("pillars.BuildInfo")),             // //<6>
      buildInfoPackage       := "example.build",                                              // //<7>
      publish / skip         := true,
      tlMimaPreviousVersions := Set.empty,
      libraryDependencySchemes ++= libDependencySchemes,
      unusedCompileDependenciesFilter -= moduleFilter("org.typelevel", "scalac-compat-annotation")
    )
    .dependsOn(core, dbSkunk, flags, httpClient, dbMigrations)
// end::example[]

lazy val docs = Project("pillars-docs", file("modules/docs"))
    .settings(sharedSettings)
    .settings(
      name                   := "pillars-docs",
      publish / skip         := true,
      tlMimaPreviousVersions := Set.empty,
      libraryDependencySchemes ++= libDependencySchemes,
      unusedCompileDependenciesFilter -= moduleFilter("org.typelevel", "scalac-compat-annotation")
    )
    .dependsOn(core)

lazy val pillars = project
    .in(file("."))
    .aggregate(core, munit, example, docs, dbSkunk, dbDoobie, dbMigrations, flags, httpClient, rabbitmqFs2, redisRediculous)
    .settings(sharedSettings)
    .settings(
      name                                       := "pillars",
      publish / skip                             := true,
      publishArtifact                            := false,
      tlMimaPreviousVersions                     := Set.empty,
      ScalaUnidoc / unidoc / unidocProjectFilter := inAnyProject -- inProjects(example, docs),
      ScalaUnidoc / unidoc / target              := file("target/microsite/output/api"),
      ScalaUnidoc / unidoc / scalacOptions ++= Seq(
        "-project",
        name.value,
        "-project-version",
        version.value,
        "-project-logo",
        "modules/docs/src/docs/images/logo.png",
        //    "-source-links:github://rlemaitre/pillars",
        "-social-links:github::https://github.com/FunktionalIO/pillars"
      ),
      unusedCompileDependenciesFilter -= moduleFilter("org.typelevel", "scalac-compat-annotation")
    )
