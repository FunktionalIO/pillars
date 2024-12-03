import org.typelevel.sbt.gha.Permissions
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

val Scala3 = "3.5.2"
ThisBuild / scalaVersion := Scala3 // the default Scala

ThisBuild / githubWorkflowOSes         := Seq("ubuntu-latest")
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.graalvm("21"))

ThisBuild / tlCiHeaderCheck          := true
ThisBuild / tlCiScalafmtCheck        := true
ThisBuild / tlCiMimaBinaryIssueCheck := true
ThisBuild / tlCiDependencyGraphJob   := true
ThisBuild / autoAPIMappings          := true

val sharedSettings     = Seq(
  scalaVersion   := "3.5.2",
  libraryDependencies ++= Seq(
    "org.scalameta" %% "munit" % "1.0.3" % Test
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
val releasePreparation = WorkflowJob(
  id = "prepare-release",
  name = "👷 Prepare release",
  oses = List("ubuntu-latest"),
  cond = Some("""github.event_name != 'pull_request' && (startsWith(github.ref, 'refs/tags/prepare-v'))"""),
  needs = List("build"),
  env = Map("DTC_HEADLESS" -> "true"),
  permissions = Some(Permissions.Specify.defaultPermissive),
  steps = List(
    WorkflowStep.CheckoutFull,
    WorkflowStep.Run(
      name = Some("Get previous tag"),
      id = Some("previousTag"),
      commands = List(
        """name=$(git --no-pager tag --sort=creatordate --merged ${{ github.ref_name }} | tail -2 | head -1)""",
        """ref_name="${{ github.ref_name }}"""",
        """prefix="refs/tags/prepare-"""",
        """next_version=${$ref_name/#$prefix}""",
        """echo "previousTag=$name"""",
        """echo "previousTag=$name" >> $GITHUB_ENV"""",
        """echo "nextTag=$next_version"""",
        """echo "nextTag=$next_version" >> $GITHUB_ENV""""
      )
    ),
    WorkflowStep.Use(
      name = Some("Update CHANGELOG"),
      id = Some("changelog"),
      ref = UseRef.Public("requarks", "changelog-action", "v1"),
      params = Map(
        "token"       -> "${{ github.token }}",
        "fromTag"     -> "${{ github.ref_name }}",
        "toTag"       -> "${{ env.previousTag }}",
        "writeToFile" -> "true"
      )
    ),
    WorkflowStep.Use(
      name = Some("Commit CHANGELOG.md"),
      ref = UseRef.Public("stefanzweifel", "git-auto-commit-action", "v5"),
      params = Map(
        "commit_message" -> "docs: update CHANGELOG.md for ${{ env.nextTag }} [skip ci]",
        "branch"         -> "main",
        "file_pattern"   -> "CHANGELOG.md docToolchainConfig.groovy"
      )
    ),
    WorkflowStep.Run(
      name = Some("Create version tag"),
      commands = List(
        """git tag -a "v${{ env.nextTag }}" -m "Release ${{ env.nextTag }}" """,
        """git push origin "v${{ env.nextTag }}""""
      )
    ),
    WorkflowStep.Use(
      name = Some("Create release"),
      ref = UseRef.Public("ncipollo", "release-action", "v1.14.0"),
      params = Map(
        "allowUpdates" -> "true",
        "draft"        -> "false",
        "makeLatest"   -> "true",
        "name"         -> "v${{ env.nextTag }}",
        "tag"          -> "v${{ env.nextTag }}",
        "body"         -> "${{ steps.changelog.outputs.changes }}",
        "token"        -> "${{ github.token }}"
      )
    )
  )
)
val websitePublication = WorkflowJob(
  id = "website",
  name = "🌐 Publish website",
  oses = List("ubuntu-latest"),
  cond = Some("""github.event_name != 'pull_request' && (startsWith(github.ref, 'refs/tags/v'))"""),
  needs = List("publish"),
  env = Map("DTC_HEADLESS" -> "true"),
  permissions = Some(Permissions.Specify.defaultPermissive),
  concurrency = Some(Concurrency(s"$${{ github.workflow }} @ $${{ github.ref }}", Some(true))),
  steps = List(
    WorkflowStep.Checkout,
    WorkflowStep.Run(
      name = Some("Setup"),
      commands = List("chmod +x dtcw")
    )
  ) ++
      WorkflowStep.SetupJava(List(JavaSpec.temurin("17"))) ++
      List(
        WorkflowStep.Run(
          name = Some("Install docToolchain"),
          commands = List("./dtcw local install doctoolchain")
        ),
        WorkflowStep.Use(
          name = Some("Cache sbt"),
          ref = UseRef.Public("actions", "cache", "v4"),
          params = Map(
            "path"         ->
                """.ivy2
                          |.sbt""".stripMargin,
            "key"          -> s"sbt-$${{ hashFiles('build.sbt', 'plugins.sbt') }}",
            "restore-keys" -> s"pillars-cache-$${{ hashFiles('build.sbt', 'plugins.sbt') }}"
          )
        ),
        WorkflowStep.Run(
          name = Some("Get latest version"),
          id = Some("version"),
          commands = List(
            """PILLARS_VERSION="$(git ls-remote --tags $REPO | awk -F"/" '{print $3}' | grep '^v[0-9]*\\.[0-9]*\\.[0-9]*' | grep -v {} | sort --version-sort | tail -n1)""",
            """echo "latest version is [$PILLARS_VERSION]"""",
            """echo "version=${PILLARS_VERSION#v}" >> "$GITHUB_OUTPUT""""
          )
        ),
        WorkflowStep.Run(
          name = Some("Generate site"),
          commands = List("""./dtcw local generateSite && sbt unidoc"""),
          env = Map("PILLARS_VERSION" -> "${{ steps.version.outputs.version }}", "DTC_HEADLESS" -> "true")
        ),
        WorkflowStep.Run(
          name = Some("Copy to public"),
          commands = List("cp -r target/microsite/output ./public")
        ),
        WorkflowStep.Use(
          name = Some("Deply to GitHub Pages"),
          ref = UseRef.Public("peaceiris", "actions-gh-pages", "v4"),
          params = Map(
            "github_token"  -> s"$${{ secrets.GITHUB_TOKEN }}",
            "publish_dir"   -> "./public",
            "cname"         -> "pillars.dev",
            "enable_jekyll" -> "false"
          )
        )
      )
)
githubWorkflowTargetTags := List("prepare-v*", "v*")
githubWorkflowGeneratedCI ++= List(releasePreparation, websitePublication)

enablePlugins(ScalaUnidocPlugin)

outputStrategy := Some(StdoutOutput)

val libDependencySchemes = Seq(
  "io.circe"      %% "circe-yaml"        % VersionScheme.Always,
  "org.typelevel" %% "otel4s-core-trace" % VersionScheme.Always
)

lazy val core = Project("pillars-core", file("modules/core"))
    .enablePlugins(BuildInfoPlugin)
    .settings(sharedSettings)
    .settings(
      name                   := "pillars-core",
      description            := "pillars-core is a scala 3 library providing base services for writing backend applications",
      libraryDependencies ++= Dependencies.core,
      buildInfoKeys          := Seq[BuildInfoKey](name, version, description),
      buildInfoPackage       := "pillars.build",
      tlMimaPreviousVersions := Set(),
      libraryDependencySchemes ++= libDependencySchemes
    )

lazy val dbSkunk = Project("pillars-db-skunk", file("modules/db-skunk"))
    .enablePlugins(BuildInfoPlugin)
    .settings(sharedSettings)
    .settings(
      name                   := "pillars-db-skunk",
      description            := "pillars-db-skunk is a scala 3 library providing database services for writing backend applications using skunk",
      libraryDependencies ++= Dependencies.skunk,
      buildInfoKeys          := Seq[BuildInfoKey](name, version, description),
      buildInfoPackage       := "pillars.db.build",
      tlMimaPreviousVersions := Set(),
      libraryDependencySchemes ++= libDependencySchemes
    )
    .dependsOn(core)

lazy val dbDoobie = Project("pillars-db-doobie", file("modules/db-doobie"))
    .enablePlugins(BuildInfoPlugin)
    .settings(sharedSettings)
    .settings(
      name                   := "pillars-db-doobie",
      description            := "pillars-db-doobie is a scala 3 library providing database services for writing backend applications using doobie",
      libraryDependencies ++= Dependencies.doobie,
      buildInfoKeys          := Seq[BuildInfoKey](name, version, description),
      buildInfoPackage       := "pillars.doobie.build",
      tlMimaPreviousVersions := Set(),
      libraryDependencySchemes ++= libDependencySchemes
    )
    .dependsOn(core)

lazy val redisRediculous = Project("pillars-redis-rediculous", file("modules/redis-rediculous"))
    .enablePlugins(BuildInfoPlugin)
    .settings(sharedSettings)
    .settings(
      name                   := "pillars-redis-rediculous",
      description            := "pillars-redis-rediculous is a scala 3 library providing redis services for writing backend applications using rediculous",
      libraryDependencies ++= Dependencies.rediculous,
      buildInfoKeys          := Seq[BuildInfoKey](name, version, description),
      buildInfoPackage       := "pillars.doobie.build",
      tlMimaPreviousVersions := Set(),
      libraryDependencySchemes ++= libDependencySchemes
    )
    .dependsOn(core)

lazy val dbMigrations = Project("pillars-db-migration", file("modules/db-migration"))
    .enablePlugins(BuildInfoPlugin)
    .settings(sharedSettings)
    .settings(
      name                                        := "pillars-db-migration",
      description                                 := "pillars-db is a scala 3 library providing database migrations",
      libraryDependencySchemes += "org.typelevel" %% "otel4s-core-trace" % VersionScheme.Always,
      libraryDependencies ++= Dependencies.migrations,
      buildInfoKeys                               := Seq[BuildInfoKey](name, version, description),
      buildInfoPackage                            := "pillars.db.migrations.build",
      tlMimaPreviousVersions                      := Set(),
      libraryDependencySchemes ++= libDependencySchemes
    )
    .dependsOn(core, dbSkunk)

lazy val rabbitmqFs2 = Project("pillars-rabbitmq-fs2", file("modules/rabbitmq-fs2"))
    .enablePlugins(BuildInfoPlugin)
    .settings(sharedSettings)
    .settings(
      name                   := "pillars-rabbitmq-fs2",
      description            := "pillars-rabbitmq-fs2 is a scala 3 library providing RabbitMQ services for writing backend applications using fs2-rabbit",
      libraryDependencies ++= Dependencies.fs2Rabbit,
      buildInfoKeys          := Seq[BuildInfoKey](name, version, description),
      buildInfoPackage       := "pillars.rabbitmq.fs2.build",
      tlMimaPreviousVersions := Set(),
      libraryDependencySchemes ++= libDependencySchemes
    )
    .dependsOn(core)

lazy val flags = Project("pillars-flags", file("modules/flags"))
    .enablePlugins(BuildInfoPlugin)
    .settings(sharedSettings)
    .settings(
      name                   := "pillars-flags",
      description            := "pillars-flag is a scala 3 library providing feature flag services for writing backend applications",
      libraryDependencies ++= Dependencies.flags,
      buildInfoKeys          := Seq[BuildInfoKey](name, version, description),
      buildInfoPackage       := "pillars.flags.build",
      tlMimaPreviousVersions := Set(),
      libraryDependencySchemes ++= libDependencySchemes
    )
    .dependsOn(core)

lazy val httpClient = Project("pillars-http-client", file("modules/http-client"))
    .enablePlugins(BuildInfoPlugin)
    .settings(sharedSettings)
    .settings(
      name                   := "pillars-http-client",
      description            := "pillars-http-client is a scala 3 library providing http client services for writing backend applications",
      libraryDependencies ++= Dependencies.httpClient,
      buildInfoKeys          := Seq[BuildInfoKey](name, version, description),
      buildInfoPackage       := "pillars.httpclient.build",
      tlMimaPreviousVersions := Set(),
      libraryDependencySchemes ++= libDependencySchemes
    )
    .dependsOn(core)

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
      libraryDependencySchemes ++= libDependencySchemes
    )
    .dependsOn(core, dbSkunk, flags, httpClient, dbMigrations)
// end::example[]
lazy val docs = Project("pillars-docs", file("modules/docs"))
    .settings(sharedSettings)
    .settings(
      name                   := "pillars-docs",
      publish / skip         := true,
      tlMimaPreviousVersions := Set.empty,
      libraryDependencySchemes ++= libDependencySchemes
    )
    .dependsOn(core)

lazy val pillars = project
    .in(file("."))
    .aggregate(core, example, docs, dbSkunk, dbDoobie, dbMigrations, flags, httpClient, rabbitmqFs2, redisRediculous)
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
        "-social-links:github::https://rlemaitre.github.io/pillars"
      )
    )
