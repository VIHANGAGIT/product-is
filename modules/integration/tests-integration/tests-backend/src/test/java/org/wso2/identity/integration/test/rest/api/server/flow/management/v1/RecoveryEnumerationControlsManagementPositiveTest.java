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

package org.wso2.identity.integration.test.rest.api.server.flow.management.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.identity.integration.test.rest.api.server.flow.execution.v1.model.FlowConfig;
import org.wso2.identity.integration.test.rest.api.server.flow.management.v1.model.FlowRequest;
import org.wso2.identity.integration.test.rest.api.server.flow.management.v1.model.FlowResponse;
import org.wso2.identity.integration.test.restclients.FlowManagementClient;

import java.io.IOException;
import java.util.Map;

import static org.wso2.identity.integration.test.rest.api.server.flow.management.v1.FlowDefinitionUtils.NOTIFY_USER_ACCOUNT_STATUS;
import static org.wso2.identity.integration.test.rest.api.server.flow.management.v1.FlowDefinitionUtils.NOTIFY_USER_EXISTENCE;
import static org.wso2.identity.integration.test.rest.api.server.flow.management.v1.FlowDefinitionUtils.USER_RESOLVE_EXECUTOR;
import static org.wso2.identity.integration.test.rest.api.server.flow.management.v1.FlowManagementTestBase.FlowTypes.PASSWORD_RECOVERY;

/**
 * Positive tests for managing the password recovery flow's user enumeration and account status controls:
 * both valid flag configurations (enabled and disabled) must round-trip on the {@code UserResolveExecutor} meta.
 */
public class RecoveryEnumerationControlsManagementPositiveTest extends FlowManagementTestBase {

    private static final String CONTROLS_ENABLED = "true";
    private static final String CONTROLS_DISABLED = "false";

    private FlowManagementClient flowManagementClient;
    private String passwordRecoveryFlowRequestJson;

    @DataProvider(name = "restAPIUserConfigProvider")
    public static Object[][] restAPIUserConfigProvider() {

        return new Object[][]{
                {TestUserMode.SUPER_TENANT_ADMIN},
                {TestUserMode.TENANT_ADMIN}
        };
    }

    @Factory(dataProvider = "restAPIUserConfigProvider")
    public RecoveryEnumerationControlsManagementPositiveTest(TestUserMode userMode) throws Exception {

        super.init(userMode);
        this.context = isServer;
        this.authenticatingUserName = context.getContextTenant().getTenantAdmin().getUserName();
        this.authenticatingCredential = context.getContextTenant().getTenantAdmin().getPassword();
        this.tenant = context.getContextTenant().getDomain();
    }

    @BeforeClass(alwaysRun = true)
    public void init() throws Exception {

        super.testInit(API_VERSION, swaggerDefinition, tenantInfo.getDomain());
        flowManagementClient = new FlowManagementClient(serverURL, tenantInfo);
        passwordRecoveryFlowRequestJson = readResource(PASSWORD_RECOVERY_FLOW);
        updatePasswordRecoveryFlowStatus(true);
    }

    @AfterClass(alwaysRun = true)
    public void testCleanup() throws Exception {

        updatePasswordRecoveryFlowStatus(false);
        flowManagementClient.closeHttpClient();
        super.testConclude();
    }

    @Test(description = "Verify the password recovery flow config is enabled.")
    public void testPasswordRecoveryFlowConfigEnabled() throws Exception {

        FlowConfig flowConfig = flowManagementClient.getFlowConfig(PASSWORD_RECOVERY);
        Assert.assertEquals(flowConfig.getFlowType(), PASSWORD_RECOVERY);
        Assert.assertTrue(flowConfig.getIsEnabled());
    }

    @Test(description = "Update the password recovery flow with the enumeration controls enabled on the user " +
            "resolve executor.", dependsOnMethods = "testPasswordRecoveryFlowConfigEnabled")
    public void testUpdateFlowWithControlsEnabled() throws Exception {

        updateFlowWithNotifyFlags(CONTROLS_ENABLED);
    }

    @Test(description = "Get the password recovery flow and verify the notify flags round-trip as enabled on the " +
            "executor meta.", dependsOnMethods = "testUpdateFlowWithControlsEnabled")
    public void testGetFlowReflectsControlsEnabled() throws Exception {

        assertNotifyFlagsRoundTrip(CONTROLS_ENABLED);
    }

    @Test(description = "Update the password recovery flow with the enumeration controls disabled on the user " +
            "resolve executor.", dependsOnMethods = "testGetFlowReflectsControlsEnabled")
    public void testUpdateFlowWithControlsDisabled() throws Exception {

        updateFlowWithNotifyFlags(CONTROLS_DISABLED);
    }

    @Test(description = "Get the password recovery flow and verify the notify flags round-trip as disabled on the " +
            "executor meta.", dependsOnMethods = "testUpdateFlowWithControlsDisabled")
    public void testGetFlowReflectsControlsDisabled() throws Exception {

        assertNotifyFlagsRoundTrip(CONTROLS_DISABLED);
    }

    private void updateFlowWithNotifyFlags(String value) throws Exception {

        FlowRequest passwordRecoveryFlowRequest = getFlowRequest();
        FlowDefinitionUtils.setUserResolveNotifyFlags(
                passwordRecoveryFlowRequest.getSteps().get(0).getData().getComponents(), value, value);
        flowManagementClient.putFlow(passwordRecoveryFlowRequest);
    }

    private void assertNotifyFlagsRoundTrip(String expectedValue) throws Exception {

        FlowResponse passwordRecoveryFlowResponse = flowManagementClient.getFlow(PASSWORD_RECOVERY);
        Map<String, Object> meta = FlowDefinitionUtils.findExecutorMeta(
                passwordRecoveryFlowResponse.getSteps().get(0).getData().getComponents(), USER_RESOLVE_EXECUTOR);
        Assert.assertNotNull(meta, "UserResolveExecutor meta not found in the retrieved flow.");
        Assert.assertEquals(String.valueOf(meta.get(NOTIFY_USER_EXISTENCE)), expectedValue);
        Assert.assertEquals(String.valueOf(meta.get(NOTIFY_USER_ACCOUNT_STATUS)), expectedValue);
    }

    private FlowRequest getFlowRequest() throws IOException {

        return new ObjectMapper().readValue(passwordRecoveryFlowRequestJson, FlowRequest.class);
    }

    private void updatePasswordRecoveryFlowStatus(boolean enable) throws Exception {

        FlowConfig flowConfig = new FlowConfig();
        flowConfig.setFlowType(PASSWORD_RECOVERY);
        flowConfig.setIsEnabled(enable);
        flowManagementClient.updateFlowConfig(flowConfig);
    }
}
