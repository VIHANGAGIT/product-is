/*
 * Copyright (c) 2025, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.identity.integration.test.rest.api.server.flow.execution.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import org.apache.commons.lang.StringUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.wso2.identity.integration.test.rest.api.server.common.RESTAPIServerTestBase;
import org.wso2.identity.integration.test.rest.api.server.flow.execution.v1.model.Component;
import org.wso2.identity.integration.test.rest.api.server.flow.execution.v1.model.FlowConfig;
import org.wso2.identity.integration.test.rest.api.server.flow.execution.v1.model.FlowExecutionResponse;
import org.wso2.identity.integration.test.rest.api.server.flow.execution.v1.model.Message;
import org.wso2.identity.integration.test.rest.api.server.flow.management.v1.FlowDefinitionUtils;
import org.wso2.identity.integration.test.rest.api.server.flow.management.v1.model.FlowRequest;
import org.wso2.identity.integration.test.restclients.FlowManagementClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class contains the test cases for Registration Execution API.
 */
public class FlowExecutionTestBase extends RESTAPIServerTestBase {

    protected static final String STATUS_INCOMPLETE = "INCOMPLETE";
    protected static final String STATUS_COMPLETE = "COMPLETE";
    protected static final String TYPE_VIEW = "VIEW";
    protected static final String TYPE_REDIRECTION = "REDIRECTION";
    protected static final String REGISTRATION_FLOW = "registration-flow.json";
    protected static final String PASSWORD_RECOVERY_FLOW = "password-recovery-flow.json";
    protected static final String API_DEFINITION_NAME = "flow-execution.yaml";
    protected static final String API_VERSION = "v1";
    protected static final String API_PACKAGE_NAME = "org.wso2.carbon.identity.api.server.flow.execution.v1";
    protected static String swaggerDefinition;

    static {
        try {
            swaggerDefinition = getAPISwaggerDefinition(API_PACKAGE_NAME, API_DEFINITION_NAME);
        } catch (Exception e) {
            throw new RuntimeException("Unable to read the swagger definition " + API_DEFINITION_NAME + " from "
                    + API_PACKAGE_NAME, e);
        }
    }

    @AfterClass(alwaysRun = true)
    public void testConclude() throws Exception {

        super.conclude();
    }

    @BeforeMethod(alwaysRun = true)
    public void testInit() {

        RestAssured.basePath = basePath;
    }

    @AfterMethod(alwaysRun = true)
    public void testFinish() {

        RestAssured.basePath = StringUtils.EMPTY;
    }

    protected void addRegistrationFlow(FlowManagementClient client) throws Exception {

        String registrationFlowRequestJson = readResource(REGISTRATION_FLOW);
        FlowRequest flowRequest = new ObjectMapper()
                .readValue(registrationFlowRequestJson, FlowRequest.class);
        client.putFlow(flowRequest);
    }

    protected FlowRequest getPasswordRecoveryFlowRequest() throws Exception {

        String passwordRecoveryFlowRequestJson = readResource(PASSWORD_RECOVERY_FLOW);
        return new ObjectMapper().readValue(passwordRecoveryFlowRequestJson, FlowRequest.class);
    }

    protected void addPasswordRecoveryFlow(FlowManagementClient client) throws Exception {

        client.putFlow(getPasswordRecoveryFlowRequest());
    }

    /**
     * Persist a password recovery flow variant derived from the base flow definition, with the enumeration
     * control flags set (or removed) on the {@code UserResolveExecutor} meta and, optionally, the identifier
     * input rebound to the given field.
     *
     * @param client                  Flow management client of the tenant under test.
     * @param notifyUserExistence     Value for {@code notifyUserExistence}, or {@code null} to remove the flag.
     * @param notifyUserAccountStatus Value for {@code notifyUserAccountStatus}, or {@code null} to remove the flag.
     * @param identifierField         Identifier field for the first input, or {@code null} to keep the default
     *                                (the username claim).
     */
    protected void addPasswordRecoveryFlowVariant(FlowManagementClient client, String notifyUserExistence,
                                                  String notifyUserAccountStatus, String identifierField)
            throws Exception {

        FlowRequest flowRequest = getPasswordRecoveryFlowRequest();
        FlowDefinitionUtils.setUserResolveNotifyFlags(flowRequest.getSteps().get(0).getData().getComponents(),
                notifyUserExistence, notifyUserAccountStatus);
        if (identifierField != null) {
            FlowDefinitionUtils.setInputIdentifier(flowRequest.getSteps().get(0).getData().getComponents(),
                    identifierField);
        }
        client.putFlow(flowRequest);
    }

    protected void enableFlow(String flowType, FlowManagementClient client) throws Exception {

        FlowConfig flowConfigDTO = new FlowConfig();
        flowConfigDTO.setIsEnabled(true);
        if (FlowTypes.REGISTRATION.equals(flowType)) {
            Map<String, String> flowCompletionConfigs = new HashMap<>();
            flowCompletionConfigs.put("isEmailVerificationEnabled", "true");
            flowCompletionConfigs.put("isAccountLockOnCreationEnabled", "true");
            flowConfigDTO.setFlowCompletionConfigs(flowCompletionConfigs);
        }
        flowConfigDTO.setFlowType(flowType);
        client.updateFlowConfig(flowConfigDTO);
    }

    protected void disableFlow(String flowType, FlowManagementClient client) throws Exception {

        FlowConfig flowConfigDTO = new FlowConfig();
        flowConfigDTO.setIsEnabled(false);
        flowConfigDTO.setFlowType(flowType);
        client.updateFlowConfig(flowConfigDTO);
    }

    /**
     * Whether the response carries any ERROR message (e.g. an enumeration or account-status notification).
     */
    protected static boolean hasErrorMessage(FlowExecutionResponse response) {

        List<Message> messages = response.getData().getMessages();
        return messages != null && messages.stream()
                .anyMatch(message -> message.getType() == Message.TypeEnum.ERROR);
    }

    /**
     * Recursively walk the rendered components to detect the OTP verification step, identified by an
     * INPUT component with the {@code OTP} variant.
     */
    protected static boolean containsOtpComponent(List<Component> components) {

        if (components == null) {
            return false;
        }
        for (Component component : components) {
            if ("OTP".equals(component.getVariant())) {
                return true;
            }
            if (containsOtpComponent(component.getComponents())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Flatten the rendered component tree into a sorted list of component ids, used to compare two responses
     * for indistinguishability.
     */
    protected static List<String> componentIds(List<Component> components) {

        List<String> ids = new ArrayList<>();
        collectComponentIds(components, ids);
        ids.sort(String::compareTo);
        return ids;
    }

    private static void collectComponentIds(List<Component> components, List<String> ids) {

        if (components == null) {
            return;
        }
        for (Component component : components) {
            ids.add(component.getId());
            collectComponentIds(component.getComponents(), ids);
        }
    }

    protected static class FlowTypes {

        public static final String REGISTRATION = "REGISTRATION";
        public static final String PASSWORD_RECOVERY = "PASSWORD_RECOVERY";
    }
}
