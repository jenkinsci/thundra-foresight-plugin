//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.thundra.plugin.maven.test.instrumentation.checker;

import io.thundra.plugin.maven.test.instrumentation.adder.AgentAdder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.Logger;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.Profile;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

public class SurefireChecker implements Checker {
    private final AgentAdder adder = new AgentAdder();
    public AtomicBoolean instrumented = new AtomicBoolean();

    public SurefireChecker() {
    }

    public void checkProfiles(Logger logger, MavenXpp3Reader mavenReader, String agentPath, String pomFile, Boolean addIfMissing) {
        try {
            Model model = mavenReader.read(new InputStreamReader(new FileInputStream(pomFile), StandardCharsets.UTF_8));
            if (!model.getProfiles().isEmpty()) {
                logger.debug(String.format("<CheckProfiles> Found profile configurations in %s", pomFile));
                Iterator var7 = model.getProfiles().iterator();

                while(var7.hasNext()) {
                    Profile profile = (Profile)var7.next();
                    logger.debug(String.format("<CheckProfiles> Processing profile with id %s in %s", profile.getId(), pomFile));
                    if (profile.getBuild() != null) {
                        logger.debug(String.format("<CheckProfiles> Checking Surefire plugin configuration for Plugins in %s", pomFile));
                        boolean addedToSurefire = this.adder.addAgentToBuildPlugins(profile.getBuild(), "org.apache.maven.plugins:maven-surefire-plugin", agentPath, false);
                        PluginManagement pluginManagement = profile.getBuild().getPluginManagement();
                        if (pluginManagement != null) {
                            logger.debug(String.format("<CheckProfiles> Found Plugin Management configuration in %s", pomFile));
                            logger.debug(String.format("<CheckProfiles> Checking Surefire plugin configuration for Plugin Management in %s", pomFile));
                            addedToSurefire = this.adder.addAgentToPluginManagement(pluginManagement, "org.apache.maven.plugins:maven-surefire-plugin", agentPath, false);
                        }

                        if (addedToSurefire) {
                            logger.info(String.format("<CheckProfiles> Added Thundra Agent configuration to Surefire plugin in %s", pomFile));
                            MavenXpp3Writer mavenWriter = new MavenXpp3Writer();
                            mavenWriter.write(new FileOutputStream(new File(pomFile)), model);
                            this.instrumented.set(true);
                        } else {
                            logger.info(String.format("<CheckProfiles> Couldn't find any Surefire configuration in %s", pomFile));
                        }
                    } else {
                        logger.warn(String.format("<CheckProfiles> Couldn't find any build data in profile %s", profile.getId(), pomFile));
                    }
                }
            } else {
                logger.warn(String.format("<CheckProfiles> Couldn't find any profile in %s", pomFile));
            }

        } catch (XmlPullParserException | IOException var12) {
            logger.error(String.format("<CheckProfiles> Something went wrong while processing %s", pomFile));
            throw new RuntimeException(var12);
        }
    }

    public void checkPom(Logger logger, MavenXpp3Reader mavenReader, String agentPath, String pomFile, Boolean addIfMissing) {
        try {
            Model model = mavenReader.read(new InputStreamReader(new FileInputStream(pomFile), StandardCharsets.UTF_8));
            if (addIfMissing && model.getBuild() == null) {
                logger.warn(String.format("<CheckPom> Couldn't find any build data in %s", pomFile));
                logger.warn(String.format("<CheckPom> Setting a new build tag for %s before the instrumentation", pomFile));
                MavenXpp3Writer mavenWriter = new MavenXpp3Writer();
                Build build = new Build();
                model.setBuild(build);
                try(FileOutputStream fos = new FileOutputStream(pomFile)){
                    mavenWriter.write(fos, model);
                }
                logger.info(String.format("<CheckPom> Added build tag in %s successfully", pomFile));
            }

            if (model.getBuild() != null) {
                logger.debug(String.format("<CheckPom> Checking Surefire plugin configuration for Plugins in %s", pomFile));
                boolean addedToSurefire = this.adder.addAgentToBuildPlugins(model.getBuild(), "org.apache.maven.plugins:maven-surefire-plugin", agentPath, addIfMissing);
                PluginManagement pluginManagement = model.getBuild().getPluginManagement();
                if (pluginManagement != null) {
                    logger.debug(String.format("<CheckPom> Found Plugin Management configuration in %s", pomFile));
                    logger.debug(String.format("<CheckPom> Checking Surefire plugin configuration for Plugin Management in %s", pomFile));
                    addedToSurefire = this.adder.addAgentToPluginManagement(pluginManagement, "org.apache.maven.plugins:maven-surefire-plugin", agentPath, addIfMissing);
                }

                if (addedToSurefire) {
                    logger.info(String.format("<CheckPom> Added Thundra Agent configuration to Surefire plugin in %s", pomFile));
                    MavenXpp3Writer mavenWriter = new MavenXpp3Writer();
                    try(FileOutputStream fos = new FileOutputStream(pomFile)){
                        mavenWriter.write(fos, model);
                    }
                    this.instrumented.set(true);
                } else {
                    logger.info(String.format("<CheckPom> Couldn't find any Surefire configuration in %s", pomFile));
                }
            } else {
                logger.warn(String.format("<CheckPom> Couldn't find any build data in %s", pomFile));
            }

        } catch (XmlPullParserException | IOException var10) {
            logger.error(String.format("<CheckPom> Something went wrong while processing %s", pomFile));
            throw new RuntimeException(var10);
        }
    }
}
