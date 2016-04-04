package au.com.agiledigital.kamon.play_extensions

import play.api.Configuration

/**
 * Contains unit tests for the [[FutureNameGeneratorProvider]].
 */
class FutureNameGeneratorProviderSpec extends BaseSpec {

  "The name generator provider" should {
    "default if no configuration is provided" in {
      // Given a provider and an empty configuration.
      val provider = new FutureNameGeneratorProvider(Configuration.empty)

      // Then the generator is provided.
      val actual = provider.get()

      // Then an instance of the default provider should be used.
      actual must beAnInstanceOf[DefaultFutureNameGenerator]
    }
    "default is bad configuration is provided" in {
      // Given a provider and a configuration that attempts to use an unknown class.
      val provider = new FutureNameGeneratorProvider(
        Configuration.from(
          Map("kamon.play-extensions.future-name-generator" ->
            "au.com.agiledigital.kamon.play_extensions.CustomNameGenerator2")))

      // Then the generator is provided.
      val actual = provider.get()

      // Then an instance of the default provider should be used.
      actual must beAnInstanceOf[DefaultFutureNameGenerator]
    }
    "use the override" in {
      // Given a provider and a configuration that attempts to use a known class.
      val provider = new FutureNameGeneratorProvider(
        Configuration.from(
          Map("kamon.play-extensions.future-name-generator" ->
            "au.com.agiledigital.kamon.play_extensions.CustomNameGenerator")))

      // Then the generator is provided.
      val actual = provider.get()

      // Then an instance of the default provider should be used.
      actual must beAnInstanceOf[CustomNameGenerator]
    }
  }
}
