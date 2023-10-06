package com.iac.soc.backend.pipeline.models

/**
  * Represents an ingestor
  */
sealed trait Ingestor {
  def id: Int
}

/**
  * Represents a lumberjack ingestor
  *
  * @param id   the id of the ingestor
  * @param port the port lumberjack ingestor must listen on
  */
case class LumberjackIngestor(id: Int, port: Int) extends Ingestor

/**
  * Represents a syslog ingestor
  *
  * @param id       the id of the ingestor
  * @param protocol the protocol syslog ingestor must listen on
  * @param port     the port syslog ingestor must listen on
  */
case class SyslogIngestor(id: Int, protocol: String, port: Int) extends Ingestor

/**
+ * Represents a S3 ingestor
+ *
+ * @param id        the id of the ingestor
+ * @param accessKey the accessKey of AWS account
+ * @param secretKey the secretKey of AWS account
+ * @param region    the region of S3 bucket
+ * @param bucket    the name of S3 bucket
+ */
case class S3Ingestor(id: Int, accessKey: String, secretKey: String, region: String, bucket: String, bucketPrefix: String, skipHeaders: Boolean, isGzipped: Boolean) extends Ingestor