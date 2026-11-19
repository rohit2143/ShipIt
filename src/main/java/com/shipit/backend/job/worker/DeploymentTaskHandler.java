package com.shipit.backend.job.worker;

import com.shipit.backend.job.entity.DeploymentJob;

public interface DeploymentTaskHandler {

    /**
     * Executes the deployment lifecycle tasks (cloning, detecting, building, starting, health checking).
     *
     * @param job Active DeploymentJob
     */
    void handleDeployment(DeploymentJob job);
}
