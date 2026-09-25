package com.mynd.watch.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.mynd.watch.studio.FaceFile
import com.mynd.watch.studio.FaceRenderer
import com.mynd.watch.studio.SimValues

@Composable
fun FacePreview(face: FaceFile, sim: SimValues, showBoxes: Boolean, modifier: Modifier = Modifier) {
    val r = remember(face, sim) { FaceRenderer.render(face, sim) }

    val bmp = remember(r, showBoxes) {
        val b = Bitmap.createBitmap(r.pixels, r.width, r.height, Bitmap.Config.ARGB_8888).copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(b)

        // Desenhar textos com recorte (igual ao firmware)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textSize = 17f
        }
        for (td in r.texts) {
            paint.color = td.argb
            canvas.save()
            canvas.clipRect(td.x.toFloat(), td.y.toFloat(), (td.x + td.width).toFloat(), (td.y + 22).toFloat())
            val w = paint.measureText(td.text)
            val x = when (td.align) {
                2 -> td.x + (td.width - w) / 2f
                3 -> td.x + td.width - w
                else -> td.x.toFloat()
            }
            canvas.drawText(td.text, x, td.y + 17f, paint)
            canvas.restore()
        }

        // Caixas de debug
        if (showBoxes) {
            val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = android.graphics.Color.argb((255 * 0.6f).toInt(), 0x00, 0xC8, 0xFF)
            }
            for (td in r.texts)
                canvas.drawRect(td.x.toFloat(), td.y.toFloat(), (td.x + td.width).toFloat(), (td.y + 20).toFloat(), boxPaint)
            for (im in face.images) {
                val s = im.sprites.firstOrNull() ?: continue
                canvas.drawRect(im.x.toFloat(), im.y.toFloat(), (im.x + s.w).toFloat(), (im.y + s.h).toFloat(), boxPaint)
            }
        }
        b
    }

    Image(
        bitmap = bmp.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.None,
        modifier = modifier
            .aspectRatio(240f / 296f)
            .clip(RoundedCornerShape(28.dp))
    )
}
