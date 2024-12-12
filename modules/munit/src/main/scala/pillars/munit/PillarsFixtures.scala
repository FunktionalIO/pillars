package pillars.munit

import cats.effect.IO
import cats.effect.Resource
import munit.CatsEffectFixtures
import munit.Fixture
import munit.Location
import munit.TestOptions
import munit.catseffect.IOFixture
import pillars.ModuleSupport
import pillars.Pillars

trait PillarsFixtures(modules: ModuleSupport*) extends CatsEffectFixtures:
    def apply(): IOFixture[Pillars[IO]] = ResourceTestLocalFixture(
      name = "Pillars",
        resource = ???,
    )
