package au.com.agiledigital.kamon.play_extensions

import javax.inject.Inject

import com.typesafe.config.Config
import kamon.statsd.SimpleMetricKeyGenerator

/**
 * Supports escaping `.` characters in metric names so that nested metrics can be created in statsd.
 */
class EscapingMetricKeyGenerator @Inject()(config: Config) extends SimpleMetricKeyGenerator(config) {

  override def createNormalizer(strategy: String): Normalizer = {
    (s: String) => s.replace(": ", "-").
      replace(" ", "_").
      replace("/", "_").
      replaceAll("""([^\\])\.""", "$1_").
      replace("""\.""", ".")
  }
}
