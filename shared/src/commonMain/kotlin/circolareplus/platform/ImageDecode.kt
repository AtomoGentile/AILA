package circolareplus.platform

import androidx.compose.ui.graphics.ImageBitmap

/** Decodifica byte di un'immagine (JPEG/PNG/WebP) in ImageBitmap per la visualizzazione in app. */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?
