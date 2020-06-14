package org.coroutines

import org.coroutines._
//for benchmarking
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

import scala.collection._

 
@State(Scope.Benchmark) //All threads running the benchmark share the same state object.
@Warmup(iterations = 5)    // translation of     exec.minWarmupRuns -> 40, exec.maxWarmupRuns -> 80,
@BenchmarkMode(Array(Mode.All))
@Measurement(iterations = 10) //"exec.benchRuns 
@Fork(value = 2) //"exec.independentSamples"
class TreeIteratorBenchJMH {

  sealed trait Tree
  case class Node(x: Int, left: Tree, right: Tree) extends Tree
  case object Empty extends Tree

  class TreeIterator(val tree: Tree) {
    var stack = new Array[Tree](30)
    var stackpos = -1
    var current: Int = _

    def goLeft(tree: Tree): Unit = {
      stackpos += 1
      stack(stackpos) = tree
      tree match {
        case Empty =>
        case Node(_, left, _) => goLeft(left)
      }
    }

    goLeft(tree)
    moveToNext()

    def moveToNext(): Unit = {
      if (stackpos != -1) stack(stackpos) match {
        case Empty =>
          stack(stackpos) = null
          stackpos -= 1
          if (stackpos > -1) assert(stack(stackpos) != Empty)
          moveToNext()
        case Node(x, _, right) =>
          stack(stackpos) = null
          stackpos -= 1
          current = x
          goLeft(right)
      }
    }

    def hasNext: Boolean = {
      stackpos != -1
    }
    def next(): Int = {
      if (!hasNext) throw new NoSuchElementException
      val x = current
      moveToNext()
      x
    }
  }

  @Param(Array("50000", "100000", "150000", "200000", "250000"))
  var size: Int = _

  def genTree(sz: Int): Tree = {
    if (sz == 0) Empty
    else {
      val rem = sz - 1
      val left = genTree(rem / 2)
      val right = genTree(rem - rem / 2)
      Node(sz, left, right)
    }
  }

  var getTree: Tree = genTree(size)

  var treePair: (Tree, Tree) = (genTree(size), genTree(size))
  

  var treeEnumerator: Coroutine._1[Tree, Int, Unit] = null
  /* max int */

  @Benchmark
  def coroutineMax = {
    val tree: Tree = getTree
    var max = Int.MinValue
    treeEnumerator = coroutine { (t: Tree) =>
      t match {
        case n: Node =>
          if (n.left != Empty) treeEnumerator(n.left)
          yieldval(n.x)
          if (n.right != Empty) treeEnumerator(n.right)
        case Empty =>
      }
    }
    val c = call(treeEnumerator(tree))

    while (c.pull) {
      val x = c.value
      if (x > max) max = x
    }
    max
  }

  @Benchmark
  def iteratorMax = {
    val tree = getTree
    var max = Int.MinValue
    val iter = new TreeIterator(tree)
    while (iter.hasNext) {
      val x = iter.next()
      if (x > max) max = x
    }
    max
  }

  @Benchmark
  def recursiveMax = {
    val tree = getTree
    var max = Int.MinValue
    def recurse(tree: Tree): Unit = {
      tree match {
        case Node(x, left, right) =>
          recurse(left)
          if (x > max) max = x
          recurse(right)
        case Empty =>
      }
    }
    recurse(tree)
    max
  }

  /* growing array */

  @Benchmark
  def coroutineToArray =  {
    val tree = getTree
    treeEnumerator = coroutine { (t: Tree) =>
      t match {
        case n: Node =>
          if (n.left != Empty) treeEnumerator(n.left)
          yieldval(n.x)
          if (n.right != Empty) treeEnumerator(n.right)
        case Empty =>
      }
    }
    val c = call(treeEnumerator(tree))

    //using lazy list because jmh is not happy with declaring buffers in benchmarks
    val result = Stream.from(1).map { i => 
        if (c.pull) Some(c.value)
        else None
    }.takeWhile(_.isDefined).force

    result
  }

  @Benchmark
  def iteratorToArray= {
    val tree: Tree = getTree
    val iter = new TreeIterator(tree)

    //using lazy list because jmh is not happy with declaring buffers in benchmarks
    Stream.from(1).map { _ => 
      if (iter.hasNext) Some(iter.next())
      else None
    }.takeWhile(_.isDefined).force
  }

  // @Benchmark
  // def recursiveToArray = {
  //   val tree: Tree = getTree
  //   val a = mutable.Buffer[Int]()
  //   def recurse(t: Tree): Seq = {
  //     t match {
  //       case Node(x, left, right) =>
  //         recurse(left)
  //         a.addOne(x)
  //         recurse(right)
  //       case Empty =>
  //     }
  //   }
  //   recurse(tree)
  //   a
  // }

    @Benchmark
    def recursiveToSeq = {
      val tree: Tree = getTree
      def recurse(t: Tree): List[Int] = {
        t match {
          case Node(x, left, right) =>
            recurse(left) ++ List(x) ++ recurse(right)
          case Empty => List()
        }
      }
      recurse(tree)
    }



  /* samefringe */

  @volatile var isSame = true

  @Benchmark
  def coroutineSameFringe = {
    val (t1, t2) = treePair
    treeEnumerator = coroutine { (t: Tree) =>
      t match {
        case n: Node =>
          if (n.left != Empty) treeEnumerator(n.left)
          yieldval(n.x)
          if (n.right != Empty) treeEnumerator(n.right)
        case Empty =>
      }
    }
    val c1 = call(treeEnumerator(t1))
    val c2 = call(treeEnumerator(t2))
    var same = true
    while (c1.pull && c2.pull) {
        val x = c1.value
        val y = c2.value
        if (x != y) same = false 
    }
    isSame = same
    same
  }

  @Benchmark
  def iteratorSameFringe = {
    val (t1, t2) = treePair
    val iter1 = new TreeIterator(t1)
    val iter2 = new TreeIterator(t2)
    var same = true
    while (iter1.hasNext && iter2.hasNext) {
      val x = iter1.next()
      val y = iter2.next()
      if (x != y) same = false
    }
    if (iter1.hasNext != iter2.hasNext) same = false
    isSame = same

    same
  }

  def treeStream(tree: Tree): Stream[Int] = {
    tree match {
      case Empty => Stream[Int]()
      case Node(x, left, right) => treeStream(left) #::: (x #:: treeStream(right))
    }
  }

  @Benchmark
  def streamSameFringe = {
    val (t1, t2) = treePair
    var s1 = treeStream(t1)
    var s2 = treeStream(t2)
    var same = true
    while (s1.nonEmpty && s2.nonEmpty) {
      val x = s1.head
      val y = s2.head
      if (x != y) same = false
      s1 = s1.tail
      s2 = s2.tail
    }
    if (s1.nonEmpty != s2.nonEmpty) same = false
    isSame = same

    same
  }

  /* tests */

  // assert({
  //   def leaf(x: Int) = Node(x, Empty, Empty)
  //   val tree = Node(1,
  //     Node(19, leaf(21), leaf(23)),
  //     Node(3,
  //       leaf(11),
  //       Node(9,
  //         leaf(5),
  //         leaf(17))))
  //   def rec(tree: Tree): Seq[Int] = tree match {
  //     case Empty => Seq()
  //     case Node(x, l, r) =>
  //       rec(l) ++ Seq(x) ++ rec(r)
  //   }

  //   val it = new TreeIterator(tree)
  //   def treeRec(result: Seq[Int]): Seq[Int] = {
  //     if (it.hasNext) treeRec(result :+ it.next())
  //     else result
  //   }

  //   rec(tree) == treeRec(Seq())
  // })

}
