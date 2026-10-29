package com.cadence.application.usecases;

import com.cadence.application.exceptions.ResourceNotFoundException;
import com.cadence.application.repositories.DurableJobRepository;
import com.cadence.domain.DurableJob;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("denyAll()")
@Transactional(readOnly = true)
public class GetJobUseCase {
  private final DurableJobRepository jobs;

  public GetJobUseCase(DurableJobRepository jobs) {
    this.jobs = jobs;
  }

  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #permittedWorkspaceIds)")
  public DurableJob execute(UUID jobId, Set<UUID> permittedWorkspaceIds) {
    if (jobId == null || permittedWorkspaceIds == null || permittedWorkspaceIds.isEmpty()) {
      throw new ResourceNotFoundException("Job not found");
    }
    return jobs.findEvidenceJobInWorkspaces(jobId, permittedWorkspaceIds)
        .orElseThrow(() -> new ResourceNotFoundException("Job not found"));
  }
}
