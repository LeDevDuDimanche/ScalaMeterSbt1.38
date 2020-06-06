
val shared = Seq(
  organization := "com.storm-enroute",
  version      := "0.8.0",
  scalaVersion := "2.12.3",
  )
  
lazy val scalaReflect = Def.setting { "org.scala-lang" % "scala-reflect" % scalaVersion.value }
lazy val parserComb = Def.setting { "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2"  }
lazy val scalaTests = Def.setting {"org.scalatest" % "scalatest_2.12" % "3.1.2" % "test" }


lazy val Benchmark = config("bench") extend Test

lazy val coroutinesCommon = (project in file("coroutines-common"))
  .settings(
    shared,
    name := "coroutines-common",
    libraryDependencies ++= Seq(
      scalaTests.value,
      parserComb.value,
      scalaReflect.value
    )
  )

lazy val coroutinesExtra = (project in file("coroutines-extra"))
  .settings(
    shared,
    name := "coroutines-common",

    libraryDependencies += scalaTests.value

  ) dependsOn(
    coroutines % "compile->compile;test->test"
  )


lazy val coroutines = (project in file("."))
  .settings(
    name := "coroutines",

    libraryDependencies ++=  
      Seq(
        scalaTests.value,
        parserComb.value,
        scalaReflect.value,
        "org.scala-lang.modules" %% "scala-async" % "0.10.0"  % "test;bench",
        "com.storm-enroute" % "scalameter_2.12" % "0.19" % "test;bench"
      ),


    testFrameworks += new TestFramework("org.scalameter.ScalaMeterFramework"),

    parallelExecution in Test := false,

  ) configs(
      Benchmark
  ) settings(
      inConfig(Benchmark)(Defaults.testSettings): _*
  ) aggregate(
    coroutinesCommon
  ) dependsOn(
    coroutinesCommon % "compile->compile;test->test"
  )
  