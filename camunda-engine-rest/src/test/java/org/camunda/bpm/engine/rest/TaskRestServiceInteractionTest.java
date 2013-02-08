package org.camunda.bpm.engine.rest;

import static com.jayway.restassured.RestAssured.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.Response.Status;

import org.activiti.engine.ActivitiException;
import org.activiti.engine.TaskService;
import org.camunda.bpm.engine.rest.helper.EqualsMap;
import org.junit.Test;
import org.mockito.Matchers;

public class TaskRestServiceInteractionTest extends AbstractRestServiceTest {

  private static final String TASK_SERVICE_URL = TEST_RESOURCE_ROOT_PATH + "/task";
  private static final String CLAIM_TASK_URL = TASK_SERVICE_URL + "/{id}/claim";
  private static final String COMPLETE_TASK_URL = TASK_SERVICE_URL + "/{id}/complete";
  
  private static final String EXAMPLE_TASK_ID = "aTaskId";
  private static final String EXAMPLE_USER_ID = "aUser";
  
  private TaskService taskServiceMock;
  
  public void setupMockTaskService() {
    loadProcessEngineService();
    
    taskServiceMock = mock(TaskService.class);
    when(processEngine.getTaskService()).thenReturn(taskServiceMock);
  }
  
  @Test
  public void testClaimTask() {
    setupMockTaskService();
    
    Map<String, Object> json = new HashMap<String, Object>();
    json.put("userId", EXAMPLE_USER_ID);
    
    given().pathParam("id", EXAMPLE_TASK_ID)
      .contentType(POST_JSON_CONTENT_TYPE).body(json)
      .then().expect()
        .statusCode(Status.NO_CONTENT.getStatusCode())
      .when().post(CLAIM_TASK_URL);
    
    verify(taskServiceMock).claim(EXAMPLE_TASK_ID, EXAMPLE_USER_ID);
  }
  
  @Test
  public void testMissingUserId() {
  setupMockTaskService();
    
    Map<String, Object> json = new HashMap<String, Object>();
    json.put("userId", null);
    
    given().pathParam("id", EXAMPLE_TASK_ID)
      .contentType(POST_JSON_CONTENT_TYPE).body(json)
      .then().expect()
        .statusCode(Status.NO_CONTENT.getStatusCode())
      .when().post(CLAIM_TASK_URL);
    
    verify(taskServiceMock).claim(EXAMPLE_TASK_ID, null);
  }
  
  @Test
  public void testUnsuccessfulClaimTask() {
    setupMockTaskService();
    
    doThrow(new ActivitiException("expected exception")).when(taskServiceMock).claim(any(String.class), any(String.class));
    
    // TODO not sure if Conflict is the correct status code we should assume here, as there could also be other reasons for the ActivitiException.
    // perhaps 500 is better?
    given().pathParam("id", EXAMPLE_TASK_ID)
      .contentType(POST_JSON_CONTENT_TYPE).body(EMPTY_JSON_OBJECT)
      .then().expect()
        .statusCode(Status.INTERNAL_SERVER_ERROR.getStatusCode())
      .when().post(CLAIM_TASK_URL);
  }
  
  @Test
  public void testCompleteTask() {
    setupMockTaskService();
    
    given().pathParam("id", EXAMPLE_TASK_ID)
    .contentType(POST_JSON_CONTENT_TYPE).body(EMPTY_JSON_OBJECT)
    .then().expect()
      .statusCode(Status.NO_CONTENT.getStatusCode())
    .when().post(COMPLETE_TASK_URL);
    
    verify(taskServiceMock).complete(EXAMPLE_TASK_ID, null);
  }
  
  @Test
  public void testCompleteWithParameters() {
    setupMockTaskService();
    
    Map<String, Object> variables = new HashMap<String, Object>();
    variables.put("aVariable", "aStringValue");
    variables.put("anotherVariable", 42);
    variables.put("aThirdVariable", Boolean.TRUE);
    
    Map<String, Object> json = new HashMap<String, Object>();
    json.put("variables", variables);
    
    given().pathParam("id", EXAMPLE_TASK_ID)
      .contentType(POST_JSON_CONTENT_TYPE).body(json)
      .then().expect()
        .statusCode(Status.NO_CONTENT.getStatusCode())
      .when().post(COMPLETE_TASK_URL);
    
    verify(taskServiceMock).complete(eq(EXAMPLE_TASK_ID), argThat(new EqualsMap(variables)));
  }
  
  @Test
  public void testUnsuccessfulCompleteTask() {
    setupMockTaskService();
    
    doThrow(new ActivitiException("expected exception")).when(taskServiceMock).complete(any(String.class), Matchers.<Map<String, Object>>any());
    
    given().pathParam("id", EXAMPLE_TASK_ID)
      .contentType(POST_JSON_CONTENT_TYPE).body(EMPTY_JSON_OBJECT)
      .then().expect()
        .statusCode(Status.INTERNAL_SERVER_ERROR.getStatusCode())
      .when().post(COMPLETE_TASK_URL);
  }
  
}
