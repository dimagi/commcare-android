package org.commcare.android.adapters;


import android.content.Context;

/**
 * Created by willpride on 12/17/15.
 */
public abstract class SquareButtonObject {
    int resource;
    Context c;
    public abstract boolean isHidden();

    public SquareButtonObject(Context c, int resource){
        this.c = c;
        this.resource = resource;
    }

    public int getResource(){
        return resource;
    }
}
