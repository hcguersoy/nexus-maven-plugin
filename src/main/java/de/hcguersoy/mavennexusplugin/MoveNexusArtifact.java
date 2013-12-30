/*
 * Copyright (c) 2013 Halil-Cem Gürsoy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.hcguersoy.mavennexusplugin;

import static org.twdata.maven.mojoexecutor.MojoExecutor.artifactId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;
import static org.twdata.maven.mojoexecutor.MojoExecutor.groupId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.name;
import static org.twdata.maven.mojoexecutor.MojoExecutor.plugin;
import static org.twdata.maven.mojoexecutor.MojoExecutor.version;

import java.io.File;
import java.util.Map;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Server;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.Base64;

/**
 * This plugin moves an artifact in Nexus OSS from one repository to another repository. For this,
 * it uses the Nexus REsT API.
 * 
 * The dependency and deployment plugins are needed to download an artifact from the origin
 * repository and to upload it into the target repository.
 * 
 * More details will come.
 * 
 * @author H.-C. Guersoy
 * 
 * 
 */
@Mojo(name = "move")
public class MoveNexusArtifact extends AbstractMojo {

	@Component
	private MavenProject mavenProject;

	@Component
	private MavenSession mavenSession;

	@Component
	private BuildPluginManager pluginManager;

	/**
	 * The group ID of the artifact to be moved.
	 */
	@Parameter(required = true)
	private String groupId;

	/**
	 * The artifact ID of the artifact to be moved.
	 */
	@Parameter(required = true)
	private String artifactId;

	/**
	 * The Version of the artifact to be moved.
	 */
	@Parameter(required = true)
	private String version;

	/**
	 * The packaging of the artifact to be moved. Be aware that we allways will move the according
	 * POM, too.
	 */
	@Parameter(required = true)
	private String packaging;

	/**
	 * The Root URL of the Staging Repository Server , e.g. ${nexusURL}:${nexusPort}
	 */
	@Parameter(required = true)
	private String stagingNexus;

	/**
	 * The Root URL of the target Repository Server , e.g. ${nexusURL}:${nexusPort}. This parameter
	 * is optional, if not provided we'll take the same URL as for stagingNexus.
	 */
	@Parameter(required = false)
	private String targetNexus;

	/**
	 * The ID of the target repository.
	 */
	@Parameter(required = true)
	private String targetRepoID;

	/**
	 * The ID of the staging repository.
	 */
	@Parameter(required = true)
	private String stagingRepoID;

	/**
	 * Skip getArtifacts step? Defaults to false.
	 */
	@Parameter(required = false, defaultValue = DONT_SKIP_STEP)
	private String skipGetArtifacts;

	/**
	 * Skip deployArtifacts step? Defaults to false.
	 */
	@Parameter(required = false, defaultValue = DONT_SKIP_STEP)
	private String skipDeployArtifacts;

	/**
	 * Skip deleteArtifacts step? Defaults to false.
	 */
	@Parameter(required = false, defaultValue = DONT_SKIP_STEP)
	private String skipDeleteArtifacts;

	/**
	 * The path to the temporary saved artifact. This is needed during the "move" of the artifact.
	 */
	private String tempArtifact;

	/**
	 * Same as tempArtifact.
	 */
	private String tempPom;

	/**
	 * The maven-dependency-plugin Version.
	 */
	private String dependencyPluginVersion = "";

	/**
	 * The maven-deploy-plugin Version. Needed to upload the artifact into the target repository.
	 */
	private String deployPluginVersion = "";

	/**
	 * The maven-dependency-plugin ArtifactID. Needed to call it.
	 */
	private final String DEPENDENCY_PLUGIN_ID = "maven-dependency-plugin";

	/**
	 * The maven-deploy-plugin ArtifactID.
	 * 
	 */
	private final String DEPLOY_PLUGIN_ID = "maven-deploy-plugin";

	/**
	 * Hard coded path to the nexus repositorys.
	 */
	private final String NEXUS_CONTENT_PATH = "/nexus/content/repositories/";

	/**
	 * The URL path delimiter.
	 */
	private final String DELI = "/";

	/**
	 * Nexus returns status code 204 then deleted successful via REsT API.
	 */
	private static final int NEXUS_DELETE_SUCESS = 204;

	private static final String ORG_APACHE_MAVEN_PLUGINS = "org.apache.maven.plugins";

	private static final String DONT_SKIP_STEP = "false";

	/**
	 * The mojo execution.
	 * 
	 * @see org.apache.maven.plugin.Mojo#execute()
	 */
	public void execute() throws MojoExecutionException, MojoFailureException {

		//targetNexus is optional
		if (targetNexus == null) {
			targetNexus = stagingNexus;
		}

		//find the dependency plugin and retrieve the
		Map<String, Plugin> plugins = mavenProject.getPluginManagement().getPluginsAsMap();
		for (String key : plugins.keySet()) {
			getLog().debug("Plugins found: [" + key + ":" + plugins.get(key).getVersion() + "]");
		}

		//get the versions of the needed plugins as defined in the POM (or superpom)
		dependencyPluginVersion = plugins.get(ORG_APACHE_MAVEN_PLUGINS + ":" + DEPENDENCY_PLUGIN_ID).getVersion();
		deployPluginVersion = plugins.get(ORG_APACHE_MAVEN_PLUGINS + ":" + DEPLOY_PLUGIN_ID).getVersion();

		tempArtifact = mavenProject.getBuild().getDirectory() + File.separator + artifactId + "-" + version + "." + packaging;
		tempPom = mavenProject.getBuild().getDirectory() + File.separator + artifactId + "-" + version + ".pom";

		if (skipGetArtifacts.equals(DONT_SKIP_STEP)) {
			getArtifacts();
		} else {
			getLog().debug("Skipping getArtifacts");
		}
		;
		if (skipDeployArtifacts.equals(DONT_SKIP_STEP)) {
			deployArtifact();
		} else {
			getLog().debug("Skipping deployArtifact");
		}
		;
		if (skipDeleteArtifacts.equals(DONT_SKIP_STEP)) {
			deleteArtifact();
		} else {
			getLog().debug("Skipping deleteArtifact");
		}
		;
	}

	// private methods to do the job

	/**
	 * This method fetches the artifact using the maven dependency plugin. To call this plugin, we
	 * use the 'mojo-executor' plugin (see http://timmoore.github.io/mojo-executor/).
	 * 
	 * @throws MojoExecutionException
	 * @throws MojoFailureException
	 */
	private void getArtifacts() throws MojoExecutionException, MojoFailureException {

		getLog().debug("Starting with retrieving the artefacts to copy.");

		//get the artifact itself
		executeMojo(
				plugin(
						groupId(ORG_APACHE_MAVEN_PLUGINS),
						artifactId(DEPENDENCY_PLUGIN_ID),
						version(dependencyPluginVersion)
				),
				goal("get"),
				configuration(
						element(name("groupId"), groupId),
						element(name("artifactId"), artifactId),
						element(name("version"), version),
						element(name("packaging"), packaging),
						element(name("destination"), tempArtifact),
						element(name("transitive"), "false")
				),
				executionEnvironment(
						mavenProject,
						mavenSession,
						pluginManager
				));

		//get the corresponding POM
		executeMojo(
				plugin(
						groupId(ORG_APACHE_MAVEN_PLUGINS),
						artifactId(DEPENDENCY_PLUGIN_ID),
						version(dependencyPluginVersion)
				),
				goal("get"),
				configuration(
						element(name("groupId"), groupId),
						element(name("artifactId"), artifactId),
						element(name("version"), version),
						element(name("packaging"), "pom"),
						element(name("destination"), tempPom),
						element(name("transitive"), "false")
				),
				executionEnvironment(
						mavenProject,
						mavenSession,
						pluginManager
				));
		getLog().debug("...done retrieving the artifacts.");
	} // getArtifacts

	/**
	 * Deploys the artifact and its POM in the target repository. Here we use the maven deployment
	 * plugin.
	 * 
	 * @throws MojoExecutionException
	 * @throws MojoFailureException
	 */
	private void deployArtifact() throws MojoExecutionException, MojoFailureException {
		getLog().debug("Starting with deploying the artifact " + artifactId + " into repository");

		//deploy the artifact in the target repo
		executeMojo(
				plugin(
						groupId(ORG_APACHE_MAVEN_PLUGINS),
						artifactId(DEPLOY_PLUGIN_ID),
						version(deployPluginVersion)
				),
				goal("deploy-file"),
				configuration(
						element(name("file"), tempArtifact),
						element(name("repositoryId"), targetRepoID),
						element(name("url"), targetNexus + NEXUS_CONTENT_PATH + targetRepoID),
						element(name("pomFile"), tempPom)
				),
				executionEnvironment(
						mavenProject,
						mavenSession,
						pluginManager
				));
		getLog().debug("Finished with deploying the artifact " + artifactId + " into epository");

	} //deployArtifact

	/**
	 * After deploying the artifact in the target repository we've to delete it from the origin
	 * repository. This is done using the Nexus OSS REST API, simply calling a HTTP DELETE on the
	 * resource.
	 * 
	 * @throws MojoExecutionException
	 * @throws MojoFailureException
	 */
	private void deleteArtifact() throws MojoExecutionException, MojoFailureException {
		getLog().debug("Starting with deleting the artifact " + artifactId + " from repository " + stagingRepoID);

		String requestURL = targetNexus + NEXUS_CONTENT_PATH + stagingRepoID + DELI + groupId.replace(".", DELI) + DELI
				+ artifactId + DELI + version + DELI;
		getLog().debug("Request URL is: [" + requestURL + "]");

		if (mavenSession.getSettings().getServer(stagingRepoID) == null) {
			getLog().debug("Dont found credentials for " + stagingRepoID + " but found those:");
			for (Server server : mavenSession.getSettings().getServers()) {
				getLog().debug("######## SERVER ########");
				getLog().debug("Id: " + server.getId());
				getLog().debug("User: " + server.getUsername());
			}
			getLog().debug("Please verify your configuration and settinfs.xml");
			throw new MojoExecutionException("Not found server in settings.xml with id " + stagingRepoID);
		}

		String user = mavenSession.getSettings().getServer(stagingRepoID).getUsername();
		String password = mavenSession.getSettings().getServer(stagingRepoID).getPassword();
		String auth = new String(Base64.encode(user + ":" + password));

		Client client = Client.create();
		WebResource webResource = client.resource(requestURL);
		ClientResponse response = webResource.header("Authorization", "Basic " + auth).type("application/json")
				.accept("application/json").delete(ClientResponse.class);

		int statusCode = response.getStatus();

		getLog().debug("Status code is: " + statusCode);

		if (statusCode == 401) {
			throw new MojoExecutionException("Invalid Username or Password while accessing target repository.");
		} else if (statusCode != NEXUS_DELETE_SUCESS) {
			throw new MojoExecutionException("The artifact is not deleted - status code is: " + statusCode);
		}
		getLog().debug("Successfully deleted artifact " + artifactId + " from repository " + stagingRepoID);
	} //delete artifact
}
