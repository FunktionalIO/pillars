// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars.tests

import cats.effect.IO
import cats.effect.Resource
import com.dimafeng.testcontainers.Container
import pillars.Module

trait ModuleTestSupport:
    def key: Module.Key
    def load(container: Container): Option[Resource[IO, Module]]
end ModuleTestSupport
