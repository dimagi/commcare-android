/**
 * 
 */
package org.commcare.android.util;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Hashtable;
import java.util.Vector;

import org.commcare.android.references.JavaFileRoot;
import org.commcare.android.references.JavaHttpRoot;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceInitializationException;
import org.commcare.resources.model.ResourceLocation;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.resources.model.installers.ProfileInstaller;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Menu;
import org.commcare.suite.model.Profile;
import org.commcare.suite.model.Suite;
import org.commcare.suite.model.Text;
import org.commcare.util.CommCareInstance;
import org.commcare.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.reference.RootTranslator;
import org.javarosa.core.services.PropertyManager;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.storage.IStorageFactory;
import org.javarosa.core.services.storage.IStorageUtility;
import org.javarosa.core.services.storage.StorageFullException;
import org.javarosa.core.services.storage.StorageManager;

/**
 * @author ctsims
 *
 */
public class CommCareConfigEngine {
	private OutputStream output; 
	private ResourceTable table;
	private PrintStream print;
	private CommCareInstance instance;
	private Vector<Suite> suites;
	private Profile profile;
	
	public CommCareConfigEngine() {
		suites = new Vector<Suite>();
		final int[] version =null;
        
		this.instance = new CommCareInstance() {

			public void registerSuite(Suite s) {
				CommCareConfigEngine.this.suites.add(s);
			}

			public void setProfile(Profile p) {
				CommCareConfigEngine.this.profile = p;
			}

			public int getMajorVersion() {
				return version[0];
			}

			public int getMinorVersion() {
				return version[1];
			}
			
		};
		
		setRoots();
		
		table = ResourceTable.RetrieveTable(new DummyIndexedStorageUtility());
		
		
		//All of the below is on account of the fact that the installers 
		//aren't going through a factory method to handle them differently
		//per device.
		StorageManager.setStorageFactory(new IStorageFactory() {

			public IStorageUtility newStorage(String name, Class type) {
				return new DummyIndexedStorageUtility();
			}
			
		});
		
		StorageManager.registerStorage(Profile.STORAGE_KEY, Profile.class);
		StorageManager.registerStorage(Suite.STORAGE_KEY, Suite.class);
		StorageManager.registerStorage(FormDef.STORAGE_KEY, Suite.class);
		//StorageManager.registerStorage(FormInstance.STORAGE_KEY, Suite.class);
		//StorageManager.registerStorage(Suite.STORAGE_KEY, Suite.class);
		
	}
	
	private void setRoots() {
		ReferenceManager._().addReferenceFactory(new JavaHttpRoot());
	}
	
	public void addLocalFileResource(String resource) {
		//Get the location of the file. In the future, we'll treat this as the resource root
		String root = resource.substring(0,resource.lastIndexOf(File.separator));
		
		//cut off the end
		resource = resource.substring(resource.lastIndexOf(File.separator));
		
		//(That root now reads as jr://file/)
		ReferenceManager._().addReferenceFactory(new JavaFileRoot(root));
		
		//(Now jr://resource/ points there too)
		ReferenceManager._().addRootTranslator(new RootTranslator("jr://resource","jr://file"));
		
		//Now build the testing reference we'll use
		String reference = "jr://file/" + resource;
		
		ResourceLocation location = new ResourceLocation(Resource.RESOURCE_AUTHORITY_LOCAL, reference);
		Vector<ResourceLocation> locations = new Vector<ResourceLocation>();
		locations.add(location);
		Resource test = new Resource(-2, resource.replace("\\",""), locations);
		try {
			table.addResource(test, new ProfileInstaller(),null);
		} catch (StorageFullException e) {
			print.println("Error with Configuration Engine, ran out of room somehow");
			e.printStackTrace(print);
			System.exit(-1);
		}
	}
	
	
	public void addResource(String reference) {
		
	}
	
	public void resolveTable() {
			try {
				table.prepareResources(null, instance);
				print.println("Table resources intialized and fully resolved.");
				print.println(table);
			} catch (UnresolvedResourceException e) {
				print.println("While attempting to resolve the necessary resources, one couldn't be found: " + e.getResource().getResourceId());
				e.printStackTrace(print);
				System.exit(-1);
			} catch (UnfullfilledRequirementsException e) {
				print.println("Unfullfilled platform requirements for suite: " + e.getMessage());
				e.printStackTrace(print);
				System.exit(-1);
			}
	}
	
	public void validateResources() {
		try {
			table.initializeResources(instance);
		} catch (ResourceInitializationException e) {
			print.println("Error while initializing one of the resolved resources");
			e.printStackTrace(print);
			System.exit(-1);
		}
	}
	
	public String describeApplication() {
		String retVal = "";
		retVal += "Locales defined: ";
		for(String locale : Localization.getGlobalLocalizerAdvanced().getAvailableLocales()) {
			retVal += "* " + locale;
		}
		
		Localization.setDefaultLocale("default");
		
		Vector<Menu> root = new Vector<Menu>();
		Hashtable<String, Vector<Menu>> mapping = new Hashtable<String, Vector<Menu>>();
		mapping.put("root",new Vector<Menu>());
		
		for(Suite s : suites) {
			for(Menu m : s.getMenus()) {
				if(m.getId().equals("root")) {
					root.add(m);
				} else {
					mapping.get(m.getRoot()).add(m);
				}
			}
		}
		
		for(String locale : Localization.getGlobalLocalizerAdvanced().getAvailableLocales()) {
			Localization.setLocale(locale);
			
			retVal += "Application details for locale: " + locale;
			retVal += "CommCare";
			
			for(Menu m : mapping.get("root")) {
				retVal += "|- " + m.getName().evaluate();
				for(String command : m.getCommandIds()) {
					for(Suite s : suites) {
						if(s.getEntries().containsKey(command)) {
							retVal += print(s,s.getEntries().get(command),2);
						}
					}
				}
				
			}
			
			for(Menu m : root) {
				for(String command : m.getCommandIds()) {
					for(Suite s : suites) {
						if(s.getEntries().containsKey(command)) {
							retVal += print(s,s.getEntries().get(command),1);
						}
					}
				}
			}
		}
		return retVal;
	}
	
	private String print(Suite s, Entry e, int level) {
		String retVal = "";
		
		String head = "";
		String emptyhead = "";
		for(int i = 0; i < level; ++i ){
			head +=      "|- ";
			emptyhead += "   ";
		}
		retVal += head + "Entry: " + e.getText().evaluate();
		if(e.getReferences().size() > 0) {
			Detail d = s.getDetail(e.getShortDetailId());
			retVal += emptyhead + "|Select: " + d.getTitle().evaluate();
			retVal += emptyhead + "| ";
			for(Text t : d.getHeaders()) {
				retVal += t.evaluate() + " | ";
			}
			retVal += "\n";
		}
		return retVal;
	}
}
