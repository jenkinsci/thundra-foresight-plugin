package io.thundra.foresight;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import io.thundra.foresight.exceptions.AgentNotFoundException;
import io.thundra.plugin.maven.test.instrumentation.checker.FailsafeChecker;
import io.thundra.plugin.maven.test.instrumentation.checker.SurefireChecker;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class MvnForesightBuilder extends Builder implements SimpleBuildStep {

    private static final Logger logger = LogManager.getLogger(MvnForesightBuilder.class);
    private final String projectId;
    private final String credentialId;
    private String thundraAgentVersion;

    @DataBoundConstructor
    public MvnForesightBuilder(String projectId, String credentialId) {
        this.credentialId = credentialId;
        this.projectId = projectId;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getCredentialId() {
        return credentialId;
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
            StringCredentials apiKeyCredentials = CredentialsProvider.findCredentialById(credentialId, StringCredentials.class, run);
            if (apiKeyCredentials == null) {
                throw new IOException("Wrong credentials provided");
            }
            String version = StringUtils.isNotEmpty(thundraAgentVersion)? thundraAgentVersion : ThundraUtils.getLatestThundraVersion();
            FilePath filePath = ThundraUtils.downloadThundraAgent(workspace, version);
            String agentConfigurations = getAgentConfigurations(filePath.toString(), apiKeyCredentials.getSecret().getPlainText());
            FilePath[] pomFiles = workspace.list("**/pom.xml");
            listener.getLogger().println("Executing maven instrumentation ...");
            listener.getLogger().printf("Found %s pom.xml files%n", pomFiles.length);
            MavenXpp3Reader mavenReader = new MavenXpp3Reader();

            SurefireChecker surefireChecker = new SurefireChecker();
            FailsafeChecker failsafeChecker = new FailsafeChecker();

            // A toggle to check once the loop ends
            AtomicBoolean surefireInstrumented = new AtomicBoolean();
            AtomicBoolean failsafeInstrumented = new AtomicBoolean();

            listener.getLogger().println("Processing the pom files");
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
                boolean ignored = localPom.delete(); // For Jenkins Checkstyle workaround 'RV_RETURN_VALUE_IGNORED_BAD_PRACTICE' is preventing successful build
            }

            listener.getLogger().println("Instrumentation is complete");
        } catch (XMLStreamException | AgentNotFoundException e) {
            listener.getLogger().println("Thundra Foresight maven initialization failed: " + e);
            throw new IOException(e.getMessage());
        }
    }

    public String getAgentConfigurations(String agentPath, String apiKey) {
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

        public ListBoxModel doFillCredentialIdItems(
                @AncestorInPath Item item,
                @QueryParameter String credentialsId
        ) {
            return ThundraUtils.fillCredentials(item, credentialsId);
        }

    }
}
