val Specs2Version = "4.0.0"

val PlayVersion = "2.6.6"

val KamonVersion = "0.6.7"

lazy val commonSettings = Seq(
  organization := "au.com.agiledigital",
  scalaVersion := "2.12.3",
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint:unchecked", "-encoding", "UTF-8"),
  scalacOptions ++= Seq(
    "-deprecation", // Emit warning and location for usages of deprecated APIs.
    "-feature", // Emit warning and location for usages of features that should be imported explicitly.
    "-unchecked", // Enable additional warnings where generated code depends on assumptions.
    "-Xfatal-warnings", // Fail the compilation if there are any warnings.
    "-Xlint", // Enable recommended additional warnings.
    "-Ywarn-adapted-args", // Warn if an argument list is modified to match the receiver.
    "-Ywarn-dead-code", // Warn when dead code is identified.
    "-Ywarn-inaccessible", // Warn about inaccessible types in method signatures.
    "-Ywarn-nullary-override", // Warn when non-nullary overrides nullary, e.g. def foo() over def foo.
    "-Ywarn-numeric-widen" // Warn when numerics are widened.
  ),
  libraryDependencies ++= Seq(
    "com.typesafe.play" %% "play"         % PlayVersion % Provided,
    "io.kamon"          %% "kamon-core"   % KamonVersion % Provided,
    "com.lihaoyi"       %% "sourcecode"   % "0.1.4"
  ),
  wartremoverErrors in (Compile, compile) ++= Seq(
    Wart.FinalCaseClass,
    Wart.Null,
    Wart.TryPartial,
    Wart.Var,
    Wart.OptionPartial,
    Wart.TraversableOps,
    Wart.EitherProjectionPartial,
    Wart.StringPlusAny,
    Wart.AsInstanceOf,
    Wart.ExplicitImplicitTypes,
    Wart.MutableDataStructures,
    Wart.Return,
    Wart.AsInstanceOf,
    Wart.IsInstanceOf
  )
)

lazy val root = (project in file("."))
  .settings(commonSettings: _*)
  .settings(
    name := "play-kamon-extensions-root",
    publish := {},
    publishArtifact := false
  )
  .aggregate(
    core,
    testkit,
    coreTests
  )

lazy val core = (project in file("core"))
  .settings(commonSettings: _*)
  .settings(
    version := (version in LocalProject("root")).value,
    name := "play-kamon-extensions"
  )

lazy val coreTests = (project in file("core-tests"))
  .settings(commonSettings: _*)
  .settings(
    version := (version in LocalProject("root")).value,
    name := "play-kamon-extensions-tests",
    libraryDependencies ++= Seq(
      "io.kamon"          %% "kamon-testkit"        % "0.6.6",
      "org.specs2"        %% "specs2-core"          % Specs2Version % Test,
      "org.specs2"        %% "specs2-junit"         % Specs2Version % Test,
      "org.specs2"        %% "specs2-matcher-extra" % Specs2Version % Test,
      "org.specs2"        %% "specs2-mock"          % Specs2Version % Test,
      "com.typesafe.play" %% "play-specs2"          % PlayVersion % Test
    ),
    fork in Test := false,
    parallelExecution in Test := false,
    publish := {},
    publishArtifact := false
  )
  .dependsOn(testkit % Test)
  .dependsOn(core)

lazy val testkit = (project in file("testkit"))
  .settings(commonSettings: _*)
  .settings(
    version := (version in LocalProject("root")).value,
    name := "play-kamon-extensions-testkit",
    libraryDependencies ++= Seq(
      "org.specs2" %% "specs2-mock" % Specs2Version % Test
    ),
    coverageExcludedPackages := ".*test.*"
  )
  .dependsOn(core)
