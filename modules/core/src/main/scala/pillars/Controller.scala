// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars

import cats.effect.IO
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.server.ServerEndpoint

type Controller = List[Controller.HttpEndpoint]

object Controller:
    type HttpEndpoint = ServerEndpoint[Fs2Streams[IO], IO]
