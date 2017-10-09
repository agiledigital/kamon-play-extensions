package au.com.agiledigital.kamon.play_extensions

import javax.inject.{Inject, Provider}

import akka.actor.ReflectiveDynamicAccess
import play.api.{Logger, Configuration, Environment}
import play.api.inject.{Binding, Module}

import scala.util.{Failure, Success}

class KamonPlayExtensionsModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[FutureNameGenerator].toProvider[FutureNameGeneratorProvider],
    bind[Metrics].to[MetricsSingleton].eagerly()
  )
}

class FutureNameGeneratorProvider @Inject()(configuration: Configuration) extends Provider[FutureNameGenerator] {

  private val configurationKey = "kamon.play-extensions.future-name-generator"

  private val dynamics = new ReflectiveDynamicAccess(getClass.getClassLoader)

  override def get(): FutureNameGenerator = {

    configuration.getOptional[String](configurationKey).flatMap { fcqn =>
      dynamics.createInstanceFor[FutureNameGenerator](fcqn, Nil) match {
        case Success(generator) => Some(generator)
        case Failure(error) =>
          Logger.warn(s"Failed to create future namer generator [$fcqn] from [$configurationKey].", error)
          None
      }
    } getOrElse new DefaultFutureNameGenerator()
  }
}
