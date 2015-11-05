package org.commcare.android.util;

import org.javarosa.core.util.externalizable.Hasher;

public class LiveHasher extends Hasher {
        LivePrototypeFactory pf;
        Hasher mHasher;
        public LiveHasher(LivePrototypeFactory pf, Hasher mHasher){
            this.pf = pf;
            this.mHasher = mHasher;
        }

        @Override
        public int getHashSize() {
            return mHasher.getHashSize();
        }

        @Override
        public byte[] getHash(Class c) {
            byte[] ret = mHasher.getClassHashValue(c);
            pf.addClass(c);
            return ret;
        }

        public Hasher getHasher(){
            return mHasher;
        }
    }