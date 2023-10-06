package com.iac.soc.backend.pipeline

import com.iac.soc.backend.Main.config
import com.iac.soc.backend.pipeline.models.{LumberjackIngestor, Normalizer, S3Ingestor, SyslogIngestor}
import com.typesafe.config.ConfigFactory

/**
  * The pipeline sub-system repository
  */
private[pipeline] object Repository {

  /**
    * Gets all the ingestors defined in the system
    *
    * @return the ingestors
    */
  def getIngestors: Vector[models.Ingestor] = {

    val accesskey = config.getString("s3.accesskey")
    val secretkey = config.getString("s3.secretkey")

    val dbaccesskey = config.getString("s3.dailbeast-accesskey")
    val dbsecretkey = config.getString("s3.dailbeast-secretkey")

    val disableS3Ingestors = config.getBoolean("s3.disabled")
    val minimizeS3Load = config.getBoolean("s3.minimize-s3-load")

    // Create dummy lumberjack and syslog ingestors
    val lumberjack = LumberjackIngestor(1, 5044)
    val syslog = SyslogIngestor(2, "tcp", 5055)
    val appsLumberjack = LumberjackIngestor(3, 8443)
    val mfaS3bucket = S3Ingestor(4, accesskey, secretkey, "us-east-2", "iacsoc-s3-bucket-angie-home-services", "homeadvisor-mfa-", skipHeaders = false, isGzipped = false)
    val cloudflareS3bucket = S3Ingestor(5, accesskey, secretkey, "us-east-2", "iacsoc-s3-bucket-angie-home-services", "homeadvisor/sumo/prod/cloudflare/", skipHeaders = false, isGzipped = true)

    val paloaltoS3bucket = S3Ingestor(6, accesskey, secretkey, "us-east-2", "iacsoc-s3-bucket-angie-home-services", "homeadvisor-palo-alto-", skipHeaders = false, isGzipped = false)
    val ciscoS3bucket = S3Ingestor(7, accesskey, secretkey, "us-east-2", "iacsoc-s3-bucket-angie-home-services", "homeadvisor-cisco-firewall-", skipHeaders = false, isGzipped = false)
    val f5S3bucket = S3Ingestor(8, accesskey, secretkey, "us-east-2", "iacsoc-s3-bucket-angie-home-services", "homeadvisor-syslog-f5-", skipHeaders = false, isGzipped = false)
    val networksyslogS3bucket = S3Ingestor(9, accesskey, secretkey, "us-east-2", "iacsoc-s3-bucket-angie-home-services", "homeadvisor-syslog-network-", skipHeaders = false, isGzipped = false)
    val sourcefireS3bucket = S3Ingestor(10, accesskey, secretkey, "us-east-2", "iacsoc-s3-bucket-angie-home-services", "homeadvisor-sourcefire-", skipHeaders = false, isGzipped = false)
    val logrythmS3bucket = S3Ingestor(11, accesskey, secretkey, "us-east-2", "iacsoc-s3-bucket-angie-home-services", "homeadvisor-logrhythm-", skipHeaders = false, isGzipped = false)
    val sumo1S3bucket = S3Ingestor(12, accesskey, secretkey, "us-east-2", "iacsoc-s3-bucket-angie-home-services", "homeadvisor/sumo/prod/api/", skipHeaders = false, isGzipped = true)
    val sumo2S3bucket = S3Ingestor(13, accesskey, secretkey, "us-east-2", "iacsoc-s3-bucket-angie-home-services", "homeadvisor/sumo/prod/cmt/", skipHeaders = false, isGzipped = true)
    val sumo3S3bucket = S3Ingestor(14, accesskey, secretkey, "us-east-2", "iacsoc-s3-bucket-angie-home-services", "homeadvisor/sumo/prod/coherence/", skipHeaders = false, isGzipped = true)
    val sumo4S3bucket = S3Ingestor(15, accesskey, secretkey, "us-east-2", "iacsoc-s3-bucket-angie-home-services", "homeadvisor/sumo/prod/site/", skipHeaders = false, isGzipped = true)
    val dbcloudflareS3bucket = S3Ingestor(16, dbaccesskey, dbsecretkey, "us-east-2", "cloudflare-logs-thedailybeast", "", skipHeaders = false, isGzipped = true)

    val main_config = ConfigFactory.load()
    val setupIngestor = main_config.getBoolean("setup.ingestor")


    // Return the ingestors
    // If S3 ingestors are disabled, don't return in collection of ingestors
    if (setupIngestor == true) {
      if (!disableS3Ingestors) {
        if (!minimizeS3Load)
          Vector(lumberjack, syslog, appsLumberjack, mfaS3bucket, cloudflareS3bucket, paloaltoS3bucket, ciscoS3bucket, f5S3bucket, networksyslogS3bucket, sourcefireS3bucket, logrythmS3bucket, sumo1S3bucket, sumo2S3bucket, sumo3S3bucket, sumo4S3bucket, dbcloudflareS3bucket)
        else
          Vector(lumberjack, syslog, appsLumberjack, mfaS3bucket, cloudflareS3bucket)
      }
      else {
        Vector(lumberjack, syslog, appsLumberjack)
      }
    }
    else {
      if (!minimizeS3Load)
        Vector(mfaS3bucket, cloudflareS3bucket, paloaltoS3bucket, ciscoS3bucket, f5S3bucket, networksyslogS3bucket, sourcefireS3bucket, logrythmS3bucket, sumo1S3bucket, sumo2S3bucket, sumo3S3bucket, sumo4S3bucket, dbcloudflareS3bucket)
      else
        Vector(mfaS3bucket, cloudflareS3bucket)
    }

  }

  /**
    * Get normalizers grouped by the ingestor ids
    *
    * @return the normalizers grouped by ingestor ids
    */
  def getNormalizersGroupedByIngestorIds: Map[Int, Vector[models.Normalizer]] = {

    // Create dummy normalizers for syslog (firewall, ad, endpoint)
    val firewallNormalizer = Normalizer(1, "Firewall", 2, """"type"\s*:\s*"firewall"""", "function normalize(log) { return log; }")
    val adNormalizer = Normalizer(1, "AD", 2, """"type"\s*:\s*"ad"""", "function normalize(log) { return log; }")
    val endpointNormalizer = Normalizer(1, "Endpoint", 2, """"type"\s*:\s*"endpoint"""", "function normalize(log) { return log; }")

    // Return the normalizers grouped by ingestor id
    Map(2 -> Vector(firewallNormalizer, adNormalizer, endpointNormalizer))
  }
}
