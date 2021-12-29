package io.thundra.plugin.maven.test.instrumentation.adder;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.BuildBase;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

public class AgentAdder {
    public AgentAdder() {
    }

    public boolean addAgentToBuildPlugins(BuildBase build, String pluginName, String agentPath, Boolean addIfMissing) {
        Plugin plugin = (Plugin)build.getPluginsAsMap().get(pluginName);
        if (plugin == null) {
            if (addIfMissing) {
                Plugin tmpPlugin = new Plugin();
                tmpPlugin.setGroupId(pluginName.split(":")[0]);
                tmpPlugin.setArtifactId(pluginName.split(":")[1]);
                build.addPlugin(tmpPlugin);
                build.getPluginsAsMap().put(pluginName, tmpPlugin);
                return this.addAgent((Plugin)build.getPluginsAsMap().get(pluginName), build.getPluginsAsMap(), pluginName, agentPath);
            } else {
                return false;
            }
        } else {
            return this.addAgent((Plugin)build.getPluginsAsMap().get(pluginName), build.getPluginsAsMap(), pluginName, agentPath);
        }
    }

    public boolean addAgentToPluginManagement(PluginManagement pluginManagement, String pluginName, String agentPath, Boolean addIfMissing) {
        Plugin plugin = (Plugin)pluginManagement.getPluginsAsMap().get(pluginName);
        if (plugin == null) {
            if (addIfMissing) {
                Plugin tmpPlugin = new Plugin();
                tmpPlugin.setGroupId(pluginName.split(":")[0]);
                tmpPlugin.setArtifactId(pluginName.split(":")[1]);
                pluginManagement.addPlugin(tmpPlugin);
                pluginManagement.getPluginsAsMap().put(pluginName, tmpPlugin);
                return this.addAgent((Plugin)pluginManagement.getPluginsAsMap().get(pluginName), pluginManagement.getPluginsAsMap(), pluginName, agentPath);
            } else {
                return false;
            }
        } else {
            return this.addAgent((Plugin)pluginManagement.getPluginsAsMap().get(pluginName), pluginManagement.getPluginsAsMap(), pluginName, agentPath);
        }
    }

    public boolean addAgent(Plugin plugin, Map<String, Plugin> pluginsAsMap, String pluginName, String agentPath) {
        try {
            Xpp3Dom configuration = (Xpp3Dom)((Plugin)pluginsAsMap.get(pluginName)).getConfiguration();
            if (configuration == null) {
                configuration = Xpp3DomBuilder.build(new StringReader("<configuration/>"));
                plugin.setConfiguration(configuration);
            }

            Xpp3Dom argLine = configuration.getChild("argLine");
            if (argLine == null) {
                argLine = Xpp3DomBuilder.build(new StringReader("<argLine/>"));
                configuration.addChild(argLine);
            }

            String origArgLine = argLine.getValue();
            argLine.setValue((StringUtils.isNotEmpty(origArgLine) ? origArgLine + " " : "") + "-javaagent:" + agentPath);
            argLine.setValue(thundraAdder(origArgLine, agentPath));
            return true;
        } catch (IOException | XmlPullParserException var8) {
            throw new RuntimeException(var8);
        }
    }

    private String thundraAdder(String argLine, String newArgLine){
        String javaAgent = "";
        String apiKey = "";
        String pId = "";
        for (String s : newArgLine.split(" ")) {
            if (!s.contains("=")) {//java agent path
                javaAgent = "-javaagent:"+s;
            }
            else if (s.startsWith("-Dthundra.apiKey")){
                apiKey = s;
            } else if(s.startsWith("-Dthundra.agent.test.project.id")){
                pId = s;
            }
        }
        StringBuilder result = new StringBuilder();
        if (argLine != null) {
            for (String s : argLine.split(" ")) {
                s = s.trim();
                if (s.startsWith("-javaagent") && s.endsWith(javaAgent)) {
                    result.append(" ").append(javaAgent);
                    javaAgent = "";
                }
                else if (s.startsWith("-Dthundra.apiKey")){
                    result.append(" ").append(apiKey);
                    apiKey = "";
                }
                else if (s.startsWith("-Dthundra.agent.test.project.id")){
                    result.append(" ").append(pId);
                    pId = "";
                }else {
                    result.append(" ").append(s);
                }
            }
        }
        result.append(" ").append(javaAgent).append(" ").append(apiKey).append(" ").append(pId).append(" ");
        return result.toString().trim();
    }
}
