// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package pillars

import io.github.iltotore.iron.*
import pillars.PillarsError.Code
import pillars.PillarsError.ErrorNumber
import pillars.PillarsError.Message
object graph:
    extension [T](items: Seq[T])
        def topologicalSort(dependencies: T => Iterable[T]): Either[GraphError, List[T]] =
            @annotation.tailrec
            def loop(
                remaining: Iterable[T],
                sorted: List[T],
                visited: Set[T],
                recursionStack: Set[T]
            ): Either[GraphError, List[T]] =
                if remaining.isEmpty then Right(sorted)
                else
                    val (allDepsResolved, hasUnresolvedDeps) = remaining.partition: value =>
                        dependencies(value).forall(visited.contains)
                    if allDepsResolved.isEmpty then
                        if hasUnresolvedDeps.exists(recursionStack.contains) then
                            Left(GraphError.CyclicDependencyError)
                        else loop(hasUnresolvedDeps, sorted, visited, recursionStack ++ hasUnresolvedDeps)
                    else
                        loop(
                          hasUnresolvedDeps,
                          sorted ++ allDepsResolved.toList,
                          visited ++ allDepsResolved.toSet,
                          recursionStack
                        )
                    end if
                end if
            end loop

            val missing = items.flatMap(dependencies).toSet -- items.toSet
            if missing.nonEmpty then Left(GraphError.MissingDependency(missing))
            else loop(items, List.empty, Set.empty, Set.empty)
    end extension

    enum GraphError(val number: ErrorNumber) extends PillarsError:
        override def code: Code = Code("GRAPH")

        case CyclicDependencyError                 extends GraphError(ErrorNumber(1))
        case MissingDependency[T](missing: Set[T]) extends GraphError(ErrorNumber(2))

        override def message: Message = this match
            case GraphError.CyclicDependencyError      => Message("Cyclic dependency found")
            case GraphError.MissingDependency(missing) =>
                if missing.size == 1 then Message.assume(s"Missing dependency: ${missing.head}")
                else Message.assume(s"${missing.size} missing dependencies: ${missing.mkString(", ")}")
    end GraphError
end graph
