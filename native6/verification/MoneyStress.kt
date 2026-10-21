import com.alsafa.native6.*
import kotlin.random.Random

fun main() {
    val random = Random(6042)
    repeat(100_000) {
        val value = random.nextLong(0L, 1_000_000_000L)
        val total = random.nextLong(1L, 1_000_000L)
        val part = random.nextLong(0L, total + 1L)
        val allocated = proportional(value, part, total)
        val remainder = proportional(value, total - part, total)
        check(kotlin.math.abs((allocated + remainder) - value) <= 1L) {
            "Allocation discrepancy: value=$value, part=$part, total=$total"
        }
        check(proportional(value, total, total) == value)
    }
    repeat(100_000) {
        val size = random.nextInt(1, 20)
        val lines = List(size) { random.nextLong(0L, 1_000_000L) }
        val gross = lines.sum()
        if (gross == 0L) return@repeat
        val discount = random.nextLong(0L, gross)
        val net = allocateInvoiceNet(lines, discount)
        check(net.size == size && net.all { it >= 0L })
        check(net.sum() == gross - discount)
    }
    repeat(100_000) {
        val originalQty = random.nextInt(1, 101).toLong()
        val originalValue = random.nextLong(0L, 100_000L)
        var returned = 0L
        var credited = 0L
        while (returned < originalQty) {
            val quantity = random.nextLong(1L, originalQty - returned + 1L)
            val increment = allocateReturnIncrement(originalValue, originalQty, returned, credited, quantity)
            check(increment >= 0L)
            credited = add(credited, increment)
            returned = add(returned, quantity)
        }
        check(credited == originalValue)
    }
    println("PASS: 100000 proportional + 100000 invoice-discount + 100000 partial-return scenarios")
}
