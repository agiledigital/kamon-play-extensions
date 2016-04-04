# kamon-play-extensions [![Build Status](https://travis-ci.org/agiledigital/kamon-play-extensions.svg?branch=master)](https://travis-ci.org/agiledigital/kamon-play-extensions)

Provides a set of Play2 injectable extentsions for [Kamon](http://kamon.io/).

## Usage

Add library as a dependency:

```scala
"au.com.agiledigital" %% "play-kamon-extensions" % "0.4"
```

Enable the Play2 module in `application.conf`

```scala
play.modules.enabled += "au.com.agiledigital.kamon.play_extensions.KamonPlayExtensionsModule"
```

## Starting Traces

To use, inject (or otherwise obtain) an instance of `au.com.agiledigital.kamon.play_extensions.Metrics`. Once obtained, you can use as a substitute for the methods available on Kamon's `Tracer` object.

Asychronous traces (optionally autocompleted and counted) can be started using `withNewAsyncContext`:

```scala
import javax.inject.Inject
import au.com.agiledigital.kamon.play_extensions.Metrics
import scala.concurrent.Future

class Service @Inject()(metrics: Metrics) {

  def doSomething(): Future[Int] = {
    metrics.withNewAsyncContext("doingSomething", autoFinish = true, count = true) {
      Future.successful(10)
    }
  }
}
```

Traces can be named automatically using `traced` and `tracedAsync`. Naming semantics are determined by [sourcecode](https://github.com/lihaoyi/sourcecode).

```scala
import javax.inject.Inject
import au.com.agiledigital.kamon.play_extensions.Metrics
import scala.concurrent.Future

class Service @Inject()(metrics: Metrics) {

  def doSomething(): Future[Int] = metrics.tracedAsync {
    Future.successful(10)
  }

}
```

## Categorising Metrics
By default Kamon records all trace metrics under `statsd.timer.$Application.Host.trace`. The extensions support writing those metrics to nested categories.

To use, set the statsd metric namer in `kamon.conf`:

```scala
kamon {
  statsd {
    metric-key-generator = "au.com.agiledigital.kamon.play_extensions.EscapingMetricKeyGenerator"
  }
}
```

Then to create nested trace categories, use `withCategory`

```scala
import javax.inject.Inject
import au.com.agiledigital.kamon.play_extensions.Metrics
import scala.concurrent.Future

class Service @Inject()(metrics: Metrics) {

  private val serviceMetrics = metrics.withCategory("service")
  
  def doSomething(): Future[Int] = metrics.tracedAsync {
    Future.successful(10)
  }

}
```

This will cause metrics for those traces to be written to `statsd.timer.$Application.Host.trace.service`


## Testing

To aid testing code that depends upon the `Metrics` extension, depend upon the testkit:

```scala
"au.com.agiledigital" %% "play-kamon-extensions-testkit" % "0.4"
```

Use the do-nothing `Metrics implementation from `au.com.agiledigital.kamon.play_extensions.test#metrics`
