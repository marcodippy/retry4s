
val catsVersion       = "1.1.0"
val catsEffectVersion = "1.0.0-RC"
val scalaTestVersion  = "3.0.5"
val scalacticVersion  = "3.0.5"
val scalaCheckVersion = "1.14.0"

lazy val core = (project in file("modules/core"))
  .settings(
    name := "retry4s",
    version := "0.0.1-SNAPSHOT",
    organization in ThisBuild := "com.mdipaola",
    scalaVersion := "2.12.6",
    libraryDependencies ++= Seq(
      "org.typelevel"                 %% "cats-core"                  % catsVersion,
      "org.typelevel"                 %% "cats-effect"                % catsEffectVersion,
      "org.scalatest"                 %% "scalatest"                  % scalaTestVersion      % Test,
      "org.scalacheck"                %% "scalacheck"                 % scalaCheckVersion     % Test,
      "org.scalactic"                 %% "scalactic"                  % scalacticVersion      % Test,
      "org.typelevel"                 %% "cats-testkit"               % "1.0.1"               % Test
    ),
    addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.6")
  )

lazy val examples = (project in file("modules/examples"))
  .dependsOn(core)

lazy val docs = project.in(file("modules/docs"))
  .dependsOn(core)
  .enablePlugins(MicrositesPlugin)
  .settings(
    publishArtifact := false,
    micrositeName := "retry4s",
    micrositeAuthor := "Marco Di Paola",
    micrositeDescription := "Purely functional retry combinators for Scala",
    micrositeBaseUrl := "/retry4s",
    micrositeDocumentationUrl := "/retry4s/docs",
    micrositeHomepage := "https://github.com/marcodippy/retry4s",
    micrositeGithubOwner := "marcodippy",
    micrositeGithubRepo := "retry4s"
  )