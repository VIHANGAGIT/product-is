/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.com).
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

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.identity.integration.test.rest.api.server.flow.execution.v1.model.FlowExecutionRequest;
import org.wso2.identity.integration.test.rest.api.server.flow.execution.v1.model.FlowExecutionResponse;
import org.wso2.identity.integration.test.rest.api.server.flow.execution.v1.model.Message;
import org.wso2.identity.integration.test.rest.api.server.flow.management.v1.FlowDefinitionUtils;
import org.wso2.identity.integration.test.rest.api.server.flow.management.v1.model.Error;
import org.wso2.identity.integration.test.rest.api.server.flow.management.v1.model.FlowRequest;
import org.wso2.identity.integration.test.restclients.FlowExecutionClient;
import org.wso2.identity.integration.test.restclients.FlowManagementClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.wso2.identity.integration.test.rest.api.server.flow.execution.v1.FlowExecutionTestBase.FlowTypes.PASSWORD_RECOVERY;

/**
 * Negative tests for the password recovery flow execution with the user enumeration and account status controls:
 * flow-level guards (not enabled, invalid flow id, missing inputs) and the {@code UserResolveExecutor} input
 * contract — a blank identifier must re-prompt for input identically whether the controls are enabled or not,
 * so the re-prompt itself carries no enumeration signal.
 */
public class RecoveryEnumerationControlsExecutionNegativeTest extends FlowExecutionTestBase {

    private static final String USERNAME_CLAIM = "http://wso2.org/claims/username";
    private static final String TEST_IDENTIFIER = "PwdRecNegTestUser";
    private static final String BLANK_IDENTIFIER = "";
    private static final String CONTROLS_DISABLED = "false";

    private static final String FLOW_NOT_ENABLED_ON_INITIATE = "FE-60101";
    private static final String FLOW_NOT_ENABLED_OR_INVALID_FLOW_ID = "FE-60001";
    private static final String INVALID_INPUTS = "FE-60008";
    // i18n key prefixes an enumeration or account-status notification would carry; no response in this class may
    // ever contain them.
    private static final String USER_NOT_FOUND_I18N_KEY_PREFIX = "{{user.not.found";
    private static final String ACCOUNT_STATUS_I18N_KEY_PREFIX = "{{account.";

    private FlowExecutionClient flowExecutionClient;
    private FlowManagementClient flowManagementClient;
    private String flowId;
    private String userResolveActionId;
    private List<String> blankIdentifierRePromptComponentIds;

    @DataProvider(name = "restAPIUserConfigProvider")
    public static Object[][] restAPIUserConfigProvider() {

        return new Object[][]{
                {TestUserMode.SUPER_TENANT_ADMIN},
                {TestUserMode.TENANT_ADMIN}
        };
    }

    @Factory(dataProvider = "restAPIUserConfigProvider")
    public RecoveryEnumerationControlsExecutionNegativeTest(TestUserMode userMode) throws Exception {

        super.init(userMode);
        this.context = isServer;
        this.authenticatingUserName = context.getContextTenant().getTenantAdmin().getUserName();
        this.authenticatingCredential = context.getContextTenant().getTenantAdmin().getPassword();
        this.tenant = context.getContextTenant().getDomain();
    }

    @BeforeClass(alwaysRun = true)
    public void setupClass() throws Exception {

        super.testInit(API_VERSION, swaggerDefinition, tenantInfo.getDomain());
        flowExecutionClient = new FlowExecutionClient(serverURL, tenantInfo);
        flowManagementClient = new FlowManagementClient(serverURL, tenantInfo);

        FlowRequest flowRequest = getPasswordRecoveryFlowRequest();
        userResolveActionId = FlowDefinitionUtils.findActionIdByExecutor(
                flowRequest.getSteps().get(0).getData().getComponents(), FlowDefinitionUtils.USER_RESOLVE_EXECUTOR);
        Assert.assertNotNull(userResolveActionId,
                "UserResolveExecutor action not found in " + PASSWORD_RECOVERY_FLOW);
        flowManagementClient.putFlow(flowRequest);
        // Disable explicitly instead of relying on the previous test class having cleaned up.
        disableFlow(PASSWORD_RECOVERY, flowManagementClient);
    }

    @AfterClass(alwaysRun = true)
    public void tearDownClass() throws Exception {

        // Restore the base flow definition (the last test leaves the controls-disabled variant persisted).
        addPasswordRecoveryFlow(flowManagementClient);
        disableFlow(PASSWORD_RECOVERY, flowManagementClient);
        flowExecutionClient.closeHttpClient();
        flowManagementClient.closeHttpClient();
    }

    @Test(description = "Initiating the password recovery flow before it is enabled returns FE-60101.")
    public void testInitiateFlowWithoutEnable() throws Exception {

        Object responseObj = flowExecutionClient.initiateFlowExecution(PASSWORD_RECOVERY);
        Assert.assertTrue(responseObj instanceof Error);
        Assert.assertEquals(((Error) responseObj).getCode(), FLOW_NOT_ENABLED_ON_INITIATE);
    }

    @Test(description = "Enable the flow and initiate it to obtain a valid flow id.",
            dependsOnMethods = "testInitiateFlowWithoutEnable")
    public void testInitiateFlow() throws Exception {

        enableFlow(PASSWORD_RECOVERY, flowManagementClient);
        Object responseObj = flowExecutionClient.initiateFlowExecution(PASSWORD_RECOVERY);
        Assert.assertTrue(responseObj instanceof FlowExecutionResponse);
        FlowExecutionResponse response = (FlowExecutionResponse) responseObj;
        Assert.assertNotNull(response.getFlowId());
        Assert.assertEquals(response.getFlowStatus(), STATUS_INCOMPLETE);
        Assert.assertEquals(response.getType().toString(), TYPE_VIEW);
        flowId = response.getFlowId();
    }

    @Test(description = "Executing with an invalid flow id returns FE-60001.",
            dependsOnMethods = "testInitiateFlow")
    public void testExecuteFlowWithInvalidFlowId() throws Exception {

        Object responseObj = flowExecutionClient.executeFlow(
                getRequest("INVALID_FLOW_ID", inputs(TEST_IDENTIFIER)));
        Assert.assertTrue(responseObj instanceof Error);
        Assert.assertEquals(((Error) responseObj).getCode(), FLOW_NOT_ENABLED_OR_INVALID_FLOW_ID);
    }

    @Test(description = "Executing with empty inputs returns FE-60008.",
            dependsOnMethods = "testExecuteFlowWithInvalidFlowId")
    public void testExecuteFlowWithEmptyInputs() throws Exception {

        Object responseObj = flowExecutionClient.executeFlow(getRequest(flowId, new HashMap<>()));
        Assert.assertTrue(responseObj instanceof Error);
        Assert.assertEquals(((Error) responseObj).getCode(), INVALID_INPUTS);
    }

    @Test(description = "Submitting a blank identifier with the enumeration controls enabled re-prompts the same " +
            "step without advancing and without any enumeration or account-status message.",
            dependsOnMethods = "testExecuteFlowWithEmptyInputs")
    public void testBlankIdentifierRePrompts() throws Exception {

        FlowExecutionResponse response = submitBlankIdentifier();
        assertRePromptWithoutEnumerationSignal(response);
        blankIdentifierRePromptComponentIds = componentIds(response.getData().getComponents());
    }

    @Test(description = "Submitting a blank identifier with the enumeration controls disabled yields exactly the " +
            "same re-prompt — the blank-input handling itself must not depend on the controls.",
            dependsOnMethods = "testBlankIdentifierRePrompts")
    public void testBlankIdentifierRePromptsWithControlsDisabled() throws Exception {

        addPasswordRecoveryFlowVariant(flowManagementClient, CONTROLS_DISABLED, CONTROLS_DISABLED, null);

        FlowExecutionResponse response = submitBlankIdentifier();
        assertRePromptWithoutEnumerationSignal(response);
        Assert.assertEquals(componentIds(response.getData().getComponents()), blankIdentifierRePromptComponentIds,
                "The blank-identifier re-prompt must be identical with the controls enabled and disabled.");
    }

    /**
     * Initiate a fresh flow and submit a blank identifier at the user resolution step.
     */
    private FlowExecutionResponse submitBlankIdentifier() throws Exception {

        Object initiationObj = flowExecutionClient.initiateFlowExecution(PASSWORD_RECOVERY);
        Assert.assertTrue(initiationObj instanceof FlowExecutionResponse);
        String freshFlowId = ((FlowExecutionResponse) initiationObj).getFlowId();

        Object responseObj = flowExecutionClient.executeFlow(getRequest(freshFlowId, inputs(BLANK_IDENTIFIER)));
        Assert.assertTrue(responseObj instanceof FlowExecutionResponse,
                "Expected a re-prompt view for a blank identifier, got: " + responseObj);
        return (FlowExecutionResponse) responseObj;
    }

    /**
     * Assert that the response re-prompts the user resolution step (no advancement to the OTP step) and carries
     * no user-not-found or account-status message.
     */
    private void assertRePromptWithoutEnumerationSignal(FlowExecutionResponse response) {

        Assert.assertEquals(response.getFlowStatus(), STATUS_INCOMPLETE);
        Assert.assertEquals(response.getType().toString(), TYPE_VIEW);
        Assert.assertFalse(containsOtpComponent(response.getData().getComponents()),
                "A blank identifier must re-prompt for input, not advance past user resolution.");

        List<Message> messages = response.getData().getMessages();
        boolean hasEnumerationSignal = messages != null && messages.stream().anyMatch(message ->
                message.getI18nKey() != null
                        && (message.getI18nKey().startsWith(USER_NOT_FOUND_I18N_KEY_PREFIX)
                        || message.getI18nKey().startsWith(ACCOUNT_STATUS_I18N_KEY_PREFIX)));
        Assert.assertFalse(hasEnumerationSignal,
                "A blank identifier must not surface enumeration or account-status messages: " + messages);
    }

    private Map<String, String> inputs(String identifier) {

        Map<String, String> inputs = new HashMap<>();
        inputs.put(USERNAME_CLAIM, identifier);
        return inputs;
    }

    private FlowExecutionRequest getRequest(String flowId, Map<String, String> inputs) {

        FlowExecutionRequest request = new FlowExecutionRequest();
        request.setFlowType(PASSWORD_RECOVERY);
        request.setFlowId(flowId);
        request.setActionId(userResolveActionId);
        request.setInputs(inputs);
        return request;
    }
}
