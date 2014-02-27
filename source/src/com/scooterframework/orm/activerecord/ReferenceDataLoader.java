/*
 *   This software is distributed under the terms of the FSF
 *   Gnu Lesser General Public License (see lgpl.txt).
 *
 *   This program is distributed WITHOUT ANY WARRANTY. See the
 *   GNU General Public License for more details.
 */
package com.scooterframework.orm.activerecord;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import com.scooterframework.orm.sqldataexpress.config.DatabaseConfig;
import com.scooterframework.orm.sqldataexpress.object.RowData;
import com.scooterframework.orm.sqldataexpress.service.SqlServiceClient;

/**
 * ReferenceDataLoader is responsible for loading reference data.
 *
 * @author (Fei) John Chen
 */
public class ReferenceDataLoader implements Observer {
    private static Map<String, ReferenceDataTimerTask> referenceDataTaskMap = new HashMap<String, ReferenceDataTimerTask>();
    private static long oneHundredDays = 8640000;
    private static boolean started = false;
    private Date oldDate = null;
    private Timer timer = null;

    public ReferenceDataLoader() {
        oldDate = new Date();
        oldDate.setTime(oldDate.getTime()-oneHundredDays);

        DatabaseConfig.getInstance().addObserver(this);
    }

    public void start() {
        if (started) return;

        timer = new Timer();

        List<String> refNames = DatabaseConfig.getInstance().getReferenceDataNames();
        if (refNames != null && refNames.size() > 0) {
            Iterator<String> it = refNames.iterator();
            while(it.hasNext()) {
                String name = it.next();
                Properties prop = DatabaseConfig.getInstance().getReferenceDataProperties(name);
                ReferenceDataTimerTask task = new ReferenceDataTimerTask(name, prop);
                referenceDataTaskMap.put(name, task);
                schedule(task, task.period);
            }
        }

        started = true;
    }

    /**
     * Terminates this loader, discarding any currently scheduled tasks.
     *
     * @see  java.util.Timer#cancel()
     */
    public void cancel() {
        timer.cancel();
    }

    /**
     * Terminates this loader, discarding any currently scheduled tasks.
     *
     * @see  java.util.Timer#cancel()
     */
    public void stop() {
        timer.cancel();
        started = false;
    }

    /**
     * Updates this loader, restarts the timer task if its period is changed.
     */
    public void update(Observable o, Object arg) {
        List<String> refNames = DatabaseConfig.getInstance().getReferenceDataNames();
        if (refNames != null && refNames.size() > 0) {
            Iterator<String> it = refNames.iterator();
            while(it.hasNext()) {
                String name = it.next();
                Properties prop = DatabaseConfig.getInstance().getReferenceDataProperties(name);
                if (referenceDataTaskMap.containsKey(name)) {
                    ReferenceDataTimerTask task = (ReferenceDataTimerTask)referenceDataTaskMap.get(name);
                    task.resetProperties(prop);
                    if (task.periodModified) {
                        task.cancel();
                        task = new ReferenceDataTimerTask(name, prop);
                        referenceDataTaskMap.put(name, task);
                        schedule(task, task.period);
                    }
                }
                else {
                    ReferenceDataTimerTask task = new ReferenceDataTimerTask(name, prop);
                    referenceDataTaskMap.put(name, task);
                    schedule(task, task.period);
                }
            }
        }

        //now remove all reference data tasks that have been removed from property file.
        for (Map.Entry<String, ReferenceDataTimerTask> entry : referenceDataTaskMap.entrySet()) {
        	String name = entry.getKey();
            if (!refNames.contains(name)) {
                ReferenceDataTimerTask task = entry.getValue();
                task.cancel();
                referenceDataTaskMap.remove(name);
            }
        }
    }

    public static boolean isStarted() {
        return started;
    }

    public static Map<String, ReferenceDataTimerTask>  getReferenceDataTasks() {
        return referenceDataTaskMap;
    }

    private void schedule(TimerTask task, long period) {
        if (period > 0) {
            timer.schedule(task, oldDate, period);
        }
        else {
            timer.schedule(task, oldDate);
        }
    }

    private static class ReferenceDataTimerTask extends TimerTask {
        String theName = "";
        String clz = null;
        String sql = null;
        String key = "";
        String value = "";
        long period = 0;//in milliseconds
        Date loadedTime = null;
        boolean hasRun = false;
        boolean periodModified = false;
        boolean runOnlyOnce = false;
        boolean donotRun = false;

        public ReferenceDataTimerTask(String name, Properties prop) {
            super();
            theName = name;
            init(prop);
        }

        private void init(Properties prop) {
            clz = prop.getProperty("class");
            sql = prop.getProperty("sql");
            if (clz == null && sql == null)
                throw new IllegalArgumentException("Either clz or sql must be specified for reference data " + theName + ".");

            key = prop.getProperty("key");
            value = prop.getProperty("value");

            long thePeriod = 0;
            try {
                thePeriod = Long.parseLong(prop.getProperty("period", "0"));
            }
            catch(NumberFormatException nfex) {
                thePeriod = 0;
            }

            periodModified = false;
            //if ((hasRun && (thePeriod != 0) || !hasRun && (thePeriod == 0)) && (period != thePeriod)) {
            if (period != thePeriod) {
                periodModified = true;
            }

            period = thePeriod;

            if (period == 0) {
                runOnlyOnce = true;
            }
            else if (period < 0) {
                donotRun = true;
            }
        }

        public void resetProperties(Properties prop) {
            init(prop);
        }

        public void run() {
            if (donotRun || (runOnlyOnce && hasRun)) return;

            if (clz != null) {
                List<ActiveRecord> records = null;
                if (sql != null) {
                    records = ActiveRecordUtil.getGateway(clz).findAllBySQL(sql);
                }
                else {
                    records = ActiveRecordUtil.getGateway(clz).findAll();
                }
                ReferenceDataStore.setReferenceData(theName, convertRecordsToReferenceDataList(records));
            }
            else {
                List<RowData> rows = SqlServiceClient.retrieveRowsBySQL(sql);
                ReferenceDataStore.setReferenceData(theName, convertRowsToReferenceDataList(rows));
            }

            loadedTime = new Date();
            hasRun = true;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("name=" + theName).append(", ");
            sb.append("clz=" + clz).append(", ");
            sb.append("sql=" + sql).append(", ");
            sb.append("key=" + key).append(", ");
            sb.append("value=" + value).append(", ");
            sb.append("period=" + period).append(", ");
            sb.append("loadedTime=" + loadedTime);
            return sb.toString();
        }

        private List<ReferenceData> convertRecordsToReferenceDataList(List<ActiveRecord> records) {
            if (records == null) return null;

            List<ReferenceData> list = new ArrayList<ReferenceData>(records.size());
            Iterator<ActiveRecord> it = records.iterator();
            while(it.hasNext()) {
                ActiveRecord record = it.next();
                ReferenceDataRecord rdr = new ReferenceDataRecord(theName, key, value, record);
                list.add(rdr);
            }
            return list;
        }

        private List<ReferenceData> convertRowsToReferenceDataList(List<RowData> records) {
            if (records == null) return null;

            List<ReferenceData> list = new ArrayList<ReferenceData>(records.size());
            Iterator<RowData> it = records.iterator();
            while(it.hasNext()) {
                RowData row = it.next();
                ReferenceData rdr = new ReferenceDataRecord(theName, key, value, row);
                list.add(rdr);
            }
            return list;
        }
    }
}
