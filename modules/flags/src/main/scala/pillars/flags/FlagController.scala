// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars.flags

import cats.syntax.all.*
import io.github.iltotore.iron.*
import pillars.AdminServer.baseEndpoint
import pillars.Controller
import pillars.PillarsError
import pillars.PillarsError.Code
import pillars.PillarsError.ErrorNumber
import pillars.PillarsError.Message
import pillars.flags.FlagController.FlagEndpoints
import pillars.flags.FlagController.FlagError
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.codec.iron.given
import sttp.tapir.json.circe.jsonBody

def flagController(manager: FeatureFlags): Controller =
    val listAll = FlagEndpoints.list.serverLogicSuccess(_ => manager.flags)
    val getOne  =
        FlagEndpoints.get.serverLogic: name =>
            manager
                .getFlag(name)
                .map:
                    case Some(flag) => Right(flag)
                    case None       => FlagError.FlagNotFound(name).httpResponse
    val modify  =
        FlagEndpoints.edit.serverLogic: (name, flag) =>
            manager
                .setStatus(name, flag.status)
                .map:
                    case Some(flag) => Right(flag)
                    case None       => FlagError.FlagNotFound(name).httpResponse

    List(listAll, getOne, modify)
end flagController

object FlagController:
    enum FlagError(
        val number: PillarsError.ErrorNumber,
        override val status: StatusCode,
        val message: PillarsError.Message
    ) extends PillarsError:
        override def code: Code = Code("FLAG")

        case FlagNotFound(name: Flag)
            extends FlagError(ErrorNumber(1), StatusCode.NotFound, Message(s"Flag ${name}not found".assume))
    end FlagError

    object FlagEndpoints:
        private val prefix = baseEndpoint.in("flags")

        def list: Endpoint[Unit, Unit, (StatusCode, PillarsError.View), List[FeatureFlag], Any] =
            prefix.get.out(jsonBody[List[FeatureFlag]])

        def get: Endpoint[Unit, Flag, (StatusCode, PillarsError.View), FeatureFlag, Any] =
            prefix.get.in(path[Flag]("name")).out(jsonBody[FeatureFlag])

        def edit: Endpoint[Unit, (Flag, FlagDetails), (StatusCode, PillarsError.View), FeatureFlag, Any] =
            prefix.put.in(path[Flag]("name")).in(jsonBody[FlagDetails]).out(jsonBody[FeatureFlag])
    end FlagEndpoints

end FlagController
