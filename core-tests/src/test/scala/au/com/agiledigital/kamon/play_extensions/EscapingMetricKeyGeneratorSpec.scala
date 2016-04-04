package au.com.agiledigital.kamon.play_extensions

import com.typesafe.config.ConfigFactory
import org.specs2.matcher.DataTables

/**
 * Contains unit tests for [[EscapingMetricKeyGenerator]].
 */
class EscapingMetricKeyGeneratorSpec extends BaseSpec with DataTables {

  "Generating metrics names" should {
    // @formatter:off
    "return expected name" in {
      "description"        || "input"            || "expected result" |>
      "leading ."          !! ".metric.name"     !! "_metric_name"    |
      "escaped"            !! "\\.metric\\.name" !! ".metric.name"    |
      "simple"             !! "metricname"       !! "metricname"      |> {
      (description, input, expectedResult) => {
        // @formatter:on

        // Given some input text.

        // When it is escaped.
        val namer = new EscapingMetricKeyGenerator(ConfigFactory.parseString(configuration))
        val actual = namer.createNormalizer("")(input)

        // Then it should match the expected result.
        actual must_=== expectedResult
      }
      }
    }
  }

  val configuration =
    """
      |kamon {
      |  statsd {
      |    simple-metric-key-generator {
      |      application = "app"
      |      include-hostname = true
      |      hostname-override = "hostname"
      |      metric-name-normalization-strategy = ""
      |    }
      |  }
      |}
    """.stripMargin

}
