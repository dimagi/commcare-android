package org.commcare.tts

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.TextUtils

import org.javarosa.core.services.locale.Localization

import java.util.Locale
import java.util.LinkedList

import kotlin.collections.HashMap

/**
 * Utility for Android's {@link android.speech.tts.TextToSpeech} that handles initialization, shutdown,
 * locale setting.
 */
object TextToSpeechConverter {

    private val MAX_TEXT_LENGTH = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        TextToSpeech.getMaxSpeechInputLength()
    } else {
        4000
    }
    private var mTextToSpeech: TextToSpeech? = null
    private var mInitialized: Boolean = false
    private var mTTSCallback: TextToSpeechCallback? = null
    private val mUtteranceProgressListener = object: UtteranceProgressListener() {
        // The callbacks specified here can be called from multiple threads.
        override fun onDone(utteranceId: String?) { }

        override fun onError(utteranceId: String?) {
            Handler(Looper.getMainLooper()).post {
                mTTSCallback?.speakFailed()
            }
        }

        override fun onStart(utteranceId: String?) { }
    }

    /**
     * Adds a listener to register callbacks for different states of Text-To-Speech engine.
     */
    fun setListener(listener: TextToSpeechCallback) {
        mTTSCallback = listener
    }

    /**
     * Attempts to speak the specified text.
     */
    fun speak(context: Context, text: String) {
        if (!mInitialized) {
            initialize(context, text)
            return
        }
        // Handle empty text
        if (TextUtils.isEmpty(text)) {
            return
        }
        mTextToSpeech?.let { tts ->
            if (text.length > MAX_TEXT_LENGTH) {
                text.chunked(MAX_TEXT_LENGTH).forEach {
                    speakInternal(tts, it, TextToSpeech.QUEUE_ADD)
                }
            } else {
                speakInternal(tts, text)
            }
        } ?: run {
            mTTSCallback?.initFailed()
        }
    }

    /**
     * Attempts to stop the TTS.
     */
    fun stop() {
        mTextToSpeech?.let {
            it.stop()
        }
    }

    /**
     * Attempts to shutdown the TTS engine. No calls should be made to this object after calling this method.
     * Good to call this from onDestroy().
     */
    fun shutDown() {
        mTextToSpeech?.let {
            it.shutdown()
        }
        mInitialized = false
    }

    /**
     * Changes the locale of text to speech engine.
     */
    fun changeLocale(language: String) {
        mTextToSpeech?.let { tts ->
            setLocale(tts, LinkedList(listOf(Locale(language))))
        }
    }

    private fun speakInternal(tts: TextToSpeech, text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        val utteranceId = System.currentTimeMillis().toString()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, queueMode, null, utteranceId)
        } else {
            val params = HashMap<String, String>()
            params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = utteranceId
            tts.speak(text, queueMode, params)
        }
    }

    private fun isTextLong(text: String): Boolean {
        // TTS can only speak 4000 characters at max at a time.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            text.length > TextToSpeech.getMaxSpeechInputLength()
        } else {
            text.length > MAX_TEXT_LENGTH
        }
    }

    /**
     * Initializes the Text-To-Speech engine
     */
    private fun initialize(context: Context, text: String) {
        mTextToSpeech = TextToSpeech(context, TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                // set language and speak
                mTextToSpeech?.let { tts ->
                    tts.setOnUtteranceProgressListener(mUtteranceProgressListener)
                    if (setLocale(tts, LinkedList(listOf(
                                    Locale(Localization.getCurrentLocale()),
                                    Locale.getDefault(),
                                    Locale.ENGLISH)))) {
                        // tts initialization completed.
                        mInitialized = true
                        speak(context, text)
                    }
                }
            } else {
                mTTSCallback?.initFailed()
            }
        })
    }

    /**
     * Sets a TTS language from the given list of locale starting from the first Locale.
     *
     * Returns a boolean indicating whether we TTS language is ready to use.
     */
    private fun setLocale(tts: TextToSpeech, localeList: LinkedList<Locale>): Boolean {
        if (localeList.isEmpty()) {
            return false
        }
        val locale = localeList.pop()
        return when (tts.isLanguageAvailable(locale)) {
            TextToSpeech.LANG_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> {
                // Set language
                tts.language = locale

                // Check if voice data is present or not.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    if (tts.voice != null) {
                        val features = tts.voice.features
                        if (features == null
                                || features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
                                || tts.voice.isNetworkConnectionRequired) {
                            // voice data is not present
                            mTTSCallback?.voiceDataMissing(locale.displayLanguage)
                            return false
                        }
                    } else {
                        // Returning true here since the voice can be null for any reason,
                        // but we know for sure that the language is available and can be used.  
                        return true
                    }
                } else {
                    val features = tts.getFeatures(locale)
                    if (features == null || features.contains("notInstalled")) {
                        mTTSCallback?.voiceDataMissing(locale.displayLanguage)
                        return false
                    }
                }
                // voice data is present so return true that we're ready to use.
                true
            }
            TextToSpeech.LANG_MISSING_DATA -> {
                // Unfortunately this callback doesn't really work.
                tts.language = locale
                mTTSCallback?.voiceDataMissing(locale.displayLanguage)
                false
            }
            TextToSpeech.LANG_NOT_SUPPORTED -> {
                setLocale(tts, localeList)
            }
            else -> {
                false
            }
        }
    }

}
