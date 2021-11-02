package io.thundra.foresight;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import io.thundra.foresight.exceptions.AgentNotFoundException;
import io.thundra.foresight.exceptions.PluginNotFoundException;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class GradleForesightBuilder extends Builder implements SimpleBuildStep {

    public static final String THUNDRA_AGENT_PATH = "THUNDRA_AGENT_PATH";
    public static final String THUNDRA_GRADLE_PLUGIN_VERSION = "THUNDRA_GRADLE_PLUGIN_VERSION";
    public static final String THUNDRAINIT_FTLH = "thundrainit.ftlh";
    public static final String THUNDRA_AGENT_TEST_PROJECT_ID = "THUNDRA_AGENT_TEST_PROJECT_ID";
    public static final String THUNDRA_APIKEY = "THUNDRA_APIKEY";
    private final String projectId;
    private final String credentialId;
    private String thundraGradlePluginVersion;
    private String thundraAgentVersion;

    @DataBoundConstructor
    public GradleForesightBuilder(String projectId, String credentialId) {
        this.credentialId = credentialId;
        this.projectId = projectId;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public String getThundraGradlePluginVersion() {
        return thundraGradlePluginVersion;
    }

    @DataBoundSetter
    public void setThundraGradlePluginVersion(String thundraGradlePluginVersion) {
        this.thundraGradlePluginVersion = thundraGradlePluginVersion;
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
            listener.getLogger().println("GradleForesight");
            if (StringUtils.isEmpty(credentialId) || StringUtils.isEmpty(projectId)) {
                throw new IOException("Required parameters are missing");
            }
            StringCredentials apiKeyCredentials = CredentialsProvider.findCredentialById(credentialId, StringCredentials.class, run);
            if (apiKeyCredentials == null) {
                throw new IOException("Wrong credentials provided");
            }
            String version = null;

            version = StringUtils.isNotEmpty(thundraAgentVersion) ? thundraAgentVersion : ThundraUtils.getLatestThundraVersion();

            FilePath filePath = ThundraUtils.downloadThundraAgent(workspace, version);
            String pluginVersion = StringUtils.isNotEmpty(thundraGradlePluginVersion) ? thundraGradlePluginVersion : ThundraUtils.getLatestPluginVersion();
            listener.getLogger().println("Latest Plugin Version : " + pluginVersion);
            final Configuration cfg = getFreemarkerConfiguration();
            final Map<String, String> root = new HashMap<>();
            root.put(THUNDRA_GRADLE_PLUGIN_VERSION, pluginVersion);
            root.put(THUNDRA_AGENT_PATH, filePath.toString());
            root.put(THUNDRA_APIKEY, apiKeyCredentials.getSecret().getPlainText());
            root.put(THUNDRA_AGENT_TEST_PROJECT_ID, projectId);
            String initScriptFile = "thundra.gradle";
            final Template template = cfg.getTemplate(THUNDRAINIT_FTLH);
            FilePath agent = workspace.child(initScriptFile);
            final Writer fileOut = new OutputStreamWriter(agent.write(), StandardCharsets.UTF_8);
            template.process(root, fileOut);
            File settingsGradle = File.createTempFile("jenkins", "settings.gradle");
            settingsGradle.deleteOnExit();
            FilePath settings = workspace.child("settings.gradle");
            if (!settings.exists()) {
                settings.touch(System.currentTimeMillis());
            }
            try (FileOutputStream out = new FileOutputStream(settingsGradle)) {
                String includePart = "include('" + initScriptFile + "')\n";
                out.write(includePart.getBytes(StandardCharsets.UTF_8));
                settings.copyTo(out);
            }
            try (FileInputStream in = new FileInputStream(settingsGradle)) {
                settings.copyFrom(in);
            }
        } catch (XMLStreamException | AgentNotFoundException | PluginNotFoundException | TemplateException e) {
            throw new IOException(e.getMessage());
        }
    }

    private Configuration getFreemarkerConfiguration() {
        final Configuration cfg = new Configuration(Configuration.VERSION_2_3_31);
        cfg.setDefaultEncoding("UTF-8");
        cfg.setClassForTemplateLoading(this.getClass(), "/META-INF/template");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        cfg.setFallbackOnNullLoopVariable(false);
        return cfg;
    }

    @Symbol("gradleForesight")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.GradleForesightBuilder_DescriptorImpl_DisplayName();
        }

        public ListBoxModel doFillCredentialIdItems(
                @AncestorInPath Item item,
                @QueryParameter String credentialsId
        ) {
            return ThundraUtils.fillCredentials(item, credentialsId);
        }

    }
}
