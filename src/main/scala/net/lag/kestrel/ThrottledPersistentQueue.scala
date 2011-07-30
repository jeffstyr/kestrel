package main.scala.net.lag.kestrel

import com.twitter.util.{Duration, Timer, Time}
import net.lag.kestrel.config.QueueConfig
import net.lag.kestrel.{QItem, PersistentQueue}

/**
 * Created by IntelliJ IDEA. User: jac, Date: 7/30/11 12:47 AM
 */

class ThrottledPersistentQueue(quota: Int, delay : Duration, name: String, persistencePath: String, config: QueueConfig,
                      timer: Timer, queueLookup: Option[(String => Option[PersistentQueue])]) extends DelayedPersistentQueue(delay, name, persistencePath, config, timer, queueLookup) {

  //initialize such that things are reset at the first remove(), rather than at queue-creation time
  private var lastReset = Time.now - delay
  private var count = quota

  override def remove(transaction: Boolean) : Option[QItem] = {
    synchronized {
      val itemOption = super.remove(transaction)
      itemOption.foreach(item => {count = count + 1; /*System.out.println("count = " + count)*/})
      return itemOption
    }
  }

  override protected def isAvailable(item : QItem) : Boolean = {
    count < quota || resetIfTime()
  }

  private def resetIfTime() = {
    val now = Time.now
    if( now - lastReset > delay ) {
      count = 0
      lastReset = now
      true
    } else {
      false
    }
  }

  override protected def waitUntil(item : QItem) : Time = {
    lastReset + delay
  }

/*
  override protected def remainingDelay(item : QItem) : Duration = {
    delay - (Time.now - lastReset)
  }
*/

}