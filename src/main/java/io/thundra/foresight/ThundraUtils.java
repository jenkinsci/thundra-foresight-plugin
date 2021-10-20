package io.thundra.foresight;

import hudson.FilePath;
import io.thundra.foresight.exceptions.AgentNotFoundException;
import io.thundra.foresight.exceptions.PluginNotFoundException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class ThundraUtils {
    public static final String GRADLE_PLUGIN_METADATA =
            "https://repo1.maven.org/maven2/io/thundra/plugin/thundra-gradle-test-plugin/maven-metadata.xml";
    public static final String THUNDRA_AGENT_METADATA =
            "https://repo.thundra.io/service/local/repositories/thundra-releases/content" +
                    "/io/thundra/agent/thundra-agent-bootstrap/maven-metadata.xml";

    public static final String THUNDRA_AGENT_BOOTSTRAP_JAR = "thundra-agent-bootstrap.jar";
    public static final String LATEST = "latest";
    public static final String THUNDRA_URL_ENV="THUNDRA_URL";

    public static String getLatestPluginVersion() throws XMLStreamException, IOException, PluginNotFoundException {
        BufferedInputStream in = new BufferedInputStream(new URL(GRADLE_PLUGIN_METADATA).openStream());
        XMLStreamReader reader1 = XMLInputFactory.newInstance().createXMLStreamReader(in);
        String latestPluginVersion = "";
        while (reader1.hasNext()) {
            if (reader1.next() == XMLStreamConstants.START_ELEMENT && reader1.getLocalName().equalsIgnoreCase(LATEST)) {
                latestPluginVersion = reader1.getElementText();
                break;
            }
        }
        if (StringUtils.isEmpty(latestPluginVersion)) {
            throw new PluginNotFoundException("Cannot extract plugin version from metadata");
        }
        return latestPluginVersion;
    }


    public static String getLatestThundraVersion() throws IOException, XMLStreamException, AgentNotFoundException {
        String latestAgentVersion = "";
        BufferedInputStream in = new BufferedInputStream(new URL(THUNDRA_AGENT_METADATA).openStream());
        XMLStreamReader reader1 = XMLInputFactory.newInstance().createXMLStreamReader(in);
        while (reader1.hasNext()) {
            if (reader1.next() == XMLStreamConstants.START_ELEMENT && reader1.getLocalName().equalsIgnoreCase(LATEST)) {
                latestAgentVersion = reader1.getElementText();
                break;
            }
        }
        if (StringUtils.isEmpty(latestAgentVersion)) {
            throw new AgentNotFoundException("Cannot extract agent version from metadata");
        }
        return latestAgentVersion;

    }

    public static FilePath downloadThundraAgent(FilePath workspace, String version) throws IOException, XMLStreamException, AgentNotFoundException, InterruptedException {
        BufferedInputStream agentStream = new BufferedInputStream(
                new URL(String.format("https://repo.thundra.io/service/local/repositories/thundra-releases" +
                                "/content/io/thundra/agent/thundra-agent-bootstrap/%s/thundra-agent-bootstrap-%s.jar",
                        version, version)).openStream());

        FilePath agent = workspace.child(THUNDRA_AGENT_BOOTSTRAP_JAR);
        IOUtils.copy(agentStream, agent.write());
        return agent;

    }
}
