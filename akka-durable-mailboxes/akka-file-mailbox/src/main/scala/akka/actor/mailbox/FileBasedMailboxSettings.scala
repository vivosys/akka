/**
 * Copyright (C) 2009-2012 Typesafe Inc. <http://www.typesafe.com>
 */
package akka.actor.mailbox

import com.typesafe.config.Config
import akka.util.Duration
import java.util.concurrent.TimeUnit.MILLISECONDS
import akka.actor.ActorSystem

class FileBasedMailboxSettings(val systemSettings: ActorSystem.Settings, val userConfig: Config) extends DurableMailboxSettings {

  def name = "file-based"

  val config = initialize

  import config._

  val QueuePath = getString("directory-path")
  val ArchivePath = getString("archive-directory-path")
  val UseArchive = getBoolean("use-archive")
  val UseChecksum = getBoolean("use-checksum")
  val MaxFileLength = getInt("max-file-length")
  val DisposeInterval = getMilliseconds("dispose-interval")
  val MaxWriteBatchSize = getBytes("max-write-batch-size").toInt
  val SyncInterval = getLong("sync-interval")
  val UsePhysicalSync = getBoolean("use-physical-sync")
}