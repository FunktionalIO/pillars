import sbt.*

object Dependencies {
    val effect: Seq[ModuleID] = Seq(
      "org.typelevel" %% "cats-core"             % "2.12.0",
      "org.typelevel" %% "cats-effect"           % "3.5.7",
      "co.fs2"        %% "fs2-core"              % "3.11.0",
      "org.typelevel" %% "cats-collections-core" % "0.9.9",
      "org.typelevel" %% "cats-time"             % "0.5.1"
    )

    val model: Seq[ModuleID] = Seq(
      "com.comcast"        %% "ip4s-core"    % "3.6.0",
      "io.github.iltotore" %% "iron"         % "2.6.0",
      "io.github.iltotore" %% "iron-cats"    % "2.6.0",
      "io.github.iltotore" %% "iron-circe"   % "2.6.0",
      "io.github.iltotore" %% "iron-decline" % "2.6.0"
    )

    val commandLine: Seq[ModuleID] = Seq(
      "com.monovore" %% "decline"        % "2.4.1",
      "com.monovore" %% "decline-effect" % "2.4.1"
    )

    val json: Seq[ModuleID] = Seq(
      "io.circe" %% "circe-core"    % "0.14.10",
      "io.circe" %% "circe-generic" % "0.14.10",
      "io.circe" %% "circe-parser"  % "0.14.10",
      "io.circe" %% "circe-yaml"    % "0.15.3"
    )

    val http4s: Seq[ModuleID] = Seq(
      "org.http4s" %% "http4s-core"  % "0.23.30",
      "org.http4s" %% "http4s-dsl"   % "0.23.30",
      "org.http4s" %% "http4s-circe" % "0.23.30"
    )

    val http4sClient: Seq[ModuleID] = Seq(
      "org.http4s"                  %% "http4s-netty-client" % "0.5.21",
      "com.softwaremill.sttp.tapir" %% "tapir-http4s-client" % "1.11.10",
      "com.alejandrohdezma"         %% "http4s-munit"        % "1.1.0" % Test
    )
    val http4sServer: Seq[ModuleID] = Seq(
      "org.http4s" %% "http4s-netty-server" % "0.5.21"
    )
    val scodec: Seq[ModuleID]       = Seq(
      "org.scodec" %% "scodec-bits" % "2.2.2",
      "org.scodec" %% "scodec-core" % "2.3.2"
    )

    private val tapir = Seq(
      "com.softwaremill.sttp.tapir"   %% "tapir-http4s-server"         % "1.11.10",
      "com.softwaremill.sttp.tapir"   %% "tapir-json-circe"            % "1.11.10",
      "com.softwaremill.sttp.tapir"   %% "tapir-opentelemetry-metrics" % "1.11.10",
      "com.softwaremill.sttp.tapir"   %% "tapir-iron"                  % "1.11.10",
      "com.softwaremill.sttp.tapir"   %% "tapir-openapi-docs"          % "1.11.10",
      "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml"          % "0.11.3" exclude ("io.circe", "circe-yaml"),
      "com.softwaremill.sttp.tapir"   %% "tapir-swagger-ui-bundle"     % "1.11.10",
      "com.softwaremill.sttp.tapir"   %% "tapir-sttp-stub-server"      % "1.11.10" % Test,
      "com.softwaremill.sttp.client3" %% "core"                        % "3.10.1"  % Test
    )

    val logging: Seq[ModuleID] = Seq( //
      "com.outr" %% "scribe"            % "3.15.2",
      "com.outr" %% "scribe-cats"       % "3.15.2",
      "com.outr" %% "scribe-slf4j"      % "3.15.2",
      "com.outr" %% "scribe-json-circe" % "3.15.2",
      "com.outr" %% "scribe-file"       % "3.15.2"
    )

    val tests: Seq[ModuleID] = Seq(
      "org.scalameta"      %% "munit"             % "1.0.2" % Test,
      "org.typelevel"      %% "munit-cats-effect" % "2.0.0" % Test,
      "org.scalameta"      %% "munit-scalacheck"  % "1.0.0" % Test,
      "io.github.iltotore" %% "iron-scalacheck"   % "2.6.0" % Test
    )

    val testContainers: Seq[ModuleID] = Seq(
      "com.dimafeng" %% "testcontainers-scala-munit"      % "0.41.4" % Test,
      "com.dimafeng" %% "testcontainers-scala-postgresql" % "0.41.4" % Test,
      "com.dimafeng" %% "testcontainers-scala-rabbitmq"   % "0.41.4" % Test
    )

    val observability: Seq[ModuleID] = Seq(
      "org.typelevel" %% "otel4s-sdk"          % "0.11.2",
      "org.typelevel" %% "otel4s-sdk-exporter" % "0.11.2"
    )

    val skunk: Seq[ModuleID] = Seq(
      "org.tpolecat" %% "skunk-core"  % "1.0.0-M8",
      "org.tpolecat" %% "skunk-circe" % "1.0.0-M8"
    ) ++ tests

    val doobie: Seq[ModuleID] = Seq(
      "org.tpolecat" %% "doobie-core"   % "1.0.0-RC6",
      "org.tpolecat" %% "doobie-hikari" % "1.0.0-RC6" // HikariCP transactor.
    ) ++ tests

    val migrationsRuntime: Seq[ModuleID] = Seq(
      "org.postgresql" % "postgresql"                 % "42.7.4",
      "org.flywaydb"   % "flyway-database-postgresql" % "11.0.1"
    )
    val migrations: Seq[ModuleID]        = Seq(
      "org.flywaydb" % "flyway-core" % "11.0.1"
    ) ++ tests ++ testContainers ++ migrationsRuntime.map(_ % Test)

    val fs2Rabbit: Seq[ModuleID] = Seq(
      "dev.profunktor" %% "fs2-rabbit" % "5.3.0"
    ) ++ tests ++ testContainers

    val rediculous: Seq[ModuleID] = Seq(
      "io.chrisdavenport" %% "rediculous" % "0.5.1"
    ) ++ tests

    val flags: Seq[ModuleID] = Seq(
      "org.typelevel" %% "literally" % "1.2.0"
    ) ++ tests

    val httpClient: Seq[ModuleID] = http4sClient ++ http4s ++ tests
    val core: Seq[ModuleID]       =
        effect ++ json ++ tapir ++ http4s ++ http4sServer ++ model ++ commandLine ++ logging ++ observability ++ tests
}
