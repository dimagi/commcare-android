package org.commcare.android.crypt;

import android.util.Log;

import java.util.HashSet;
import java.util.Stack;

import javax.crypto.Cipher;

/**
 * @author ctsims
 */
public abstract class CipherPool {
    private static final String TAG = CipherPool.class.getSimpleName();

    private static final int GROWTH_FACTOR = 5;

    HashSet<Cipher> issued = new HashSet<Cipher>();
    Stack<Cipher> free = new Stack<Cipher>();

    //TODO: Pass in factory and finalize all API's rather than
    //leaving the class to be anonymous?
    public CipherPool() {

    }

    public synchronized final void init() {
        grow();
    }

    public synchronized final Cipher borrow() {
        if (free.isEmpty()) {
            grow();
            Log.d(TAG, "Growing cipher pool. Current size is: " + free.size() + issued.size());
        }
        Cipher toLend = free.pop();
        issued.add(toLend);
        return toLend;
    }

    public synchronized final void remit(Cipher cipher) {
        issued.remove(cipher);
        free.push(cipher);
    }

    private synchronized void grow() {
        for (int i = 0; i < GROWTH_FACTOR; ++i) {
            free.push(generateNewCipher());
        }
    }

    protected abstract Cipher generateNewCipher();

    public synchronized final void expire() {
        //do we want to try to destroy the final object here?
        issued.clear();
        free.clear();
    }
}
