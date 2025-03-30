// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package example

import cats.effect.*
import example.build.BuildInfo
import io.github.iltotore.iron.*
import pillars.*
import pillars.db.*
import pillars.db.migrations.*
import pillars.flags.*
import pillars.httpclient.*
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*

// tag::quick-start[]
object app extends pillars.IOApp(DB, DBMigration, FeatureFlags, HttpClient): // // <1>
    def infos: AppInfo = BuildInfo.toAppInfo // // <2>

    def run: Run[IO[Unit]] = // // <3>
        for
            _ <- logger.info(s"📚 Welcome to ${config.name}!")
            _ <- dbMigration.migrate("classpath:db-migrations") // // <4>
            _ <- flag"feature-1".whenEnabled:
                     sessions.use: session =>
                         for
                             date <- session.unique(sql"select now()".query(timestamptz))
                             _    <- logger.info(s"The current date is $date.")
                         yield ()
            _ <- http.get("https://swapi.dev/api/people/1"): response =>
                     for
                         _    <- logger.info(s"Response: ${response.status}")
                         size <- response.body.compile.count
                         _    <- logger.info(s"Body: $size bytes")
                     yield ()
            _ <- server.start(homeController, userController)   // // <5>
        yield ()
        end for
    end run
end app
// end::quick-start[]
