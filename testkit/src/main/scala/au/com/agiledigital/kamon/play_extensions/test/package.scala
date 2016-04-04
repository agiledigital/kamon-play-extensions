package au.com.agiledigital.kamon.play_extensions

import kamon.trace.{EmptyTraceContext, TraceContext}

import scala.concurrent.{ExecutionContext, Future}

/**
 * Contains helpers to facilitate testing code dependent upon [[Metrics]].
 */
package object test {

  /**
   * Do nothing metrics implementation. Executes supplied code blocks directly on the calling thread.
   */
  val metrics = new Metrics {
    override def withNewAsyncContext[T](traceName: String, autoFinish: Boolean, count: Boolean)
                                       (code: => Future[T])
                                       (implicit executionContext: ExecutionContext): Future[T] = code

    override def clearCurrentContext(): Unit = ()

    override def setCurrentContext(context: TraceContext): Unit = ()

    override def currentContext: TraceContext = EmptyTraceContext

    override def withNewContext[T](traceName: String, traceToken: Option[String], autoFinish: Boolean)(code: => T): T = code

    override def withCategory(category: String): Metrics = this
  }

}
