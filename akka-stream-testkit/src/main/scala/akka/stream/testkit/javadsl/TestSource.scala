/**
 * Copyright (C) 2015-2016 Typesafe Inc. <http://www.typesafe.com>
 */
package akka.stream.testkit.javadsl

import akka.actor.ActorSystem
import akka.stream.javadsl.Source
import akka.stream.testkit._

/** Java API */
object TestSource {

  /**
   * A Source that materializes to a [[TestPublisher.Probe]].
   */
  def probe[T](system: ActorSystem): Source[T, TestPublisher.Probe[T]] =
    new Source(scaladsl.TestSource.probe[T](system))

}
