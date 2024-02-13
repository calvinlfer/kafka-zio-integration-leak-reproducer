package com.kaizensolutions.experiments

import org.apache.kafka.clients.producer.{ProducerConfig, ProducerRecord}
import zio.*
import zio.kafka.consumer.*
import zio.kafka.consumer.Consumer.*
import zio.kafka.consumer.diagnostics.Diagnostics
import zio.kafka.producer.*
import zio.kafka.serde.Serde
import zio.logging.backend.SLF4J
import zio.stream.ZStream

// No leak
object ReproducerZIOKafka extends ZIOAppDefault:
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers ++ SLF4J.slf4j

  val topic = "example-topic"

  val consumerSettings: ConsumerSettings =
    ConsumerSettings(List("localhost:9092"))
      .withGroupId("zio-kafka-consumer-group")
      .withClientId("zio-kafka-client")
      .withOffsetRetrieval(OffsetRetrieval.Auto(AutoOffsetStrategy.Earliest))

  val consumer: ZStream[Consumer, Throwable, Nothing] =
    Consumer
      .plainStream(
        subscription = Subscription.topics(topic),
        keyDeserializer = Serde.string,
        valueDeserializer = Serde.string
      )
      .mapChunks(_.map(_.offset))
      .groupedWithin(2048, 10.seconds)
      .map(_.foldLeft(OffsetBatch.empty)(_ add _))
      .mapZIO(_.commit)
      .drain

  val producerSettings: ProducerSettings =
    ProducerSettings(List("localhost:9092"))
      .withClientId("zio-kafka-producer")
      .withProperties(
        Map(
          ProducerConfig.RETRIES_CONFIG            -> "0",
          ProducerConfig.BATCH_SIZE_CONFIG         -> "128",
          ProducerConfig.ACKS_CONFIG               -> "1",
          ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG -> "false"
        )
      )

  val producer: ZStream[Producer, Throwable, Nothing] =
    ZStream
      .iterate(0L)(_ + 1L)
      .map(n => new ProducerRecord(topic, s"key: $n", s"value: $n"))
      .chunks
      .mapZIO(records => Producer.produceChunk(records, Serde.string, Serde.string))
      .drain

  override val run =
    ZStream
      .mergeAllUnbounded()(producer, consumer)
      .runDrain
      .provide(
        ZLayer.succeed(Diagnostics.NoOp),
        ZLayer.succeed(producerSettings),
        Producer.live,
        ZLayer.succeed(consumerSettings),
        Consumer.live
      )
