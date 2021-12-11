package net.starlegacy.util

import java.awt.geom.Ellipse2D
import java.awt.geom.Rectangle2D

fun circleToEllipse2D(x: Number, z: Number, radius: Number): Ellipse2D.Double {
    return Ellipse2D.Double(x.d() - radius.d(), z.d() - radius.d(), radius.d() * 2.0, radius.d() * 2.0)
}

fun chunkToRectangle2D(chunkX: Int, chunkZ: Int): Rectangle2D.Double {
    val blockX = chunkX shl 4
    val blockZ = chunkZ shl 4
    return Rectangle2D.Double(blockX.d(), blockZ.d(), 16.0, 16.0)
}
