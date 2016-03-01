package org.commcare.utils;

import org.commcare.android.tasks.TemplatePrinterTask;

/**
 * Created by amstone326 on 7/31/15.
 */
public class PrintValidationException extends Exception {

    private final TemplatePrinterTask.PrintTaskResult errorType;

    public PrintValidationException(String msg, TemplatePrinterTask.PrintTaskResult errorType) {
        super(msg);
        this.errorType = errorType;
    }

    public TemplatePrinterTask.PrintTaskResult getErrorType() {
        return this.errorType;
    }
}
