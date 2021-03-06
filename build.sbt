name := "Spark2Grafana"

version := "0.1"

//scalaVersion := "2.12.4"
scalaVersion := "2.11.12"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-streaming-kafka-0-10" % "2.3.0",
  "org.apache.spark" %% "spark-streaming" % "2.3.0" % "provided",
  "org.apache.avro" % "avro" % "1.8.2",
  "org.json4s" %% "json4s-native" % "3.5.3",
  "org.influxdb" % "influxdb-java" % "2.10"
)

assemblyMergeStrategy in assembly := {
  case PathList("org", "apache", xs@_*) => MergeStrategy.last
  case "META-INF/io.netty.versions.properties" => MergeStrategy.last
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

assemblyJarName in assembly := "spark2grafana.jar"

mainClass in assembly := Some("npl.de.labs.KafkaStream")