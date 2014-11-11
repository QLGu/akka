/**
 * Copyright (C) 2014 Typesafe Inc. <http://www.typesafe.com>
 */
package akka.stream.impl

import akka.actor._
import akka.stream.StreamSubscriptionTimeoutTerminationMode.{ CancelTermination, NoopTermination, WarnTermination }
import akka.stream.StreamSubscriptionTimeoutSettings
import org.reactivestreams._
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NoStackTrace

object StreamSubscriptionTimeoutSupport {

  /**
   * A subscriber who calls `cancel` directly from `unSubscribe` and ignores all other callbacks.
   */
  final case object CancelingSubscriber extends Subscriber[Any] {
    override def onSubscribe(s: Subscription): Unit = s.cancel()
    override def onError(t: Throwable): Unit = ()
    override def onComplete(): Unit = ()
    override def onNext(t: Any): Unit = ()
  }

  /**
   * INTERNAL API
   *
   * Subscription timeout which does not start any scheduled events and always returns `true`.
   * This specialized implementation is to be used for "noop" timeout mode.
   */
  private final case object NoopSubscriptionTimeout extends Cancellable {
    override def cancel() = true
    override def isCancelled = true
  }
}

/**
 * Provides support methods to create Publishers and Subscribers which time-out gracefully,
 * and are cancelled subscribing an `CancellingSubscriber` to the publisher, or by calling `onError` on the timed-out subscriber.
 *
 * See `akka.stream.materializer.subscription-timeout` for configuration options.
 */
trait StreamSubscriptionTimeoutSupport {
  this: Actor with ActorLogging ⇒

  import StreamSubscriptionTimeoutSupport._

  /**
   * Default settings for subscription timeouts.
   */
  protected def subscriptionTimeoutSettings: StreamSubscriptionTimeoutSettings

  /**
   * Schedules a Subscription timeout.
   * The actor will receive the message created by the provided block if the timeout triggers.
   */
  protected def scheduleSubscriptionTimeout(actor: ActorRef, block: FiniteDuration ⇒ AnyRef): Cancellable =
    subscriptionTimeoutSettings.mode match {
      case NoopTermination ⇒
        NoopSubscriptionTimeout
      case _ ⇒
        val settings = subscriptionTimeoutSettings
        val timeout = settings.timeout
        implicit val dispatcher =
          if (settings.dispatcher.trim.isEmpty) context.dispatcher
          else context.system.dispatchers.lookup(settings.dispatcher)
        val cancellable = context.system.scheduler.scheduleOnce(timeout, actor, block(timeout))
        cancellable
    }

  private def cancel(target: AnyRef, timeout: FiniteDuration): Unit = target match {
    case p: Processor[_, _] ⇒
      log.debug("Cancelling {} Processor's publisher and subscriber sides (after {})", p, timeout)
      subscriptionTimeoutForTarget(target, new SubscriptionTimeoutException(s"Publisher was not attached to upstream within deadline (${timeout})") with NoStackTrace)

    case p: Publisher[_] ⇒
      log.debug("Cancelling {} (after: {})", p, timeout)
      subscriptionTimeoutForTarget(target, new SubscriptionTimeoutException(s"Publisher (${p}) you are trying to subscribe to has been shut-down " +
        s"because exceeding it's subscription-timeout.") with NoStackTrace)
  }

  private def warn(target: Any, timeout: FiniteDuration): Unit = {
    log.warning("Timed out {} detected (after {})! You should investigate if you either cancel or consume all {} instances",
      target, timeout, target.getClass.getCanonicalName)
  }

  /**
   * Called by the actor to handle the subscription timeout. Expects the actual `Publisher` or `Processor` target.
   */
  protected def handlSubscriptionTimeoutManagement(target: AnyRef, timeout: FiniteDuration): Unit =
    subscriptionTimeoutSettings.mode match {
      case NoopTermination   ⇒ // ignore...
      case WarnTermination   ⇒ warn(target, timeout)
      case CancelTermination ⇒ cancel(target, timeout)
    }

  /**
   * Callback that should ensure that the target is cancelled with the given cause.
   */
  protected def subscriptionTimeoutForTarget(target: AnyRef, cause: Exception): Unit
}

class SubscriptionTimeoutException(msg: String) extends RuntimeException(msg)
