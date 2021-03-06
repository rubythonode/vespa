// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService.BuildJob;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Stores a queue for each type of job, and offers jobs from each of these to a periodic
 * polling mechanism which is responsible for triggering the offered jobs in an external build service.
 *
 * @author jvenstad
 * @author mpolden
 */
public class PolledBuildSystem implements BuildSystem {

    private final Controller controller;

    private final CuratorDb curator;
    
    public PolledBuildSystem(Controller controller, CuratorDb curator) {
        this.controller = controller;
        this.curator = curator;
    }

    @Override
    public void addJob(ApplicationId application, JobType jobType, boolean first) {
        try (Lock lock = curator.lockJobQueues()) {
            Deque<ApplicationId> queue = curator.readJobQueue(jobType);
            if ( ! queue.contains(application)) {
                if (first) {
                    queue.addFirst(application);
                } else {
                    queue.add(application);
                }
            }
            curator.writeJobQueue(jobType, queue);
        }
    }

    @Override
    public List<BuildJob> jobs() {
        return getJobs(false);
    }

    @Override
    public List<BuildJob> takeJobsToRun() {
        return getJobs(true);
    }
    
    
    @Override
    public void removeJobs(ApplicationId application) {
        try (Lock lock = curator.lockJobQueues()) {
            for (JobType jobType : JobType.values()) {
                Deque<ApplicationId> queue = curator.readJobQueue(jobType);
                while (queue.remove(application)) {
                    // keep removing until not found 
                }
                curator.writeJobQueue(jobType, queue);
            }
        }
    }
    
    private List<BuildJob> getJobs(boolean removeFromQueue) {
        try (Lock lock = curator.lockJobQueues()) {
            List<BuildJob> jobsToRun = new ArrayList<>();
            for (JobType jobType : JobType.values()) {
                Deque<ApplicationId> queue = curator.readJobQueue(jobType);
                for (ApplicationId a : queue) {
                    ApplicationId application = removeFromQueue ? queue.poll() : a;
                    jobsToRun.add(new BuildJob(projectIdFor(application), jobType.id()));

                    // Return only one job at a time for capacity constrained queues
                    if (removeFromQueue && isCapacityConstrained(jobType)) break;
                }
                if (removeFromQueue)
                    curator.writeJobQueue(jobType, queue);
            }
            return Collections.unmodifiableList(jobsToRun);
        }
    }

    private Long projectIdFor(ApplicationId applicationId) {
        return controller.applications().require(applicationId).deploymentJobs().projectId().get();
    }

    private static boolean isCapacityConstrained(JobType jobType) {
        return jobType == JobType.stagingTest || jobType == JobType.systemTest;
    }

}
