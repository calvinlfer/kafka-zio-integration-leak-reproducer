package com.kaizensolutions.experiments

import fs2.Stream
import fs2.kafka.*
import zio.{durationInt as _, *}
import zio.interop.catz.*
import zio.logging.backend.SLF4J

import scala.concurrent.duration.*

// Leak
object ReproducerFs2KafkaZIO extends ZIOAppDefault:
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers ++ SLF4J.slf4j

  val topic = "example-topic"

  val consumer: Stream[Task, Unit] =
    val settings =
      ConsumerSettings[Task, String, String]
        .withBootstrapServers("localhost:9092")
        .withGroupId("test-consumer-group-id")
        .withAutoOffsetReset(AutoOffsetReset.Earliest)
        .withPollInterval(5.millis)

    KafkaConsumer
      .stream[Task, String, String](settings)
      .evalTap(_.subscribeTo(topic))
      .stream
      .mapChunks(_.map(_.offset))
      .through(commitBatchWithin[Task](2048, 10.seconds))

  val producer: Stream[Task, ProducerResult[String, String]] =
    val settings =
      ProducerSettings[Task, String, String]
        .withBootstrapServers("localhost:9092")
        .withRetries(0)
        .withBatchSize(128)
        .withAcks(Acks.One)
        .withEnableIdempotence(false)

    val producerPipe = KafkaProducer.pipe[Task, String, String](settings)

    Stream
      .iterate[Task, Long](0L)(_ + 1L)
      .map(n => ProducerRecord(topic = topic, key = s"key: $n", value = s"value: $n"))
      .chunkN(n = 128, allowFewer = true)
      .map(ProducerRecords[fs2.Chunk, String, String])
      .through(producerPipe)

  override val run: Task[Unit] =
    Stream(consumer, producer).parJoinUnbounded.compile.drain
