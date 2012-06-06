/**
 * Copyright (C) 2011, 2012 camunda services GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.camunda.fox.platform.subsystem.impl.extension.handler;

import static com.camunda.fox.platform.subsystem.impl.extension.ModelConstants.DATASOURCE;
import static com.camunda.fox.platform.subsystem.impl.extension.ModelConstants.DEFAULT;
import static com.camunda.fox.platform.subsystem.impl.extension.ModelConstants.HISTORY_LEVEL;
import static com.camunda.fox.platform.subsystem.impl.extension.ModelConstants.NAME;
import static com.camunda.fox.platform.subsystem.impl.extension.ModelConstants.PROPERTIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REQUEST_PROPERTIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REQUIRED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE_TYPE;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.transaction.TransactionManager;

import org.jboss.as.connector.subsystems.datasources.DataSourceReferenceFactoryService;
import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ServiceVerificationHandler;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.naming.deployment.ContextNames;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;

import com.camunda.fox.platform.spi.ProcessEngineConfiguration;
import com.camunda.fox.platform.subsystem.impl.extension.Element;
import com.camunda.fox.platform.subsystem.impl.platform.ContainerJobExecutorService;
import com.camunda.fox.platform.subsystem.impl.platform.ContainerPlatformService;
import com.camunda.fox.platform.subsystem.impl.platform.ProcessEngineConfigurationImpl;
import com.camunda.fox.platform.subsystem.impl.platform.ProcessEngineControllerService;

/**
 * Provides the description and the implementation of the process-engine#add operation.
 * 
 * @author Daniel Meyer
 */
public class ProcessEngineAdd extends AbstractAddStepHandler implements DescriptionProvider {
    
  public static final ProcessEngineAdd INSTANCE = new ProcessEngineAdd();

  public ModelNode getModelDescription(Locale locale) {
    ModelNode node = new ModelNode();
    node.get(DESCRIPTION).set("Adds a process engine");
    node.get(OPERATION_NAME).set(ADD);
    
    node.get(REQUEST_PROPERTIES, NAME, DESCRIPTION).set("Name of the process engine");
    node.get(REQUEST_PROPERTIES, NAME, TYPE).set(ModelType.STRING);
    node.get(REQUEST_PROPERTIES, NAME, REQUIRED).set(true);
    
    node.get(REQUEST_PROPERTIES, DATASOURCE, DESCRIPTION).set("Which datasource to use");
    node.get(REQUEST_PROPERTIES, DATASOURCE, TYPE).set(ModelType.STRING);
    node.get(REQUEST_PROPERTIES, DATASOURCE, REQUIRED).set(true);
    
    node.get(REQUEST_PROPERTIES, DEFAULT, DESCRIPTION).set("Should it be the default engine");
    node.get(REQUEST_PROPERTIES, DEFAULT, TYPE).set(ModelType.BOOLEAN);
    node.get(REQUEST_PROPERTIES, DEFAULT, REQUIRED).set(false);
    
    node.get(REQUEST_PROPERTIES, HISTORY_LEVEL, DESCRIPTION).set("Which history level to use");
    node.get(REQUEST_PROPERTIES, HISTORY_LEVEL, TYPE).set(ModelType.STRING);
    node.get(REQUEST_PROPERTIES, HISTORY_LEVEL, REQUIRED).set(false);
    
    node.get(REQUEST_PROPERTIES, PROPERTIES, DESCRIPTION).set("Additional properties");
    node.get(REQUEST_PROPERTIES, PROPERTIES, TYPE).set(ModelType.OBJECT);
    node.get(REQUEST_PROPERTIES, PROPERTIES, VALUE_TYPE).set(ModelType.LIST);
    node.get(REQUEST_PROPERTIES, PROPERTIES, REQUIRED).set(false);

    return node;
  }

  @Override
  protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
    String name = "default";
    if (operation.hasDefined(NAME)) {
      name = operation.get(NAME).asString();
    }
    model.get(NAME).set(name);
    
    Boolean isDefault = Boolean.FALSE;
    if (operation.hasDefined(DEFAULT)) {
      isDefault = operation.get(DEFAULT).asBoolean();
    }
    model.get(DEFAULT).set(isDefault);
    
    String historyLevel = "audit";
    if (operation.hasDefined(HISTORY_LEVEL)) {
      historyLevel = operation.get(HISTORY_LEVEL).asString();
    }
    model.get(HISTORY_LEVEL).set(historyLevel);
    
    String datasource = "java:jboss/datasources/ExampleDS";
    if (operation.hasDefined(DATASOURCE)) {
      datasource = operation.get(DATASOURCE).asString();
    }
    model.get(DATASOURCE).set(datasource);
    
    // retrieve all properties
    ModelNode properties = new ModelNode();
    if (operation.hasDefined(PROPERTIES)) {
      properties = operation.get(PROPERTIES).asObject();
    }
    model.get(PROPERTIES).set(properties);
  }
  
  @Override
  protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model,
          ServiceVerificationHandler verificationHandler, List<ServiceController<?>> newControllers)
          throws OperationFailedException {
    
    String engineName = PathAddress.pathAddress(operation.get(ModelDescriptionConstants.ADDRESS)).getLastElement().getValue();

    ProcessEngineConfiguration processEngineConfiguration = transformConfiguration(context, engineName, model);
    ProcessEngineControllerService service = new ProcessEngineControllerService(processEngineConfiguration);
        
    ServiceName name = ProcessEngineControllerService.createServiceName(processEngineConfiguration.getProcessEngineName());    
    ContextNames.BindInfo datasourceBindInfo = ContextNames.bindInfoFor(processEngineConfiguration.getDatasourceJndiName());
    
    ServiceController<ProcessEngineControllerService> controller = context.getServiceTarget()           
            .addService(name, service)
            .addDependency(ServiceName.JBOSS.append("txn").append("TransactionManager"), TransactionManager.class, service.getTransactionManagerInjector())
            .addDependency(datasourceBindInfo.getBinderServiceName(), DataSourceReferenceFactoryService.class, service.getDatasourceBinderServiceInjector())
            .addDependency(ContainerPlatformService.getServiceName(), ContainerPlatformService.class, service.getContainerPlatformServiceInjector())
            .addDependency(ContainerJobExecutorService.getServiceName(), ContainerJobExecutorService.class, service.getContainerJobExecutorInjector())
            .addListener(verificationHandler)
            .setInitialMode(Mode.ACTIVE)
            .install();
    
    newControllers.add(controller);
  }

  private ProcessEngineConfiguration transformConfiguration(final OperationContext context, String engineName, final ModelNode model) {
    String datasourceJndiName = model.get(DATASOURCE).asString();   
    String historyLevel = model.get(HISTORY_LEVEL).asString();
    boolean isDefault = model.get(DEFAULT).asBoolean();
    
    Map<String,Object> properties = new HashMap<String, Object>();
    if (model.hasDefined(Element.PROPERTIES.getLocalName())) {
      ModelNode propertiesNode = model.get(Element.PROPERTIES.getLocalName());
      List<Property> propertyList = propertiesNode.asPropertyList();
      if (!propertyList.isEmpty()) {
        for (Property property : propertyList) {
          properties.put(property.getName(), property.getValue().asString());
        }
      }
    }
    
    // TODO: read these values from config
    int jobExecutor_maxJobsPerAcquisition =3;
    int jobExecutor_corePoolSize=1;
    int jobExecutor_maxPoolSize=3;
    int jobExecutor_queueSize=3;
    int jobExecutor_lockTimeInMillis= 5 * 60 * 1000;
    int jobExecutor_waitTimeInMillis = 5 * 1000;
    
    ProcessEngineConfiguration processEngineConfiguration = 
            new ProcessEngineConfigurationImpl(isDefault, engineName, datasourceJndiName, historyLevel, properties);
    
    return processEngineConfiguration;
  }
  
}
