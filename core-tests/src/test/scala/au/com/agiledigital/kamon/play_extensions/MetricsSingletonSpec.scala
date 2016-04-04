package au.com.agiledigital.kamon.play_extensions

import java.nio.LongBuffer

import kamon.Kamon
import kamon.metric.instrument.{CollectionContext, Counter}
import kamon.trace.TraceContext
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.BeforeAfter
import play.api.Logger

import scala.concurrent.{Await, Future, Promise}
import scala.language.postfixOps

/**
 * Contains unit tests for the [[MetricsSingleton]].
 */
class MetricsSingletonSpec(implicit ev: ExecutionEnv) extends BaseSpec {

  sequential

  "Clearing the context" should {
    "reset the context" in new WithKamon {
      // Given a metrics singleton
      private val nameGenerator = new DefaultFutureNameGenerator
      val metrics = new MetricsSingleton(nameGenerator)

      // When the context is set.
      val context = Kamon.tracer.newContext("context")
      metrics.setCurrentContext(context)

      // Then is should have been set.
      metrics.currentContext must_=== context

      // Then when it is cleared.
      metrics.clearCurrentContext()

      // Then it should no longer be set.
      metrics.currentContext must_!= context
    }
  }

  "Creating a context" should {
    "default to no token" in new WithKamon {
      // Given a metrics singleton
      private val nameGenerator = new DefaultFutureNameGenerator
      val metrics = new MetricsSingleton(nameGenerator)

      // When a new context is created.
      val result = metrics.withNewContext("no_token") {
        // Then the current context should be set.
        metrics.currentContext.name must_=== "no_token"
        // With a default token.
        metrics.currentContext.token must not beEmpty

        metrics.currentContext.isOpen must beTrue

        10
      }

      // And it should return the result of executing the code.
      result must_=== 10

      // And it should be replaced after is has executed.
      metrics.currentContext.isOpen must_!= "no_token"
    }
    "record the token" in new WithKamon {
      // Given a metrics singleton
      private val nameGenerator = new DefaultFutureNameGenerator
      val metrics = new MetricsSingleton(nameGenerator)

      // When a new context is created with a token.
      val result = metrics.withNewContext("with_token", Some("token")) {
        // Then the current context should be set.
        metrics.currentContext.name must_=== "with_token"
        // With a default token.
        metrics.currentContext.token must_=== "token"

        metrics.currentContext.isOpen must beTrue

        false
      }

      // And it should return the result of executing the code.
      result must beFalse

      // And it should be replaced after is has executed.
      metrics.currentContext.isOpen must_!= "with_token"
    }
    "auto-close" in new WithKamon {
      // Given a metrics singleton
      private val nameGenerator = new DefaultFutureNameGenerator
      val metrics = new MetricsSingleton(nameGenerator)

      // When a new context is created that auto-closes
      val context = metrics.withNewContext("with_token", autoFinish = true) {
        // Then the current context should be set.
        metrics.currentContext.name must_=== "with_token"
        metrics.currentContext.isOpen must beTrue
        metrics.currentContext
      }

      // Then the context should have been closed.
      context.isClosed must beTrue
    }
  }

  "Tracing" should {
    "use the name of the nearest enclosing method" in new WithKamon {
      // Given a metrics singleton
      private val nameGenerator = new DefaultFutureNameGenerator
      val metrics = new MetricsSingleton(nameGenerator)

      // And a traced method.
      def doSomething(x: Int): Int = metrics.traced {
        // That checks the current trace name.
        metrics.currentContext.name must_== "doSomething"
        x * 2
      }

      // When it is invoked.
      val result = doSomething(10)

      // Then the expected result should have been returned.
      result must_== 20
    }
    "auto-close" in new WithKamon {
      // Given a metrics singleton
      private val nameGenerator = new DefaultFutureNameGenerator
      val metrics = new MetricsSingleton(nameGenerator)

      // And a traced method that returns the context.
      def doSomething(x: Int): TraceContext = metrics.traced {
        // That checks the current trace name.
        metrics.currentContext.name must_== "doSomething"
        metrics.currentContext
      }

      // When it is invoked.
      val result = doSomething(10)

      // Then the context should have been closed.
      result.isClosed must beTrue
    }
  }

  "Tracing an async method" should {
    "use the name of the nearest enclosing method" in new WithKamon {
      // Given a metrics singleton
      private val nameGenerator = new DefaultFutureNameGenerator
      val metrics = new MetricsSingleton(nameGenerator)

      // And a traced method.
      def doSomethingAsync(x: Int): Future[Int] = metrics.tracedAsync {
        // That checks the current trace name.
        metrics.currentContext.name must_== "doSomethingAsync"
        Future.successful(x * 2)
      }

      // When it is invoked.
      val result = doSomethingAsync(10)

      // Then the expected result should have been returned.
      result must beEqualTo(20).awaitFor(defaultAwait)
    }
    "auto-close" in new WithKamon {
      // Given a metrics singleton
      private val nameGenerator = new DefaultFutureNameGenerator
      val metrics = new MetricsSingleton(nameGenerator)

      // And a traced method that returns the context.
      def doSomethingAsync(x: Int): Future[TraceContext] = metrics.tracedAsync {
        // That checks the current trace name.
        val currentContext = metrics.currentContext
        currentContext.name must_== "doSomethingAsync"
        Future {
          currentContext
        }
      }

      // When it is invoked.
      val result = doSomethingAsync(10)

      // Then the context should have been closed after the future completes.
      result must beLike[TraceContext]({
        case context: TraceContext => eventually(defaultRetries, defaultTimeout) {
          context.isClosed must beTrue
        }
      }).awaitFor(defaultAwait)
    }
  }

  "An async thread context" should {
    "be completed when the Future succeeds" in new WithKamon {
      // Given a metrics singleton
      private val nameGenerator = new DefaultFutureNameGenerator
      val metrics = new MetricsSingleton(nameGenerator)

      // And a promise that will be completed.
      val p = Promise[Int]()

      // When an async context is created.
      val contextFut = metrics.withNewAsyncContext("success", autoFinish = true, count = false) {

        val context = metrics.currentContext

        p.future.map { _ =>
          // Then it should be open before the future is completed.
          // Must move the context through the future manually as we can not rely on the weaving to do it when
          // running tests.
          context.name must_== "success"
          context.isOpen must beTrue
          context
        }
      }

      // And it should closed after the future completes.
      p.success(10)
      val context = Await.result(contextFut, defaultAwait)
      context.isOpen must beFalse

      // And the counters should not have been incremented.
      takeSnapshotFrom(Kamon.metrics.counter(nameGenerator.generateSuccessName("success", "success"))).count must_=== 0
      takeSnapshotFrom(Kamon.metrics.counter(nameGenerator.generateFailureName("success", "success"))).count must_=== 0
    }

    "should increment the success count if requested" in new WithKamon {
      // Given a metrics singleton
      private val nameGenerator = new DefaultFutureNameGenerator
      val metrics = new MetricsSingleton(nameGenerator)

      // And a promise that will be completed.
      val p = Promise[Int]()

      // When an async context is created with count enabled.
      val contextFut = metrics.withNewAsyncContext("count-success", autoFinish = true, count = true) {

        val context = metrics.currentContext

        p.future.map { _ =>
          // Then it should be open before the future is completed.
          // Must move the context through the future manually as we can not rely on the weaving to do it when
          // running tests.
          context.name must_== "count-success"
          context.isOpen must beTrue
          context
        }
      }

      // After the promise is completed.
      p.success(10)
      val context = Await.result(contextFut, defaultAwait)

      // Then the success count for the trace should have been incremented.

      eventually(defaultRetries, defaultTimeout) {
        takeSnapshotFrom(Kamon.metrics.counter(nameGenerator.generateSuccessName("count-success", "count-success"))).count must_=== 1
      }

      // And the failure count should not have been.
      takeSnapshotFrom(Kamon.metrics.counter(nameGenerator.generateFailureName("count-success", "count-success"))).count must_=== 0
    }
    "should increment the failure count if requested" in new WithKamon {
      private val nameGenerator = new DefaultFutureNameGenerator
      // Given a metrics singleton
      val metrics = new MetricsSingleton(nameGenerator)

      // And a promise that will be failed.
      val p = Promise[Int]()

      // When an async context is created with count enabled.
      val contextFut = metrics.withNewAsyncContext("count-failures", autoFinish = true, count = true) {

        val context = metrics.currentContext

        p.future.map { _ =>
          // Then it should be open before the future is completed.
          // Must move the context through the future manually as we can not rely on the weaving to do it when
          // running tests.
          context.name must_== "count-failures"
          context.isOpen must beTrue
          context
        }
      }

      // After the promise is failed.
      p.failure(new Exception("expected exception"))
      Await.result(contextFut, defaultAwait) must throwAn[Exception]

      // Then the success count for the trace should not have been incremented.
      takeSnapshotFrom(Kamon.metrics.counter(nameGenerator.generateSuccessName("count-failures", "count-failures"))).count must_=== 0

      // And the failure count should have been.
      eventually(defaultRetries, defaultTimeout) {
        takeSnapshotFrom(Kamon.metrics.counter(nameGenerator.generateFailureName("count-failures", "count-failures"))).count must_=== 1
      }
    }
  }

  "Setting the category" should {
    "affect the names of the timer and counters" in new WithKamon {
      // Given a metrics singleton that has a category set.
      private val nameGenerator = new DefaultFutureNameGenerator
      val metrics = new MetricsSingleton(nameGenerator).withCategory("category")

      // When a future is traced and completed.
      val p = Promise[Int]()
      val contextFut = metrics.withNewAsyncContext("with-category", autoFinish = true, count = true) {
        val context = metrics.currentContext

        context.name must_== "category\\.with-category"

        p.future.map { _ =>
          context
        }
      }

      // After the promise is completed.
      p.success(10)
      val context = Await.result(contextFut, defaultAwait)

      // Then the counters should have been created with the expected names.
      eventually(defaultRetries, defaultTimeout) {
        takeSnapshotFrom(Kamon.metrics.counter(nameGenerator.generateSuccessName("with-category", "category\\.with-category"))).count must_=== 1
      }
    }
    "support multiple nestings" in new WithKamon {
      // Given a metrics singleton that has a category set multiple times.
      private val nameGenerator = new DefaultFutureNameGenerator
      val metrics = new MetricsSingleton(nameGenerator).withCategory("category").withCategory("uber-category")

      // When a future is traced and completed.
      val p = Promise[Int]()
      val contextFut = metrics.withNewAsyncContext("with-category", autoFinish = true, count = true) {
        val context = metrics.currentContext

        context.name must_== "category\\.uber-category\\.with-category"

        p.future.map { _ =>
          context
        }
      }

      // After the promise is completed.
      p.success(10)
      val context = Await.result(contextFut, defaultAwait)

      // Then the counters should have been created with the expected names.
      eventually(defaultRetries, defaultTimeout) {
        val count = takeSnapshotFrom(Kamon.metrics.counter(nameGenerator.generateSuccessName("with-category", "category\\.uber-category\\.with-category"))).count
        Logger.info(s"Count is [$count].")
        count must_=== 1
      }
    }
  }
}

trait WithKamon extends BeforeAfter {

  override def before: Any = {
    Kamon.start()
    Kamon.shutdown()
  }

  override def after: Any = {
    Kamon.shutdown()
  }

  def collectionContext = new CollectionContext {
    val buffer: LongBuffer = LongBuffer.allocate(1)
  }

  def takeSnapshotFrom(counter: Counter): Counter.Snapshot = counter.collect(collectionContext)
}
