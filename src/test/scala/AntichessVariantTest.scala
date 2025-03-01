package chess

import cats.syntax.option.*
import org.specs2.matcher.ValidatedMatchers

import chess.format.{ EpdFen, Fen }
import chess.format.pgn.Reader
import chess.variant.Antichess

class AntichessVariantTest extends ChessTest with ValidatedMatchers:

  // Random PGN taken from FICS
  val fullGame =
    """[Event "3 0 rated antichess"]
[Site "freechess.org"]
[Date "2014.12.12"]
[Round "?"]
[White "Gnarus"]
[Black "CatNail"]
[Result "0-1"]
[ResultDescription "CatNail wins by losing all material"]
[WhiteElo "1778"]
[BlackElo "2025"]
[PlyCount "67"]
[Variant "antichess"]
[TimeControl "180+0"]
[WhiteClock "00:03:00.0"]
[BlackClock "00:03:00.0"]
[WhiteLagMillis "1470"]
[BlackLagMillis "0"]
[WhiteRemainingMillis "139297"]
[BlackRemainingMillis "173163"]
[WhiteOnTop "0"]

1. g3 {[%emt 0.000]} h6 {[%emt 0.000]} 2. Nh3 {[%emt 1.156]} a6 {[%emt 0.221]} 3. Ng5 {[%emt 0.454]}
hxg5 {[%emt 0.220]} 4. Bh3 {[%emt 0.671]} Rxh3 {[%emt 0.221]} 5. Rg1 {[%emt 0.516]}
Rxg3 {[%emt 0.201]} 6. hxg3 {[%emt 0.578]} b6 {[%emt 0.201]} 7. Na3 {[%emt 1.204]}
g6 {[%emt 0.225]} 8. Nb5 {[%emt 0.436]} axb5 {[%emt 0.219]} 9. a4 {[%emt 0.735]}
Rxa4 {[%emt 0.206]} 10. Rxa4 {[%emt 0.875]} bxa4 {[%emt 0.221]} 11. b3 {[%emt 0.296]}
axb3 {[%emt 0.201]} 12. cxb3 {[%emt 0.109]} Nh6 {[%emt 0.200]} 13. Ba3 {[%emt 0.656]}
Na6 {[%emt 0.221]} 14. Bxe7 {[%emt 0.703]} Bxe7 {[%emt 0.223]} 15. b4 {[%emt 0.656]}
Bxb4 {[%emt 0.200]} 16. g4 {[%emt 5.594]} Bxd2 {[%emt 0.201]} 17. Qxd2 {[%emt 0.906]}
Nxg4 {[%emt 0.199]} 18. Qxg5 {[%emt 0.907]} Nxf2 {[%emt 0.221]} 19. Qxd8 {[%emt 4.313]}
Kxd8 {[%emt 0.220]} 20. Rxg6 {[%emt 1.718]} fxg6 {[%emt 0.221]} 21. Kxf2 {[%emt 0.767]}
c5 {[%emt 0.200]} 22. Kf3 {[%emt 1.594]} Ke8 {[%emt 0.201]} 23. e4 {[%emt 0.939]}
Nb8 {[%emt 0.201]} 24. e5 {[%emt 0.484]} d5 {[%emt 0.201]} 25. exd6 {[%emt 0.437]}
Nd7 {[%emt 0.201]} 26. Ke3 {[%emt 3.984]} Ke7 {[%emt 0.201]} 27. dxe7 {[%emt 1.422]}
g5 {[%emt 0.200]} 28. Kd4 {[%emt 1.172]} cxd4 {[%emt 0.200]} 29. e8=R {[%emt 4.329]}
Ne5 {[%emt 0.364]} 30. Rxc8 {[%emt 0.469]} Nc4 {[%emt 0.201]} 31. Rxc4 {[%emt 1.592]}
b5 {[%emt 0.223]} 32. Rxd4 {[%emt 0.359]} b4 {[%emt 0.202]} 33. Rxb4 {[%emt 0.500]}
g4 {[%emt 0.200]} 34. Rxg4 {[%emt 0.172]} 0-1"""

  "Antichess " should {

    "Allow an opening move for white taking into account a player may move without taking if possible" in {
      val startingPosition = Game(Antichess)
      val afterFirstMove   = startingPosition.playMove(Pos.E2, Pos.E4, None)

      afterFirstMove must beValid.like { newGame =>
        val fen = Fen write newGame
        fen mustEqual EpdFen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b - - 0 1")
      }
    }

    "Not allow a player to make a non capturing move if a capturing move is available" in {
      val game             = Game(Antichess)
      val gameAfterOpening = game.playMoves((Pos.E2, Pos.E4), (Pos.F7, Pos.F5))

      val invalidGame = gameAfterOpening flatMap (_.playMove(Pos.H2, Pos.H4))

      invalidGame must beInvalid("Piece on h2 cannot move to h4")
    }

    "A situation in antichess should only present the capturing moves if the player can capture" in {
      val game             = Game(Antichess)
      val gameAfterOpening = game.playMoves((Pos.E2, Pos.E4), (Pos.F7, Pos.F5))

      gameAfterOpening must beValid.like { case newGame =>
        newGame.situation.moves.size must beEqualTo(1)
        newGame.situation.moves.values.find(_.exists(_.captures == false)) must beNone
      }

    }

    "Allow a capturing move to be made" in {
      val game = Game(Antichess).playMoves((Pos.E2, Pos.E4), (Pos.F7, Pos.F5), (Pos.E4, Pos.F5))
      game must beValid
    }

    "Not permit a player to castle" in {
      // Castling is not allowed in antichess
      val game = Game(Antichess).playMoves(
        (Pos.E2, Pos.E4),
        (Pos.E7, Pos.E5),
        (Pos.F1, Pos.E2),
        (Pos.G8, Pos.H6),
        (Pos.G1, Pos.H3)
      )

      val possibleDestinations = game flatMap (_.board.destsFrom(Pos.E1).toValid("king has no destinations"))

      possibleDestinations must beValid.like { case dests =>
        // G1 (to castle) should not be a valid destination
        dests must beEqualTo(List(Pos.F1))
      }

    }

    "Not allow a king to be put into check" in {
      val game = Game(Antichess).playMoves(
        Pos.E2 -> Pos.E4,
        Pos.E7 -> Pos.E5,
        Pos.D1 -> Pos.H5
      )

      game must beValid.like { case newGame =>
        newGame.situation.check must beFalse
      }
    }

    "Allow kings to be captured" in {
      val game = Game(Antichess).playMoves(
        Pos.E2 -> Pos.E4,
        Pos.E7 -> Pos.E5,
        Pos.D1 -> Pos.H5,
        Pos.F7 -> Pos.F6,
        Pos.H5 -> Pos.E8
      )

      game must beValid.like { case newGame =>
        newGame.board.kingPosOf(Color.black) must beNone
      }
    }

    "Not allow a king to be check mated" in {
      val game = Game(Antichess).playMoves(
        Pos.F2 -> Pos.F3,
        Pos.E7 -> Pos.E6,
        Pos.G2 -> Pos.G4,
        Pos.D8 -> Pos.H4
      )

      game must beValid.like { case newGame =>
        newGame.situation.checkMate must beFalse
      }
    }

    "Allow a pawn to be promoted to a king" in {
      val position     = EpdFen("8/5P2/8/2b5/8/8/4B3/8 w - -")
      val originalGame = fenToGame(position, Antichess)

      val newGame = originalGame flatMap (_.apply(Pos.F7, Pos.F8, Option(King))) map (_._1)

      newGame must beValid {
        (_: Game).board(Pos.F8).mustEqual(Option(White - King))
      }

    }

    "Be drawn when there are only opposite colour bishops remaining" in {
      val position     = EpdFen("8/2b5/8/8/8/6Q1/4B3/8 b - -")
      val originalGame = fenToGame(position, Antichess)

      val newGame = originalGame flatMap (_.apply(Pos.C7, Pos.G3, None)) map (_._1)

      newGame must beValid.like { case (drawnGame: Game) =>
        drawnGame.situation.end must beTrue
        drawnGame.situation.autoDraw must beTrue
        drawnGame.situation.winner must beNone
        drawnGame.situation.status must beSome(Status.Draw)
      }
    }

    "Be drawn on multiple bishops on the opposite color" in {
      val position     = EpdFen("8/6P1/8/8/1b6/8/8/5B2 w - -")
      val originalGame = fenToGame(position, Antichess)

      val newGame = originalGame flatMap (_.apply(Pos.G7, Pos.G8, Bishop.some)) map (_._1)

      newGame must beValid.like { case (drawnGame: Game) =>
        drawnGame.situation.end must beTrue
        drawnGame.situation.autoDraw must beTrue
        drawnGame.situation.winner must beNone
        drawnGame.situation.status must beSome(Status.Draw)
      }

    }

    "Not be drawn when the black and white bishops are on the same coloured squares " in {
      val position     = EpdFen("7b/8/1p6/8/8/8/5B2/8 w - -")
      val originalGame = fenToGame(position, Antichess)

      val newGame = originalGame flatMap (_.apply(Pos.F2, Pos.B6, None)) map (_._1)

      newGame must beValid.like { case nonDrawnGame =>
        nonDrawnGame.situation.end must beFalse
        nonDrawnGame.situation.autoDraw must beFalse
        nonDrawnGame.situation.winner must beNone
      }
    }

    "Be drawn when there are only opposite colour bishops and pawns which could not attack those bishops remaining" in {
      val position     = EpdFen("8/6p1/4B1P1/4p3/4P3/8/2p5/8 b - - 1 28")
      val originalGame = fenToGame(position, Antichess)

      val newGame = originalGame flatMap (_.apply(Pos.C2, Pos.C1, Option(Bishop))) map (_._1)

      newGame must beValid.like { case (drawnGame: Game) =>
        drawnGame.situation.end must beTrue
        drawnGame.situation.autoDraw must beTrue
        drawnGame.situation.status must beSome(Status.Draw)
      }
    }

    "Not be drawn on opposite color bishops but with pawns that could be forced to attack a bishop" in {
      val position     = EpdFen("8/6p1/1B4P1/4p3/4P3/8/3p4/8 b - -")
      val originalGame = fenToGame(position, Antichess)

      val newGame = originalGame flatMap (_.apply(Pos.D2, Pos.D1, Option(Bishop))) map (_._1)

      newGame must beValid.like { case nonDrawnGame =>
        nonDrawnGame.situation.end must beFalse
        nonDrawnGame.situation.autoDraw must beFalse
        nonDrawnGame.situation.status must beNone
      }
    }

    "Not be drawn where a white bishop can attack a black pawn in an almost closed position" in {
      val position     = EpdFen("5b2/1P4p1/4B1P1/4p3/4P3/8/8/8 w - -")
      val originalGame = fenToGame(position, Antichess)

      val newGame = originalGame flatMap (_.apply(Pos.B7, Pos.B8, Bishop.some)) map (_._1)

      newGame must beValid.like { case nonDrawnGame =>
        nonDrawnGame.situation.end must beFalse
        nonDrawnGame.situation.autoDraw must beFalse
        nonDrawnGame.situation.status must beNone
      }

    }

    "Not be drawn where a pawn is unattackable, but is blocked by a bishop, not a pawn" in {
      val position     = EpdFen("8/8/4BbP1/4p3/4P3/8/8/8 b - -")
      val originalGame = fenToGame(position, Antichess)

      val newGame = originalGame flatMap (_.playMoves(Pos.F6 -> Pos.G7))

      newGame must beValid.like { case nonDrawnGame =>
        nonDrawnGame.situation.end must beFalse
        nonDrawnGame.situation.autoDraw must beFalse
        nonDrawnGame.situation.status must beNone
      }
    }

    "Opponent has insufficient material when there are only two remaining knights on same color squares" in {
      val position     = EpdFen("8/8/3n2N1/8/8/8/8/8 w - -")
      val originalGame = fenToGame(position, Antichess)

      val newGame = originalGame flatMap (_.playMoves(Pos.G6 -> Pos.F4))

      newGame must beValid.like {
        _.situation.opponentHasInsufficientMaterial must beTrue
      }
    }

    "Opponent has sufficient material when there are only two remaining knights on opposite color squares" in {
      val position     = EpdFen("7n/8/8/8/8/8/8/N7 w - -")
      val originalGame = fenToGame(position, Antichess)

      val newGame = originalGame flatMap (_.playMoves(Pos.A1 -> Pos.B3))

      newGame must beValid.like {
        _.situation.opponentHasInsufficientMaterial must beFalse
      }
    }

    "Not be drawn on insufficient mating material" in {
      val position  = EpdFen("4K3/8/1b6/8/8/8/5B2/3k4 b - -")
      val maybeGame = fenToGame(position, Antichess)

      maybeGame must beValid.like { case game =>
        game.situation.end must beFalse
      }
    }

    "Be drawn on a three move repetition" in {
      val game = Game(Antichess)

      val moves = List((Pos.G1, Pos.F3), (Pos.G8, Pos.F6), (Pos.F3, Pos.G1), (Pos.F6, Pos.G8))
      val repeatedMoves: List[(Pos, Pos)] = List.fill(3)(moves).flatten

      val drawnGame = game.playMoveList(repeatedMoves)

      drawnGame must beValid.like { case g =>
        g.situation.threefoldRepetition must beTrue
      }

    }

    "Successfully play through a full game until one player loses all their pieces" in {
      val game = Reader.full(fullGame)

      game must beValid.like { case Reader.Result.Complete(replay) =>
        val game = replay.state

        game.situation.end must beTrue

        // In antichess, the player who has just lost all their pieces is the winner
        game.situation.winner must beSome(Black)
      }
    }

    "Win on a traditional stalemate where the player has no valid moves" in {
      val position  = EpdFen("8/p7/8/P7/8/8/8/8 w - -")
      val maybeGame = fenToGame(position, Antichess)

      val drawnGame = maybeGame flatMap (_.playMoves((Pos.A5, Pos.A6)))

      drawnGame must beValid.like { case game =>
        game.situation.end must beTrue
        game.situation.winner must beSome(Black)
      }
    }

    "Stalemate is a win - second test" in {
      val fen       = EpdFen("2Q5/8/p7/8/8/8/6PR/8 w - -")
      val maybeGame = fenToGame(fen, Antichess)

      val drawnGame = maybeGame flatMap (_.playMoves((Pos.C8, Pos.A6)))

      drawnGame must beValid.like { case game =>
        game.situation.end must beTrue
        game.situation.status must beSome(Status.VariantEnd)
        game.situation.winner must beSome(Black)
      }
    }

  }
