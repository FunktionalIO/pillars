// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars.flags

import cats.effect.IO
import cats.syntax.all.*
import munit.CatsEffectSuite
import pillars.flags.*

class FeatureFlagsTests extends CatsEffectSuite:

    private val flag1               = FeatureFlag(flag"flag1", Status.Enabled)
    private val flag2               = FeatureFlag(flag"flag2", Status.Disabled)
    private val config: FlagsConfig = FlagsConfig(flags = List(flag1, flag2))

    test("FlagManager should return the correct flag"):
        val flag =
            for
                manager <- FeatureFlags.createManager(config)
                flag    <- manager.getFlag(flag"flag1")
            yield flag
        assertIO(flag, flag1.some)

    test("FlagManager should return None if flag is not found"):
        val flag =
            for
                manager <- FeatureFlags.createManager(config)
                flag    <- manager.getFlag(flag"undefined")
            yield flag
        assertIO(flag, none)

    test("FlagManager should return the correct flag status"):
        def isEnabled(flag: Flag) =
            for
                manager <- FeatureFlags.createManager(config)
                enabled <- manager.isEnabled(flag)
            yield enabled
        for
            _ <- assertIO(isEnabled(flag"flag1"), true)
            _ <- assertIO(isEnabled(flag"flag2"), false)
            _ <- assertIO(isEnabled(flag"undefined"), false)
        yield ()
        end for

    test("FlagManager should perform the action if flag is enabled"):
        var called    = false
        val performed =
            for
                manager <- FeatureFlags.createManager(config)
                _       <- manager.when(flag"flag1")(IO { called = true })
            yield called
        assertIO(performed, true)

    test("FlagManager should not perform the action if flag is disabled"):
        var called    = false
        val performed =
            for
                manager <- FeatureFlags.createManager(config)
                _       <- manager.when(flag"flag2")(IO { called = true })
            yield called
        assertIO(performed, false)

    test("FlagManager should not perform the action if flag is not found"):
        var called    = false
        val performed =
            for
                manager <- FeatureFlags.createManager(config)
                _       <- manager.when(flag"undefined")(IO { called = true })
            yield called
        assertIO(performed, false)

    test("FlagManager should correctly modify an existing flag"):
        val modified =
            for
                manager <- FeatureFlags.createManager(config)
                _       <- manager.setStatus(flag"flag1", Status.Disabled)
                flag    <- manager.getFlag(flag"flag1")
            yield flag
        assertIO(modified, flag1.copy(status = Status.Disabled).some)

    test("FlagManager should correctly return None if flag is not found"):
        val modified =
            for
                manager <- FeatureFlags.createManager(config)
                _       <- manager.setStatus(flag"undefined", Status.Disabled)
                flag    <- manager.getFlag(flag"undefined")
            yield flag
        assertIO(modified, none)

end FeatureFlagsTests
