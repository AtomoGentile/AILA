package circolareplus.platform

/** These APIs never send audio to a remote recognition service. Null means unavailable/unsupported. */
expect fun isLocalAudioTranscriptionAvailable(): Boolean
expect suspend fun transcribeAudioLocally(audio: IncomingContent): String?
