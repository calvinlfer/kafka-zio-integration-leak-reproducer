package com.kaizensolutions.experiments

import cats.effect.*
import fs2.Stream
import fs2.kafka.*

import scala.concurrent.duration.*

object ReproducerFS2KafkaCatsEffect extends IOApp:
  val topic = "example-topic"

  def consumer[F[_]: Async]: Stream[F, Unit] =
    val settings =
      ConsumerSettings[F, String, String]
        .withBootstrapServers("localhost:9092")
        .withGroupId("test-consumer-group-id")
        .withAutoOffsetReset(AutoOffsetReset.Earliest)

    KafkaConsumer
      .stream[F, String, String](settings)
      .evalTap(_.subscribeTo(topic))
      .stream
      .mapChunks(_.map(_.offset))
      .through(commitBatchWithin[F](2048, 10.seconds))

  def producer[F[_]: Async]: Stream[F, ProducerResult[String, String]] =
    val settings =
      ProducerSettings[F, String, String]
        .withBootstrapServers("localhost:9092")
        .withRetries(0)
        .withBatchSize(128)
        .withAcks(Acks.One)
        .withEnableIdempotence(false)

    val producerPipe = KafkaProducer.pipe[F, String, String](settings)

    Stream
      .iterate[F, Long](0L)(_ + 1L)
      .map(n => ProducerRecord(topic = topic, key = s"key: $n", value = s"value: $n"))
      .chunkN(n = 128, allowFewer = true)
      .map(ProducerRecords[fs2.Chunk, String, String])
      .through(producerPipe)

  def app[F[_]: Async] = Stream(consumer[F], producer[F]).parJoinUnbounded.compile.drain

  override def run(args: List[String]): IO[ExitCode] =
    app[IO].as(ExitCode.Error)
