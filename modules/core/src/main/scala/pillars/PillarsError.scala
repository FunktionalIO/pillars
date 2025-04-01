// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars

import io.circe.Codec
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import pillars.PillarsError.Code
import pillars.PillarsError.ErrorNumber
import pillars.PillarsError.Message
import scala.util.control.NoStackTrace
import sttp.model.StatusCode
import sttp.tapir.EndpointOutput
import sttp.tapir.Schema
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.statusCode

type HttpErrorResponse = (StatusCode, PillarsError.View)

trait PillarsError extends Throwable, NoStackTrace:
    def code: Code
    def number: ErrorNumber
    def message: Message
    def details: Option[String]     = None
    def status: StatusCode          = StatusCode.InternalServerError
    override def getMessage: String = f"$code-$number%04d : $message"

    def httpResponse[T]: Either[HttpErrorResponse, T] =
        Left((status, PillarsError.View(f"$code-$number%04d", message, details)))

    def view: PillarsError.View = PillarsError.View(f"$code-$number%04d", message, details)

end PillarsError

object PillarsError:
    def fromThrowable(throwable: Throwable): PillarsError =
        throwable match
            case error: PillarsError => error
            case _                   => Unknown(throwable)
    case class View(code: String, message: String, details: Option[String]) derives Codec.AsObject, Schema
    object View:
        val output: EndpointOutput[HttpErrorResponse] = statusCode.and(jsonBody[View])

    private final case class Unknown(reason: Throwable) extends PillarsError:
        override def code: Code              = Code("ERR")
        override def number: ErrorNumber     = ErrorNumber(9999)
        override def message: Message        = Message("Internal server error")
        override def status: StatusCode      = StatusCode.InternalServerError
        override def details: Option[String] = Some(reason.getMessage)
    end Unknown

    private[pillars] final case class PayloadTooLarge(maxLength: Long) extends PillarsError:
        override def code: Code              = Code("ERR")
        override def number: ErrorNumber     = ErrorNumber(Int.MaxValue)
        override def message: Message        = Message(s"Payload limit ($maxLength) exceeded".refineUnsafe)
        override def status: StatusCode      = StatusCode.PayloadTooLarge
        override def details: Option[String] = Some(s"Payload limit ($maxLength) exceeded")
    end PayloadTooLarge

    private type CodeConstraint = (Not[Empty] & LettersUpperCase) DescribedAs "Code cannot be empty"
    opaque type Code <: String  = String :| CodeConstraint

    object Code extends RefinedTypeOps[String, CodeConstraint, Code]

    private type MessageConstraint = Not[Empty] DescribedAs "Message cannot be empty"
    opaque type Message <: String  = String :| MessageConstraint

    object Message extends RefinedTypeOps[String, MessageConstraint, Message]

    private type ErrorNumberConstraint = Positive DescribedAs "Number must be strictly positive"
    opaque type ErrorNumber <: Int     = Int :| ErrorNumberConstraint

    object ErrorNumber extends RefinedTypeOps[Int, ErrorNumberConstraint, ErrorNumber]
end PillarsError
