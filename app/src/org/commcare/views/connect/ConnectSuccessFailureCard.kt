package org.commcare.views.connect

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.annotation.StringRes
import androidx.cardview.widget.CardView
import androidx.core.content.withStyledAttributes
import androidx.core.widget.ImageViewCompat
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ViewConnectSuccessFailureCardBinding

/**
 * Reusable dismissible card that shows a short status message in a [Mode.SUCCESS] or
 * [Mode.FAILURE] style. Mode drives the background, leading icon, and tint; the message
 * text is supplied by the caller. The card is hidden by default (unless the XML sets an
 * explicit `android:visibility`) and revealed via [show]; tapping the close icon hides it
 * again and invokes the handler passed to [show].
 */
class ConnectSuccessFailureCard
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : CardView(context, attrs, defStyleAttr) {
        enum class Mode { SUCCESS, FAILURE }

        private val binding =
            ViewConnectSuccessFailureCardBinding.inflate(LayoutInflater.from(context), this, true)

        var mode: Mode? = null
            set(value) {
                field = value
                value?.let { applyMode(it) }
            }

        var messageText: CharSequence?
            get() = binding.successFailureCardText.text
            set(value) {
                binding.successFailureCardText.text = value
            }

        private var onDismiss: (() -> Unit)? = null

        /**
         * Configures the card for [mode] with [message], attaches an optional [onDismiss]
         * handler for this showing, and makes the card visible.
         */
        @JvmOverloads
        fun show(
            mode: Mode,
            message: CharSequence,
            onDismiss: (() -> Unit)? = null,
        ) {
            this.onDismiss = onDismiss
            this.mode = mode
            messageText = message
            visibility = VISIBLE
        }

        @JvmOverloads
        fun show(
            mode: Mode,
            @StringRes messageRes: Int,
            onDismiss: (() -> Unit)? = null,
        ) = show(mode, context.getString(messageRes), onDismiss)

        private var successBackgroundColor = 0
        private var successAccentColor = 0
        private var failureBackgroundColor = 0
        private var failureAccentColor = 0

        init {
            useCompatPadding = false
            if (attrs?.getAttributeValue(ANDROID_NAMESPACE, "visibility") == null) {
                visibility = GONE
            }

            binding.successFailureCardClose.setOnClickListener {
                visibility = GONE
                onDismiss?.invoke()
            }

            context.withStyledAttributes(
                attrs,
                R.styleable.ConnectSuccessFailureCard,
                defStyleAttr,
                R.style.Widget_CommCare_ConnectSuccessFailureCard,
            ) {
                radius = getDimension(R.styleable.ConnectSuccessFailureCard_cardCornerRadius, radius)
                cardElevation = getDimension(R.styleable.ConnectSuccessFailureCard_cardElevation, cardElevation)
                successBackgroundColor = getColor(R.styleable.ConnectSuccessFailureCard_successBackgroundColor, 0)
                successAccentColor = getColor(R.styleable.ConnectSuccessFailureCard_successAccentColor, 0)
                failureBackgroundColor = getColor(R.styleable.ConnectSuccessFailureCard_failureBackgroundColor, 0)
                failureAccentColor = getColor(R.styleable.ConnectSuccessFailureCard_failureAccentColor, 0)
                if (hasValue(R.styleable.ConnectSuccessFailureCard_mode)) {
                    mode = Mode.values()[getInt(R.styleable.ConnectSuccessFailureCard_mode, 0)]
                }
                getString(R.styleable.ConnectSuccessFailureCard_messageText)?.let { messageText = it }
            }
        }

        private fun applyMode(mode: Mode) {
            val (backgroundColor, iconRes, accent) =
                when (mode) {
                    Mode.SUCCESS -> Triple(successBackgroundColor, R.drawable.check_update, successAccentColor)
                    Mode.FAILURE -> Triple(failureBackgroundColor, R.drawable.ic_connect_warning, failureAccentColor)
                }
            val accentTint = ColorStateList.valueOf(accent)

            setCardBackgroundColor(backgroundColor)
            binding.successFailureCardIcon.setImageResource(iconRes)
            ImageViewCompat.setImageTintList(binding.successFailureCardIcon, accentTint)
            binding.successFailureCardText.setTextColor(accent)
            ImageViewCompat.setImageTintList(binding.successFailureCardClose, accentTint)
        }

        companion object {
            private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        }
    }
