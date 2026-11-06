package circolareplus.platform

// Browser dictation APIs cannot reliably guarantee local processing of an imported audio file.
actual fun isLocalAudioTranscriptionAvailable(): Boolean = false
actual suspend fun transcribeAudioLocally(audio: IncomingContent): String? = null
