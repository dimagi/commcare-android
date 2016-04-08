package org.commcare.tasks;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class ResultAndError<Data> {
    public final Data data;
    public final String errorMessage;

    public ResultAndError(Data data, String errorMessage) {
        this.data = data;
        this.errorMessage = errorMessage;
    }

    public ResultAndError(Data data) {
        this.data = data;
        this.errorMessage = "";
    }
}
