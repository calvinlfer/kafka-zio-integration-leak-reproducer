ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.1"

lazy val root = (project in file("."))
  .settings(
    name := "Fs2kafkaZIO2MemoryLeak",
    libraryDependencies ++= {
      Seq(
        "dev.zio"         %% "zio"               % "2.0.21",
        "dev.zio"         %% "zio-interop-cats"  % "23.1.0.0",
        "dev.zio"         %% "zio-kafka"         % "2.7.2",
        "dev.zio"         %% "zio-logging-slf4j" % "2.2.0",
        "com.github.fd4s" %% "fs2-kafka"         % "3.2.0",
        "ch.qos.logback"   % "logback-classic"   % "1.4.14"
      )
    }
  )
