package org.coroutines

import org.coroutines._

//for benchmarking
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

import scala.collection._
import scala.language.implicitConversions


@State(Scope.Thread) //All threads running the benchmark share the same state object.
@Warmup(iterations = 5)    // translation of "exec.minWarmupRuns", 50 ; "exec.maxWarmupRuns", 100 
@BenchmarkMode(Array(Mode.All))
@Measurement(iterations = 10) //"exec.benchRuns"
@Fork(value = 2) //"exec.independentSamples"
class TaylorBenchJMH  {

  // @Param(Array("50000", "100000", "150000", "200000", "250000"))
  @Param(Array("50000", "150000", "250000"))
  var taylorSize: Int = _
  
  

  //TODO separate taylor and fibonnaci in two different files.
  // @gen("taylorSizes")
  // @benchmark("coroutines.stream.taylor.sum")
  // @curve("stream")
  @Benchmark
  def streamTaylorSum = {
    val sz = taylorSize
    var sum = 0.0
    class TaylorInvX(x: Double) {
      lazy val values: Stream[Double] =
        1.0 #:: values.map(_ * (x - 1) * -1)
    }
    var i = 0
    var s = new TaylorInvX(0.5).values
    while (i < sz) {
      sum += s.head
      s = s.tail
      i += 1
    }
    sum
  }

  // @gen("taylorSizes")
  // @benchmark("coroutines.stream.taylor.sum")
  // @curve("coroutine")
  @Benchmark
  def coroutineTaylorSum = {
    val sz = taylorSize
    var sum = 0.0
    val taylor = coroutine { (x: Double) =>
      var last = 1.0
      yieldval(last)
      while (true) {
        last *= -1.0 * (x - 1)
        yieldval(last)
      }
    }
    var i = 0
    val c = call(taylor(0.5))
    while (i < sz) {
      c.resume
      sum += c.value
      i += 1
    }
    sum
  }

}