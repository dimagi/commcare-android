package org.commcare.interfaces;

public interface AdvanceToNextListener {

    void advance(boolean allowAutoSubmission); //Move on to the next question
}