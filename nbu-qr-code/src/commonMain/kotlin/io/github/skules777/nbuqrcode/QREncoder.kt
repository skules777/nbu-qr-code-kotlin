/*
 * Ported from the QR Code generator library by Project Nayuki (MIT License):
 * https://www.nayuki.io/page/qr-code-generator-library
 * Copyright (c) Project Nayuki. See NOTICE for the full license text.
 *
 * Reduced to what the NBU format needs: byte mode only, error correction levels M and Q.
 */
package io.github.skules777.nbuqrcode

import kotlin.math.abs

/** "Гола" QR-матриця, без тихої зони: `size × size` модулів, `true` — темний. */
internal class QRMatrix(val version: Int, val size: Int, private val modules: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = modules[y * size + x]
}

/**
 * QR-кодувальник у спільному коді — замість `CIQRCodeGenerator` Swift-версії. Дані
 * завжди кодуються в byte mode з найменшою версією, що їх вміщує: так само чинить
 * `CIQRCodeGenerator` для посилання зі змішаним регістром.
 */
internal object QREncoder {
    const val MIN_VERSION = 1
    const val MAX_VERSION = 40

    /** Найменша версія, що вміщує `byteCount` байтів на рівні `level`, або `null`, якщо не вміщує жодна. */
    fun minimalVersion(byteCount: Int, level: NBUQRErrorCorrectionLevel): Int? =
        (MIN_VERSION..MAX_VERSION).firstOrNull { version ->
            byteModeBitLength(byteCount, version) <= numDataCodewords(version, level) * 8
        }

    /**
     * Матриця для даних або `null`, якщо вони не вміщуються у версію 40. `mask` — номер
     * маски 0–7; за замовчуванням обирається маска з найменшим штрафом.
     */
    fun encode(data: ByteArray, level: NBUQRErrorCorrectionLevel, mask: Int = -1): QRMatrix? {
        require(mask in -1..7) { "Mask must be -1 (automatic) or 0..7, got $mask" }
        val version = minimalVersion(data.size, level) ?: return null
        val codewords = addEccAndInterleave(dataCodewords(data, version, level), version, level)
        return Builder(version, level).build(codewords, mask)
    }

    private fun charCountBits(version: Int): Int = if (version <= 9) 8 else 16

    private fun byteModeBitLength(byteCount: Int, version: Int): Int {
        val countBits = charCountBits(version)
        if (byteCount >= 1 shl countBits) return Int.MAX_VALUE
        return 4 + countBits + byteCount * 8
    }

    private fun dataCodewords(data: ByteArray, version: Int, level: NBUQRErrorCorrectionLevel): ByteArray {
        val bits = BitBuffer()
        bits.append(0x4, 4)
        bits.append(data.size, charCountBits(version))
        for (byte in data) bits.append(byte.toInt() and 0xFF, 8)

        val capacityBits = numDataCodewords(version, level) * 8
        bits.append(0, minOf(4, capacityBits - bits.size))
        bits.append(0, (8 - bits.size % 8) % 8)
        var pad = 0xEC
        while (bits.size < capacityBits) {
            bits.append(pad, 8)
            pad = pad xor 0xEC xor 0x11
        }
        return bits.toBytes()
    }

    private fun addEccAndInterleave(data: ByteArray, version: Int, level: NBUQRErrorCorrectionLevel): ByteArray {
        val numBlocks = NUM_ERROR_CORRECTION_BLOCKS[level.ordinal][version]
        val blockEccLength = ECC_CODEWORDS_PER_BLOCK[level.ordinal][version]
        val rawCodewords = numRawDataModules(version) / 8
        val numShortBlocks = numBlocks - rawCodewords % numBlocks
        val shortBlockLength = rawCodewords / numBlocks

        val divisor = reedSolomonDivisor(blockEccLength)
        val blocks = ArrayList<ByteArray>(numBlocks)
        var offset = 0
        for (i in 0 until numBlocks) {
            val dataLength = shortBlockLength - blockEccLength + if (i < numShortBlocks) 0 else 1
            val blockData = data.copyOfRange(offset, offset + dataLength)
            offset += dataLength
            val ecc = reedSolomonRemainder(blockData, divisor)
            // Short blocks get a placeholder byte so that all blocks share one length; it is skipped below.
            val block = ByteArray(shortBlockLength + 1)
            blockData.copyInto(block)
            ecc.copyInto(block, block.size - blockEccLength)
            blocks += block
        }

        val result = ByteArray(rawCodewords)
        var k = 0
        for (i in 0 until shortBlockLength + 1) {
            for (j in blocks.indices) {
                if (i != shortBlockLength - blockEccLength || j >= numShortBlocks) {
                    result[k++] = blocks[j][i]
                }
            }
        }
        return result
    }

    private fun numRawDataModules(version: Int): Int {
        var result = (16 * version + 128) * version + 64
        if (version >= 2) {
            val numAlign = version / 7 + 2
            result -= (25 * numAlign - 10) * numAlign - 55
            if (version >= 7) result -= 36
        }
        return result
    }

    private fun numDataCodewords(version: Int, level: NBUQRErrorCorrectionLevel): Int =
        numRawDataModules(version) / 8 -
            ECC_CODEWORDS_PER_BLOCK[level.ordinal][version] * NUM_ERROR_CORRECTION_BLOCKS[level.ordinal][version]

    private fun reedSolomonDivisor(degree: Int): IntArray {
        val result = IntArray(degree)
        result[degree - 1] = 1
        var root = 1
        for (i in 0 until degree) {
            for (j in result.indices) {
                result[j] = gfMultiply(result[j], root)
                if (j + 1 < result.size) result[j] = result[j] xor result[j + 1]
            }
            root = gfMultiply(root, 0x02)
        }
        return result
    }

    private fun reedSolomonRemainder(data: ByteArray, divisor: IntArray): ByteArray {
        val result = IntArray(divisor.size)
        for (byte in data) {
            val factor = (byte.toInt() and 0xFF) xor result[0]
            result.copyInto(result, 0, 1)
            result[result.size - 1] = 0
            for (i in result.indices) result[i] = result[i] xor gfMultiply(divisor[i], factor)
        }
        return ByteArray(result.size) { result[it].toByte() }
    }

    private fun gfMultiply(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor ((y ushr i) and 1) * x
        }
        return z
    }

    private class Builder(val version: Int, val level: NBUQRErrorCorrectionLevel) {
        val size = version * 4 + 17
        val modules = BooleanArray(size * size)
        val isFunction = BooleanArray(size * size)

        fun build(codewords: ByteArray, requestedMask: Int): QRMatrix {
            drawFunctionPatterns()
            drawCodewords(codewords)

            var mask = requestedMask
            if (mask == -1) {
                var minPenalty = Int.MAX_VALUE
                for (candidate in 0..7) {
                    applyMask(candidate)
                    drawFormatBits(candidate)
                    val penalty = penaltyScore()
                    if (penalty < minPenalty) {
                        mask = candidate
                        minPenalty = penalty
                    }
                    applyMask(candidate)
                }
            }
            applyMask(mask)
            drawFormatBits(mask)
            return QRMatrix(version, size, modules)
        }

        private fun module(x: Int, y: Int) = modules[y * size + x]

        private fun setFunctionModule(x: Int, y: Int, isDark: Boolean) {
            modules[y * size + x] = isDark
            isFunction[y * size + x] = true
        }

        private fun drawFunctionPatterns() {
            for (i in 0 until size) {
                setFunctionModule(6, i, i % 2 == 0)
                setFunctionModule(i, 6, i % 2 == 0)
            }
            drawFinderPattern(3, 3)
            drawFinderPattern(size - 4, 3)
            drawFinderPattern(3, size - 4)

            val positions = alignmentPatternPositions()
            val count = positions.size
            for (i in 0 until count) {
                for (j in 0 until count) {
                    val isFinderCorner = (i == 0 && j == 0) || (i == 0 && j == count - 1) || (i == count - 1 && j == 0)
                    if (!isFinderCorner) drawAlignmentPattern(positions[i], positions[j])
                }
            }
            drawFormatBits(0)
            drawVersion()
        }

        private fun drawFormatBits(mask: Int) {
            val data = FORMAT_BITS[level.ordinal] shl 3 or mask
            var remainder = data
            repeat(10) { remainder = (remainder shl 1) xor ((remainder ushr 9) * 0x537) }
            val bits = (data shl 10 or remainder) xor 0x5412

            for (i in 0..5) setFunctionModule(8, i, bit(bits, i))
            setFunctionModule(8, 7, bit(bits, 6))
            setFunctionModule(8, 8, bit(bits, 7))
            setFunctionModule(7, 8, bit(bits, 8))
            for (i in 9 until 15) setFunctionModule(14 - i, 8, bit(bits, i))

            for (i in 0 until 8) setFunctionModule(size - 1 - i, 8, bit(bits, i))
            for (i in 8 until 15) setFunctionModule(8, size - 15 + i, bit(bits, i))
            setFunctionModule(8, size - 8, true)
        }

        private fun drawVersion() {
            if (version < 7) return
            var remainder = version
            repeat(12) { remainder = (remainder shl 1) xor ((remainder ushr 11) * 0x1F25) }
            val bits = version shl 12 or remainder
            for (i in 0 until 18) {
                val dark = bit(bits, i)
                val a = size - 11 + i % 3
                val b = i / 3
                setFunctionModule(a, b, dark)
                setFunctionModule(b, a, dark)
            }
        }

        private fun drawFinderPattern(x: Int, y: Int) {
            for (dy in -4..4) {
                for (dx in -4..4) {
                    val distance = maxOf(abs(dx), abs(dy))
                    val xx = x + dx
                    val yy = y + dy
                    if (xx in 0 until size && yy in 0 until size) {
                        setFunctionModule(xx, yy, distance != 2 && distance != 4)
                    }
                }
            }
        }

        private fun drawAlignmentPattern(x: Int, y: Int) {
            for (dy in -2..2) {
                for (dx in -2..2) setFunctionModule(x + dx, y + dy, maxOf(abs(dx), abs(dy)) != 1)
            }
        }

        private fun alignmentPatternPositions(): IntArray {
            if (version == 1) return IntArray(0)
            val numAlign = version / 7 + 2
            val step = (version * 8 + numAlign * 3 + 5) / (numAlign * 4 - 4) * 2
            val result = IntArray(numAlign)
            result[0] = 6
            var position = size - 7
            for (i in numAlign - 1 downTo 1) {
                result[i] = position
                position -= step
            }
            return result
        }

        private fun drawCodewords(data: ByteArray) {
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) {
                    for (j in 0..1) {
                        val x = right - j
                        val upward = (right + 1) and 2 == 0
                        val y = if (upward) size - 1 - vert else vert
                        if (!isFunction[y * size + x] && i < data.size * 8) {
                            modules[y * size + x] = bit(data[i ushr 3].toInt(), 7 - (i and 7))
                            i++
                        }
                    }
                }
                right -= 2
            }
        }

        private fun applyMask(mask: Int) {
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val invert = when (mask) {
                        0 -> (x + y) % 2 == 0
                        1 -> y % 2 == 0
                        2 -> x % 3 == 0
                        3 -> (x + y) % 3 == 0
                        4 -> (x / 3 + y / 2) % 2 == 0
                        5 -> x * y % 2 + x * y % 3 == 0
                        6 -> (x * y % 2 + x * y % 3) % 2 == 0
                        else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                    }
                    val index = y * size + x
                    if (invert && !isFunction[index]) modules[index] = !modules[index]
                }
            }
        }

        private fun penaltyScore(): Int {
            var result = 0

            for (y in 0 until size) {
                result += linePenalty { module(it, y) }
            }
            for (x in 0 until size) {
                result += linePenalty { module(x, it) }
            }

            for (y in 0 until size - 1) {
                for (x in 0 until size - 1) {
                    val color = module(x, y)
                    if (color == module(x + 1, y) && color == module(x, y + 1) && color == module(x + 1, y + 1)) {
                        result += PENALTY_N2
                    }
                }
            }

            val dark = modules.count { it }
            val total = size * size
            val k = (abs(dark * 20 - total * 10) + total - 1) / total - 1
            result += k * PENALTY_N4
            return result
        }

        private inline fun linePenalty(moduleAt: (Int) -> Boolean): Int {
            var result = 0
            var runColor = false
            var runLength = 0
            val runHistory = IntArray(7)
            for (i in 0 until size) {
                if (moduleAt(i) == runColor) {
                    runLength++
                    if (runLength == 5) result += PENALTY_N1 else if (runLength > 5) result++
                } else {
                    addRunToHistory(runLength, runHistory)
                    if (!runColor) result += countFinderLikePatterns(runHistory) * PENALTY_N3
                    runColor = moduleAt(i)
                    runLength = 1
                }
            }
            if (runColor) {
                addRunToHistory(runLength, runHistory)
                runLength = 0
            }
            addRunToHistory(runLength + size, runHistory)
            result += countFinderLikePatterns(runHistory) * PENALTY_N3
            return result
        }

        private fun addRunToHistory(runLength: Int, history: IntArray) {
            // The light border outside the symbol counts towards the first run.
            val length = if (history[0] == 0) runLength + size else runLength
            history.copyInto(history, 1, 0, history.size - 1)
            history[0] = length
        }

        private fun countFinderLikePatterns(history: IntArray): Int {
            val n = history[1]
            val core = n > 0 && history[2] == n && history[3] == n * 3 && history[4] == n && history[5] == n
            return (if (core && history[0] >= n * 4 && history[6] >= n) 1 else 0) +
                (if (core && history[6] >= n * 4 && history[0] >= n) 1 else 0)
        }
    }

    private fun bit(value: Int, index: Int): Boolean = (value ushr index) and 1 != 0

    private class BitBuffer {
        private val bits = ArrayList<Boolean>()
        val size: Int get() = bits.size

        fun append(value: Int, length: Int) {
            for (i in length - 1 downTo 0) bits += (value ushr i) and 1 != 0
        }

        fun toBytes(): ByteArray {
            val result = ByteArray(bits.size / 8)
            for (i in bits.indices) {
                if (bits[i]) result[i ushr 3] = (result[i ushr 3].toInt() or (0x80 ushr (i and 7))).toByte()
            }
            return result
        }
    }

    private const val PENALTY_N1 = 3
    private const val PENALTY_N2 = 3
    private const val PENALTY_N3 = 40
    private const val PENALTY_N4 = 10

    // Indexed by NBUQRErrorCorrectionLevel.ordinal: MEDIUM, QUARTILE.
    private val FORMAT_BITS = intArrayOf(0, 3)

    private val ECC_CODEWORDS_PER_BLOCK = arrayOf(
        intArrayOf(
            -1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26,
            26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28,
        ),
        intArrayOf(
            -1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30,
            28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30,
        ),
    )

    private val NUM_ERROR_CORRECTION_BLOCKS = arrayOf(
        intArrayOf(
            -1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16,
            17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49,
        ),
        intArrayOf(
            -1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20,
            23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68,
        ),
    )
}
