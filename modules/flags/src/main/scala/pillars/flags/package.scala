// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars

import cats.effect.IO
import io.circe.Codec
import io.circe.Decoder
import io.circe.Encoder
import io.github.iltotore.iron.*
import io.github.iltotore.iron.circe.given
import org.typelevel.literally.Literally
import sttp.tapir.Schema
import sttp.tapir.codec.iron.*
import sttp.tapir.codec.iron.given

package object flags:
    given Encoder[Status] = Encoder.encodeString.contramap {
        case Status.Enabled  => "enabled"
        case Status.Disabled => "disabled"
    }

    given Decoder[Status] = Decoder.decodeString.emap {
        case "enabled"  => Right(Status.Enabled)
        case "disabled" => Right(Status.Disabled)
        case other      => Left(s"Invalid status $other")
    }

    given Schema[Status] = Schema.derived

    given Codec[FeatureFlag] = Codec.AsObject.derived

    given Schema[FeatureFlag] = Schema.derived
    extension (p: Pillars)
        def flags: FeatureFlags = p.module(FeatureFlags.Key)

        def whenEnabled[A](flag: Flag)(thunk: => IO[A]): IO[Unit] =
            p.module[FeatureFlags](FeatureFlags.Key).when(flag)(thunk)
    end extension

    extension (inline ctx: StringContext)
        inline def flag(inline args: Any*): Flag = ${ FlagLiteral('ctx, 'args) }
    // noinspection ScalaWeakerAccess
    object FlagLiteral extends Literally[Flag]:
        override def validate(s: String)(using Quotes): Either[String, Expr[Flag]] =
            if Flag.rtc.test(s) then Right('{ Flag.applyUnsafe(${ Expr(s) }) })
            else Left(Flag.rtc.message)

    extension (flag: Flag)
        def whenEnabled[A](using p: Pillars)(thunk: => IO[A]): IO[Unit] =
            p.module[FeatureFlags](FeatureFlags.Key).when(flag)(thunk)
end flags
