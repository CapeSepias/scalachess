package chess
package variant

import cats.data.Validated
import cats.syntax.option.*
import scala.annotation.nowarn

import chess.format.EpdFen

// Correctness depends on singletons for each variant ID
abstract class Variant private[variant] (
    val id: Variant.Id,
    val key: Variant.LilaKey,
    val uciKey: Variant.UciKey,
    val name: String,
    val shortName: String,
    val title: String,
    val standardInitialPosition: Boolean
):

  def pieces: Map[Pos, Piece]

  inline def standard      = this == Standard
  inline def chess960      = this == Chess960
  inline def fromPosition  = this == FromPosition
  inline def kingOfTheHill = this == KingOfTheHill
  inline def threeCheck    = this == ThreeCheck
  inline def antichess     = this == Antichess
  inline def atomic        = this == Atomic
  inline def horde         = this == Horde
  inline def racingKings   = this == RacingKings
  inline def crazyhouse    = this == Crazyhouse

  inline def exotic = !standard

  def allowsCastling = !castles.isEmpty

  protected val backRank = Vector(Rook, Knight, Bishop, Queen, King, Bishop, Knight, Rook)

  def castles: Castles = Castles.all

  val initialFen: EpdFen = EpdFen.initial

  def isValidPromotion(promotion: Option[PromotableRole]) =
    promotion match
      case None                                 => true
      case Some(Queen | Rook | Knight | Bishop) => true
      case _                                    => false

  def validMoves(situation: Situation): Map[Pos, List[Move]] =
    situation.actors.collect {
      case actor if actor.moves.nonEmpty => actor.pos -> actor.moves
    }.toMap

  // Optimised for performance
  def pieceThreatened(board: Board, color: Color, to: Pos, filter: Piece => Boolean = _ => true): Boolean =
    board.pieces exists {
      case (pos, piece) if piece.color == color && filter(piece) && piece.eyes(pos, to) =>
        (!piece.role.projection) || piece.role.dir(pos, to).exists {
          longRangeThreatens(board, pos, _, to)
        }
      case _ => false
    }

  def kingThreatened(board: Board, color: Color, to: Pos, filter: Piece => Boolean = _ => true) =
    pieceThreatened(board, color, to, filter)

  def kingSafety(m: Move, filter: Piece => Boolean, kingPos: Option[Pos]): Boolean =
    !kingPos.exists { kingThreatened(m.after, !m.color, _, filter) }

  def kingSafety(a: Actor, m: Move): Boolean =
    kingSafety(
      m,
      if ((a.piece is King) || a.check) (_ => true) else (_.role.projection),
      if (a.piece.role == King) None else a.board kingPosOf a.color
    )

  def longRangeThreatens(board: Board, p: Pos, dir: Direction, to: Pos): Boolean =
    dir(p) exists { next =>
      next == to || (!board.pieces.contains(next) && longRangeThreatens(board, next, dir, to))
    }

  def move(
      situation: Situation,
      from: Pos,
      to: Pos,
      promotion: Option[PromotableRole]
  ): Validated[String, Move] =

    // Find the move in the variant specific list of valid moves
    def findMove(from: Pos, to: Pos) = situation.moves get from flatMap (_.find(_.dest == to))

    for {
      actor <- situation.board.actors get from toValid s"No piece on ${from.key}"
      _ <-
        if (actor is situation.color) Validated.valid(actor)
        else Validated.invalid(s"Not my piece on ${from.key}")
      m1 <- findMove(from, to) toValid s"Piece on ${from.key} cannot move to ${to.key}"
      m2 <- m1 withPromotion promotion toValid s"Piece on ${from.key} cannot promote to $promotion"
      m3 <-
        if (isValidPromotion(promotion)) Validated.valid(m2)
        else Validated.invalid(s"Cannot promote to $promotion in this game mode")
    } yield m3

  def drop(situation: Situation, role: Role, pos: Pos): Validated[String, Drop] =
    Validated.invalid(s"$this variant cannot drop $situation $role $pos")

  def staleMate(situation: Situation): Boolean = !situation.check && situation.moves.isEmpty

  def checkmate(situation: Situation) = situation.check && situation.moves.isEmpty

  // In most variants, the winner is the last player to have played and there is a possibility of either a traditional
  // checkmate or a variant end condition
  def winner(situation: Situation): Option[Color] =
    if (situation.checkMate || specialEnd(situation)) Option(!situation.color) else None

  @nowarn def specialEnd(situation: Situation) = false

  @nowarn def specialDraw(situation: Situation) = false

  /** Returns the material imbalance in pawns (overridden in Antichess)
    */
  def materialImbalance(board: Board): Int =
    board.pieces.values.foldLeft(0) { case (acc, Piece(color, role)) =>
      Role.valueOf(role).fold(acc) { value =>
        acc + value * color.fold(1, -1)
      }
    }

  /** Returns true if neither player can win. The game should end immediately.
    */
  def isInsufficientMaterial(board: Board) = InsufficientMatingMaterial(board)

  /** Returns true if the other player cannot win. This is relevant when the
    * side to move times out or disconnects. Instead of losing on time,
    * the game should be drawn.
    */
  def opponentHasInsufficientMaterial(situation: Situation) =
    InsufficientMatingMaterial(situation.board, !situation.color)

  // Some variants have an extra effect on the board on a move. For example, in Atomic, some
  // pieces surrounding a capture explode
  def hasMoveEffects = false

  /** Applies a variant specific effect to the move. This helps decide whether a king is endangered by a move, for
    * example
    */
  def addVariantEffect(move: Move): Move = move

  def fiftyMoves(history: History): Boolean = history.halfMoveClock >= 100

  def isIrreversible(move: Move): Boolean =
    (move.piece is Pawn) || move.captures || move.promotes || move.castles

  /** Once a move has been decided upon from the available legal moves, the board is finalized
    */
  @nowarn def finalizeBoard(board: Board, uci: format.Uci, captured: Option[Piece]): Board = board

  protected def pawnsOnPromotionRank(board: Board, color: Color) =
    board.pieces.exists {
      case (pos, Piece(c, Pawn)) if c == color && pos.rank == color.promotablePawnRank => true
      case _                                                                           => false
    }

  protected def pawnsOnBackRank(board: Board, color: Color) =
    board.pieces.exists {
      case (pos, Piece(c, Pawn)) if c == color && pos.rank == color.backRank => true
      case _                                                                 => false
    }

  protected def validSide(board: Board, strict: Boolean)(color: Color) =
    val roles = board rolesOf color
    roles.count(_ == King) == 1 &&
    (!strict || { roles.count(_ == Pawn) <= 8 && roles.lengthCompare(16) <= 0 }) &&
    !pawnsOnPromotionRank(board, color) &&
    !pawnsOnBackRank(board, color)

  def valid(board: Board, strict: Boolean) = Color.all forall validSide(board, strict)

  val roles = List(Rook, Knight, King, Bishop, King, Queen, Pawn)

  val promotableRoles: List[PromotableRole] = List(Queen, Rook, Bishop, Knight)

  lazy val rolesByPgn: Map[Char, Role] = roles.mapBy(_.pgn)

  lazy val rolesPromotableByPgn: Map[Char, PromotableRole] = promotableRoles.mapBy(_.pgn)

  def isUnmovedPawn(color: Color, pos: Pos) = pos.rank == color.fold(Rank.Second, Rank.Seventh)

  override def toString = s"Variant($name)"

  override def equals(that: Any): Boolean = this eq that.asInstanceOf[AnyRef]

  override def hashCode: Int = id.value

object Variant:

  opaque type Id = Int
  object Id extends OpaqueInt[Id]

  opaque type LilaKey = String
  object LilaKey extends OpaqueString[LilaKey]

  opaque type UciKey = String
  object UciKey extends OpaqueString[UciKey]

  object list:
    val all: List[Variant] = List(
      Standard,
      Crazyhouse,
      Chess960,
      FromPosition,
      KingOfTheHill,
      ThreeCheck,
      Antichess,
      Atomic,
      Horde,
      RacingKings
    )
    val byId  = all.mapBy(_.id)
    val byKey = all.mapBy(_.key)

    val openingSensibleVariants: Set[Variant] = Set(
      chess.variant.Standard,
      chess.variant.Crazyhouse,
      chess.variant.ThreeCheck,
      chess.variant.KingOfTheHill
    )
    val divisionSensibleVariants: Set[Variant] = Set(
      chess.variant.Standard,
      chess.variant.Chess960,
      chess.variant.ThreeCheck,
      chess.variant.KingOfTheHill,
      chess.variant.FromPosition
    )

  inline def default: Variant = Standard

  inline def apply(inline id: Id): Option[Variant]       = list.byId get id
  inline def apply(inline key: LilaKey): Option[Variant] = list.byKey get key
  def orDefault(id: Id): Variant                         = apply(id) | default
  def orDefault(key: LilaKey): Variant                   = apply(key) | default
  def idOrDefault(id: Option[Id]): Variant               = id.flatMap(apply(_)) | default
  def orDefault(key: Option[LilaKey]): Variant           = key.flatMap(apply(_)) | default

  def byName(name: String): Option[Variant] =
    list.all.find(_.name.toLowerCase == name.toLowerCase)

  def exists(id: Id): Boolean = list.byId contains id

  private[variant] def symmetricRank(rank: IndexedSeq[Role]): Map[Pos, Piece] =
    File.all.zip(rank).map { (x, role) => Pos(x, Rank.First) -> (White - role) } ++
      File.all.map { Pos(_, Rank.Second) -> White.pawn } ++
      File.all.map { Pos(_, Rank.Seventh) -> Black.pawn } ++
      File.all.zip(rank).map { (x, role) => Pos(x, Rank.Eighth) -> (Black - role) } toMap
