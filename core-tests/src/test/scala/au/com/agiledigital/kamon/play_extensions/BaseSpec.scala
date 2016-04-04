package au.com.agiledigital.kamon.play_extensions

import java.util.concurrent.TimeUnit

import org.specs2.control.NoLanguageFeatures
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import scala.concurrent.duration.FiniteDuration

/**
 * Base spec for all specifications.
 */
class BaseSpec extends Specification with Mockito with NoLanguageFeatures {

  val defaultRetries = 5
  val defaultTimeout = new FiniteDuration(2, TimeUnit.SECONDS)
  val defaultAwait = defaultTimeout * defaultRetries.toLong
  val defaultDurationInSeconds = defaultAwait.toSeconds

}
