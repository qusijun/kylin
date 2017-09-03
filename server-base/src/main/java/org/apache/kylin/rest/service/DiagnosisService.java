/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kylin.rest.service;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;
import org.apache.kylin.common.KylinConfig;
import org.apache.kylin.common.util.CliCommandExecutor;
import org.apache.kylin.common.util.Pair;
import org.apache.kylin.metadata.badquery.BadQueryHistory;
import org.apache.kylin.rest.exception.BadRequestException;
import org.apache.kylin.rest.msg.Message;
import org.apache.kylin.rest.msg.MsgPicker;
import org.apache.kylin.rest.util.AclEvaluate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.io.Files;

@Component("diagnosisService")
public class DiagnosisService extends BasicService {

    private static final Logger logger = LoggerFactory.getLogger(DiagnosisService.class);

    protected File getDumpDir() {
        return Files.createTempDir();
    }

    @Autowired
    private AclEvaluate aclEvaluate;

    @Autowired
    private JobService jobService;

    private String getDiagnosisPackageName(File destDir) {
        Message msg = MsgPicker.getMsg();

        File[] files = destDir.listFiles();
        if (files == null) {
            throw new BadRequestException(String.format(msg.getDIAG_PACKAGE_NOT_AVAILABLE(), destDir.getAbsolutePath()));
        }
        for (File subDir : files) {
            if (subDir.isDirectory()) {
                for (File file : subDir.listFiles()) {
                    if (file.getName().endsWith(".zip")) {
                        return file.getAbsolutePath();
                    }
                }
            }
        }
        throw new BadRequestException(String.format(msg.getDIAG_PACKAGE_NOT_FOUND(), destDir.getAbsolutePath()));
    }

    public BadQueryHistory getProjectBadQueryHistory(String project) throws IOException {
        aclEvaluate.checkProjectOperationPermission(project);
        return getBadQueryHistoryManager().getBadQueriesForProject(project);
    }

    public String dumpProjectDiagnosisInfo(String project) throws IOException {
        aclEvaluate.checkProjectOperationPermission(project);
        File exportPath = getDumpDir();
        String[] args = { project, exportPath.getAbsolutePath() };
        runDiagnosisCLI(args);
        return getDiagnosisPackageName(exportPath);
    }

    public String dumpJobDiagnosisInfo(String jobId) throws IOException {
        aclEvaluate.checkProjectOperationPermission(jobService.getJobInstance(jobId));
        File exportPath = getDumpDir();
        String[] args = {jobId, exportPath.getAbsolutePath()};
        runDiagnosisCLI(args);
        return getDiagnosisPackageName(exportPath);
    }

    private void runDiagnosisCLI(String[] args) throws IOException {
        Message msg = MsgPicker.getMsg();

        File cwd = new File("");
        logger.debug("Current path: " + cwd.getAbsolutePath());

        logger.debug("DiagnosisInfoCLI args: " + Arrays.toString(args));
        File script = new File(KylinConfig.getKylinHome() + File.separator + "bin", "diag.sh");
        if (!script.exists()) {
            throw new BadRequestException(String.format(msg.getDIAG_NOT_FOUND(), script.getAbsolutePath()));
        }

        String diagCmd = script.getAbsolutePath() + " " + StringUtils.join(args, " ");
        CliCommandExecutor executor = KylinConfig.getInstanceFromEnv().getCliCommandExecutor();
        Pair<Integer, String> cmdOutput = executor.execute(diagCmd);

        if (cmdOutput.getKey() != 0) {
            throw new BadRequestException(msg.getGENERATE_DIAG_PACKAGE_FAIL());
        }
    }

}