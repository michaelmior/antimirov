package antimirov

import scala.reflect.ClassTag

// start = 0
// size = edges.length
case class Dfa(
  accept: BitSet,
  edges: Array[LetterMap[Int]]) {

  override def toString: String =
    edges.iterator.zipWithIndex.map { case (lm, i) =>
      val label = if (accept(i)) s"[$i]" else s"$i"
      s"$label -> $lm"
    }.mkString("\n")

  def get(n: Int, c: Char): Option[Int] =
    edges(n).get(c)

  def accepts(s: String): Boolean = {
    var state = 0
    var i = 0
    while (i < s.length) {
      get(state, s.charAt(i)) match {
        case Some(n) => state = n
        case None => return false
      }
      i += 1
    }
    accept(state)
  }

  def rejects(s: String): Boolean =
    !accepts(s)

  def minimize: Dfa =
    Dfa.minimize(this)

  private def alphabet: List[LetterSet] = {
    def keySets(m: LetterMap[Int]): List[LetterSet] =
      m.iterator.map { case ((c1, c2), _) => LetterSet(c1 to c2) }.toList
    edges.iterator.map(keySets).toList match {
      case h :: t =>
        t.foldLeft(h)((x, y) => LetterSet.venn(x, y).map(_.value))
      case Nil =>
        Nil
    }
  }
}

object Dfa {

  def fromNfa(nfa: Nfa): Dfa = {
    val bldr = new DfaBuilder(nfa.start)
    var queue: List[BitSet] = List(nfa.start)
    var seen: Set[BitSet] = Set.empty
    while (queue.nonEmpty) {
      val st0 = queue.head
      queue = queue.tail
      if (!seen(st0)) {
        seen = seen + st0
        bldr.addNode(st0, st0 intersects nfa.accept)
        nfa.followAll(st0).iterator.foreach { case ((c1, c2), st1) =>
          bldr.addNode(st1, st1 intersects nfa.accept)
          bldr.addEdge(st0, LetterSet(c1 to c2), st1)
          queue = st1 :: queue
        }
      }
    }
    bldr.build
  }

  class DfaBuilder[S: ClassTag](val start: S) {

    override def toString: String = s"DfaBuilder($start, $acceptSet, $edges)"

    var acceptSet: Set[S] =
      Set.empty

    var edges: Map[S, LetterMap[S]] =
      Map(start -> LetterMap.empty[S])

    def addNode(node: S, accepting: Boolean = false): Unit = {
      if (accepting) acceptSet += node
      if (edges.contains(node)) return ()
      edges = edges.updated(node, LetterMap.empty[S])
    }

    def addEdge(src: S, keys: LetterSet, dst: S): Unit = {
      addNode(src)
      addNode(dst)
      edges = edges.updated(src, edges(src) ++ LetterMap(keys, dst))
    }

    def build: Dfa = {
      val vi = new ValueIndex[S]
      require(vi.indexOf(start) == 0) // ensure start is 0
      val count = edges.size
      val accept1 = BitSet(count, acceptSet.map(vi.indexOf))
      val edges1 = new Array[LetterMap[Int]](count)
      edges.foreach { case (src, letterMap) =>
        edges1(vi.indexOf(src)) = letterMap.mapValues(vi.indexOf)
      }
      Dfa(accept1, edges1)
    }
  }

  def minimize(dfa: Dfa): Dfa = {
    type Partition = Set[Int]

    val F: Partition = dfa.accept.toSet
    val Q: Partition = (0 until dfa.edges.length).toSet
    val Sigma: List[LetterSet] = dfa.alphabet

    var P: Set[Partition] = Set(F, Q -- F)
    var W: Set[Partition] = Set(F, Q -- F)

    while (W.nonEmpty) {
      val A = W.head
      W = W - A
      Sigma.foreach { cs =>
        val c = cs.minOption.get
        val X = Q.filter(s0 => dfa.get(s0, c).filter(A(_)).isDefined)
        P.foreach { Y =>
          val XaY = X & Y
          val YmX = Y -- X
          if (XaY.nonEmpty && YmX.nonEmpty) {
            P = P - Y + XaY + YmX
            W =
              if (W(Y)) W - Y + XaY + YmX
              else if (XaY.size <= YmX.size) W + XaY
              else W + YmX
          }
        }
      }
    }

    val m: Map[Int, Int] =
      P.iterator.flatMap { p =>
        if (p.nonEmpty) {
          val m = p.min
          p.iterator.map(n => (n, m))
        } else Iterator.empty
      }.toMap

    val bldr = new DfaBuilder[Int](0)
    var i = 0
    while (i < dfa.edges.length) {
      val x = m.getOrElse(i, i)
      if (x == i) {
        bldr.addNode(x, dfa.accept(x))
        dfa.edges(i).iterator.foreach { case ((c1, c2), j) =>
          val y = m.getOrElse(j, j)
          bldr.addNode(y, dfa.accept(y))
          bldr.addEdge(x, LetterSet(c1 to c2), y)
        }
      }
      i += 1
    }
    bldr.build
  }
}
