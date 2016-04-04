package au.com.agiledigital.kamon.play_extensions

import kamon.Kamon
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.BeforeAfter

import scala.concurrent.{Await, Promise}

/**
 * Contains unit tests for the [[MetricsSingleton]].
 */
class MetricsSingletonSpec(implicit ev: ExecutionEnv) extends BaseSpec {

  sequential

  "An async thread context" should {
    "be completed when the Future succeeds" in new WithKamon {
      // Given a metrics singleton.
      val metrics = new MetricsSingleton(new DefaultFutureNameGenerator)

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
      metrics.currentContext.isOpen must beFalse
    }
  }

}

trait WithKamon extends BeforeAfter {

  override def before: Any = {
    Kamon.start()
  }

  override def after: Any = {
    Kamon.shutdown()
  }
}
