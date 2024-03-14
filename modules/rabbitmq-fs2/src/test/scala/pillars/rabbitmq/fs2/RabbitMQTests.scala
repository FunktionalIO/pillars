package pillars.rabbitmq.fs2

import cats.effect.IO
import com.comcast.ip4s.*
import com.dimafeng.testcontainers.RabbitMQContainer
import com.dimafeng.testcontainers.munit.TestContainerForEach
import dev.profunktor.fs2rabbit.model.*
import io.circe.Decoder
import munit.Assertions
import munit.CatsEffectSuite
import org.testcontainers.utility.DockerImageName
import org.typelevel.otel4s.trace.Tracer
import pillars.Module
import pillars.Pillars
import pillars.rabbitmq.*

class RabbitMQTests extends CatsEffectSuite, TestContainerForEach:

    private val exchange   = ExchangeName("testExchangeName")
    private val routingKey = RoutingKey("testRoutingKey")
    private val queue      = QueueName("testQueue")

    override val containerDef: RabbitMQContainer.Def = RabbitMQContainer.Def(
      users = Seq(RabbitMQContainer.User("pillars", "pillars")),
      exchanges = Seq(RabbitMQContainer.Exchange(exchange.value, "direct")),
      queues = Seq(RabbitMQContainer.Queue(queue.value)),
      bindings = Seq(RabbitMQContainer.Binding(exchange.value, queue.value, routingKey.value))
    )

    given Pillars[IO] = new Pillars[IO]:
        def observability                   = ???
        def config                          = ???
        def apiServer                       = ???
        def logger                          = scribe.cats.io
        def readConfig[T](using Decoder[T]) = ???
        def module[T](key: Module.Key): T   = ???

    given Tracer[IO] = Tracer.noop[IO]

    private def configFor(container: RabbitMQContainer): RabbitMQConfig = RabbitMQConfig(
      host = Host.fromString(container.host).get,
      port = Port.fromInt(container.container.getMappedPort(5672)).get
    )

    test("allow exchanging messages"):
        withContainers { container =>
            for
                client <- RabbitMQ[IO](configFor(container)).map(_.client)
                _      <- client.createConnectionChannel.evalMap { implicit channel =>
                              for
                                  publisher  <- client.createPublisher[String](exchange, routingKey)
                                  _          <- publisher("test message")
                                  subscriber <- client.createAutoAckConsumer[String](queue)
                                  out        <- subscriber.head.compile.onlyOrError
                              yield Assertions.assertEquals(out.payload, "test message")
                          }
            yield ()
            end for
        }
end RabbitMQTests
