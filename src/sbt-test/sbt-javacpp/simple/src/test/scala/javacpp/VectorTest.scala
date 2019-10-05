package javacpp;

import org.scalatest._
import org.bytedeco.javacpp._

class VectorTest extends FlatSpec with BeforeAndAfterAll {

  it should "test PointerVectorVector" in {
    val v = new PointerVectorVector(13)
    v.resize(0, 42) // v[0].resize(42)
    val p = new Pointer() {
      address = 0xDEADBEEFL
    }
    v.put(0, 0, p) // v[0][0] = p
    assert(!v.empty)
  }
}
