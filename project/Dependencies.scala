import sbt.*

object versions {
    val scala            = "3.5.2"
    // Dependencies
    val cats             = "2.12.0"
    val catsEffect       = "3.5.7"
    val circe            = "0.14.10"
    val circeYaml        = "0.15.3"
    val decline          = "2.4.1"
    val doobie           = "1.0.0-RC6"
    val flyway           = "11.1.0"
    val fs2              = "3.11.0"
    val fs2Rabbit        = "5.3.0"
    val http4s           = "0.23.30"
    val http4sNetty      = "0.5.21"
    val ip4s             = "3.6.0"
    val iron             = "2.6.0"
    val literally        = "1.2.0"
    val openApiCirce     = "0.11.3"
    val otel4s           = "0.11.2"
    val postgresqlDriver = "42.7.4"
    val rediculous       = "0.5.1"
    val scribe           = "3.15.3"
    val skunk            = "1.0.0-M8"
    val tapir            = "1.11.11"
    val testContainers   = "0.41.5"

    object munit {
        val core       = "1.0.3"
        val catsEffect = "2.0.0"
        val scalacheck = "1.0.0"
        val http4s     = "1.1.0"
    }

    object scodec {
        val bits = "1.2.1"
        val core = "2.3.2"
    }
}

object Dependencies {
    val effect: Seq[ModuleID] = Seq(
      "org.typelevel" %% "cats-core"   % versions.cats,
      "org.typelevel" %% "cats-effect" % versions.catsEffect,
      "co.fs2"        %% "fs2-core"    % versions.fs2
    )

    val model: Seq[ModuleID] = Seq(
      "com.comcast"        %% "ip4s-core"  % versions.ip4s,
      "io.github.iltotore" %% "iron"       % versions.iron,
//      "io.github.iltotore" %% "iron-cats"    % versions.iron,
      "io.github.iltotore" %% "iron-circe" % versions.iron
//      "io.github.iltotore" %% "iron-decline" % versions.iron
    )

    val decline: Seq[ModuleID] = Seq(
      "com.monovore" %% "decline" % versions.decline
    )

    val json: Seq[ModuleID] = Seq(
      "io.circe" %% "circe-core" % versions.circe,
//      "io.circe" %% "circe-generic" % versions.circe,
//      "io.circe" %% "circe-parser"  % versions.circe,
      "io.circe" %% "circe-yaml" % versions.circeYaml
    )

    val http4s: Seq[ModuleID] = Seq(
      "org.http4s" %% "http4s-core" % versions.http4s
    )

    val http4sClient: Seq[ModuleID] = Seq(
      "org.http4s"                  %% "http4s-netty-client" % versions.http4sNetty,
      "com.softwaremill.sttp.tapir" %% "tapir-http4s-client" % versions.tapir,
      "com.alejandrohdezma"         %% "http4s-munit"        % versions.munit.http4s % Test
    )
    val http4sServer: Seq[ModuleID] = Seq(
      "org.http4s" %% "http4s-netty-server" % versions.http4sNetty
    )
    val scodec: Seq[ModuleID]       = Seq(
      "org.scodec" %% "scodec-bits" % versions.scodec.bits,
      "org.scodec" %% "scodec-core" % versions.scodec.core
    )

    val tapir: Seq[ModuleID]     = Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-http4s-server"     % versions.tapir,
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe"        % versions.tapir,
//      "com.softwaremill.sttp.tapir" %% "tapir-opentelemetry-metrics" % versions.tapir,
      "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs"      % versions.tapir,
      "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % versions.tapir,
      "com.softwaremill.sttp.tapir" %% "tapir-sttp-stub-server"  % versions.tapir % Test
    )
    val tapirIron: Seq[ModuleID] = Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-iron" % versions.tapir
    )

    val logging: Seq[ModuleID] = Seq( //
      "com.outr" %% "scribe"            % versions.scribe,
      "com.outr" %% "scribe-cats"       % versions.scribe,
      "com.outr" %% "scribe-slf4j"      % versions.scribe % Runtime,
      "com.outr" %% "scribe-json-circe" % versions.scribe,
      "com.outr" %% "scribe-file"       % versions.scribe
    )

    val tests: Seq[ModuleID] = Seq(
      "org.scalameta"      %% "munit"             % versions.munit.core       % Test,
      "org.typelevel"      %% "munit-cats-effect" % versions.munit.catsEffect % Test,
      "org.scalameta"      %% "munit-scalacheck"  % versions.munit.scalacheck % Test,
      "io.github.iltotore" %% "iron-scalacheck"   % versions.iron             % Test
    )

    val testContainers: Seq[ModuleID] = Seq(
      "com.dimafeng" %% "testcontainers-scala-munit"      % versions.testContainers % Test,
      "com.dimafeng" %% "testcontainers-scala-postgresql" % versions.testContainers % Test,
      "com.dimafeng" %% "testcontainers-scala-rabbitmq"   % versions.testContainers % Test
    )

    val observability: Seq[ModuleID] = Seq(
      "org.typelevel" %% "otel4s-sdk"          % versions.otel4s,
      "org.typelevel" %% "otel4s-sdk-exporter" % versions.otel4s
    )

    val skunk: Seq[ModuleID] = Seq(
      "org.tpolecat" %% "skunk-core" % versions.skunk
    )

    val doobie: Seq[ModuleID] = Seq(
      "org.tpolecat" %% "doobie-core"   % versions.doobie,
      "org.tpolecat" %% "doobie-hikari" % versions.doobie // HikariCP transactor.
    )

    val migrationsRuntime: Seq[ModuleID] = Seq(
      "org.postgresql" % "postgresql"                 % versions.postgresqlDriver % Runtime,
      "org.flywaydb"   % "flyway-database-postgresql" % versions.flyway           % Runtime
    )
    val migrations: Seq[ModuleID]        = Seq(
      "org.flywaydb" % "flyway-core" % versions.flyway
    )

    val fs2Rabbit: Seq[ModuleID] = Seq(
      "dev.profunktor" %% "fs2-rabbit" % versions.fs2Rabbit
    )

    val rediculous: Seq[ModuleID] = Seq(
      "io.chrisdavenport" %% "rediculous" % versions.rediculous
    )

    val literally: Seq[ModuleID] = Seq(
      "org.typelevel" %% "literally" % versions.literally
    )
}
