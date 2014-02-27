/*
 *   This software is distributed under the terms of the FSF 
 *   Gnu Lesser General Public License (see lgpl.txt). 
 *
 *   This program is distributed WITHOUT ANY WARRANTY. See the
 *   GNU General Public License for more details.
 */
package com.scooterframework.autoloader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.scooterframework.common.logging.LogUtil;

/**
 * <p>
 * FileMonitor is responsible for monitoring file changes. Changed files are
 * automatically recompiled.
 * </p>
 * 
 * <p>
 * The default monitor interval is 1000 milliseconds. This can be changed by
 * updating the <tt>source_file_monitor_period</tt> property in
 * <tt>autoloader.properties</tt> file.
 * </p>
 * 
 * @author (Fei) John Chen
 */
public class FileMonitor {
    private LogUtil log = LogUtil.getLogger(this.getClass().getName());
    
    private static long oneHundredDays = 8640000;
    private static boolean started = false;
    private Date oldDate = null;
    private Timer timer = null;
    private long period = 0L;
    private String sourcePath = "";
    private long lastScanTime = 0L;
    private static ConcurrentMap<String, SourceFile> sourceMap = new ConcurrentHashMap<String, SourceFile>();
    private ConcurrentMap<String, SourceFile> modifiedSources = new ConcurrentHashMap<String, SourceFile>();
    private long latestChange = 0L;
    
    private static FileMonitor fm;
    
    public static boolean turnOff = false;
    
    static {
        fm = new FileMonitor();
    }
    
    private FileMonitor() {
        sourcePath = AutoLoaderConfig.getInstance().getSourcePath();
        
        AutoLoaderConfig.getInstance().registerFileMonitor(this);
    }
    
    public static FileMonitor getInstance() {
        return fm;
    }
    
    public void start() {
        if (turnOff || started) return;
        
        oldDate = new Date();
        oldDate.setTime(oldDate.getTime()-oneHundredDays);
        
        timer = new Timer();
        sourcePath = AutoLoaderConfig.getInstance().getSourcePath();
        period = AutoLoaderConfig.getInstance().getPeriod();
        
        if (period > 0) {
            SourceFileTimerTask sourceTask = 
                new SourceFileTimerTask(sourcePath);
            schedule(sourceTask, period);
            
            started = true;
            log.debug("Java source file change monitor started with an interval of " + period + " milliseconds.");
        }
    }
    
    /**
     * Terminates this loader, discarding any currently scheduled tasks.
     * 
     * @see  java.util.Timer#cancel()
     */
    public void stop() {
    	if (!started) return;
        if (timer != null) {
            timer.cancel();
            log.debug("Java source file change monitor stopped.");
        }
        started = false;
    }
    
    /**
     * Updates the FileMonitor, restarts the timer if the period is changed.
     */
    public void update() {
        long newPeriod = AutoLoaderConfig.getInstance().getPeriod();
        if (newPeriod != period) {
            stop();
            if (newPeriod > 0) {
                start();
            }
        }
    }
    
    public static boolean isStarted() {
        return started;
    }
    
    /**
     * Only those classes that are under src directory are monitored.
     * 
     * @param className
     * @return true if the class is monitored.
     */
    public static boolean isClassMonitored(String className) {
        return sourceMap.containsKey(className);
    }
    
    public static SourceFile getSourceFile(String className) {
        SourceFile sf = (SourceFile)sourceMap.get(className);
        if (sf == null) {
            sf = SourceFileHelper.getSourceFileFromClassName(className);
        }
        return sf;
    }
    
    public long getLastScanTime() {
        return lastScanTime;
    }
    
    private void schedule(TimerTask task, long period) {
        if (period > 0) {
            timer.schedule(task, oldDate, period);
        }
        else {
            timer.schedule(task, oldDate);
        }
    }
    
    private void scanAllSources(String sourceLocation) {
    	if (sourceLocation == null) return;
    	
		try {
			StringTokenizer st = new StringTokenizer(sourceLocation, File.pathSeparator);
			while (st.hasMoreElements()) {
				String sourceDirPath = (String) st.nextElement();
				File base = new File(sourceDirPath);
				scanFiles(base, sourceDirPath);
			}
			lastScanTime = (new Date()).getTime();

			recompile();
		} catch (Exception ex) {
			log.error("Error in scanAllSources() for " + sourceLocation + ": " + ex);
		}
    }
        
    private void scanFiles(File file, String sourceDirPath) throws IOException {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            int length = files.length;
            for (int i=0; i<length; i++) {
                File f = files[i];
                scanFiles(f, sourceDirPath);
            }
        }
        else {
            String fn = file.getName();
            if (fn.endsWith("java") && (fn.indexOf(' ') == -1)) {
                processJavaFile(file, sourceDirPath);
            }
        }
    }
    
    private void processJavaFile(File file, String sourceDirPath) throws IOException {
        String filePath = file.getCanonicalPath();
        String className = SourceFileHelper.getClassNameFromSourceFile(file, sourceDirPath);
        if (sourceMap.containsKey(className)) {
            SourceFile sf = (SourceFile)sourceMap.get(className);
            if (sf.isUpdated(file) || sf.availableForRecompile()) {
            	modifiedSources.putIfAbsent(filePath, sf);
            }
        }
        else {
        	SourceFile sf =  new SourceFile(file, sourceDirPath);
            sourceMap.put(className, sf);
            
            if (sf.availableForRecompile()) modifiedSources.put(filePath, sf);
        }
    }
    
    private void recompile() throws Exception {
    	if (modifiedSources.size() <= 0) return;
    	
        //1. check if there is any change in source files
        List<File> files = new ArrayList<File>(modifiedSources.size());
        long sumTime = 0L;
        for (Map.Entry<String, SourceFile> entry : modifiedSources.entrySet()) {
            SourceFile sf = entry.getValue();
            
            if (sf.getSource().exists()) {
                files.add(sf.getSource());
                sumTime += sf.getLastSourceModifiedTime();
            }
        }
        
        if (sumTime == latestChange) return;
        
        //2. recompile
        log.debug("recompile classes: " + files);
        latestChange = sumTime;
        
        String result = JavaCompiler.compile(files);
        
        //3. transform
        if (result == null || "".equals(result)) {
        	List<String> classNames = new ArrayList<String>();
            for (Map.Entry<String, SourceFile> entry : modifiedSources.entrySet()) {
                SourceFile sf = entry.getValue();
                if (sf.getClassFile().exists()) {
                	String className = SourceFileHelper.getClassNameFromClassFile(sf.getClassFile());
                	classNames.add(className);
                }
            }
            ClassWorkHelper.preloadClasses(classNames);
        }
        
        //4. cleanup
        if (result == null || "".equals(result)) {
        	modifiedSources.clear();
        }
    }
    
    /**
     * SourceFileTimerTask is responsible for scanning files.
     */
    public class SourceFileTimerTask extends TimerTask {
        private String sourceLocation = "";
        
        public SourceFileTimerTask(String sourceLocation) {
            super();
            this.sourceLocation = sourceLocation;
        }
        
        public void run() {
            if (started) scanAllSources(sourceLocation);
        }
    }
}
