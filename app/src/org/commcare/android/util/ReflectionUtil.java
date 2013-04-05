package org.commcare.android.util;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.os.Environment;

public class ReflectionUtil {
   private static Method mExternalStorageEmulated;

   static {
       initCompatibility();
   };

   private static void initCompatibility() {
       try {
    	   mExternalStorageEmulated = Environment.class.getMethod("isExternalStorageEmulated", new Class[0]);    	      	   
           /* success, this is a newer device */
       } catch (NoSuchMethodException nsme) {
           /* failure, must be older device */
       }
   }

   private static boolean mIsExternalStorageEmulated() throws IOException {
       try {
    	   Object obj = mExternalStorageEmulated.invoke(null , new Object[0]);
    	   boolean isEmulated = (Boolean) obj;
    	   return isEmulated;
       } catch (InvocationTargetException ite) {
           /* unpack original exception when possible */
           Throwable cause = ite.getCause();
           if (cause instanceof IOException) {
               throw (IOException) cause;
           } else if (cause instanceof RuntimeException) {
               throw (RuntimeException) cause;
           } else if (cause instanceof Error) {
               throw (Error) cause;
           } else {
               /* unexpected checked exception; wrap and re-throw */
               throw new RuntimeException(ite);
           }
       } catch (IllegalAccessException ie) {
           System.err.println("unexpected " + ie);
           throw new RuntimeException(ie);
       } catch(ClassCastException cce){
    	   System.err.println("unexpected " + cce);
    	   throw new RuntimeException(cce);
       }
   }

   public static boolean fiddle() {
       if (mExternalStorageEmulated != null) {
           /* feature is supported */
           try {
        	   System.out.println("405 function supported");
               return mIsExternalStorageEmulated();
           } catch (RuntimeException e) {
        	   System.out.println("405 function NOT supported");
               return true;
           } catch (IOException ie){
        	   System.out.println("405 function NOT supported");
        	   return true;
           }
       } else {
           /* external SD emulation not possible, return false */
           return false;
       }
   }
}