package com.example.widgetvoiceroid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

// 画像の取り込み・読み込みを担当する
object ImageStore {
    private const val TAG = "ImageStore"

    // 読み込んだ画像を覚えておく(メモリの1/8まで)
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    private fun dir(context: Context) = File(context.filesDir, "images").apply { mkdirs() }

    // 端末で選んだ画像をアプリ内にコピーする。画像として読めなければ null
    suspend fun import(context: Context, uri: Uri): ImageRef.Stored? = withContext(Dispatchers.IO) {
        val ext = when (context.contentResolver.getType(uri)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "jpg"
        }
        val target = File(dir(context), "${UUID.randomUUID()}.$ext")
        runCatching {
            context.contentResolver.openInputStream(uri)!!.use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(target.path, bounds)
            check(bounds.outWidth > 0 && bounds.outHeight > 0) { "画像として読めません" }
            ImageRef.Stored(target.name)
        }.getOrElse { e ->
            Log.w(TAG, "画像を取り込めませんでした: $uri", e)
            target.delete()
            null
        }
    }

    // 画像を読み込む。長い辺が maxSize ピクセル以下になるよう縮小する
    fun loadBitmap(context: Context, ref: ImageRef, maxSize: Int): Bitmap? {
        val key = "$ref@$maxSize"
        cache.get(key)?.let { return it }

        val decode: (BitmapFactory.Options) -> Bitmap? = when (ref) {
            is ImageRef.BuiltIn -> {
                val resId = BuiltInCharacters.drawableFor(ref) ?: return null
                { options -> BitmapFactory.decodeResource(context.resources, resId, options) }
            }
            is ImageRef.Stored -> {
                val file = File(dir(context), ref.fileName)
                if (!file.exists()) return null
                { options -> BitmapFactory.decodeFile(file.path, options) }
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decode(bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSize) sample *= 2
        val decoded = decode(BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null

        val longSide = maxOf(decoded.width, decoded.height)
        val bitmap = if (longSide > maxSize) {
            val scale = maxSize.toFloat() / longSide
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            decoded
        }
        cache.put(key, bitmap)
        return bitmap
    }

    // どのキャラからも使われなくなった画像ファイルを消す
    fun cleanUp(context: Context, characters: List<Character>) {
        val used = characters
            .flatMap { it.faces }
            .mapNotNull { (it.image as? ImageRef.Stored)?.fileName }
            .toSet()
        dir(context).listFiles()?.filter { it.name !in used }?.forEach { it.delete() }
    }
}
