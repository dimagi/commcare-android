/**
 * 
 */
package org.commcare.android.tasks;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.mime.EncryptedFileBody;
import org.commcare.android.models.FormRecord;
import org.commcare.data.xml.DataModelPullParser;
import org.commcare.data.xml.TransactionParser;
import org.commcare.data.xml.TransactionParserFactory;
import org.commcare.xml.CaseXmlParser;
import org.commcare.xml.util.InvalidStructureException;
import org.commcare.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.util.StreamUtil;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

/**
 * @author ctsims
 *
 */
public class ProcessAndSendTask extends AsyncTask<FormRecord, Integer, Integer> {

	Context c;
	String url;
	
	public static final int FULL_SUCCESS = 0;
	public static final int PARTIAL_SUCCESS = 1;
	public static final int FAILURE = 2;
	public static final int TRANSPORT_FAILURE = 4;
	
	ProcessAndSendListener listener;
	
	private static long MAX_BYTES = 1048576-1024; // 1MB less 1KB overhead
	
	public ProcessAndSendTask(Context c, String url) {
		this.c = c;
		this.url = url;
	}
	
	/* (non-Javadoc)
	 * @see android.os.AsyncTask#doInBackground(Params[])
	 */
	protected Integer doInBackground(FormRecord... records) {
		Integer[] results = new Integer[records.length];
		for(int i = 0 ; i < records.length ; ++i) {
			String form = records[i].getPath();
			try{
				DataModelPullParser parser;
				File f = new File(form);
				InputStream is = new CipherInputStream(new FileInputStream(f), getDecryptCipher(records[i].getAesKey()));
				parser = new DataModelPullParser(is, new TransactionParserFactory() {
					
					public TransactionParser getParser(String name, String namespace, KXmlParser parser) {
						if(name.toLowerCase().equals("case")) {
							return new CaseXmlParser(parser, c);
						} 
						return null;
					}
					
				});
				
				parser.parse();
				is.close();
				
				//Ok, file's all parsed. Move the instance folder to be ready for sending.
				File folder = f.getCanonicalFile().getParentFile();

				String folderName = folder.getName();
				File newFolder = new File(GlobalConstants.FILE_CC_PROCESSED + folderName);
				if(folder.renameTo(newFolder)) {
					//Good!
					//Time to Send!
					results[i] = sendInstance(newFolder, records[i].getAesKey());
				}
				
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvalidStructureException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (XmlPullParserException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (UnfullfilledRequirementsException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		int result = 0;
		for(int i = 0 ; i < records.length ; ++ i) {
			if(results[i] > result) {
				result = results[i];
			}
		}
		return result;
	}
	
	private static Cipher getDecryptCipher(byte[] key) {
		Cipher cipher;
		try {
			cipher = Cipher.getInstance("AES");
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
			return cipher;
			//TODO: Something smart here;
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	public void setListener(ProcessAndSendListener listener) {
		this.listener = listener;
	}

	@Override
	protected void onPostExecute(Integer result) {
		if(listener != null) {
			if(result == null) {
				result = FAILURE;
			}
			listener.processAndSendFinished(result);
		}
		//These will never get Zero'd otherwise
		c = null;
		url = null;
	}
	
	private int sendInstance(File folder, byte[] aesKey) throws FileNotFoundException {
        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, GlobalConstants.CONNECTION_TIMEOUT);
        HttpConnectionParams.setSoTimeout(params, GlobalConstants.CONNECTION_TIMEOUT);
        HttpClientParams.setRedirecting(params, false);

        // setup client
        DefaultHttpClient httpclient = new DefaultHttpClient(params);
        HttpPost httppost = new HttpPost(url);
        
        String t = "p+a+s";
        
        File[] files = folder.listFiles();
        if (files == null) {
            Log.e(t, "no files to upload");
            cancel(true);
        }
        
        // mime post
        MultipartEntity entity = new MultipartEntity();
        
        for (int j = 0; j < files.length; j++) {
            File f = files[j];
            ContentBody fb;
            if (f.getName().endsWith(".xml")) {
            	
            	//fb = new InputStreamBody(new CipherInputStream(new FileInputStream(f), getDecryptCipher(aesKey)), "text/xml", f.getName());
            	fb = new EncryptedFileBody(f, getDecryptCipher(aesKey), "text/xml");
                entity.addPart("xml_submission_file", fb);
                
                //fb = new FileBody(f, "text/xml");
                //Don't know if we can ask for the content length on the input stream, so skip it.
//                if (fb.getContentLength() <= MAX_BYTES) {
//                    Log.i(t, "added xml file " + f.getName());
//                } else {
//                    Log.i(t, "file " + f.getName() + " is too big");
//                }
            } else if (f.getName().endsWith(".jpg")) {
                fb = new FileBody(f, "image/jpeg");
                if (fb.getContentLength() <= MAX_BYTES) {
                    entity.addPart(f.getName(), fb);
                    Log.i(t, "added image file " + f.getName());
                } else {
                    Log.i(t, "file " + f.getName() + " is too big");
                }
            } else if (f.getName().endsWith(".3gpp")) {
                fb = new FileBody(f, "audio/3gpp");
                if (fb.getContentLength() <= MAX_BYTES) {
                    entity.addPart(f.getName(), fb);
                    Log.i(t, "added audio file " + f.getName());
                } else {
                    Log.i(t, "file " + f.getName() + " is too big");
                }
            } else if (f.getName().endsWith(".3gp")) {
                fb = new FileBody(f, "video/3gpp");
                if (fb.getContentLength() <= MAX_BYTES) {
                    entity.addPart(f.getName(), fb);
                    Log.i(t, "added video file " + f.getName());
                } else {
                    Log.i(t, "file " + f.getName() + " is too big");
                }
            } else {
                Log.w(t, "unsupported file type, not adding file: " + f.getName());
            }
        }
        httppost.setEntity(entity);

        // prepare response and return uploaded
        HttpResponse response = null;
        try {
            response = httpclient.execute(httppost);
        } catch (ClientProtocolException e) {
            e.printStackTrace();
            return TRANSPORT_FAILURE;
        } catch (IOException e) {
            e.printStackTrace();
            return TRANSPORT_FAILURE;
        } catch (IllegalStateException e) {
            e.printStackTrace();
            return TRANSPORT_FAILURE;
        }

        String serverLocation = null;
        Header[] h = response.getHeaders("Location");
        if (h != null && h.length > 0) {
            serverLocation = h[0].getValue();
        } else {
            // something should be done here...
            Log.e(t, "Location header was absent");
        }
        int responseCode = response.getStatusLine().getStatusCode();
        Log.e(t, "Response code:" + responseCode);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        
        try {
			StreamUtil.transfer(response.getEntity().getContent(), bos);
		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        System.out.println(new String(bos.toByteArray()));
        

        if(responseCode >= 200 && responseCode < 300) {
            return FULL_SUCCESS;
        } else {
        	return FAILURE;
        }
	}

}
