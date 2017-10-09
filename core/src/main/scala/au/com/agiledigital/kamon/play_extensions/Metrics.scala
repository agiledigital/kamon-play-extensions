package au.com.agiledigital.kamon.play_extensions

import javax.inject.{Inject, Singleton}

import kamon.Kamon
import kamon.trace.{TraceContext, TraceLocal, Tracer}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
  * Injectable wrapper around the Kamon Tracer singleton. Provides additional methods to automatically name
  * traces and to maintain a completion count of futures that succeed / fail.
  *
  * Supports creating new instances that categorise contexts created within them. Enables nesting traces metrics.
  */
trait Metrics {

  /**
    * Returns the current TraceContext. May return an empty trace context if not trace context is currently active.
    *
    * @return the current trace context.
    */
  def currentContext: TraceContext

  /**
    * Sets the current TraceContext.
    * @param context the context that should be set as the current trace context.
    */
  def setCurrentContext(context: TraceContext): Unit

  /**
    * Clears the current TraceContext.
    */
  def clearCurrentContext(): Unit

  /**
    * Creates a new trace context that wrap the supplied code and sets it as the current context.
    *
    * @param traceName the name of the trace.
    * @param traceToken the trace token.
    * @param autoFinish whether the trace should be finished when the code has been executed.
    * @param code the code to be executed within the context.
    * @tparam T the type of the result produced by the code.
    * @return the result produced by the code.
    */
  def withNewContext[T](traceName: String, traceToken: Option[String], autoFinish: Boolean)(code: ⇒ T): T

  /**
    * Creates a new trace context that wrap the supplied code and sets it as the current context.
    *
    * Does not autoFinish.
    *
    * @param traceName the name of the trace.
    * @param code the code to be executed within the context.
    * @tparam T the type of the result produced by the code.
    * @return the result produced by the code.
    */
  def withNewContext[T](traceName: String)(code: ⇒ T): T = withNewContext(traceName, None)(code)

  /**
    * Creates a new trace context that wrap the supplied code and sets it as the current context.
    *
    * Does not autoFinish.
    *
    * @param traceName the name of the trace.
    * @param traceToken the trace token.
    * @param code the code to be executed within the context.
    * @tparam T the type of the result produced by the code.
    * @return the result produced by the code.
    */
  def withNewContext[T](traceName: String, traceToken: Option[String])(code: ⇒ T): T =
    withNewContext(traceName, traceToken, autoFinish = false)(code)

  /**
    * Creates a new trace context that wrap the supplied code and sets it as the current context.
    *
    * @param traceName the name of the trace.
    * @param autoFinish whether the trace should be finished when the code has been executed.
    * @param code the code to be executed within the context.
    * @tparam T the type of the result produced by the code.
    * @return the result produced by the code.
    */
  def withNewContext[T](traceName: String, autoFinish: Boolean)(code: ⇒ T): T =
    withNewContext(traceName, None, autoFinish = autoFinish)(code)

  /**
    * Creates a new context wrapping the specified code named using the implicitly passed name.
    *
    * Auto-finishes.
    *
    * @param code the code that will be executed within the context.
    * @param name the implicitly supplied name of the caller.
    * @tparam T the type produced by the code block.
    * @return the result produced by the code.
    */
  def traced[T](code: => T)(implicit name: sourcecode.Name): T = withNewContext(name.value, autoFinish = true)(code)

  /**
    * Creates a new context wrapping code that produces a Future. If autoFinish is set to true, will finish the context
    * when the future completes.
    *
    * @param traceName the name of the trace to be started.
    * @param autoFinish whether the trace should be finished when the future completes.
    * @param count whether to count the number of futures that complete with success / failure.
    * @param code the code that will be executed within the context.
    * @param executionContext used to handle the onComplete callback.
    * @tparam T the type produced by the Future.
    * @return the Future produced by the code.
    */
  def withNewAsyncContext[T](traceName: String, autoFinish: Boolean, count: Boolean)(code: ⇒ Future[T])(implicit executionContext: ExecutionContext): Future[T]

  /**
    * Creates a new context wrapping the specified Future producing code named using the implicitly passed name.
    *
    * Auto-finishes and counts.
    *
    * @param code the code that will be executed within the context.
    * @param name the implicitly supplied name of the caller.
    * @tparam T the type produced by the Future.
    * @param executionContext used to handle the onComplete callback.
    * @return the Future produced by the code.
    */
  def tracedAsync[T](code: => Future[T])(implicit name: sourcecode.Name, executionContext: ExecutionContext): Future[T] =
    withNewAsyncContext(name.value, autoFinish = true, count = true)(code)

  /**
    * Creates a new Metrics with the specified category. Chaining calls to this method will produce nested categories.
    *
    * @param category the name of the category (or sub-category).
    * @return the new Metrics.
    */
  def withCategory(category: String): Metrics

  /**
    * Adds the supplied context to the trace context and tags for use in the MDC. Then runs the supplied code with
    * the changed context.
    *
    * NOTE: this will mutate the current context and will not roll the changes back.
    *
    * @param context the context to add.
    * @param code the code to be run.
    * @tparam T the type produced by the code block.
    * @return the result produced by the code.
    */
  def withMdc[T](context: (String, String)*)(code: => T): T = {
    context.foreach {
      case (key, value) => TraceLocal.storeForMdc(key, value)
    }
    code
  }
}

/**
  * Uses the Kamon Tracer object to implement the Metrics trait.
  */
@Singleton
class MetricsSingleton(category: Option[String], futureNameGenerator: FutureNameGenerator) extends Metrics {

  /**
    * Constructs a new instance with no category.
    */
  @Inject
  def this(futureNameGenerator: FutureNameGenerator) {
    this(None, futureNameGenerator)
  }

  override def currentContext: TraceContext = Tracer.currentContext

  override def clearCurrentContext(): Unit = Tracer.clearCurrentContext

  override def setCurrentContext(context: TraceContext): Unit =
    Tracer.setCurrentContext(context)

  override def withNewContext[T](traceName: String, traceToken: Option[String], autoFinish: Boolean)(code: => T): T =
    Tracer.withNewContext(fullName(traceName), traceToken, Map.empty[String, String], autoFinish)(code)

  override def withNewAsyncContext[T](traceName: String, autoFinish: Boolean, count: Boolean)(code: ⇒ Future[T])(implicit executionContext: ExecutionContext): Future[T] =
    Tracer.withContext(Kamon.tracer.newContext(fullName(traceName))) {
      val context    = currentContext
      val codeResult = code
      if (autoFinish) {
        codeResult onComplete { _ =>
          context.finish()
        }
      }

      if (count) {
        codeResult.onComplete {
          case Failure(_) => Kamon.metrics.counter(futureNameGenerator.generateFailureName(traceName, fullName(traceName))).increment()
          case Success(_) => Kamon.metrics.counter(futureNameGenerator.generateSuccessName(traceName, fullName(traceName))).increment()
        }
      }

      codeResult
    }

  override def withCategory(category: String): Metrics = {
    val newCategory = this.category.map(_ + "\\." + category).orElse(Some(category))
    new MetricsSingleton(newCategory, futureNameGenerator)
  }

  private def fullName(traceName: String) = category.map(_ + "\\." + traceName).getOrElse(traceName)

}

/**
  * Determines how the success and failure counters for Futures will be named.
  */
trait FutureNameGenerator {

  /**
    * Generates a name for the Future that succeeded.
    * @param traceName the name of the trace.
    * @param fullTraceName the full name of the trace, including any category information.
    * @return the name of the counter to be used.
    */
  def generateSuccessName(traceName: String, fullTraceName: String): String

  /**
    * Generates a name for the Future that failed.
    * @param traceName the name of the trace.
    * @param fullTraceName the full name of the trace, including any category information.
    * @return the name of the counter to be used.
    */
  def generateFailureName(traceName: String, fullTraceName: String): String
}

/**
  * Default implementation.
  */
class DefaultFutureNameGenerator extends FutureNameGenerator {

  override def generateSuccessName(traceName: String, fullTraceName: String): String = fullTraceName + "\\.success"

  override def generateFailureName(traceName: String, fullTraceName: String): String = fullTraceName + "\\.failure"
}
