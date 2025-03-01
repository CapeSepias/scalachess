package benchmarks

import org.openjdk.jmh.annotations._

import cats.syntax.all.*

import chess.PerftTestCase

import java.util.concurrent.TimeUnit
import chess.format.pgn.Fixtures
import chess.format.pgn.Parser

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 10, timeUnit = TimeUnit.SECONDS, time = 3)
@Warmup(iterations = 3, timeUnit = TimeUnit.SECONDS, time = 3)
@Fork(2)
class ParserBench {

  var games = Fixtures.gamesForPerfTest
  @Benchmark
  def PgnParser(): Boolean =
    games.traverse(Parser.full).isValid

}
