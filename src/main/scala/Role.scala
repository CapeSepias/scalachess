package chess

sealed trait Role:
  val forsyth: Char
  lazy val forsythUpper: Char = forsyth.toUpper
  lazy val pgn: Char          = forsythUpper
  lazy val name               = toString.toLowerCase
  val projection: Boolean
  val dirs: Directions
  def dir(from: Pos, to: Pos): Option[Direction]

sealed trait PromotableRole extends Role

/** Promotable in antichess. */
case object King extends PromotableRole:
  val forsyth                 = 'k'
  val dirs: Directions        = Queen.dirs
  def dir(from: Pos, to: Pos) = None
  val projection              = false

case object Queen extends PromotableRole:
  val forsyth                 = 'q'
  val dirs: Directions        = Rook.dirs ::: Bishop.dirs
  def dir(from: Pos, to: Pos) = Rook.dir(from, to) orElse Bishop.dir(from, to)
  val projection              = true

case object Rook extends PromotableRole:
  val forsyth          = 'r'
  val dirs: Directions = List(_.up, _.down, _.left, _.right)
  def dir(from: Pos, to: Pos) =
    if (to ?| from)
      Option(if (to ?^ from) (_.up) else (_.down))
    else if (to ?- from)
      Option(if (to ?< from) (_.left) else (_.right))
    else None
  val projection = true

case object Bishop extends PromotableRole:
  val forsyth          = 'b'
  val dirs: Directions = List(_.upLeft, _.upRight, _.downLeft, _.downRight)
  def dir(from: Pos, to: Pos) =
    if (to onSameDiagonal from)
      Option(if (to ?^ from) {
        if (to ?< from) (_.upLeft) else (_.upRight)
      } else {
        if (to ?< from) (_.downLeft) else (_.downRight)
      })
    else None
  val projection = true

case object Knight extends PromotableRole:
  val forsyth = 'n'
  val dirs: Directions = List(
    p => Pos.at(p.file.index - 1, p.rank.index + 2),
    p => Pos.at(p.file.index - 1, p.rank.index - 2),
    p => Pos.at(p.file.index + 1, p.rank.index + 2),
    p => Pos.at(p.file.index + 1, p.rank.index - 2),
    p => Pos.at(p.file.index - 2, p.rank.index + 1),
    p => Pos.at(p.file.index - 2, p.rank.index - 1),
    p => Pos.at(p.file.index + 2, p.rank.index + 1),
    p => Pos.at(p.file.index + 2, p.rank.index - 1)
  )
  def dir(from: Pos, to: Pos) = None
  val projection              = false

case object Pawn extends Role:
  val forsyth                 = 'p'
  val dirs: Directions        = Nil
  def dir(from: Pos, to: Pos) = None
  val projection              = false

object Role:

  val all: List[Role]                                   = List(King, Queen, Rook, Bishop, Knight, Pawn)
  val allPromotable: List[PromotableRole]               = List(Queen, Rook, Bishop, Knight, King)
  val allByForsyth: Map[Char, Role]                     = all.mapBy(_.forsyth)
  val allByPgn: Map[Char, Role]                         = all.mapBy(_.pgn)
  val allByName: Map[String, Role]                      = all.mapBy(_.name)
  val allPromotableByName: Map[String, PromotableRole]  = allPromotable.mapBy(_.toString)
  val allPromotableByForsyth: Map[Char, PromotableRole] = allPromotable.mapBy(_.forsyth)
  val allPromotableByPgn: Map[Char, PromotableRole]     = allPromotable.mapBy(_.pgn)

  def forsyth(c: Char): Option[Role] = allByForsyth get c

  def promotable(c: Char): Option[PromotableRole] =
    allPromotableByForsyth get c

  def promotable(name: String): Option[PromotableRole] =
    allPromotableByName get name.capitalize

  def promotable(name: Option[String]): Option[PromotableRole] =
    name flatMap promotable

  def valueOf(r: Role): Option[Int] =
    r match
      case Pawn   => Option(1)
      case Knight => Option(3)
      case Bishop => Option(3)
      case Rook   => Option(5)
      case Queen  => Option(9)
      case King   => None
