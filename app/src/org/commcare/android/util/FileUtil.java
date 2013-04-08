/**
 * 
 */
package org.commcare.android.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;

import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.util.PropertyUtils;

import android.util.Log;

/**
 * @author ctsims
 *
 */
public class FileUtil {
	
	public static final String LOG_TOKEN = "cc-file-util";

    public static boolean createFolder(String path) {
            boolean made = true;
            File dir = new File(path);
            if (!dir.exists()) {
                made = dir.mkdirs();
            }
            return made;
    }
	
	public static boolean deleteFile(File f) {
		if(!f.exists()) { return true; }
		if(!f.isDirectory()) {
			return f.delete();
		} else {
			for(File child : f.listFiles()) {
				if(!deleteFile(child)) {
					return false;
				}
			}
			return f.delete();
		}
	}

	public static boolean cleanFilePath(String fullPath, String extendedPath) {
		//There are actually a few things that can go wrong here, should be careful
		
		//No extended path, life is good.
		if(extendedPath == null) { return true;}
		
		//Something's weird, bail!
		if(!fullPath.contains(extendedPath)) { return true;}
		
		//Get the root that we should stop at
		File terminal = new File(fullPath.replace(extendedPath, ""));
		
		File walker = new File(fullPath);
		
		//technically we shouldn't ever hit the first case here, but also don't wanna get stuck by a weird equality bug. 
		while(walker != null && !terminal.equals(walker)) {
			if(walker.isDirectory()) {
				//only wipe out empty directories.
				if(walker.list().length == 0) {
					if(!walker.delete()) {
						//I don't think we actually want to fail here, it's not a showstopper.
						Log.w("cleanup", "couldn't delete directory " + walker.getAbsolutePath() + " while cleaning up file paths");
						//throw an exception/false here if we care.
					}
				}
			}
			walker = walker.getParentFile();
		}
		return true;
	}
	
	 public static void deleteFileOrDir(String fileName) {
	        File file = new File(fileName);
	        if (file.exists()) {
	            if (file.isDirectory()) {
	                // delete all the containing files
	                File[] files = file.listFiles();
	                for (File f : files) {
	                    // should make this recursive if we get worried about
	                    // the media directory containing directories
	                    Log.i(LOG_TOKEN, "attempting to delete file: " + f.getAbsolutePath());
	                    f.delete();
	                }
	            }
	            file.delete();
	            Log.i(LOG_TOKEN, "attempting to delete file: " + file.getAbsolutePath());
	        }
	    }
	 
	    public static String getMd5Hash(File file) {
	        try {
	            // CTS (6/15/2010) : stream file through digest instead of handing it the byte[]
	            MessageDigest md = MessageDigest.getInstance("MD5");
	            int chunkSize = 256;

	            byte[] chunk = new byte[chunkSize];

	            // Get the size of the file
	            long lLength = file.length();

	            if (lLength > Integer.MAX_VALUE) {
	                Log.e(LOG_TOKEN, "File " + file.getName() + "is too large");
	                return null;
	            }

	            int length = (int) lLength;

	            InputStream is = null;
	            is = new FileInputStream(file);

	            int l = 0;
	            for (l = 0; l + chunkSize < length; l += chunkSize) {
	                is.read(chunk, 0, chunkSize);
	                md.update(chunk, 0, chunkSize);
	            }

	            int remaining = length - l;
	            if (remaining > 0) {
	                is.read(chunk, 0, remaining);
	                md.update(chunk, 0, remaining);
	            }
	            byte[] messageDigest = md.digest();

	            BigInteger number = new BigInteger(1, messageDigest);
	            String md5 = number.toString(16);
	            while (md5.length() < 32)
	                md5 = "0" + md5;
	            is.close();
	            return md5;

	        } catch (NoSuchAlgorithmException e) {
	            Log.e("MD5", e.getMessage());
	            return null;

	        } catch (FileNotFoundException e) {
	            Log.e("No Cache File", e.getMessage());
	            return null;
	        } catch (IOException e) {
	            Log.e("Problem reading from file", e.getMessage());
	            return null;
	        }

	    }
	    
	    private static final String illegalChars = "'*','+'~|<> !?:./\\";
	    public static String SanitizeFileName(String input) {
	    	for(char c : illegalChars.toCharArray()) {
	    		input = input.replace(c, '_');
	    	} 
	    	return input;
	    }
	    
	    public static void copyFile(File oldPath, File newPath) throws IOException {
	    	copyFile(oldPath, newPath, null, null);
	    }
	    
	    public static void copyFile(File oldPath, File newPath, Cipher oldRead, Cipher newWrite) throws IOException {
	    	if(!newPath.createNewFile()) { throw new IOException("Couldn't create new file @ " + newPath.toString()); }
	    	
	    	InputStream is = null;
	    	OutputStream os = null;
            try {
	
	            is = new FileInputStream(oldPath);
	            if(oldRead != null) {
	            	is = new CipherInputStream(is, oldRead);
	            }
	            
	            os = new FileOutputStream(newPath);
	            if(newWrite != null) {
	            	os = new CipherOutputStream(os, newWrite);
	            }
	            
	            AndroidStreamUtil.writeFromInputToOutput(is, os);
            } finally {
            	try{
            		if(is != null) {
            			is.close();
            		}
            	} catch(IOException e) {
            		
            	}
            	
            	try{
            		if(os != null) {
            			os.close();
            		}
            	} catch(IOException e) {
            		
            	}
            }
	    }
	    
	    /**
	     * Get a new, clean location to put a file in the same path as the incoming file
	     * 
	     * @param f The existing file
	     * @param slug A new chunk to append to the file name 
	     * @param removeExisting Whether to remove any files which already appear in this location.
	     * If false, the method will continue trying to generate new paths until there is no conflict
	     * 
	     * @return A new file location which does not reference an existing file.
	     */
	    public static File getNewFileLocation(File f, String slug, boolean removeExisting) {
	    	if(slug == null) {
	    		slug = PropertyUtils.genGUID(5);
	    	}
	    	String name = f.getName();
	    	
	    	int lastDot = name.lastIndexOf(".");
	    	if(lastDot != -1) {
	    		String prefix = name.substring(0, lastDot);
	    		String postfix = name.substring(lastDot);
	    		
	    		name = prefix + "_" + slug + postfix;
	    	} else { 
	    		name = name + "_" + slug;
	    	}
	    	
	    	File newLocation = new File(f.getParent() + File.separator + name);
	    	if(newLocation.exists()) {
	    		if(removeExisting) {
	    			deleteFile(newLocation);
	    		} else {
	    			return getNewFileLocation(newLocation, null, removeExisting);
	    		}
	    	}
	    	return newLocation;
	    }

		public static void copyFileDeep(File oldFolder, File newFolder) throws IOException {
			//Create the new folder
			newFolder.mkdir();
			
			if(oldFolder.listFiles() != null) {
				//Start copying over files
				for(File oldFile : oldFolder.listFiles()) {
					File newFile = new File(newFolder.getPath() + File.separator + oldFile.getName());
					if(oldFile.isDirectory()) {
						copyFileDeep(oldFile, newFile);
					} else {
						FileUtil.copyFile(oldFile, newFile);
					}
				}
			}
		}
}
