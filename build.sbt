val shared = Seq(
  organization := "com.storm-enroute",
  version      := "0.8.0",
  scalaVersion := "2.12.3",

  //common dependencies
  libraryDependencies ++= Seq(
    "org.scalatest" % "scalatest_2.12" % "3.0.4" % "test",
    "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.6",
    "org.scala-lang" % "scala-reflect" % "2.12.3"
  )
)


lazy val scalaReflect = Def.setting { "org.scala-lang" % "scala-reflect" % scalaVersion.value }

lazy val Benchmark = config("bench") extend Test

lazy val coroutinesCommon = (project in file("coroutines-common"))
  .settings(
    shared,
    name := "coroutines-common"
  )

lazy val coroutinesExtra = (project in file("coroutines-extra"))
  .settings(
    shared,
    name := "coroutines-common"
  ) dependsOn(
    coroutines % "compile->compile;test->test"
  )


lazy val coroutines = (project in file("."))
  .settings(
    shared,
    name := "coroutines",

    libraryDependencies ++= Seq( 
        //extra dependencies
        "com.storm-enroute" % "scalameter_2.12" % "0.19",
        "org.scala-lang.modules" %% "scala-async" % "0.10.0",
         
        //macros
        scalaReflect.value
    )
  ) configs(
      Benchmark
  ) settings(
      inConfig(Benchmark)(Defaults.testSettings): _*
  ) aggregate(
    coroutinesCommon
  ) dependsOn(
    coroutinesCommon % "compile->compile;test->test"
  )
  