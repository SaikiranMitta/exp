package com.iac.soc.backend.notification

import java.util.Properties

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import javax.mail._
import javax.mail.internet.{InternetAddress, MimeMessage}

/**
  * The mail agent utility used for sending emails
  */
private[notification] object MailAgent extends LazyLogging {

  /**
    * Get the application config
    */
  private[this] val config: Config = ConfigFactory.load()

  /**
    * Get the SMTP settings from configuration
    */
  private[this] val host: String = config.getString("smtp.host")
  private[this] val port: String = config.getInt("smtp.port").toString()
  private[this] val username: String = config.getString("smtp.username")
  private[this] val password: String = config.getString("smtp.password")
  private[this] val from_email: String = config.getString("smtp.from_email")

  println(s"Email ====================> ${host} ${port} ${username} ${from_email}")

  /**
    * Create the mail properties
    */
  private[this] val properties = new Properties()
  properties.put("mail.smtp.host", host)
  properties.put("mail.smtp.port", port)
  properties.put("mail.smtp.auth", "true")
  properties.put("mail.smtp.starttls.enable", "true")

  /**
    * Create the mail session object with the username and password provided
    */
  private[this] val session: Session = Session.getInstance(properties, new Authenticator {
    override def getPasswordAuthentication = new PasswordAuthentication(username, password)
  })

  /**
    * Sends an email to the provided address with the specified subject and body
    *
    * @param to      the address the email must be sent to
    * @param subject the subject of the email
    * @param body    the body of the email
    */
  def sendEmail(to: String, subject: String, body: String): Unit = {

    logger.info(s"Attempting to send email to: ${to} with subject: ${subject}")

    try {

      // Create the message
      val message = new MimeMessage(session)

      // Set the message details
      message.setFrom(new InternetAddress(from_email))
      message.setRecipients(Message.RecipientType.TO, to)
      message.setSubject(subject)
      message.setContent(body, "text/html")

      // Send the message
      Transport.send(message)

      logger.info(s"Sent email to: ${to} with subject: ${subject}")
//      logger.info(s"Temp stopped Sent email to: ${to} with subject: ${subject}")
    }
    catch {
      case e: Exception => logger.error(s"Error while sending email: ${e}")
    }

  }
}
