package io.thundra.foresight;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import io.thundra.foresight.exceptions.AgentNotFoundException;
import io.thundra.foresight.exceptions.PluginNotFoundException;
import io.thundra.plugin.maven.test.instrumentation.checker.FailsafeChecker;
import io.thundra.plugin.maven.test.instrumentation.checker.SurefireChecker;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;


import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class MvnForesightBuilder extends Builder implements SimpleBuildStep {

    private static final Logger logger = LogManager.getLogger(MvnForesightBuilder.class);
    private final String projectId;
    private final String apiKey;
    private String thundraAgentVersion;

    @DataBoundConstructor
    public MvnForesightBuilder(String projectId, String apiKey) {
        this.apiKey = apiKey;
        this.projectId = projectId;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getThundraAgentVersion() {
        return thundraAgentVersion;
    }

    @DataBoundSetter
    public void setThundraAgentVersion(String thundraVersion) {
        this.thundraAgentVersion = thundraVersion;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        try {
            String version = StringUtils.isNotEmpty(thundraAgentVersion)? thundraAgentVersion : ThundraUtils.getLatestThundraVersion();
            FilePath filePath = ThundraUtils.downloadThundraAgent(workspace, version);
            String agentConfigurations = getAgentConfigurations(filePath.toString());
            FilePath[] pomFiles = workspace.list("**/pom.xml");
            listener.getLogger().println("<Execute> Executing maven instrumentation ...");
            listener.getLogger().printf("<Execute> Found %s pom.xml files%n", pomFiles.length);
            MavenXpp3Reader mavenReader = new MavenXpp3Reader();

            SurefireChecker surefireChecker = new SurefireChecker();
            FailsafeChecker failsafeChecker = new FailsafeChecker();

            // A toggle to check once the loop ends
            AtomicBoolean surefireInstrumented = new AtomicBoolean();
            AtomicBoolean failsafeInstrumented = new AtomicBoolean();

            listener.getLogger().println("<Execute> Processing the pom files");
            for (FilePath pomFile : pomFiles) {
                listener.getLogger().printf("Processing %s%n", pomFile);

                // Start checking and processing the pom files
                listener.getLogger().printf("Checking %s for Surefire plugin%n", pomFile);
                // Check for Surefire
                File localPom = File.createTempFile("jenkins", "localPom.xml");
                localPom.deleteOnExit();
                try (FileOutputStream out = new FileOutputStream(localPom)) {
                    IOUtils.copy(pomFile.read(), out);
                }

                surefireChecker.checkProfiles(logger, mavenReader, agentConfigurations, localPom.getAbsolutePath(), true);
                surefireChecker.checkPom(logger, mavenReader, agentConfigurations, localPom.getAbsolutePath(), true);
                surefireInstrumented.set(surefireChecker.instrumented.get() || surefireInstrumented.get());

                listener.getLogger().printf("Checking %s for Failsafe plugin%n", pomFile);
                // Check for Failsafe
                failsafeChecker.checkProfiles(logger, mavenReader, agentConfigurations, localPom.getAbsolutePath(), true);
                failsafeChecker.checkPom(logger, mavenReader, agentConfigurations, localPom.getAbsolutePath(), true);
                failsafeInstrumented.set(failsafeChecker.instrumented.get() || failsafeInstrumented.get());
                try (FileInputStream in = new FileInputStream(localPom)) {
                    IOUtils.copy(in, pomFile.write());
                }
                localPom.delete();
            }

            listener.getLogger().println("Instrumentation is complete");
        } catch (XMLStreamException | AgentNotFoundException e) {
            listener.getLogger().println("Thundra Foresight maven initialization failed: " + e);
            throw new IOException(e.getMessage());
        }
    }

    public String getAgentConfigurations(String agentPath) {
        //FIXME why test run id is random uuid?
        String thundraUrl = System.getenv(ThundraUtils.THUNDRA_URL_ENV);
        String restBaseUrlParam = StringUtils.isNotEmpty(thundraUrl) ? " -Dthundra.agent.report.rest.baseurl="
                + thundraUrl : "";
        agentPath = agentPath + restBaseUrlParam;
        agentPath += (String.format(" -Dthundra.apiKey=%s -Dthundra.agent.test.project.id=%s" +
                " -Dthundra.agent.test.run.id=%s", apiKey, projectId, UUID.randomUUID().toString()));

        return agentPath;
    }

    @Symbol("mavenForesight")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.MvnForesightBuilder_DescriptorImpl_DisplayName();
        }

    }
}
