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
 * Negative tests for managing the password recovery flow's user enumeration and account status controls:
 * the flags are free-form executor metadata, so the API accepts non-boolean and absent values. These tests pin
 * that lenient contract — invalid or missing values must round-trip untouched, because the executor is expected
 * to degrade them to the safe (disabled) state at runtime, which is asserted by
 * {@code RecoveryEnumerationControlsExecutionPositiveTest}.
 */
public class RecoveryEnumerationControlsManagementNegativeTest extends FlowManagementTestBase {

    private static final String NON_BOOLEAN_FLAG_VALUE = "notABoolean";

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
    public RecoveryEnumerationControlsManagementNegativeTest(TestUserMode userMode) throws Exception {

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

        // Restore a well-formed flow definition so later classes never inherit the malformed variants.
        flowManagementClient.putFlow(getFlowRequest());
        updatePasswordRecoveryFlowStatus(false);
        flowManagementClient.closeHttpClient();
        super.testConclude();
    }

    @Test(description = "Update the password recovery flow with non-boolean enumeration control values and verify " +
            "the API accepts the meta without coercing or rejecting it.")
    public void testUpdateFlowWithNonBooleanNotifyFlags() throws Exception {

        FlowRequest passwordRecoveryFlowRequest = getFlowRequest();
        FlowDefinitionUtils.setUserResolveNotifyFlags(
                passwordRecoveryFlowRequest.getSteps().get(0).getData().getComponents(),
                NON_BOOLEAN_FLAG_VALUE, NON_BOOLEAN_FLAG_VALUE);
        flowManagementClient.putFlow(passwordRecoveryFlowRequest);

        Map<String, Object> meta = getUserResolveExecutorMetaFromServer();
        Assert.assertNotNull(meta, "UserResolveExecutor meta not found in the retrieved flow.");
        Assert.assertEquals(String.valueOf(meta.get(NOTIFY_USER_EXISTENCE)), NON_BOOLEAN_FLAG_VALUE);
        Assert.assertEquals(String.valueOf(meta.get(NOTIFY_USER_ACCOUNT_STATUS)), NON_BOOLEAN_FLAG_VALUE);
    }

    @Test(description = "Update the password recovery flow with the enumeration control flags absent from the " +
            "executor meta and verify they stay absent, pinning the out-of-the-box (secure) posture.",
            dependsOnMethods = "testUpdateFlowWithNonBooleanNotifyFlags")
    public void testUpdateFlowWithoutNotifyFlags() throws Exception {

        FlowRequest passwordRecoveryFlowRequest = getFlowRequest();
        FlowDefinitionUtils.setUserResolveNotifyFlags(
                passwordRecoveryFlowRequest.getSteps().get(0).getData().getComponents(), null, null);
        flowManagementClient.putFlow(passwordRecoveryFlowRequest);

        Map<String, Object> meta = getUserResolveExecutorMetaFromServer();
        if (meta != null) {
            Assert.assertFalse(meta.containsKey(NOTIFY_USER_EXISTENCE),
                    "notifyUserExistence must stay absent when not configured, but was: "
                            + meta.get(NOTIFY_USER_EXISTENCE));
            Assert.assertFalse(meta.containsKey(NOTIFY_USER_ACCOUNT_STATUS),
                    "notifyUserAccountStatus must stay absent when not configured, but was: "
                            + meta.get(NOTIFY_USER_ACCOUNT_STATUS));
        }
    }

    private Map<String, Object> getUserResolveExecutorMetaFromServer() throws Exception {

        FlowResponse passwordRecoveryFlowResponse = flowManagementClient.getFlow(PASSWORD_RECOVERY);
        return FlowDefinitionUtils.findExecutorMeta(
                passwordRecoveryFlowResponse.getSteps().get(0).getData().getComponents(), USER_RESOLVE_EXECUTOR);
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
