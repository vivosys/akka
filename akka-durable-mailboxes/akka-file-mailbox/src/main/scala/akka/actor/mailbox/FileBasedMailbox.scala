/**
 *  Copyright (C) 2009-2012 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.actor.mailbox

import akka.actor.ActorContext
import akka.dispatch.{ Envelope, MessageQueue }
import akka.event.Logging
import akka.actor.ActorRef
import akka.dispatch.MailboxType
import com.typesafe.config.Config
import akka.util.NonFatal
import akka.config.ConfigurationException
import akka.actor.ActorSystem
import java.io.IOException
import journal.io.api.{ Journal, Location }
import java.lang.{ Iterable ⇒ JIterable }
import java.util.{ Iterator ⇒ JIterator }

class FileBasedMailboxType(systemSettings: ActorSystem.Settings, config: Config) extends MailboxType {
  private val settings = new FileBasedMailboxSettings(systemSettings, config)
  override def create(owner: Option[ActorContext]): MessageQueue = owner match {
    case Some(o) ⇒ new FileBasedMessageQueue(o, settings)
    case None    ⇒ throw new ConfigurationException("creating a durable mailbox requires an owner (i.e. does not work with BalancingDispatcher)")
  }
}

class FileBasedMessageQueue(_owner: ActorContext, val settings: FileBasedMailboxSettings) extends DurableMessageQueue(_owner) with DurableMessageSerialization {

  class AkkaJournal(name: String, settings: FileBasedMailboxSettings) extends Journal {

    val dir = new java.io.File(settings.QueuePath)
    if (!dir.exists) dir.mkdirs()
    if (!dir.isDirectory) throw new IOException("Couldn't create mailbox directory '" + dir + "'")

    this setDirectory dir
    this setArchiveFiles settings.UseArchive
    this setChecksum settings.UseChecksum
    this setMaxFileLength settings.MaxFileLength
    this setDirectoryArchive new java.io.File(settings.ArchivePath)
    this setDisposeInterval settings.DisposeInterval
    this.setFilePrefix(name)
    this.setFileSuffix(".mbox")
    this setMaxWriteBatchSize settings.MaxWriteBatchSize
    this setPhysicalSync settings.UsePhysicalSync
    //journal.setReplicationTarget(system.dynamicAccess.createInstanceFor())

    private var currentRead: JIterator[Location] = redo().iterator()

    def dequeue(): Array[Byte] = {
      if (!currentRead.hasNext)
        currentRead = redo().iterator()

      if (currentRead.hasNext) {
        val data = read(currentRead.next(), Journal.ReadType.SYNC) // TODO read async?
        currentRead.remove() // Remove the entry once it's out
        data
      } else null
    }

    def enqueue(data: Array[Byte]) {
      write(data, Journal.WriteType.SYNC) //TODO write sync?
    }
  }

  val log = Logging(system, "FileBasedMessageQueue")

  private val journal = try {
    val journal = new AkkaJournal(name, settings)
    journal.open()
    journal
  } catch {
    case NonFatal(e) ⇒
      log.error(e, "Could not create a file-based mailbox")
      throw e
  }

  def enqueue(receiver: ActorRef, envelope: Envelope): Unit = journal.enqueue(serialize(envelope))

  def dequeue(): Envelope = journal.dequeue() match {
    case null ⇒ null
    case some ⇒ deserialize(some)
  }

  def numberOfMessages: Int = 0

  def hasMessages: Boolean = numberOfMessages > 0

  def cleanUp(owner: ActorContext, deadLetters: MessageQueue): Unit = ()
}
