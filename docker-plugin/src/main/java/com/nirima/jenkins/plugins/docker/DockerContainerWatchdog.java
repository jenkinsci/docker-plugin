package com.nirima.jenkins.plugins.docker;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.nirima.jenkins.plugins.docker.utils.JenkinsUtils;

import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.slaves.Cloud;
import jenkins.model.Jenkins;

/**
 * Periodic job, which gets executed by Jenkins automatically, to ensure
 * the consistency of the containers currently running on the docker and the
 * slaves which are attached to this Jenkins instance.
 * @author eaglerainbow
 *
 */
@Extension
public class DockerContainerWatchdog extends AsyncPeriodicWork {
    private TaskListener currentTaskListener;
    
    protected DockerContainerWatchdog(String name) {
        super(name);
    }
    
    public DockerContainerWatchdog() {
        super("Docker Container Watchdog Asynchronouse Periodic Work");
    }

    private static final long RECURRENCE_PERIOD_IN_MS =  1 * 60 * 1000; // 5 minutes
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerContainerWatchdog.class);

    private transient long currentUnixTimestamp;
    private transient static final DateFormat dfISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");
    private transient static final Pattern removeNanoSeconds = Pattern.compile("(.*)\\.[0-9]{9}Z$");
    private HashSet<String> allComputers;
    
    @Override
    public long getRecurrencePeriod() {
        // value is in ms.
        return RECURRENCE_PERIOD_IN_MS;
    }

    @Override
    protected void execute(TaskListener listener) throws IOException, InterruptedException {
        LOGGER.info("Docker Container Watchdog has been triggered");

        // preparations
        this.currentTaskListener = listener;

        Calendar aGMTCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        this.currentUnixTimestamp = (long) (aGMTCalendar.getTimeInMillis() / 1000);
        
        this.loadComputers();
        
        for (Cloud c : Jenkins.getInstance().clouds) {
            if (!(c instanceof DockerCloud)) {
                continue;
            }
            DockerCloud dc = (DockerCloud) c;
            LOGGER.info("Checking Docker Cloud '{}'", dc.getDisplayName());
            this.currentTaskListener.getLogger().println(String.format("Checking Docker Cloud %s", dc.getDisplayName()));
            
            this.checkCloud(dc);
        }
        
        LOGGER.info("Docker Container Watchdog check has been completed");
    }

    private void checkCloud(DockerCloud dc) {
        DockerClient client = dc.getClient();
        String label = String.format("%s=%s", "JenkinsId", JenkinsUtils.getInstanceId());
        
        /* get a list of all the containers which this Jenkins instance 
         * has started
         */
        List<Container> containers = client.listContainersCmd()
                .withShowAll(true)
                .withLabelFilter(label)
                .exec();
        
        for (Container c : containers) {
            String containerId = c.getId();
            String status = c.getStatus();
            
            if (status == null) {
                LOGGER.warn("Container {} has a null-status and thus cannot be checked", containerId);
                continue;
            }
            
            // check for already exited containers
            if (status.startsWith("Exited")) {
                InspectContainerResponse icr = client.inspectContainerCmd(containerId).exec();
                LOGGER.info("Container {} has already exited with status '{}', but was not removed; it finished at {}", containerId, status, icr.getState().getFinishedAt());

				if (dc.isWatchdogTtlExitedEnabled()) { 
	                // check if the TTL has already expired
	                Date finishedAt = parseFinishedDate(icr.getState().getFinishedAt());
	                if (finishedAt == null) {
	                	LOGGER.warn("Unable to retrieve finished timestamp of java-docker API; skipping this container");
	                	continue;
	                }
	                
	                long finishedAtUnixTimestamp = finishedAt.getTime() / 1000;
	                
	                if (finishedAtUnixTimestamp + dc.getWatchdogTtlExited() * 60 < this.currentUnixTimestamp) { 
	                	LOGGER.info("TTL for exited container {} has passed; now removing it", containerId);
	                	try {
	                		client.removeContainerCmd(containerId).exec();
	                	} catch (Exception e) {
	                		LOGGER.warn("Removing the container failed due to an exception of the docker-java API", e);
	                	}
	                }
				}
                
                continue; // no further checks to perform on this container
            }
            
            // check on long running containers, if they are still attached to this
            // Jenkins instance
            long created = c.getCreated(); // UTC unix timestamp
            
            long lifetimeContainer = this.currentUnixTimestamp - created;
            // contains the lifetime of the container in seconds
            
            if (lifetimeContainer < 60) {
                /* Containers which are running less than 60s
                 * are ignored. This value can also be considered as a 
                 * "grace period" until the container would have been expected
                 * to be removed after it terminated.
                 * NB: This also resolves the problem of containers currently
                 * in the provisioning phase, as provisioning should
                 * not take so long.
                 */
                continue;
            }
            
            // verify that this container is still being used by one of our slaves
            if (!isContainerStillRunning(containerId)) {
                LOGGER.info("Our container {} is still running for {} seconds on docker, but there is no computer associated in Jenkins to it anymore", 
                        containerId, new Long(lifetimeContainer).toString());
                
                if (dc.isWatchdogTtlRunningEnabled() && lifetimeContainer > dc.getWatchdogTtlRunning() * 60) {
                	LOGGER.info("The container {} has passed the Running TTL; requesting to stop and remove it", containerId);
                	try {
                		client.stopContainerCmd(containerId).exec();
                		LOGGER.info("Container {} is stopped; trying to remove it", containerId);
                        client.removeContainerCmd(containerId).exec();
                        LOGGER.info("Container {} is removed", containerId);
                	} catch (Exception e) {
                		LOGGER.warn("Stopping and removing the container failed due to an exception by the docker-java API", e);
                		// besides reporting, ignore this error
                		continue;
                	}
                }
            }

        }
    }
    
    private static Date parseFinishedDate(String finishedAtString) {
    	/* Docker provides nanoseconds in the string
    	 * Unfortunately, DateFormat can only deal with microseconds (at best) 
    	 * and the better approach (using DateTimeFormatter) is only available with JDK 8.
    	 * As we don't need that high precision, we just remove it
    	 * from the string by using a regular expression.
    	 */
    	String dateTimeString = finishedAtString;
    	Matcher m = removeNanoSeconds.matcher(dateTimeString);
    	if (m.matches()) {
    		dateTimeString = m.group(1)+"Z";
    	} 
    	// if it does not match, still try to parse it - perhaps we are lucky...
    	
    	Date finishedAt = null;
        try {
        	finishedAt = dfISO8601.parse(dateTimeString);
        } catch (ParseException pe) {
        	/* the date is not parsable. Therefore, we do not have any chance
        	 * to determine if the TTL has passed or not. Thus, let's log this
        	 * event and skip the entry
        	 */
        	LOGGER.warn("Finished timestamp is not in the ISO-8601 format, which should be the case", pe);
        }
        return finishedAt;

    }

    private void loadComputers() {
        Computer[] computers = Jenkins.getInstance().getComputers();
        
        if (computers.length == 0) {
            this.allComputers = new HashSet<>();
            return; // nothing to do
        }
        
        this.allComputers = new HashSet<>(computers.length); // only rough estimation of size necessary
        
        for (Computer c : computers) {
            if (c instanceof DockerComputer) {
                this.allComputers.add( ((DockerComputer) c).getContainerId() );
            }
        }
    }

    private boolean isContainerStillRunning(String containerId) {
        return this.allComputers.contains(containerId);
    }
}
