package io.github.skules777.nbuqrcode

/**
 * Контур знака гривні (₴), який [NBUQRImageRenderer] ставить у центр QR-коду
 * (додаток 1, пп. 10–12).
 *
 * Форму протрасовано з чотирьох QR-кодів формату 003 у постанові №97 (додаток 4,
 * приклади 1–4), а не взято зі шрифту: п. 14 лишає форму знака за НБУ, а гліф шрифту
 * і відрізняється від неї, і може бути відсутній під час виконання. Чотири растри
 * суміщено й усереднено, контур протрасовано, прямі ребра вирівняно, решту
 * апроксимовано кубічними кривими. Відрендерений назад у масштабі кожного прикладу,
 * контур відрізняється від нього в середньому на 0,013 покриття на піксель
 * (IoU 0,987) — стільки ж, скільки самі приклади відрізняються один від одного.
 *
 * Координати даних: рамка знака центрована на (0, 0), мінімальне описане коло має
 * радіус 1, вісь y спрямована вгору — ті самі числа, що й у Swift-версії. [path]
 * перевертає y, бо полотно рендеру має вісь y донизу.
 */
internal object NBUHryvniaSign {
    fun path(enclosingRadius: Double, centerX: Double, centerY: Double): List<PathCommand> {
        fun x(value: Double) = centerX + value * enclosingRadius
        fun y(value: Double) = centerY - value * enclosingRadius

        val commands = ArrayList<PathCommand>(segments.size + 2)
        commands += PathCommand.MoveTo(x(START.first), y(START.second))
        for (segment in segments) {
            commands += when (segment) {
                is Segment.Line -> PathCommand.LineTo(x(segment.x), y(segment.y))
                is Segment.Curve -> PathCommand.CubicTo(
                    x(segment.x1), y(segment.y1), x(segment.x2), y(segment.y2), x(segment.x), y(segment.y),
                )
            }
        }
        commands += PathCommand.Close
        return commands
    }

    private sealed interface Segment {
        class Line(val x: Double, val y: Double) : Segment
        class Curve(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val x: Double, val y: Double) : Segment
    }

    private fun line(x: Double, y: Double) = Segment.Line(x, y)

    private fun curve(x1: Double, y1: Double, x2: Double, y2: Double, x: Double, y: Double) = Segment.Curve(x1, y1, x2, y2, x, y)

    private val START = -0.5334 to 0.8491

    private val segments: List<Segment> = listOf(
        line(-0.5448, 0.5659),
        curve(-0.493, 0.5581, -0.4168, 0.5942, -0.3645, 0.6071),
        curve(-0.2186, 0.6432, 0.0084, 0.6567, 0.0875, 0.4983),
        curve(0.12, 0.4334, 0.1024, 0.3756, 0.064, 0.318),
        curve(0.0368, 0.2771, 0.0006, 0.2413, -0.0356, 0.2094),
        line(-0.6022, 0.2094),
        line(-0.6096, 0.0257),
        line(-0.263, 0.0257),
        line(-0.3549, -0.0561),
        line(-0.6265, -0.0561),
        line(-0.6339, -0.2399),
        line(-0.5346, -0.2399),
        curve(-0.5353, -0.2719, -0.5559, -0.3019, -0.5655, -0.3333),
        curve(-0.5978, -0.4398, -0.5731, -0.5748, -0.5175, -0.669),
        curve(-0.3669, -0.9248, 0.0775, -0.9313, 0.3352, -0.8798),
        curve(0.4038, -0.866, 0.4884, -0.8699, 0.5488, -0.8329),
        line(0.561, -0.5267),
        curve(0.51, -0.525, 0.4529, -0.5659, 0.4019, -0.5781),
        curve(0.2717, -0.6094, 0.1421, -0.6383, 0.0078, -0.6152),
        curve(-0.1043, -0.5959, -0.2089, -0.4807, -0.1658, -0.3634),
        curve(-0.1478, -0.3143, -0.105, -0.2729, -0.0674, -0.2398),
        line(0.6004, -0.2398),
        line(0.6078, -0.0561),
        line(0.1358, -0.0561),
        line(0.2278, 0.0257),
        line(0.6265, 0.0257),
        line(0.6339, 0.2094),
        line(0.4249, 0.2094),
        curve(0.4299, 0.2515, 0.4763, 0.2956, 0.4931, 0.338),
        curve(0.5239, 0.4154, 0.5107, 0.5181, 0.4849, 0.5952),
        curve(0.3823, 0.9027, -0.017, 0.9227, -0.2861, 0.8977),
        curve(-0.3682, 0.89, -0.4579, 0.8832, -0.5334, 0.8491),
    )
}
