package com.camunda.consulting.zeebemonitor.rest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.camunda.consulting.zeebemonitor.Constants;
import com.camunda.consulting.zeebemonitor.entity.WorkflowInstance;
import com.camunda.consulting.zeebemonitor.repository.WorkflowInstanceRepository;
import com.camunda.consulting.zeebemonitor.zeebe.ZeebeConnections;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.event.TopicSubscription;
import io.zeebe.client.event.WorkflowInstanceEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workflow-instance")
public class WorkflowInstanceResource {

  @Autowired
  private ZeebeConnections connections;

  @Autowired
  private WorkflowInstanceRepository workflowInstanceRepository;

  @RequestMapping("/")
  public Iterable<WorkflowInstance> getWorkflowInstances() {
    return workflowInstanceRepository.findAll();
  }

  @RequestMapping(path="/{key}", method=RequestMethod.DELETE)
  public void cancelWorkflowInstance(@PathVariable("key") long workflowInstanceKey) throws Exception {

    final WorkflowInstance workflowInstance = workflowInstanceRepository.findOne(workflowInstanceKey);
    if (workflowInstance != null)
    {
      final ZeebeClient client = connections.getZeebeClient(workflowInstance.getBroker());

      final WorkflowInstanceEvent event = findLastWorkflowInstanceEvent(workflowInstance, workflowInstanceKey);

      client.workflows() //
          .cancel(event)
          .execute();
      }
  }

  @RequestMapping(path="/{key}/update-payload", method=RequestMethod.PUT)
  public void updatePayload(@PathVariable("key") long workflowInstanceKey, @RequestBody String payload) throws Exception {

    final WorkflowInstance workflowInstance = workflowInstanceRepository.findOne(workflowInstanceKey);
    if (workflowInstance != null)
    {
      final ZeebeClient client = connections.getZeebeClient(workflowInstance.getBroker());

      final WorkflowInstanceEvent event = findLastWorkflowInstanceEvent(workflowInstance, workflowInstance.getLastEventPosition());

      client.workflows() //
        .updatePayload(event)
        .payload(payload)
        .execute();
    }
  }

  private WorkflowInstanceEvent findLastWorkflowInstanceEvent(WorkflowInstance workflowInstance, long position) throws Exception
  {
    final CompletableFuture<WorkflowInstanceEvent> future = new CompletableFuture<>();

    final ZeebeClient client = connections.getZeebeClient(workflowInstance.getBroker());

    TopicSubscription subscription = null;
    try
    {
      subscription = client.topics().newSubscription(Constants.DEFAULT_TOPIC)
        .name("wf-instance-lookup")
        .forcedStart()
        .startAtPosition(position - 1)
        .workflowInstanceEventHandler(wfEvent ->
        {
          if (wfEvent.getMetadata().getPosition() == position)
          {
            future.complete(wfEvent);
          }
        }).open();

       return future.get(10, TimeUnit.SECONDS);
    }
    finally {
      if (subscription != null)
      {
        subscription.close();
      }
    }
  }

}