package org.commcare.utils

import androidx.test.espresso.matcher.ViewMatchers.withText

fun Array<String>.areDisplayed() {
    this.forEach {
        withText(it).isDisplayed()
    }
}

fun Array<String>.areGone() {
    this.forEach {
        withText(it).doesNotExist()
    }
}
