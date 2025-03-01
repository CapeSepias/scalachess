package chess
package format
package pgn

import cats.implicits.*

// Nf6
opaque type SanStr = String
object SanStr extends OpaqueString[SanStr]

// 1. d4 Nf6 2. c4 e6 3. g3
opaque type PgnMovesStr = String
object PgnMovesStr extends OpaqueString[PgnMovesStr]

case class Pgn(tags: Tags, turns: List[Turn], initial: Initial = Initial.empty):

  // index is NOT a full move turn!
  private def updateTurnAt(index: Int, f: Turn => Turn) =
    // val index = fullMove.value - 1
    (turns lift index).fold(this) { turn =>
      copy(turns = turns.updated(index, f(turn)))
    }

  def updatePly(ply: Ply, f: Move => Move) =
    updateTurnAt((ply + 1).value / 2 - 1, _.update(!ply.color, f))

  def updateLastPly(f: Move => Move) = updatePly(Ply(nbPlies), f)

  def nbPlies = turns.foldLeft(0)(_ + _.count)

  def moves =
    turns.flatMap { t =>
      List(t.white, t.black).flatten
    }

  def withEvent(title: String) =
    copy(
      tags = tags + Tag(_.Event, title)
    )

  def render: String = {
    val initStr =
      if (initial.comments.nonEmpty) initial.comments.mkString("{ ", " } { ", " }\n")
      else ""
    val turnStr   = turns mkString " "
    val resultStr = tags(_.Result) | ""
    val endStr =
      if (turnStr.nonEmpty) s" $resultStr"
      else resultStr
    s"$tags\n\n$initStr$turnStr$endStr"
  }.trim

  override def toString = render

case class Initial(comments: List[String] = Nil)

object Initial:
  val empty = Initial(Nil)

case class Turn(
    number: Int,
    white: Option[Move],
    black: Option[Move]
):

  def update(color: Color, f: Move => Move) =
    color.fold(
      copy(white = white map f),
      copy(black = black map f)
    )

  def updateLast(f: Move => Move) = {
    black.map(m => copy(black = f(m).some)) orElse
      white.map(m => copy(white = f(m).some))
  } | this

  def isEmpty = white.isEmpty && black.isEmpty

  def plyOf(color: Color) = number * 2 - color.fold(1, 0)

  def count = List(white, black) count (_.isDefined)

  override def toString =
    val text = (white, black) match
      case (Some(w), Some(b)) if w.isLong => s" $w $number... $b"
      case (Some(w), Some(b))             => s" $w $b"
      case (Some(w), None)                => s" $w"
      case (None, Some(b))                => s".. $b"
      case _                              => ""
    s"$number.$text"

object Turn:

  def fromMoves(moves: List[Move], ply: Ply): List[Turn] = {
    moves.foldLeft((List[Turn](), ply)) {
      case ((turns, p), move) if (p.value & 1) == 1 =>
        (Turn((p.value + 1) / 2, move.some, none) :: turns) -> (p + 1)
      case ((Nil, p), move) =>
        (Turn((p.value + 1) / 2, none, move.some) :: Nil) -> (p + 1)
      case ((t :: tt, p), move) =>
        (t.copy(black = move.some) :: tt) -> (p + 1)
    }
  }._1.reverse

case class Move(
    san: SanStr,
    comments: List[String] = Nil,
    glyphs: Glyphs = Glyphs.empty,
    opening: Option[String] = None,
    result: Option[String] = None,
    variations: List[List[Turn]] = Nil,
    // time left for the user who made the move, after he made it
    secondsLeft: Option[Int] = None
):

  def isLong = comments.nonEmpty || variations.nonEmpty || secondsLeft.isDefined

  private def clockString: Option[String] =
    secondsLeft.map(seconds => "[%clk " + Move.formatPgnSeconds(seconds) + "]")

  override def toString =
    val glyphStr = glyphs.toList.map {
      case glyph if glyph.id <= 6 => glyph.symbol
      case glyph                  => s" $$${glyph.id}"
    }.mkString
    val commentsOrTime =
      if (comments.nonEmpty || secondsLeft.isDefined || opening.isDefined || result.isDefined)
        List(clockString, opening, result).flatten
          .:::(comments map Move.noDoubleLineBreak)
          .map { text =>
            s" { $text }"
          }
          .mkString
      else ""
    val variationString =
      if (variations.isEmpty) ""
      else variations.map(_.mkString(" (", " ", ")")).mkString(" ")
    s"$san$glyphStr$commentsOrTime$variationString"

object Move:

  private val noDoubleLineBreakRegex = "(\r?\n){2,}".r

  private def noDoubleLineBreak(txt: String) =
    noDoubleLineBreakRegex.replaceAllIn(txt, "\n")

  private def formatPgnSeconds(t: Int) =
    periodFormatter.print(
      org.joda.time.Duration.standardSeconds(t).toPeriod
    )

  private[this] val periodFormatter = new org.joda.time.format.PeriodFormatterBuilder().printZeroAlways
    .minimumPrintedDigits(1)
    .appendHours
    .appendSeparator(":")
    .minimumPrintedDigits(2)
    .appendMinutes
    .appendSeparator(":")
    .appendSeconds
    .toFormatter
