package org.commcare.activities

import org.javarosa.xpath.XPathException

/**
 * Outcome of stepping the form to the next renderable event.
 */
sealed class NavResult {
    object Question : NavResult()
    object FieldListGroup : NavResult()
    object PromptNewRepeat : NavResult()
    object EndOfForm : NavResult()
    data class Error(val exception: XPathException) : NavResult()
}