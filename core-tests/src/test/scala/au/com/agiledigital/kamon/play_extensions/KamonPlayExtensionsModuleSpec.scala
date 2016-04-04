package au.com.agiledigital.kamon.play_extensions

import play.api.Mode
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test._

/**
 * Contains unit tests for the [[KamonPlayExtensionsModule]]
 */
class KamonPlayExtensionsModuleSpec extends BaseSpec with PlayRunners {

  def application = new GuiceApplicationBuilder()
    .in(Mode.Test)
    .bindings(new KamonPlayExtensionsModule)


  "Enabling the module" should {
    "bind the metrics singleton" in new WithApplication(application.build()) {

      // Given a fake application with the module enabled.

      // Then the metrics should be injectable.
      val metrics = app.injector.instanceOf[Metrics]

      // And no exception must have been thrown.
    }
    "support overriding the future name generator" in new WithApplication(
      application.configure("kamon.play-extensions.future-name-generator" ->
        "au.com.agiledigital.kamon.play_extensions.CustomNameGenerator").build()) {

      // Given a fake application with the module enabled and the configuration for the name generator overridden.

      // Then the name generator should be injectable.
      val nameGenerator = app.injector.instanceOf[FutureNameGenerator]

      // And should generate names as expected.
      nameGenerator.generateSuccessName("", "") must_=== "custom-success"
      nameGenerator.generateFailureName("", "") must_=== "custom-fail"
    }
  }

}

class CustomNameGenerator extends FutureNameGenerator {
  override def generateSuccessName(traceName: String, fullTraceName: String): String = "custom-success"

  override def generateFailureName(traceName: String, fullTraceName: String): String = "custom-fail"
}