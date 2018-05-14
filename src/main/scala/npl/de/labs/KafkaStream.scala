package npl.de.labs

import java.util.concurrent.Executors

import com.paulgoldbaum.influxdbclient.{InfluxDB, Point}
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.apache.avro.io.{DatumReader, Decoder, DecoderFactory}
import org.apache.avro.specific.SpecificDatumReader
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringDeserializer}
import org.apache.spark.SparkConf
import org.apache.spark.internal.Logging
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.streaming.kafka010.KafkaUtils
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.json4s._
import org.json4s.native.JsonMethods._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor}
import scala.io.Source

object KafkaStream extends App with Logging {

  //read avro schema file
  val schemaString = Source.fromURL(getClass.getResource("/event.avsc")).mkString
  val schema: Schema = new Schema.Parser().parse(schemaString)
  val avroReader: DatumReader[GenericRecord] = new SpecificDatumReader[GenericRecord](schema)

  val kafkaParams = Map[String, Object](
    "bootstrap.servers" -> "instance-1.c.spheric-vine-200212.internal:6667",
    "key.deserializer" -> classOf[StringDeserializer],
    "value.deserializer" -> classOf[ByteArrayDeserializer],
    "group.id" -> "spark-reader",
    "auto.offset.reset" -> "latest",
    "enable.auto.commit" -> (false: java.lang.Boolean)
  )

  val topics = Array("de.andrei.malov")

  val conf = new SparkConf().setAppName("KafkaSparkGrafana")
  val ssc = new StreamingContext(conf, Seconds(5))

  val stream = KafkaUtils.createDirectStream[String, Array[Byte]](
    ssc,
    PreferConsistent,
    Subscribe[String, Array[Byte]](topics, kafkaParams)
  )

  implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(4))

  val influxDb = InfluxDB.connect(host = "35.187.190.106", port = 8086, username = "test", password = "test").selectDatabase("grafana")

  def decodeAvro(message: Array[Byte]) = {
    // Deserialize and create generic record
    val decoder: Decoder = DecoderFactory.get().binaryDecoder(message, null)
    avroReader.read(null, decoder).toString
  }

  def sendToInflux(counter: (String, Int)) = {
    Await.result(influxDb.write(Point("words").addField("token", counter._1).addField("cnt", counter._2)),100.milliseconds)
  }



  stream.map(record => {
    parse(decodeAvro(record.value())) \ "location"
  }).
    map {
      case JString(loc) => Some(new java.net.URI(loc).getPath)
      case x => None
    }.
    map {
      case Some(word) =>
        print(">>> got WORD:  ", word)
        (word, 1)
      case None => ("", 0)
    }.
    reduceByKey(_ + _).
    filter {
      case (_, cnt) => cnt > 0
    }.
    map(sendToInflux).
    map(print(">>> reduced results:  ", _)).
    print()

  ssc.start()
  ssc.awaitTerminationOrTimeout(timeout = args(0).toInt*1000)

}
