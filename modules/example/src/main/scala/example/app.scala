package example

import cats.effect.IO
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import pillars.App.*
import pillars.Pillars
import pillars.db.*
import pillars.flags.*
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*

// tag::quick-start[]
object Main extends pillars.EntryPoint: // // <1>
    def app: pillars.App[IO] = new pillars.App[IO]: // // <2>
        def name        = Name("BookStore")
        def version     = Version("0.0.1")
        def description = Description("A simple bookstore")

        def run(pillars: Pillars[IO]): IO[Unit] = // // <3>
            import pillars.*
            for
                _ <- logger.info(s"📚 Welcome to ${pillars.config.name}!")
                _ <- pillars.whenEnabled(flag"feature-1"):
                         pillars.db.use: s =>
                             for
                                 d <- s.unique(sql"select now()".query(timestamptz))
                                 _ <- logger.info(s"The current date is $d.")
                             yield ()
                _ <- pillars.apiServer.start(endpoints.all)
            yield ()
            end for
        end run
end Main
// end::quick-start[]