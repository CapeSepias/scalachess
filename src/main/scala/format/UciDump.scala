package chess
package format

import cats.data.Validated

import chess.variant.Variant

object UciDump:

  // a2a4, b8c6
  def apply(force960Notation: Boolean)(replay: Replay): List[String] =
    replay.chronoMoves map move(replay.setup.board.variant, force960Notation)

  def apply(
      moves: Seq[pgn.SanStr],
      initialFen: Option[EpdFen],
      variant: Variant,
      force960Notation: Boolean = false
  ): Validated[String, List[String]] =
    if (moves.isEmpty) Validated.valid(Nil)
    else Replay(moves, initialFen, variant) andThen (_.valid) map apply(force960Notation)

  def move(variant: Variant, force960Notation: Boolean = false)(mod: MoveOrDrop): String =
    mod match
      case Left(m) =>
        m.castle.fold(m.toUci.uci) {
          case ((kf, kt), (rf, _))
              if force960Notation || kf == kt || variant.chess960 || variant.fromPosition =>
            kf.key + rf.key
          case ((kf, kt), _) => kf.key + kt.key
        }
      case Right(d) => d.toUci.uci
