package chess

import format.{ EpdFen, Fen, Uci }
import chess.format.pgn.SanStr

class ReplayTest extends ChessTest:

  "from prod" in {
    "replay from position close chess" in {
      val fen   = EpdFen("""8/rnbqkbnr/pppppppp/8/8/PPPPPPPP/RNBQKBNR/8 w - - 0 1""")
      val moves = SanStr from """d4 d5 Nf4 Nf5 g4 g5 gxf5 exf5""".split(' ').toList
      Replay.gameMoveWhileValid(moves, fen, variant.FromPosition) must beLike { case (_, games, None) =>
        games.size must_== 8
        games(1)._2._2 === "d5"
      }
    }
  }

  "castle rights" in {
    "bongcloud attack" in {
      Replay.situationsFromUci(
        moves = List(Uci("e2e4"), Uci("e7e5"), Uci("e1e2")).flatten,
        initialFen = None,
        variant = variant.Standard
      ) must beValid.like { situations =>
        situations.map(Fen.write) must_== List(
          EpdFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"),
          EpdFen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"),
          EpdFen("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 1"),
          EpdFen("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPPKPPP/RNBQ1BNR b kq - 1 1")
        )
      }
    }
  }

  "variant situations" in {
    "racing kings" in {
      Replay.situations(
        sans = SanStr.from("Be3 Ne4 Rg3 Nxe3 Rxe3" split " "),
        initialFen = None,
        variant = chess.variant.RacingKings
      ) must beValid
    }
  }
