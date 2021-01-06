package org.commcare.tts


/**
 * @author $|-|!˅@M
 */
interface TextToSpeechCallback {
    fun initFailed()
    fun speakFailed()
    fun voiceDataMissing(language: String)
}
