// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars.tests

import cats.effect.Async
import cats.effect.Resource
import cats.effect.std.Console
import com.dimafeng.testcontainers.Container
import fs2.io.net.Network
import org.typelevel.otel4s.trace.Tracer
import pillars.Module

trait ModuleTestSupport:
    def key: Module.Key
    def load[F[_]: Async: Network: Tracer: Console](container: Container): Option[Resource[F, Module[F[_]]]]
end ModuleTestSupport
