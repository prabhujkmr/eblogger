/*
 *   This software is distributed under the terms of the FSF
 *   Gnu Lesser General Public License (see lgpl.txt).
 *
 *   This program is distributed WITHOUT ANY WARRANTY. See the
 *   GNU General Public License for more details.
 */
package com.scooterframework.autoloader;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.Set;

import com.scooterframework.admin.ApplicationConfig;
import com.scooterframework.admin.PropertyFileChangeMonitor;
import com.scooterframework.admin.PropertyReader;
import com.scooterframework.common.logging.LogUtil;
import com.scooterframework.common.util.Converters;

/**
 * AutoLoaderConfig class configs autoloading related properties.
 *
 * @author (Fei) John Chen
 */
public class AutoLoaderConfig implements Observer {
    private LogUtil log = LogUtil.getLogger(this.getClass().getName());

    private static final AutoLoaderConfig me = new AutoLoaderConfig();
    private Properties appProperties = null;

    private String outputClassLocation = "";
    private String classpath = "";
    private String sourcepath = "";
    private String pluginsLocation = "";
    private String referencesLocation = "";
    private long period = 0;//in milliseconds
    private String forbiddenFilesPrefix = "java., javax., com.sun., org.apache., com.scooterframework.";

    private Set<String> forbiddenSet = new HashSet<String>();
    private List<String> jarFiles = new ArrayList<String>();

    private FileMonitor fm = null;

    public static final String DATA_PROPERTIES_FILE = "autoloader.properties";
    public static final String GENERATED_MODEL_CLASS_PREFIX = "_";
    public static final String GENERATED_MODEL_CLASS_SUFFIX = "ModelHelper";

    private AutoLoaderConfig() {
        init();

        PropertyFileChangeMonitor.getInstance().registerObserver(this, DATA_PROPERTIES_FILE);
    }

    private void init() {
        loadProperties();

        jarFiles.clear();

        outputClassLocation = "";
        outputClassLocation = ApplicationConfig.getInstance().getClassFileLocationPath();
        outputClassLocation = unifySeparator(getProperty(ApplicationConfig.SYSTEM_KEY_CLASSFILE, outputClassLocation));
        setProperty(ApplicationConfig.SYSTEM_KEY_CLASSFILE, outputClassLocation);
        log.debug("output class location: " + outputClassLocation);

        pluginsLocation = "";
        pluginsLocation = ApplicationConfig.getInstance().getPluginsPath();
        pluginsLocation = unifySeparator(getProperty(ApplicationConfig.SYSTEM_KEY_PLUGINFILE, pluginsLocation));
        setProperty(ApplicationConfig.SYSTEM_KEY_PLUGINFILE, pluginsLocation);
        log.debug("plugins location: " + pluginsLocation);

        referencesLocation = "";
        referencesLocation = ApplicationConfig.getInstance().getReferencesLibPath();
        referencesLocation = unifySeparator(getProperty(ApplicationConfig.SYSTEM_KEY_REFERENCEFILE, referencesLocation));
        setProperty(ApplicationConfig.SYSTEM_KEY_REFERENCEFILE, referencesLocation);
        log.debug("references location: " + referencesLocation);

        classpath = "";
        classpath = appendClassPath(outputClassLocation, getProperty("additional_classpath", classpath));
        classpath = appendClassPath(classpath, getAllWebInfLibFiles());
        classpath = appendClassPath(classpath, getAllOtherLibFiles(pluginsLocation));
        classpath = appendClassPath(classpath, getAllOtherLibFiles(referencesLocation));
        
        String supportPath = System.getProperty("scooter.home") + File.separatorChar + "support";
        classpath = appendClassPath(classpath, getAllOtherLibFiles(supportPath));
        
        //classpath = appendClassPath(classpath, getJDKToolsJar());
        log.debug("class path: " + classpath);

        sourcepath = "";
        sourcepath = ApplicationConfig.getInstance().getSourceFileLocationPath();
        sourcepath = unifySeparator(getProperty(ApplicationConfig.SYSTEM_KEY_SOURCEFILE, sourcepath));
        setProperty(ApplicationConfig.SYSTEM_KEY_SOURCEFILE, sourcepath);
        log.debug("source file location: " + sourcepath);

        forbiddenSet.addAll(Converters.convertStringToSet(forbiddenFilesPrefix));
        String additionalTypes = getProperty("additional_forbidden_files_prefix", null);
        if (additionalTypes != null) forbiddenSet.addAll(Converters.convertStringToSet(additionalTypes));
        log.debug("compiler forbidden files: " + forbiddenSet);

        try {
            period = Long.parseLong(getProperty("source_file_monitor_period", "0"));
        }
        catch(NumberFormatException nfex) {
            period = 0;
        }
    }

    private void loadProperties() {
        if (appProperties != null) appProperties.clear();

        appProperties = PropertyReader.loadPropertiesFromFile(DATA_PROPERTIES_FILE);

        if (appProperties == null) appProperties = new Properties();
    }

    private String unifySeparator(String name) {
        if (name == null) return null;

        char separator = File.separatorChar;
        if (name.indexOf('\\') != -1 &&  '\\' != separator) {
            name = name.replace('\\', separator);
        }
        else
        if (name.indexOf('/') != -1 &&  '/' != separator) {
            name = name.replace('/', separator);
        }
        return name;
    }

    private String appendClassPath(String current, String additional) {
        String result = "";
        if (additional == null || "".equals(additional)) return current;
        if (current != null) {
            if (current.trim().endsWith(File.pathSeparator)) {
                result = current + additional;
            }
            else {
                result = current + File.pathSeparatorChar + additional;
            }
        }
        return result;
    }

    private String getAllWebInfLibFiles() {
        String libdirPath = ApplicationConfig.getInstance().getWebappLibPath();
        File libdir = new File(libdirPath);
        if (!libdir.isDirectory()) {
			log.warn("Path [" + libdirPath + "] is not a directory path under WEB-INF/.");
            return "";
        }

        StringBuilder sb = new StringBuilder();
        File[] files = libdir.listFiles();
        int length = files.length;
        for (int i=0; i<length; i++) {
            File file = files[i];
            String fileName = file.getName();
            if (isLibraryFile(fileName) && !jarFiles.contains(fileName)) {
                jarFiles.add(fileName);
                sb.append(file.getAbsolutePath()).append(File.pathSeparatorChar);
            }
        }
        return sb.toString();
    }

    private boolean isLibraryFile(String fileName) {
        return (fileName.endsWith(".jar") || fileName.endsWith(".zip"))?true:false;
    }

    private String getAllOtherLibFiles(String location) {
        String libdirPath = location;
        File libdir = new File(libdirPath);
        if (!libdir.isDirectory()) {
			log.warn("Path for references [" + libdirPath + "] is not a directory path.");
            return "";
		}
        libdirPath = libdirPath + File.separator;

        List<File> files = getAllFiles(libdirPath);
        StringBuilder sb = new StringBuilder();
        Iterator<File> it = files.iterator();
        while(it.hasNext()) {
            File file = (File)it.next();
            String fileName = file.getName();
            if (!jarFiles.contains(fileName) &&
                (fileName.endsWith(".jar") ||
                (fileName.endsWith(".zip")) ||
                (fileName.endsWith(".tar")) ||
                (fileName.endsWith(".class")))) {
                jarFiles.add(fileName);
                sb.append(file.getAbsolutePath()).append(File.pathSeparatorChar);
            }
        }
        return sb.toString();
    }

    private List<File> getAllFiles(String path) {
        File dir = new File(path);
        List<File> l = new ArrayList<File>();
        appendFilesToList(l, dir);
        return l;
    }

    private void appendFilesToList(List<File> list, File file) {
		if (file.isDirectory()) {
			File[] files = file.listFiles();
			int length = files.length;
			for (int i=0; i<length; i++) {
				File f = files[i];
				appendFilesToList(list, f);
			}
		}
		else
			if (file.isFile()) {
				list.add(file);
			}
	}


    public static AutoLoaderConfig getInstance() {
        return me;
    }

    public void update(Observable o, Object arg) {
        init();

        if (fm != null) {
            fm.update();
        }
    }

    /**
     * Returns all properties.
     */
    public Properties getProperties() {
        return appProperties;
    }

    /**
     * Returns a String property corresponding to a key. The method returns the
     * default value argument if the property is not found.
     */
    public String getProperty(String key, String defaultValue) {
        return appProperties.getProperty(key, defaultValue);
    }

    /**
     * Sets a property.
     */
    public void setProperty(String key, String value) {
        appProperties.setProperty(key, value);
    }

    public long getPeriod() {
        return period;
    }

    public String getClassPath() {
        return classpath;
    }

    public String getOutputClassLocation() {
        return outputClassLocation;
    }

    public String getSourcePath() {
        return sourcepath;
    }

    public String getPluginsLocation() {
        return pluginsLocation;
    }

    public String getReferencesLocation() {
        return referencesLocation;
    }

    public boolean notAllowedToChange(String className) {
        if (className == null) return true;
        
        if (className.endsWith(GENERATED_MODEL_CLASS_SUFFIX)) {
        	int lastDot = className.lastIndexOf('.');
        	String tmp = className;
        	if (lastDot != -1) tmp = className.substring(lastDot + 1);
        	if (tmp.startsWith(GENERATED_MODEL_CLASS_PREFIX)) return true;
        }

        boolean result = false;
        Iterator<String> it = forbiddenSet.iterator();
        while(it.hasNext()) {
            String item = it.next();
            if (className.startsWith(item)) {
                result = true;
                break;
            }
        }
        return result;
    }

    public void registerFileMonitor(FileMonitor fm) {
        this.fm = fm;
    }
}