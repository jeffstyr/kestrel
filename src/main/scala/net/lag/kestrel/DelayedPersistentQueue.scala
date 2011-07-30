package main.scala.net.lag.kestrel

import net.lag.kestrel.{QItem, PersistentQueue}
import net.lag.kestrel.config.QueueConfig
import com.twitter.util.{TimerTask, Timer, Duration, Time}

/**
 * Created by IntelliJ IDEA. User: jclites, Date: 7/3/2011 10:27 AM
 */

class DelayedPersistentQueue(delay : Duration, name: String, persistencePath: String, config: QueueConfig,
                      timer: Timer, queueLookup: Option[(String => Option[PersistentQueue])]) extends PersistentQueue(name, persistencePath, config, timer, queueLookup) {

  private var hasPendingAwakeTimer = false
  //private var timerTask : TimerTask;

  override def remove(transaction: Boolean) : Option[QItem] = {
    synchronized {
      peek.flatMap { item =>
        super.remove(transaction)
      }
    }
  }

  private def _headItem: Option[QItem] = {
    super.peek
  }

  override def peek() : Option[QItem] = {
    synchronized {
       _headItem.flatMap { item =>
         if( isAvailable(item) ) Some(item) else { scheduleTrigger(item); None }
         //val remainingDelay = delay - (Time.now - item.addTime)
         //if(remainingDelay <= 0) Some(item) else { scheduleTrigger(remainingDelay); None }
       }
    }
  }

  protected def isAvailable(item : QItem) : Boolean = {
    waitUntil(item) <= Time.now
  }

/*
  protected def remainingDelay(item : QItem) : Duration = {
    delay - (Time.now - item.addTime)
  }
*/

  protected def waitUntil(item : QItem) : Time = {
    item.addTime  + delay
  }

  protected def scheduleTrigger(item : QItem) {
    synchronized {
      if( !hasPendingAwakeTimer ) {
        hasPendingAwakeTimer = true
        //System.out.println("Calling timer.schedule()")
        /*timerTask =*/ timer.schedule(waitUntil(item)) {
          synchronized {
            while( waiterCount > 0 && peek.isDefined ) waiters.trigger();
            //System.out.println("Falsing hasPendingAwakeTimer")
            hasPendingAwakeTimer = false
            if( waiterCount > 0 ) _headItem.foreach(scheduleTrigger(_))
          }
        }
      }
    }
  }

  override def itemWasAdded(item : QItem): Unit = {
    if(isAvailable(item)) super.itemWasAdded(item)
    else synchronized { if(waiterCount > 0) scheduleTrigger(item) }
  }

}

/*
  override def remove(transaction: Boolean) : Option[QItem] = {
    synchronized {
      peek match {
        case Some(x) => if(Time.now - x.addTime > delay) super.remove(transaction) else None
        case None => None
      }
    }
  }

  override def peek() : Option[QItem] = {
    synchronized {
       super.peek.flatMap { item =>
         val remainingDelay = delay - (Time.now - item.addTime)
         if(remainingDelay <= 0) Some(item) else { scheduleTrigger(remainingDelay); None }
       }
    }
  }

  private def scheduleTrigger(remainingDelay : Duration) {
    synchronized {
      if( !hasPendingAwakeTimer ) {
        hasPendingAwakeTimer = true
        timerTask = timer.schedule(remainingDelay) {
          synchronized {
            while( waiters.size > 0 && peek.isDefined ) waiters.trigger();
            hasPendingAwakeTimer = false
            if( waiters.size > 0 ) peek //this will schedule a new timer iff there is anything in the queue
          }
        }
      }
    }
  }

  private def isAvailable(item : Option[QItem]) : Boolean = {
    item.exists(remainingDelay(_) <= 0)
  }

*/