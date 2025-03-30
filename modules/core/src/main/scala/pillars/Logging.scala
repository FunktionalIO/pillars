// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars

import cats.Show
import cats.effect.*
import fs2.io.file.Path
import io.circe.*
import io.circe.derivation.Configuration
import io.circe.syntax.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import scribe.Level
import scribe.Logger
import scribe.Scribe
import scribe.file.PathBuilder
import scribe.format.Formatter
import scribe.json.ScribeCirceJsonSupport
import scribe.mdc.MDC
import scribe.writer.ConsoleWriter
import scribe.writer.Writer

def logger(using p: Pillars): Scribe[IO] = p.logger

object Logging:
    def init(config: Config): IO[Unit] =
        IO.delay(
          scribe.Logger.root
              .clearHandlers()
              .clearModifiers()
              .withHandler(
                formatter = config.format.formatter,
                minimumLevel = Some(config.level),
                writer = writer(config)
              ).replace()
        ).void

    private def writer(config: Config): Writer =
        config.format match
            case Format.Json => ScribeCirceJsonSupport.writer(config.output.writer)
            case _           => config.output.writer

    private type BufferSizeConstraint = Positive DescribedAs "Buffer size should be positive"
    opaque type BufferSize <: Int     = Int :| BufferSizeConstraint

    object BufferSize extends RefinedTypeOps[Int, BufferSizeConstraint, BufferSize]

    enum Format:
        case Json
        case Simple
        case Colored
        case Classic
        case Compact
        case Enhanced
        case Advanced
        case Strict

        def formatter: Formatter = this match
            case Format.Json     => Formatter.default
            case Format.Simple   => Formatter.simple
            case Format.Colored  => Formatter.colored
            case Format.Classic  => Formatter.classic
            case Format.Compact  => Formatter.compact
            case Format.Enhanced => Formatter.enhanced
            case Format.Advanced => Formatter.advanced
            case Format.Strict   => Formatter.strict
    end Format

    private object Format:
        given Show[Format] = Show.fromToString

        given Encoder[Format] = Encoder.encodeString.contramap(_.toString.toLowerCase)

        given Decoder[Format] = Decoder.decodeString.emap {
            case "json"     => Right(Format.Json)
            case "simple"   => Right(Format.Simple)
            case "colored"  => Right(Format.Colored)
            case "classic"  => Right(Format.Classic)
            case "compact"  => Right(Format.Compact)
            case "enhanced" => Right(Format.Enhanced)
            case "advanced" => Right(Format.Advanced)
            case "strict"   => Right(Format.Strict)
            case other      => Left(s"Unknown output format: $other")
        }
    end Format

    enum Output:
        case Console
        case File(path: Path)

        def writer: Writer = this match
            case Output.Console    => ConsoleWriter
            case Output.File(path) => scribe.file.FileWriter(PathBuilder.static(path.toNioPath))
    end Output

    private object Output:
        given Show[Output] = Show.show:
            case Console    => "console"
            case File(path) => s"file($path)"

        given Encoder[Output] = Encoder.instance:
            case Output.File(path) => Json.obj("type" -> "file".asJson, "path" -> path.toString.asJson)
            case Output.Console    => Json.obj("type" -> "console".asJson)

        given Decoder[Output] = Decoder.instance: cursor =>
            cursor
                .downField("type")
                .as[String]
                .flatMap:
                    case "console" => Right(Output.Console)
                    case "file"    =>
                        for
                            p    <- cursor.downField("path").as[String]
                            path <- Either.cond(
                                      p.nonEmpty,
                                      Path(p),
                                      DecodingFailure("Missing path for file output", cursor.history)
                                    )
                        yield Output.File(path)
                    case other     => Left(DecodingFailure(s"Unknown output type: $other", cursor.history))
    end Output

    final case class Config(
        level: Level = Level.Info,
        format: Logging.Format = Logging.Format.Enhanced,
        output: Logging.Output = Logging.Output.Console,
        excludeHikari: Boolean = false
    ) extends pillars.Config

    object Config:
        given Configuration = Configuration.default.withKebabCaseMemberNames.withKebabCaseConstructorNames.withDefaults

        given Decoder[Level] = Decoder.decodeString.emap(s => Level.get(s).toRight(s"Invalid log level $s"))

        given Encoder[Level] = Encoder.encodeString.contramap(_.name)

        given Codec[Config] = Codec.AsObject.derivedConfigured
    end Config

    final case class HttpConfig(
        enabled: Boolean = false,
        level: Level = Level.Debug,
        headers: Boolean = false,
        body: Boolean = true
    ) extends pillars.Config:
        def logAction: Option[String => IO[Unit]] = Some(scribe.cats.effect[IO].log(level, MDC.instance, _))
    end HttpConfig

    object HttpConfig:
        import Config.given
        given Codec[HttpConfig] = Codec.AsObject.derivedConfigured
    end HttpConfig

end Logging
