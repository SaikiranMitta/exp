/**
  * Project details
  */

name := "backend"
version := "1.0"
organization := "com.iac.soc"

/**
  * Project dependencies
  */

val keycloakVersion = "4.8.3.Final"

libraryDependencies ++= {

  // Versions of libraries
  val typeSafeConfigVersion = "1.3.3"
  val scalaLoggingVersion = "3.9.2"
  val log4j2Version = "2.11.2"
  val akkaVersion = "2.5.23"
  val akkaHttpVersion = "10.1.8"
  val camelVersion = "3.0.0-M1"
  val javaxApiVersion = "2.3.0"
  val javaxActivationVersion = "1.2.0"
  val javaxMailVersion = "1.6.2"
  val kuduVersion = "1.9.0"
  val scaLikeJDBCVersion = "3.3.2"
  val mysqlDriverVersion = "8.0.15"
  val graalvmVersion = "1.0.0-rc13"
  val javaGrokVersion = "0.1.9"
  val scalateVersion = "1.9.1"
  val akkaStreamKafkaVersion = "1.0.4"
  val geoLocationVersion = "1.2.2"
  val quartzVersion = "2.3.1"
  val akkaManagementVersion = "1.0.1"
  val awsVersion = "1.11.515"

  // List of libraries
  Seq(

    // Typesafe configuration library
    "com.typesafe" % "config" % typeSafeConfigVersion,

    // Logging libraries
    // (scala-logging is wrapper over SLF4J, Log4j2 is underlying implementation used)
    "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion,
    "org.apache.logging.log4j" % "log4j-api" % log4j2Version,
    "org.apache.logging.log4j" % "log4j-core" % log4j2Version,
    "org.apache.logging.log4j" % "log4j-slf4j-impl" % log4j2Version,

    // ScalaPB libraries
    "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
    "com.thesamet.scalapb" %% "scalapb-json4s" % "0.7.2",
    "fr.davit" %% "akka-http-scalapb" % "0.1.0",
    "fr.davit" %% "akka-http-scalapb-json4s" % "0.1.0",

    // Akka libraries
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
    "com.typesafe.akka" %% "akka-distributed-data" % akkaVersion,
    "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" % "akka-stream-kafka_2.12" % akkaStreamKafkaVersion,
    "com.typesafe.akka" %% "akka-discovery" % akkaVersion,
    "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % akkaManagementVersion,
    "com.lightbend.akka.management" %% "akka-management" % akkaManagementVersion,
    "com.lightbend.akka.management" %% "akka-management-cluster-http" % akkaManagementVersion,
    "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % akkaManagementVersion,

    // Apache Camel libraries
    "org.apache.camel" % "camel-core" % camelVersion,
    "org.apache.camel" % "camel-lumberjack" % camelVersion,
    "org.apache.camel" % "camel-jackson" % camelVersion,
    "org.apache.camel" % "camel-syslog" % camelVersion,
    "org.apache.camel" % "camel-netty4" % camelVersion,
    "org.apache.camel" % "camel-log" % camelVersion,
    "org.apache.camel" % "camel-http4" % camelVersion,
    "org.apache.camel" % "camel-quartz2" % camelVersion,
    "org.apache.camel" % "camel-aws" % camelVersion,
    "org.apache.camel" % "camel-kafka" % camelVersion,

    // Javax libraries
    "javax.xml.bind" % "jaxb-api" % javaxApiVersion,
    "com.sun.activation" % "javax.activation" % javaxActivationVersion,
    "com.sun.mail" % "javax.mail" % javaxMailVersion,

    // Apache Kudu library
    "org.apache.kudu" % "kudu-client" % kuduVersion,

    "mysql" % "mysql-connector-java" % mysqlDriverVersion,
    "org.scalikejdbc" %% "scalikejdbc" % scaLikeJDBCVersion,
    "org.scalikejdbc" %% "scalikejdbc-config" % scaLikeJDBCVersion,
    "io.prestosql" % "presto-jdbc" % "305",

    // Graal VM
    "org.graalvm.sdk" % "graal-sdk" % graalvmVersion,
    "org.graalvm.js" % "js" % graalvmVersion,
    "org.graalvm.js" % "js-scriptengine" % graalvmVersion,
    "org.graalvm.tools" % "profiler" % graalvmVersion,
    "org.graalvm.tools" % "chromeinspector" % graalvmVersion,
    "io.krakens" % "java-grok" % javaGrokVersion,

    // Scalate library
    "org.scalatra.scalate" %% "scalate-core" % scalateVersion,

    // Max Mind library
    "com.maxmind.db" % "maxmind-db" % geoLocationVersion,

    // Quartz library
    "org.quartz-scheduler" % "quartz" % quartzVersion,

    // AWS SDK libraries
    "com.amazonaws" % "aws-java-sdk" % awsVersion

  )

}

val currentDirectory = new java.io.File(".").getCanonicalPath
libraryDependencies += "org.aspectj" % "aspectjrt" % "1.9.2" from ("file://" + currentDirectory + "/lib/akka-cluster-custom-downing-assembly-0.0.12.jar")

val keycloak = Seq(
  "org.keycloak" % "keycloak-adapter-core" % keycloakVersion,
  "org.keycloak" % "keycloak-core" % keycloakVersion,
  "org.keycloak" % "keycloak-admin-client" % keycloakVersion,

  "org.jboss.logging" % "jboss-logging" % "3.3.0.Final",
  "org.jboss.logging" % "jboss-logging-annotations" % "2.1.0.Final" % "provided",
  "org.jboss.logging" % "jboss-logging-processor" % "2.1.0.Final" % "provided",

  "org.jboss.resteasy" % "resteasy-client" % "3.0.24.Final" excludeAll(
    ExclusionRule("junit", "junit"),
    ExclusionRule("org.jboss.logging"),
    ExclusionRule("net.jcip"),
    ExclusionRule("org.jboss.spec.javax.ws.rs"),
    ExclusionRule("org.jboss.spec.javax.servlet"),
    ExclusionRule("org.jboss.spec.javax.annotation"),
    ExclusionRule("javax.activation"),
    ExclusionRule("commons-io"),
    ExclusionRule("org.apache.httpcomponents")),

  "org.jboss.resteasy" % "resteasy-jaxrs" % "3.0.24.Final" excludeAll(
    ExclusionRule("junit", "junit"),
    ExclusionRule("org.jboss.logging"),
    ExclusionRule("net.jcip"),
    ExclusionRule("org.jboss.spec.javax.ws.rs"),
    ExclusionRule("org.jboss.spec.javax.servlet"),
    ExclusionRule("org.jboss.spec.javax.annotation"),
    ExclusionRule("javax.activation"),
    ExclusionRule("commons-io"),
    ExclusionRule("org.apache.httpcomponents")),

  "org.jboss.resteasy" % "resteasy-jackson2-provider" % "3.0.24.Final" excludeAll(
    ExclusionRule("com.fasterxml.jackson.jaxrs"),
    ExclusionRule("org.jboss.spec.javax.servlet")
  ),

  "org.jboss.spec.javax.annotation" % "jboss-annotations-api_1.2_spec" % "1.0.2.Final",

  "com.fasterxml.jackson.jaxrs" % "jackson-jaxrs-json-provider" % "2.9.8",
  "org.apache.httpcomponents" % "httpclient" % "4.5.1",
  "javax.ws.rs" % "javax.ws.rs-api" % "2.0",
  "commons-io" % "commons-io" % "2.6")

libraryDependencies ++= keycloak


/**
  * Additional dependency resolvers for the project
  */
resolvers ++= Seq(
  "Apache public" at "https://repository.apache.org/content/groups/public/"
)
resolvers += Resolver.bintrayRepo("tanukkii007", "maven")

/**
  * Scan main scala folder for protobuf files, instead of default src/main/protobuf
  */
PB.protoSources in Compile := Seq(file("src/main/scala"))

/**
  * Generate scalaPB files during compile time
  */
PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value
)

/**
  * Packaging options
  */
enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)

mappings.in(Universal) +=
  ((sourceDirectory.value / "main" / "resources" / "GeoLite2-City.mmdb"), "resources/GeoLite2-City.mmdb")
mappings.in(Universal) +=
  ((sourceDirectory.value / "main" / "resources" / "iac-wildcard.jks"), "resources/certs/iac-wildcard.jks")

assemblyMergeStrategy in assembly := {
 case PathList("META-INF", _*) => MergeStrategy.discard
 case _                        => MergeStrategy.first
}

mainClass in Compile := Some("com.iac.soc.backend.Main")
